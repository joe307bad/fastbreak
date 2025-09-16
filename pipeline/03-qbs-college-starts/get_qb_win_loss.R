#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(nflreadr)
  library(dplyr)
  library(tidyr)
  library(ggplot2)
})

# Get command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Set year
if (length(args) > 0) {
  year <- as.integer(args[1])
} else {
  year <- 2024
}

# Function to get QB win-loss records from nflreadr
get_qb_win_loss <- function(qb_name, through_year = 2024) {
  tryCatch({
    # Load play-by-play data for all years
    cat(sprintf("Fetching NFL career data for %s...\n", qb_name))
    
    # Get QB stats using nflreadr
    qb_stats <- load_player_stats(stat_type = "offense") %>%
      filter(player_name == qb_name | player_display_name == qb_name,
             position == "QB") %>%
      select(season, week, player_name, team = recent_team, 
             attempts, completions, passing_yards, passing_tds)
    
    if (nrow(qb_stats) == 0) {
      return(list(wins = NA, losses = NA, ties = NA, games_started = NA))
    }
    
    # Load game results
    games <- load_schedules() %>%
      filter(season <= through_year)
    
    # Match QB games with results
    qb_games <- qb_stats %>%
      left_join(games, by = c("season" = "season", "week" = "week", "team" = "home_team")) %>%
      mutate(is_home = TRUE) %>%
      bind_rows(
        qb_stats %>%
          left_join(games, by = c("season" = "season", "week" = "week", "team" = "away_team")) %>%
          mutate(is_home = FALSE)
      ) %>%
      filter(!is.na(game_id))
    
    # Calculate W-L record
    qb_record <- qb_games %>%
      mutate(
        result = case_when(
          is_home & home_score > away_score ~ "W",
          is_home & home_score < away_score ~ "L",
          !is_home & away_score > home_score ~ "W",
          !is_home & away_score < home_score ~ "L",
          home_score == away_score ~ "T",
          TRUE ~ NA_character_
        )
      ) %>%
      group_by(result) %>%
      summarise(count = n()) %>%
      ungroup()
    
    wins <- sum(qb_record$count[qb_record$result == "W"], na.rm = TRUE)
    losses <- sum(qb_record$count[qb_record$result == "L"], na.rm = TRUE)
    ties <- sum(qb_record$count[qb_record$result == "T"], na.rm = TRUE)
    
    return(list(
      wins = wins,
      losses = losses,
      ties = ties,
      games_started = wins + losses + ties
    ))
    
  }, error = function(e) {
    cat(sprintf("Error getting data for %s: %s\n", qb_name, e$message))
    return(list(wins = NA, losses = NA, ties = NA, games_started = NA))
  })
}

# Function to process all QBs and merge with college data
process_qb_comparison <- function(year) {
  cat(sprintf("\n=== Processing NFL W/L Records for %d QBs ===\n\n", year))
  
  # Load starting QBs
  qb_file <- sprintf("starting_qbs_%d.csv", year)
  if (!file.exists(qb_file)) {
    cat("Starting QB file not found. Run get_starting_qbs.R first.\n")
    return(NULL)
  }
  
  # Load college starts data
  college_file <- sprintf("qb_college_starts_%d.csv", year)
  college_data <- NULL
  if (file.exists(college_file)) {
    college_data <- read.csv(college_file, stringsAsFactors = FALSE)
  } else {
    cat("College starts file not found. Run get_college_starts.R first.\n")
  }
  
  # Read QB data
  qbs <- read.csv(qb_file, stringsAsFactors = FALSE)
  
  # Initialize results
  results <- data.frame()
  
  # Process each QB
  for (i in 1:nrow(qbs)) {
    qb_name <- qbs$Starting_QB[i]
    team <- qbs$Team[i]
    
    cat(sprintf("[%d/%d] Processing %s...\n", i, nrow(qbs), qb_name))
    
    # Get NFL W/L record
    nfl_record <- get_qb_win_loss(qb_name, year)
    
    # Get college starts if available
    college_starts <- NA
    college_name <- NA
    if (!is.null(college_data)) {
      qb_college <- college_data[college_data$Starting_QB == qb_name, ]
      if (nrow(qb_college) > 0) {
        college_starts <- qb_college$College_Starts[1]
        college_name <- qb_college$College[1]
      }
    }
    
    # Calculate win percentage
    win_pct <- NA
    if (!is.na(nfl_record$wins) && !is.na(nfl_record$losses)) {
      total_games <- nfl_record$wins + nfl_record$losses + nfl_record$ties
      if (total_games > 0) {
        win_pct <- nfl_record$wins / total_games
      }
    }
    
    # Add to results
    results <- rbind(results, data.frame(
      Team = team,
      QB = qb_name,
      College = ifelse(is.na(college_name), NA, college_name),
      College_Starts = college_starts,
      NFL_Wins = nfl_record$wins,
      NFL_Losses = nfl_record$losses,
      NFL_Ties = nfl_record$ties,
      NFL_Games = nfl_record$games_started,
      NFL_Win_Pct = win_pct,
      stringsAsFactors = FALSE
    ))
  }
  
  return(results)
}

