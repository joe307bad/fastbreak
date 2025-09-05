# Utility functions for baseball data processing

#' Rate limiting delay for API calls
#' @param min_seconds Minimum seconds to wait (default: 0)
#' @param max_seconds Maximum seconds to wait (default: 5)
rate_limit_delay <- function(min_seconds = 0, max_seconds = 5) {
  delay_seconds <- runif(1, min_seconds, max_seconds)
  cat(sprintf("Waiting %.1f seconds...\n", delay_seconds))
  Sys.sleep(delay_seconds)
}

#' Get starting pitchers for a specific game
#' @param game_pk MLB game primary key
#' @param game_date Date of the game
get_starting_pitchers <- function(game_pk, game_date) {
  tryCatch({
    # Get play-by-play data which contains reliable pitcher information
    cat("Loading pitcher stats...\n")
    pbp <- mlb_pbp(game_pk)
    cat("Pitcher stats resolved from MLB, status code 200\n")
    
    # Rate limiting delay
    rate_limit_delay()
    
    if (is.null(pbp) || nrow(pbp) == 0) {
      stop(sprintf("No play-by-play data available for game %s", game_pk))
    }
    
    # Get starting pitchers from first inning
    # Home starter pitches when away team bats (top of 1st inning)
    # Away starter pitches when home team bats (bottom of 1st inning)
    
    home_starter <- pbp %>%
      filter(about.inning == 1, about.halfInning == "top", !is.na(matchup.pitcher.fullName)) %>%
      slice(1) %>%
      pull(matchup.pitcher.fullName)
    
    away_starter <- pbp %>%
      filter(about.inning == 1, about.halfInning == "bottom", !is.na(matchup.pitcher.fullName)) %>%
      slice(1) %>%
      pull(matchup.pitcher.fullName)
    
    # Validate that we found both pitchers
    if (length(home_starter) == 0 || is.na(home_starter)) {
      stop(sprintf("Could not find home starting pitcher for game %s", game_pk))
    }
    
    if (length(away_starter) == 0 || is.na(away_starter)) {
      stop(sprintf("Could not find away starting pitcher for game %s", game_pk))
    }
    
    list(
      home_pitcher = home_starter,
      away_pitcher = away_starter
    )
  }, error = function(e) {
    # Re-throw the error to fail the script
    stop(sprintf("Failed to get starting pitchers for game %s: %s", game_pk, e$message))
  })
}

#' Get pitcher statistics as of a specific date
#' @param pitcher_name Name of the pitcher
#' @param as_of_date Date to get stats through
get_pitcher_stats <- function(pitcher_name, as_of_date) {
  if (is.na(pitcher_name) || pitcher_name == "TBD" || pitcher_name == "") {
    return(list(name = pitcher_name, era = NA, whip = NA, k = NA, bb = NA, ip = NA))
  }
  
  tryCatch({
    # Ensure date format is correct
    t1_date <- as.Date("2024-03-28")
    t2_date <- as.Date(as_of_date)
    
    if (t2_date < t1_date) {
      t2_date <- t1_date
    }
    
    # Get pitcher stats using bref_daily_pitcher
    cat("Loading pitcher stats...\n")
    cat(sprintf("Date range: %s to %s\n", format(t1_date, "%Y-%m-%d"), format(t2_date, "%Y-%m-%d")))
    
    pitcher_data <- tryCatch({
      # Suppress console output from baseballr package
      capture.output({
        result <- bref_daily_pitcher(
          t1 = format(t1_date, "%Y-%m-%d"),  # Season start
          t2 = format(t2_date, "%Y-%m-%d")
        )
      }, type = "message")
      result
    }, warning = function(w) {
      # cat("Warning from bref_daily_pitcher:", w$message, "\n")
      data.frame()  # Return empty data frame on warning
    }, error = function(e) {
      # cat("Error from bref_daily_pitcher:", e$message, "\n")
      data.frame()  # Return empty data frame on error
    })
    
    cat(sprintf("Pitcher stats resolved from Baseball Reference, got %d rows\n", nrow(pitcher_data)))
    
    # Rate limiting delay
    rate_limit_delay()
    
    pitcher_filtered <- NULL
    if (nrow(pitcher_data) > 0) {
      cat(sprintf("Looking for pitcher: '%s'\n", pitcher_name))
      # cat(sprintf("Available pitchers: %s\n", paste(head(pitcher_data$Name, 5), collapse = ", ")))
      
      # Filter for specific pitcher
      pitcher_filtered <- pitcher_data %>%
        filter(Name == pitcher_name) %>%
        slice_tail(n = 1)  # Get most recent entry
        
      # cat(sprintf("Found %d matches for pitcher '%s'\n", nrow(pitcher_filtered), pitcher_name))
    }
    
    if (!is.null(pitcher_filtered) && nrow(pitcher_filtered) > 0) {
      list(
        name = pitcher_filtered$Name,
        era = pitcher_filtered$ERA,
        whip = pitcher_filtered$WHIP,
        k = pitcher_filtered$SO,
        bb = pitcher_filtered$BB,
        ip = pitcher_filtered$IP
      )
    } else {
      list(name = pitcher_name, era = NA, whip = NA, k = NA, bb = NA, ip = NA)
    }
  }, error = function(e) {
    list(name = pitcher_name, era = NA, whip = NA, k = NA, bb = NA, ip = NA)
  })
}

