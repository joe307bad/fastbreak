# Utility functions for baseball data processing

#' Get starting pitchers for a specific game
#' @param game_pk MLB game primary key
#' @param game_date Date of the game
get_starting_pitchers <- function(game_pk, game_date) {
  tryCatch({
    # Get game info to find starting pitchers
    game_info <- mlb_game_info(game_pk)
    
    # Extract starting pitcher info (this is a simplified version)
    # In practice, you'd need to parse the game data to find actual starters
    list(
      home_pitcher = "TBD",  # Would extract from game data
      away_pitcher = "TBD"
    )
  }, error = function(e) {
    list(home_pitcher = NA, away_pitcher = NA)
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
#' @param team_abbrev Team abbreviation (e.g., "LAD")
#' @param as_of_date Date to get stats through
get_team_stats <- function(team_abbrev, as_of_date) {
  cat(sprintf("    Getting team stats for: %s as of %s\n", team_abbrev, as_of_date))
  
  # Convert full team name to abbreviation if needed
  team_code <- team_abbrev
  if (team_abbrev %in% names(MLB_TEAMS)) {
    team_code <- MLB_TEAMS[[team_abbrev]]
    cat(sprintf("    Converted team name '%s' to abbreviation '%s'\n", team_abbrev, team_code))
  }
  
  tryCatch({
    cat("    Getting batting stats...\n")
    cat(sprintf("    Calling bref_daily_batter with t1='2024-03-28', t2='%s'\n", as_of_date))
    
    # Ensure date format is correct
    t1_date <- as.Date("2024-03-28")
    t2_date <- as.Date(as_of_date)
    
    cat(sprintf("    Date check: t1=%s, t2=%s, t2 >= t1: %s\n", 
                t1_date, t2_date, t2_date >= t1_date))
    
    if (t2_date < t1_date) {
      cat("    Warning: End date is before start date, using start date for both\n")
      t2_date <- t1_date
    }
    
    # Get team batting stats
    batting_data <- bref_daily_batter(
      t1 = format(t1_date, "%Y-%m-%d"),
      t2 = format(t2_date, "%Y-%m-%d")
    )
    
    cat(sprintf("    Retrieved %d batter records\n", nrow(batting_data)))
    
    if (nrow(batting_data) > 0) {
      unique_teams <- unique(batting_data$Tm)[1:min(10, length(unique(batting_data$Tm)))]
      cat("    Available teams in batting data:\n")
      cat(paste("     ", unique_teams, collapse = "\n"))
      cat("\n")
      
      team_batting <- batting_data %>%
        filter(Tm == team_code) %>%
        summarise(
          team_ops = weighted.mean(OPS, AB, na.rm = TRUE),
          team_woba = weighted.mean(wOBA, PA, na.rm = TRUE),
          .groups = 'drop'
        )
      cat(sprintf("    Found %d batting records for team %s\n", 
                  sum(batting_data$Tm == team_code, na.rm = TRUE), team_code))
    } else {
      team_batting <- data.frame(team_ops = NA, team_woba = NA)
    }
    
    cat("    Getting pitching stats...\n")
    cat(sprintf("    Calling bref_daily_pitcher with t1='2024-03-28', t2='%s'\n", as_of_date))
    
    # Get team pitching stats
    pitching_data <- bref_daily_pitcher(
      t1 = format(t1_date, "%Y-%m-%d"), 
      t2 = format(t2_date, "%Y-%m-%d")
    )
    
    cat(sprintf("    Retrieved %d pitcher records\n", nrow(pitching_data)))
    
    if (nrow(pitching_data) > 0) {
      team_pitching <- pitching_data %>%
        filter(Tm == team_code) %>%
        summarise(
          team_era_plus = weighted.mean(ERA_plus, IP, na.rm = TRUE),
          team_fip = weighted.mean(FIP, IP, na.rm = TRUE),
          .groups = 'drop'
        )
      cat(sprintf("    Found %d pitching records for team %s\n", 
                  sum(pitching_data$Tm == team_code, na.rm = TRUE), team_code))
    } else {
      team_pitching <- data.frame(team_era_plus = NA, team_fip = NA)
    }
    
    # Combine stats
    result <- list(
      ops = ifelse(nrow(team_batting) > 0, team_batting$team_ops, NA),
      woba = ifelse(nrow(team_batting) > 0, team_batting$team_woba, NA), 
      era_plus = ifelse(nrow(team_pitching) > 0, team_pitching$team_era_plus, NA),
      fip = ifelse(nrow(team_pitching) > 0, team_pitching$team_fip, NA)
    )
    
    cat("    Team stats retrieved successfully\n")
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