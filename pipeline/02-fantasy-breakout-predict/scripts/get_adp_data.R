#!/usr/bin/env Rscript

# Function to fetch ADP data from FantasyPros API/CSV exports
# FantasyPros provides consensus ADP data

library(httr)
library(jsonlite)
library(dplyr)
library(readr)
library(rvest)

get_fantasypros_adp <- function(year, format = "half-ppr") {
  
  # FantasyPros typically provides ADP data through their API
  # Format options: "standard", "ppr", "half-ppr"
  
  cat("Fetching ADP data for", year, "season...\n")
  
  # Add delay to be respectful
  Sys.sleep(runif(1, 1, 3))
  
  # Try to fetch from a public endpoint
  # Note: FantasyPros data might require authentication for full access
  
  # Alternative approach: Use nflfastR's built-in data which includes some ADP info
  tryCatch({
    
    # Method 1: Try using nflreadr which has some draft data
    if (require(nflreadr, quietly = TRUE)) {
      
      # Get draft picks data
      draft_data <- nflreadr::load_draft_picks(seasons = year)
      
      # Get fantasy positions data if available
      # This gives us some baseline for rookie ADPs
      rookie_adp <- draft_data %>%
        filter(season == year) %>%
        mutate(
          # Estimate fantasy ADP based on NFL draft position
          # This is a rough conversion
          estimated_fantasy_adp = case_when(
            pick <= 10 ~ pick * 5,  # Top 10 picks
            pick <= 32 ~ 50 + (pick - 10) * 3,  # First round
            pick <= 64 ~ 100 + (pick - 32) * 2,  # Second round
            pick <= 100 ~ 150 + (pick - 64) * 1.5,  # Third round
            pick <= 150 ~ 200 + (pick - 100) * 0.8,
            TRUE ~ 250 + (pick - 150) * 0.3
          )
        ) %>%
        select(
          player = pfr_name,
          position = category,
          team,
          draft_pick = pick,
          fantasy_adp = estimated_fantasy_adp
        )
      
      return(rookie_adp)
    }
    
    # Method 2: Try scraping from a public CSV if available
    # Some sites provide CSV exports
    csv_url <- paste0(
      "https://github.com/fantasydatapros/data/raw/master/",
      "yearly/", year, "/adp.csv"
    )
    
    response <- httr::GET(csv_url)
    
    if (httr::status_code(response) == 200) {
      adp_data <- read_csv(content(response, "text"), show_col_types = FALSE)
      return(adp_data)
    }
    
    # Method 3: Return NULL if no data source available
    warning("Could not fetch ADP data from external sources")
    return(NULL)
    
  }, error = function(e) {
    warning("Error fetching ADP data: ", e$message)
    return(NULL)
  })
}

# Function to match ADP data with player names
match_adp_to_players <- function(players_df, adp_data) {
  
  if (is.null(adp_data)) {
    # If no ADP data, return original with NA values
    players_df$actual_adp <- NA
    return(players_df)
  }
  
  # Clean player names for matching
  clean_name <- function(name) {
    gsub("[^A-Za-z0-9 ]", "", tolower(trimws(name)))
  }
  
  players_df$clean_name <- sapply(players_df$player, clean_name)
  adp_data$clean_name <- sapply(adp_data$player, clean_name)
  
  # Try to match by cleaned name and position
  matched <- players_df %>%
    left_join(
      adp_data %>% select(clean_name, position, actual_adp = fantasy_adp),
      by = c("clean_name", "position")
    )
  
  # For unmatched players, try fuzzy matching or use estimated ADP
  matched <- matched %>%
    mutate(
      final_adp = coalesce(actual_adp, estimated_adp)
    )
  
  return(matched)
}

# Export the functions
if (sys.nframe() == 0) {
  # Script is being run directly
  args <- commandArgs(trailingOnly = TRUE)
  
  if (length(args) < 1) {
    cat("Usage: Rscript get_adp_data.R [year]\n")
    quit(status = 1)
  }
  
  year <- as.integer(args[1])
  adp_data <- get_fantasypros_adp(year)
  
  if (!is.null(adp_data)) {
    output_file <- paste0("adp_data_", year, ".csv")
    write_csv(adp_data, output_file)
    cat("ADP data saved to", output_file, "\n")
  } else {
    cat("No ADP data available for", year, "\n")
  }
}