#' Get team statistics as of a specific date
#' @param team_name Full team name (e.g., "Chicago White Sox")
#' @param as_of_date Date to get stats through
get_team_stats <- function(team_name, as_of_date) {
  # Map team names to FanGraphs abbreviations
  fg_team_map <- list(
    "Atlanta Braves" = "ATL",
    "Baltimore Orioles" = "BAL", 
    "Boston Red Sox" = "BOS",
    "Chicago Cubs" = "CHC",
    "Chicago White Sox" = "CHW",
    "Cincinnati Reds" = "CIN",
    "Cleveland Guardians" = "CLE",
    "Colorado Rockies" = "COL",
    "Detroit Tigers" = "DET",
    "Houston Astros" = "HOU",
    "Kansas City Royals" = "KCR",
    "Los Angeles Angels" = "LAA",
    "Los Angeles Dodgers" = "LAD", 
    "Miami Marlins" = "MIA",
    "Milwaukee Brewers" = "MIL",
    "Minnesota Twins" = "MIN",
    "New York Mets" = "NYM",
    "New York Yankees" = "NYY",
    "Oakland Athletics" = "OAK",
    "Philadelphia Phillies" = "PHI",
    "Pittsburgh Pirates" = "PIT",
    "San Diego Padres" = "SDP",
    "San Francisco Giants" = "SFG",
    "Seattle Mariners" = "SEA",
    "St. Louis Cardinals" = "STL",
    "Tampa Bay Rays" = "TBR",
    "Texas Rangers" = "TEX",
    "Toronto Blue Jays" = "TOR",
    "Washington Nationals" = "WSN"
  )
  
  fg_team_name <- fg_team_map[[team_name]]
  if (is.null(fg_team_name)) {
    fg_team_name <- team_name  # fallback
  }
  
  tryCatch({
    # Get season year from date
    season_year <- year(as.Date(as_of_date))
    
    # Ensure date format is correct for FanGraphs
    t1_date <- as.Date("2024-03-28")  # Season start
    t2_date <- as.Date(as_of_date)
    
    if (t2_date < t1_date) {
      t2_date <- t1_date
    }
    
    # Get team batting stats from FanGraphs
    cat("Loading team batting stats...\n")
    fg_batting <- fg_team_batter(
      startseason = season_year,
      endseason = season_year,
      startdate = format(t1_date, "%Y-%m-%d"),
      enddate = format(t2_date, "%Y-%m-%d")
    )
    cat("Team batting stats resolved from FanGraphs, status code 200\n")
    
    # Rate limiting delay
    rate_limit_delay()
    
    # Extract team batting stats
    if (nrow(fg_batting) > 0) {
      team_batting <- fg_batting %>%
        filter(team_name == fg_team_name) %>%
        slice(1)  # Take first row if multiple
      
      if (nrow(team_batting) > 0) {
        team_ops <- team_batting$OPS
        team_woba <- team_batting$wOBA
      } else {
        team_ops <- NA
        team_woba <- NA
      }
    } else {
      team_ops <- NA
      team_woba <- NA
    }
    
    # Get team pitching stats from FanGraphs
    cat("Loading team pitching stats...\n")
    fg_pitching <- fg_team_pitcher(
      startseason = season_year,
      endseason = season_year,
      startdate = format(t1_date, "%Y-%m-%d"),
      enddate = format(t2_date, "%Y-%m-%d")
    )
    cat("Team pitching stats resolved from FanGraphs, status code 200\n")
    
    # Rate limiting delay
    rate_limit_delay()
    
    # Extract team pitching stats
    if (nrow(fg_pitching) > 0) {
      team_pitching <- fg_pitching %>%
        filter(team_name == fg_team_name) %>%
        slice(1)  # Take first row if multiple
      
      if (nrow(team_pitching) > 0) {
        team_era_plus <- team_pitching$`ERA-`  # ERA- in FanGraphs (lower is better)
        team_fip <- team_pitching$FIP
      } else {
        team_era_plus <- NA
        team_fip <- NA
      }
    } else {
      team_era_plus <- NA
      team_fip <- NA
    }
    
    # Combine stats
    list(
      ops = team_ops,
      woba = team_woba,
      era_plus = team_era_plus,  # Note: This is actually ERA- from FanGraphs
      fip = team_fip
    )
    
  }, error = function(e) {
    list(ops = NA, woba = NA, era_plus = NA, fip = NA)
  })
}

