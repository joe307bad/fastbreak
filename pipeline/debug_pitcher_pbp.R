# Debug script to extract starting pitchers from play-by-play data
source("config/setup.R")

game_pk <- 746817
game_date <- "2024-04-01"

cat("=== Extracting Starting Pitchers from Play-by-Play ===\n")

tryCatch({
  # Get play-by-play data
  pbp <- mlb_pbp(game_pk)
  
  if (!is.null(pbp) && nrow(pbp) > 0) {
    cat("Play-by-play data loaded. Total plays:", nrow(pbp), "\n")
    
    # Get unique pitchers by inning and half-inning to identify starters
    pitcher_summary <- pbp %>%
      filter(!is.na(matchup.pitcher.fullName)) %>%
      group_by(about.inning, about.halfInning, batting_team, fielding_team) %>%
      summarise(
        pitcher_id = first(matchup.pitcher.id),
        pitcher_name = first(matchup.pitcher.fullName),
        first_play = min(about.atBatIndex, na.rm = TRUE),
        .groups = 'drop'
      ) %>%
      arrange(about.inning, about.halfInning)
    
    cat("\nPitcher summary by inning/half:\n")
    print(pitcher_summary)
    
    # Identify starting pitchers (first pitcher for each team)
    home_team <- unique(pbp$home_team)[1]
    away_team <- unique(pbp$away_team)[1]
    
    cat(sprintf("\nHome team: %s, Away team: %s\n", home_team, away_team))
    
    # Away team bats first (top of 1st), so away starter pitches bottom 1st  
    # Home team starter pitches top 1st
    
    # Get home starting pitcher (pitches in top of first inning)
    home_starter <- pitcher_summary %>%
      filter(about.inning == 1, about.halfInning == "top") %>%
      slice(1)
    
    # Get away starting pitcher (pitches in bottom of first inning) 
    away_starter <- pitcher_summary %>%
      filter(about.inning == 1, about.halfInning == "bottom") %>%
      slice(1)
    
    cat("\nStarting Pitchers Identified:\n")
    if (nrow(home_starter) > 0) {
      cat(sprintf("Home starter: %s (ID: %s)\n", home_starter$pitcher_name, home_starter$pitcher_id))
    } else {
      cat("Home starter: NOT FOUND\n")
    }
    
    if (nrow(away_starter) > 0) {
      cat(sprintf("Away starter: %s (ID: %s)\n", away_starter$pitcher_name, away_starter$pitcher_id))
    } else {
      cat("Away starter: NOT FOUND\n")
    }
    
    # Double-check by looking at first few at-bats for each team
    cat("\nDouble-checking with first at-bats:\n")
    
    # First at-bat when away team is batting (top 1st)
    first_away_batting <- pbp %>%
      filter(about.inning == 1, about.halfInning == "top", !is.na(matchup.pitcher.fullName)) %>%
      slice(1)
    
    if (nrow(first_away_batting) > 0) {
      cat(sprintf("Pitcher when away team bats first: %s (this is home starter)\n", 
                  first_away_batting$matchup.pitcher.fullName))
    }
    
    # First at-bat when home team is batting (bottom 1st)  
    first_home_batting <- pbp %>%
      filter(about.inning == 1, about.halfInning == "bottom", !is.na(matchup.pitcher.fullName)) %>%
      slice(1)
    
    if (nrow(first_home_batting) > 0) {
      cat(sprintf("Pitcher when home team bats first: %s (this is away starter)\n", 
                  first_home_batting$matchup.pitcher.fullName))
    }
    
  } else {
    cat("No play-by-play data available\n")
  }
  
}, error = function(e) {
  cat("Error:", e$message, "\n")
})

cat("\n=== End Analysis ===\n")