# Utility functions for baseball data processing

#' Get starting pitchers for a specific game
#' @param game_pk MLB game primary key
#' @param game_date Date of the game
get_starting_pitchers <- function(game_pk, game_date) {
  tryCatch({
    # Get play-by-play data which contains reliable pitcher information
    pbp <- mlb_pbp(game_pk)
    
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
  cat(sprintf("    Getting pitcher stats for: %s as of %s\n", pitcher_name, as_of_date))
  
  if (is.na(pitcher_name) || pitcher_name == "TBD" || pitcher_name == "") {
    cat("    Skipping pitcher stats - invalid name\n")
    return(list(name = pitcher_name, era = NA, whip = NA, k = NA, bb = NA, ip = NA))
  }
  
  tryCatch({
    cat("    Calling bref_daily_pitcher...\n")
    cat(sprintf("    Using date range: t1='2024-03-28', t2='%s'\n", as_of_date))
    
    # Ensure date format is correct
    t1_date <- as.Date("2024-03-28")
    t2_date <- as.Date(as_of_date)
    
    if (t2_date < t1_date) {
      cat("    Warning: End date is before start date, using start date for both\n")
      t2_date <- t1_date
    }
    
    # Get pitcher stats using bref_daily_pitcher
    pitcher_data <- bref_daily_pitcher(
      t1 = format(t1_date, "%Y-%m-%d"),  # Season start
      t2 = format(t2_date, "%Y-%m-%d")
    )
    
    cat(sprintf("    Retrieved %d pitcher records\n", nrow(pitcher_data)))
    
    if (nrow(pitcher_data) > 0) {
      cat("    Available pitchers in data:\n")
      unique_names <- unique(pitcher_data$Name)[1:min(5, length(unique(pitcher_data$Name)))]
      cat(paste("     ", unique_names, collapse = "\n"))
      cat("\n")
      
      # Filter for specific pitcher
      pitcher_filtered <- pitcher_data %>%
        filter(Name == pitcher_name) %>%
        slice_tail(n = 1)  # Get most recent entry
      
      cat(sprintf("    Found %d records for pitcher: %s\n", nrow(pitcher_filtered), pitcher_name))
    }
    
    if (nrow(pitcher_data) > 0 && nrow(pitcher_filtered) > 0) {
      cat("    Successfully retrieved pitcher stats\n")
      list(
        name = pitcher_filtered$Name,
        era = pitcher_filtered$ERA,
        whip = pitcher_filtered$WHIP,
        k = pitcher_filtered$SO,
        bb = pitcher_filtered$BB,
        ip = pitcher_filtered$IP
      )
    } else {
      cat("    No pitcher data found - using NA values\n")
      list(name = pitcher_name, era = NA, whip = NA, k = NA, bb = NA, ip = NA)
    }
  }, error = function(e) {
    cat(sprintf("    Error in pitcher stats: %s\n", e$message))
    list(name = pitcher_name, era = NA, whip = NA, k = NA, bb = NA, ip = NA)
  })
}

#' Get team statistics as of a specific date
#' @param team_name Full team name (e.g., "Chicago White Sox")
#' @param as_of_date Date to get stats through
get_team_stats <- function(team_name, as_of_date) {
  cat(sprintf("    Getting team stats for: %s as of %s\n", team_name, as_of_date))
  
  # Map full team names to the city names used in Baseball Reference data
  team_name_map <- list(
    "Atlanta Braves" = "Atlanta",
    "Baltimore Orioles" = "Baltimore", 
    "Boston Red Sox" = "Boston",
    "Chicago Cubs" = "Chicago",
    "Chicago White Sox" = "Chicago",
    "Cincinnati Reds" = "Cincinnati",
    "Cleveland Guardians" = "Cleveland",
    "Colorado Rockies" = "Colorado",
    "Detroit Tigers" = "Detroit",
    "Houston Astros" = "Houston",
    "Kansas City Royals" = "Kansas City",
    "Los Angeles Angels" = "Los Angeles",
    "Los Angeles Dodgers" = "Los Angeles", 
    "Miami Marlins" = "Miami",
    "Milwaukee Brewers" = "Milwaukee",
    "Minnesota Twins" = "Minnesota",
    "New York Mets" = "New York",
    "New York Yankees" = "New York",
    "Oakland Athletics" = "Oakland",
    "Philadelphia Phillies" = "Philadelphia",
    "Pittsburgh Pirates" = "Pittsburgh",
    "San Diego Padres" = "San Diego",
    "San Francisco Giants" = "San Francisco",
    "Seattle Mariners" = "Seattle",
    "St. Louis Cardinals" = "St. Louis",
    "Tampa Bay Rays" = "Tampa Bay",
    "Texas Rangers" = "Texas",
    "Toronto Blue Jays" = "Toronto",
    "Washington Nationals" = "Washington"
  )
  
  # Get the Baseball Reference team name
  bref_team_name <- team_name_map[[team_name]]
  if (is.null(bref_team_name)) {
    bref_team_name <- team_name  # fallback to original name
    cat(sprintf("    Warning: No mapping found for '%s', using original name\n", team_name))
  } else {
    cat(sprintf("    Mapped '%s' to Baseball Reference name '%s'\n", team_name, bref_team_name))
  }
  
  tryCatch({
    # Get season year from date
    season_year <- year(as.Date(as_of_date))
    
    # Ensure date format is correct for FanGraphs
    t1_date <- as.Date("2024-03-28")  # Season start
    t2_date <- as.Date(as_of_date)
    
    cat(sprintf("    Date check: t1=%s, t2=%s, season=%d\n", 
                t1_date, t2_date, season_year))
    
    if (t2_date < t1_date) {
      cat("    Warning: End date is before start date, using start date for both\n")
      t2_date <- t1_date
    }
    
    # Get team batting stats from FanGraphs
    cat("    Getting batting stats from FanGraphs...\n")
    fg_batting <- fg_team_batter(
      startseason = season_year,
      endseason = season_year,
      startdate = format(t1_date, "%Y-%m-%d"),
      enddate = format(t2_date, "%Y-%m-%d")
    )
    
    cat(sprintf("    Retrieved FanGraphs batting data: %d teams\n", nrow(fg_batting)))
    
    # Map team names to FanGraphs abbreviations (based on the available teams shown above)
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
    
    # Get FanGraphs team abbreviation using full team name
    fg_team_name <- fg_team_map[[team_name]]
    if (is.null(fg_team_name)) {
      fg_team_name <- bref_team_name  # fallback
      cat(sprintf("    Warning: No FanGraphs mapping found for '%s', using '%s'\n", team_name, bref_team_name))
    }
    
    cat(sprintf("    Looking for FanGraphs team: %s\n", fg_team_name))
    
    # Extract team batting stats
    if (nrow(fg_batting) > 0) {
      cat("    Available teams in FanGraphs batting data:\n")
      cat(paste("     ", unique(fg_batting$team_name), collapse = "\n"))
      cat("\n")
      
      team_batting <- fg_batting %>%
        filter(team_name == fg_team_name) %>%
        slice(1)  # Take first row if multiple
      
      if (nrow(team_batting) > 0) {
        team_ops <- team_batting$OPS
        team_woba <- team_batting$wOBA
        cat(sprintf("    Found batting stats for %s: OPS=%.3f, wOBA=%.3f\n", 
                    fg_team_name, team_ops, team_woba))
      } else {
        team_ops <- NA
        team_woba <- NA
        cat(sprintf("    No batting stats found for team %s\n", fg_team_name))
      }
    } else {
      team_ops <- NA
      team_woba <- NA
      cat("    No FanGraphs batting data available\n")
    }
    
    # Get team pitching stats from FanGraphs
    cat("    Getting pitching stats from FanGraphs...\n")
    fg_pitching <- fg_team_pitcher(
      startseason = season_year,
      endseason = season_year,
      startdate = format(t1_date, "%Y-%m-%d"),
      enddate = format(t2_date, "%Y-%m-%d")
    )
    
    cat(sprintf("    Retrieved FanGraphs pitching data: %d teams\n", nrow(fg_pitching)))
    
    # Extract team pitching stats
    if (nrow(fg_pitching) > 0) {
      team_pitching <- fg_pitching %>%
        filter(team_name == fg_team_name) %>%
        slice(1)  # Take first row if multiple
      
      if (nrow(team_pitching) > 0) {
        team_era_plus <- team_pitching$`ERA-`  # ERA- in FanGraphs (lower is better)
        team_fip <- team_pitching$FIP
        cat(sprintf("    Found pitching stats for %s: ERA-=%.1f, FIP=%.2f\n", 
                    fg_team_name, team_era_plus, team_fip))
      } else {
        team_era_plus <- NA
        team_fip <- NA
        cat(sprintf("    No pitching stats found for team %s\n", fg_team_name))
      }
    } else {
      team_era_plus <- NA
      team_fip <- NA
      cat("    No FanGraphs pitching data available\n")
    }
    
    # Combine stats
    result <- list(
      ops = team_ops,
      woba = team_woba,
      era_plus = team_era_plus,  # Note: This is actually ERA- from FanGraphs
      fip = team_fip
    )
    
    cat("    Team stats retrieved successfully from FanGraphs\n")
    return(result)
    
  }, error = function(e) {
    cat(sprintf("    Error in team stats: %s\n", e$message))
    list(ops = NA, woba = NA, era_plus = NA, fip = NA)
  })
}

#' Format collected data into final output structure
#' @param game_data Raw game data with nested stats
format_output_data <- function(game_data) {
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
      away_team_stats,
      weather
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
      
      # Weather data
      Temperature = map_dbl(weather, ~.x$temperature %||% NA),
      WindSpeed = map_dbl(weather, ~.x$wind_speed %||% NA),
      WindDirection = map_chr(weather, ~.x$wind_direction %||% NA),
      Precipitation = map_dbl(weather, ~.x$precipitation %||% NA),
      
      # Home pitcher stats
      HomePitcherName = map_chr(home_pitcher_stats, ~.x$name %||% NA),
      HomePitcherERA = map_dbl(home_pitcher_stats, ~.x$era %||% NA),
      HomePitcherWHIP = map_dbl(home_pitcher_stats, ~.x$whip %||% NA),
      HomePitcherK = map_dbl(home_pitcher_stats, ~.x$k %||% NA),
      HomePitcherBB = map_dbl(home_pitcher_stats, ~.x$bb %||% NA),
      HomePitcherIP = map_dbl(home_pitcher_stats, ~.x$ip %||% NA),
      
      # Away pitcher stats  
      AwayPitcherName = map_chr(away_pitcher_stats, ~.x$name %||% NA),
      AwayPitcherERA = map_dbl(away_pitcher_stats, ~.x$era %||% NA),
      AwayPitcherWHIP = map_dbl(away_pitcher_stats, ~.x$whip %||% NA),
      AwayPitcherK = map_dbl(away_pitcher_stats, ~.x$k %||% NA),
      AwayPitcherBB = map_dbl(away_pitcher_stats, ~.x$bb %||% NA),
      AwayPitcherIP = map_dbl(away_pitcher_stats, ~.x$ip %||% NA),
      
      # Team stats
      HomeOPS = map_dbl(home_team_stats, ~.x$ops %||% NA),
      AwayOPS = map_dbl(away_team_stats, ~.x$ops %||% NA),
      HomeWOBA = map_dbl(home_team_stats, ~.x$woba %||% NA),
      AwayWOBA = map_dbl(away_team_stats, ~.x$woba %||% NA),
      HomeERAPlus = map_dbl(home_team_stats, ~.x$era_plus %||% NA),
      AwayERAPlus = map_dbl(away_team_stats, ~.x$era_plus %||% NA),
      HomeFIP = map_dbl(home_team_stats, ~.x$fip %||% NA),
      AwayFIP = map_dbl(away_team_stats, ~.x$fip %||% NA)
    ) %>%
    select(
      GameId, Date, HomeTeam, AwayTeam, HomeScore, AwayScore,
      Temperature, WindSpeed, WindDirection, Precipitation,
      HomePitcherName, HomePitcherERA, HomePitcherWHIP, HomePitcherK, HomePitcherBB, HomePitcherIP,
      AwayPitcherName, AwayPitcherERA, AwayPitcherWHIP, AwayPitcherK, AwayPitcherBB, AwayPitcherIP,
      HomeOPS, AwayOPS, HomeWOBA, AwayWOBA, HomeERAPlus, AwayERAPlus, HomeFIP, AwayFIP
    )
}