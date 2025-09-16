#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(nflreadr)
  library(dplyr)
  library(tidyr)
})

# Get command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Set year (default to current year if not provided)
if (length(args) > 0) {
  year <- as.integer(args[1])
} else {
  year <- as.integer(format(Sys.Date(), "%Y"))
}

# Function to get starting QBs for a given year
get_starting_qbs <- function(season_year) {
  
  cat(paste("\nFetching starting QBs for", season_year, "season...\n\n"))
  
  tryCatch({
    # Load roster data
    rosters <- load_rosters(seasons = season_year) %>%
      filter(position == "QB")
    
    # Load starter designation from depth charts
    depth_charts <- load_depth_charts(seasons = season_year) %>%
      filter(position == "QB", depth_team == 1) %>%
      select(season, week, team = club_code, player_name = full_name, gsis_id) %>%
      distinct()
    
    # Get week 1 starters as primary starters
    week1_starters <- depth_charts %>%
      filter(week == 1) %>%
      select(team, starter_name = player_name, season) %>%
      distinct()
    
    # Load pbp data to get actual game starters
    pbp <- load_pbp(seasons = season_year) %>%
      filter(!is.na(passer_player_name), 
             quarter == 1,
             play_type == "pass") %>%
      group_by(game_id, posteam) %>%
      slice_head(n = 1) %>%
      ungroup() %>%
      select(game_id, week, team = posteam, actual_starter = passer_player_name)
    
    # Get most common starter per team
    season_starters <- pbp %>%
      group_by(team) %>%
      count(actual_starter) %>%
      slice_max(n, n = 1) %>%
      select(team, primary_starter = actual_starter, games_started = n) %>%
      ungroup()
    
    # Combine with roster info
    final_starters <- season_starters %>%
      left_join(
        rosters %>% 
          select(player_name = full_name, team = team_abbr, 
                 draft_year = entry_year, college, years_exp),
        by = c("primary_starter" = "player_name", "team" = "team")
      ) %>%
      arrange(team)
    
    # Add team full names
    teams <- load_teams() %>%
      select(team_abbr, team_name, team_conf, team_division)
    
    result <- final_starters %>%
      left_join(teams, by = c("team" = "team_abbr")) %>%
      select(
        Team = team_name,
        Team_Abbr = team,
        Conference = team_conf,
        Division = team_division,
        Starting_QB = primary_starter,
        Games_Started = games_started,
        College = college,
        Draft_Year = draft_year,
        Years_Exp = years_exp
      ) %>%
      arrange(Conference, Division, Team)
    
    return(result)
    
  }, error = function(e) {
    cat(paste("Error fetching data:", e$message, "\n"))
    
    # Fallback to manual data for recent years
    if (season_year == 2024) {
      cat("Using 2024 season manual data...\n")
      return(get_2024_manual_starters())
    } else if (season_year == 2023) {
      cat("Using 2023 season manual data...\n")
      return(get_2023_manual_starters())
    } else {
      stop("Unable to fetch data for this year")
    }
  })
}

# Manual fallback for 2024
get_2024_manual_starters <- function() {
  data.frame(
    Team = c("Arizona Cardinals", "Atlanta Falcons", "Baltimore Ravens", "Buffalo Bills",
             "Carolina Panthers", "Chicago Bears", "Cincinnati Bengals", "Cleveland Browns",
             "Dallas Cowboys", "Denver Broncos", "Detroit Lions", "Green Bay Packers",
             "Houston Texans", "Indianapolis Colts", "Jacksonville Jaguars", "Kansas City Chiefs",
             "Las Vegas Raiders", "Los Angeles Chargers", "Los Angeles Rams", "Miami Dolphins",
             "Minnesota Vikings", "New England Patriots", "New Orleans Saints", "New York Giants",
             "New York Jets", "Philadelphia Eagles", "Pittsburgh Steelers", "San Francisco 49ers",
             "Seattle Seahawks", "Tampa Bay Buccaneers", "Tennessee Titans", "Washington Commanders"),
    Starting_QB = c("Kyler Murray", "Kirk Cousins", "Lamar Jackson", "Josh Allen",
                   "Bryce Young", "Caleb Williams", "Joe Burrow", "Deshaun Watson",
                   "Dak Prescott", "Bo Nix", "Jared Goff", "Jordan Love",
                   "C.J. Stroud", "Anthony Richardson", "Trevor Lawrence", "Patrick Mahomes",
                   "Gardner Minshew", "Justin Herbert", "Matthew Stafford", "Tua Tagovailoa",
                   "Sam Darnold", "Jacoby Brissett", "Derek Carr", "Daniel Jones",
                   "Aaron Rodgers", "Jalen Hurts", "Russell Wilson", "Brock Purdy",
                   "Geno Smith", "Baker Mayfield", "Will Levis", "Jayden Daniels"),
    stringsAsFactors = FALSE
  )
}