# Function to create comparison visualization
create_comparison_plot <- function(data) {
  # Filter out QBs with missing data
  plot_data <- data %>%
    filter(!is.na(College_Starts), !is.na(NFL_Win_Pct), NFL_Games > 0)
  
  if (nrow(plot_data) == 0) {
    cat("Not enough data for visualization\n")
    return(NULL)
  }
  
  # Create scatter plot
  p <- ggplot(plot_data, aes(x = College_Starts, y = NFL_Win_Pct)) +
    geom_point(aes(size = NFL_Games), alpha = 0.6, color = "blue") +
    geom_text(aes(label = QB), vjust = -0.5, hjust = 0.5, size = 3, check_overlap = TRUE) +
    geom_smooth(method = "lm", se = TRUE, alpha = 0.2, color = "red") +
    labs(
      title = "QB College Starts vs NFL Win Percentage",
      x = "College Starts",
      y = "NFL Win Percentage",
      size = "NFL Games"
    ) +
    theme_minimal() +
    scale_y_continuous(labels = scales::percent) +
    theme(
      plot.title = element_text(hjust = 0.5, size = 14, face = "bold"),
      axis.title = element_text(size = 12)
    )
  
  # Save plot
  filename <- sprintf("qb_comparison_%d.png", year)
  ggsave(filename, p, width = 12, height = 8)
  cat(sprintf("Plot saved to %s\n", filename))
  
  return(p)
}

# Function to display analysis summary
display_analysis <- function(data) {
  cat("\n=== QB COLLEGE STARTS vs NFL W/L ANALYSIS ===\n\n")
  
  # Filter complete data
  complete_data <- data %>%
    filter(!is.na(College_Starts), !is.na(NFL_Win_Pct), NFL_Games > 0)
  
  if (nrow(complete_data) > 0) {
    # Calculate correlation
    correlation <- cor(complete_data$College_Starts, complete_data$NFL_Win_Pct, 
                      use = "complete.obs")
    
    cat(sprintf("Correlation between College Starts and NFL Win%%: %.3f\n\n", correlation))
    
    # Top performers
    cat("Top 5 QBs by NFL Win Percentage:\n")
    top_winners <- complete_data %>%
      arrange(desc(NFL_Win_Pct)) %>%
      head(5)
    
    for (i in 1:nrow(top_winners)) {
      cat(sprintf("%d. %s - %.1f%% (%d-%d, %d college starts)\n",
                  i,
                  top_winners$QB[i],
                  top_winners$NFL_Win_Pct[i] * 100,
                  top_winners$NFL_Wins[i],
                  top_winners$NFL_Losses[i],
                  top_winners$College_Starts[i]))
    }
    
    cat("\nQBs with Most College Starts:\n")
    most_starts <- complete_data %>%
      arrange(desc(College_Starts)) %>%
      head(5)
    
    for (i in 1:nrow(most_starts)) {
      cat(sprintf("%d. %s - %d starts (%.1f%% NFL win rate)\n",
                  i,
                  most_starts$QB[i],
                  most_starts$College_Starts[i],
                  most_starts$NFL_Win_Pct[i] * 100))
    }
    
    # Statistical summary
    cat("\n--- Statistical Summary ---\n")
    cat(sprintf("Average College Starts: %.1f\n", mean(complete_data$College_Starts)))
    cat(sprintf("Average NFL Win%%: %.1f%%\n", mean(complete_data$NFL_Win_Pct) * 100))
    
    # Quartile analysis
    q1 <- quantile(complete_data$College_Starts, 0.25)
    q3 <- quantile(complete_data$College_Starts, 0.75)
    
    low_starts <- complete_data %>% filter(College_Starts <= q1)
    high_starts <- complete_data %>% filter(College_Starts >= q3)
    
    cat(sprintf("\nQBs with <= %.0f college starts: Avg NFL Win%% = %.1f%%\n",
                q1, mean(low_starts$NFL_Win_Pct) * 100))
    cat(sprintf("QBs with >= %.0f college starts: Avg NFL Win%% = %.1f%%\n",
                q3, mean(high_starts$NFL_Win_Pct) * 100))
  }
  
  # Display full table
  cat("\n=== FULL COMPARISON TABLE ===\n")
  print(data %>%
          select(QB, College_Starts, NFL_Wins, NFL_Losses, NFL_Win_Pct) %>%
          arrange(desc(NFL_Win_Pct)))
}

# Main execution
main <- function() {
  # Process QB data
  comparison_data <- process_qb_comparison(year)
  
  if (!is.null(comparison_data)) {
    # Save results
    csv_filename <- sprintf("qb_wl_comparison_%d.csv", year)
    write.csv(comparison_data, csv_filename, row.names = FALSE)
    cat(sprintf("\nData saved to %s\n", csv_filename))
    
    # Display analysis
    display_analysis(comparison_data)
    
    # Create visualization
    create_comparison_plot(comparison_data)
  }
}

# Run main function
main()