#' Format collected data into final output structure
#' @param game_data Raw game data with nested stats
format_output_data <- function(game_data) {
  # Helper function to safely extract numeric values
  safe_extract_num <- function(lst, field) {
    sapply(lst, function(x) {
      val <- x[[field]] %||% NA
      if (is.null(val) || is.na(val) || val == "null") return(NA_real_)
      as.numeric(val)
    })
  }
  
  # Helper function to safely extract character values  
  safe_extract_chr <- function(lst, field) {
    sapply(lst, function(x) {
      val <- x[[field]] %||% NA
      if (is.null(val) || is.na(val) || val == "null") return(NA_character_)
      as.character(val)
    })
  }
  
  game_data %>%
    select(
      game_pk,
      official_date,
      teams_home_team_name,
      teams_away_team_name, 
      teams_home_score,
      teams_away_score,
      home_pitcher_stats,
      away_pitcher_stats,
      home_team_stats,
      away_team_stats
    ) %>%
    mutate(
      # Create GameId in format: YYYY-MM-DD-AWAY-HOME
      GameId = paste(
        official_date,
        str_replace_all(teams_away_team_name, " ", ""),
        str_replace_all(teams_home_team_name, " ", ""),
        sep = "-"
      ),
      Date = official_date,
      HomeTeam = teams_home_team_name,
      AwayTeam = teams_away_team_name,
      HomeScore = teams_home_score,
      AwayScore = teams_away_score,
      
      # Home pitcher stats
      HomePitcherName = safe_extract_chr(home_pitcher_stats, "name"),
      HomePitcherERA = safe_extract_num(home_pitcher_stats, "era"),
      HomePitcherWHIP = safe_extract_num(home_pitcher_stats, "whip"),
      HomePitcherK = safe_extract_num(home_pitcher_stats, "k"),
      HomePitcherBB = safe_extract_num(home_pitcher_stats, "bb"),
      HomePitcherIP = safe_extract_num(home_pitcher_stats, "ip"),
      
      # Away pitcher stats  
      AwayPitcherName = safe_extract_chr(away_pitcher_stats, "name"),
      AwayPitcherERA = safe_extract_num(away_pitcher_stats, "era"),
      AwayPitcherWHIP = safe_extract_num(away_pitcher_stats, "whip"),
      AwayPitcherK = safe_extract_num(away_pitcher_stats, "k"),
      AwayPitcherBB = safe_extract_num(away_pitcher_stats, "bb"),
      AwayPitcherIP = safe_extract_num(away_pitcher_stats, "ip"),
      
      # Team stats
      HomeOPS = safe_extract_num(home_team_stats, "ops"),
      AwayOPS = safe_extract_num(away_team_stats, "ops"),
      HomeWOBA = safe_extract_num(home_team_stats, "woba"),
      AwayWOBA = safe_extract_num(away_team_stats, "woba"),
      HomeERAPlus = safe_extract_num(home_team_stats, "era_plus"),
      AwayERAPlus = safe_extract_num(away_team_stats, "era_plus"),
      HomeFIP = safe_extract_num(home_team_stats, "fip"),
      AwayFIP = safe_extract_num(away_team_stats, "fip")
    ) %>%
    select(
      GameId, Date, HomeTeam, AwayTeam, HomeScore, AwayScore,
      HomePitcherName, HomePitcherERA, HomePitcherWHIP, HomePitcherK, HomePitcherBB, HomePitcherIP,
      AwayPitcherName, AwayPitcherERA, AwayPitcherWHIP, AwayPitcherK, AwayPitcherBB, AwayPitcherIP,
      HomeOPS, AwayOPS, HomeWOBA, AwayWOBA, HomeERAPlus, AwayERAPlus, HomeFIP, AwayFIP
    )
}