# Manual fallback for 2023
get_2023_manual_starters <- function() {
  data.frame(
    Team = c("Arizona Cardinals", "Atlanta Falcons", "Baltimore Ravens", "Buffalo Bills",
             "Carolina Panthers", "Chicago Bears", "Cincinnati Bengals", "Cleveland Browns",
             "Dallas Cowboys", "Denver Broncos", "Detroit Lions", "Green Bay Packers",
             "Houston Texans", "Indianapolis Colts", "Jacksonville Jaguars", "Kansas City Chiefs",
             "Las Vegas Raiders", "Los Angeles Chargers", "Los Angeles Rams", "Miami Dolphins",
             "Minnesota Vikings", "New England Patriots", "New Orleans Saints", "New York Giants",
             "New York Jets", "Philadelphia Eagles", "Pittsburgh Steelers", "San Francisco 49ers",
             "Seattle Seahawks", "Tampa Bay Buccaneers", "Tennessee Titans", "Washington Commanders"),
    Starting_QB = c("Kyler Murray", "Desmond Ridder", "Lamar Jackson", "Josh Allen",
                   "Bryce Young", "Justin Fields", "Joe Burrow", "Deshaun Watson",
                   "Dak Prescott", "Russell Wilson", "Jared Goff", "Jordan Love",
                   "C.J. Stroud", "Anthony Richardson", "Trevor Lawrence", "Patrick Mahomes",
                   "Jimmy Garoppolo", "Justin Herbert", "Matthew Stafford", "Tua Tagovailoa",
                   "Kirk Cousins", "Mac Jones", "Derek Carr", "Daniel Jones",
                   "Aaron Rodgers", "Jalen Hurts", "Kenny Pickett", "Brock Purdy",
                   "Geno Smith", "Baker Mayfield", "Ryan Tannehill", "Sam Howell"),
    stringsAsFactors = FALSE
  )
}

# Display function
display_starters <- function(df, year) {
  cat("\n")
  cat(paste(rep("=", 60), collapse = ""))
  cat(paste("\n", year, "NFL STARTING QUARTERBACKS\n"))
  cat(paste(rep("=", 60), collapse = ""))
  cat("\n\n")
  
  # Check if we have conference data
  if ("Conference" %in% names(df)) {
    # Display by conference
    for (conf in unique(df$Conference)) {
      if (!is.na(conf)) {
        cat(paste("\n", conf, ":\n"))
        cat(paste(rep("-", 40), collapse = ""))
        cat("\n")
        
        conf_data <- df %>% filter(Conference == conf)
        for (i in 1:nrow(conf_data)) {
          cat(sprintf("%-30s %s\n", 
                     conf_data$Team[i], 
                     conf_data$Starting_QB[i]))
        }
      }
    }
  } else {
    # Simple display
    for (i in 1:nrow(df)) {
      cat(sprintf("%-30s %s\n", df$Team[i], df$Starting_QB[i]))
    }
  }
  
  cat("\n")
  cat(paste(rep("=", 60), collapse = ""))
  cat(paste("\nTotal:", nrow(df), "starting QBs\n"))
}

# Save function
save_starters <- function(df, year) {
  # Save as CSV
  csv_filename <- paste0("starting_qbs_", year, ".csv")
  write.csv(df, csv_filename, row.names = FALSE)
  cat(paste("\nData saved to:", csv_filename, "\n"))
  
  # Save as RDS for R users
  rds_filename <- paste0("starting_qbs_", year, ".rds")
  saveRDS(df, rds_filename)
  cat(paste("R data saved to:", rds_filename, "\n"))
}

# Main execution
main <- function() {
  # Get the starting QBs
  starters_df <- get_starting_qbs(year)
  
  # Display the results
  display_starters(starters_df, year)
  
  # Save the data
  save_starters(starters_df, year)
  
  # Return summary statistics
  cat("\n--- Summary ---\n")
  cat(paste("Year:", year, "\n"))
  cat(paste("Total teams:", n_distinct(starters_df$Team), "\n"))
  cat(paste("Unique QBs:", n_distinct(starters_df$Starting_QB), "\n"))
  
  if ("College" %in% names(starters_df) && any(!is.na(starters_df$College))) {
    top_colleges <- starters_df %>%
      filter(!is.na(College)) %>%
      count(College) %>%
      arrange(desc(n)) %>%
      head(5)
    
    cat("\nTop QB-producing colleges:\n")
    print(top_colleges)
  }
  
  if ("Draft_Year" %in% names(starters_df) && any(!is.na(starters_df$Draft_Year))) {
    cat(paste("\nAverage draft year:", 
              round(mean(starters_df$Draft_Year, na.rm = TRUE), 1), "\n"))
  }
}

# Run the main function
main()