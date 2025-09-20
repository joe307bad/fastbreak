# Weekly Fantasy Breakout Metrics Visualization
# This script reads weekly_metrics.csv and creates a line graph comparing:
# 1. Top 10 sleeper score hits per week
# 2. Top 3 sleeper score hits per week
# 3. ML model successful hits per week

library(ggplot2)
library(dplyr)
library(readr)
library(tidyr)

# Function to plot weekly metrics
plot_weekly_metrics <- function(csv_path = "weekly_metrics.csv", output_path = "weekly_metrics_plot.png") {

  # Check if file exists
  if (!file.exists(csv_path)) {
    stop(paste("CSV file not found:", csv_path))
  }

  # Read the data
  cat("Reading data from:", csv_path, "\n")
  data <- read_csv(csv_path, show_col_types = FALSE)

  # Check required columns
  required_cols <- c("Week", "TopTenSleeperHits", "TopThreeSleeperHits", "MLModelSuccessfulHits")
  missing_cols <- setdiff(required_cols, names(data))
  if (length(missing_cols) > 0) {
    stop(paste("Missing required columns:", paste(missing_cols, collapse = ", ")))
  }

  cat("Data loaded successfully:", nrow(data), "weeks\n")

  # Transform data to long format for ggplot
  data_long <- data %>%
    select(Week, TopTenSleeperHits, TopThreeSleeperHits, MLModelSuccessfulHits) %>%
    pivot_longer(
      cols = c(TopTenSleeperHits, TopThreeSleeperHits, MLModelSuccessfulHits),
      names_to = "Metric",
      values_to = "Hits"
    ) %>%
    mutate(
      Metric = case_when(
        Metric == "TopTenSleeperHits" ~ "Top 10 Sleeper Hits",
        Metric == "TopThreeSleeperHits" ~ "Top 3 Sleeper Hits",
        Metric == "MLModelSuccessfulHits" ~ "ML Model Successful Hits"
      )
    )

  # Create the plot
  p <- ggplot(data_long, aes(x = Week, y = Hits, color = Metric)) +
    geom_line(size = 1.2, alpha = 0.8) +
    geom_point(size = 2.5, alpha = 0.9) +
    scale_color_manual(
      values = c(
        "Top 10 Sleeper Hits" = "#1f77b4",      # Blue
        "Top 3 Sleeper Hits" = "#ff7f0e",       # Orange
        "ML Model Successful Hits" = "#2ca02c"   # Green
      )
    ) +
    labs(
      title = "Weekly Fantasy Breakout Performance Comparison",
      subtitle = "Comparing sleeper score hits vs ML model successful predictions",
      x = "NFL Week",
      y = "Number of Hits",
      color = "Metric",
      caption = "Data source: Fantasy breakout prediction analysis"
    ) +
    theme_minimal() +
    theme(
      plot.title = element_text(size = 16, face = "bold", hjust = 0.5),
      plot.subtitle = element_text(size = 12, hjust = 0.5, color = "gray60"),
      axis.title = element_text(size = 12),
      axis.text = element_text(size = 10),
      legend.title = element_text(size = 11, face = "bold"),
      legend.text = element_text(size = 10),
      legend.position = "bottom",
      panel.grid.minor = element_blank(),
      plot.caption = element_text(size = 9, color = "gray50")
    ) +
    scale_x_continuous(
      breaks = seq(min(data$Week), max(data$Week), by = 1),
      minor_breaks = NULL
    ) +
    scale_y_continuous(
      breaks = function(x) seq(0, max(x), by = max(1, ceiling(max(x)/10))),
      minor_breaks = NULL
    )

  # Print summary statistics
  cat("\n=== SUMMARY STATISTICS ===\n")
  summary_stats <- data %>%
    summarise(
      weeks = n(),
      avg_top10 = round(mean(TopTenSleeperHits), 1),
      avg_top3 = round(mean(TopThreeSleeperHits), 1),
      avg_ml = round(mean(MLModelSuccessfulHits), 1),
      total_top10 = sum(TopTenSleeperHits),
      total_top3 = sum(TopThreeSleeperHits),
      total_ml = sum(MLModelSuccessfulHits)
    )

  cat("Weeks analyzed:", summary_stats$weeks, "\n")
  cat("Average per week:\n")
  cat("  Top 10 Sleeper Hits:", summary_stats$avg_top10, "\n")
  cat("  Top 3 Sleeper Hits:", summary_stats$avg_top3, "\n")
  cat("  ML Model Hits:", summary_stats$avg_ml, "\n")
  cat("Total across all weeks:\n")
  cat("  Top 10 Sleeper Hits:", summary_stats$total_top10, "\n")
  cat("  Top 3 Sleeper Hits:", summary_stats$total_top3, "\n")
  cat("  ML Model Hits:", summary_stats$total_ml, "\n")

  # Calculate performance ratios
  if (summary_stats$total_top10 > 0) {
    top3_efficiency <- round((summary_stats$total_top3 / summary_stats$total_top10) * 100, 1)
    cat("Top 3 efficiency (% of top 10):", top3_efficiency, "%\n")
  }

  if (summary_stats$total_top3 > 0) {
    ml_vs_top3 <- round((summary_stats$total_ml / summary_stats$total_top3) * 100, 1)
    cat("ML model vs Top 3 sleeper ratio:", ml_vs_top3, "%\n")
  }

  # Save the plot
  ggsave(output_path, plot = p, width = 12, height = 8, dpi = 300, bg = "white")
  cat("\nPlot saved to:", output_path, "\n")

  # Display the plot
  print(p)

  return(p)
}

# Function to plot weekly metrics from sleeper analysis directory
plot_from_sleeper_analysis <- function(sleeper_dir = "../sleeper_analysis_2024", output_path = "weekly_metrics_plot.png") {

  # Look for weekly_metrics.csv in the sleeper analysis directory
  csv_path <- file.path(sleeper_dir, "weekly_metrics.csv")

  if (!file.exists(csv_path)) {
    stop(paste("weekly_metrics.csv not found in:", sleeper_dir, "\n",
               "Make sure to run the F# command first to generate the CSV file."))
  }

  cat("Found weekly metrics CSV in sleeper analysis directory\n")
  plot_weekly_metrics(csv_path, output_path)
}

# Main execution
if (!interactive()) {
  # Command line usage
  args <- commandArgs(trailingOnly = TRUE)

  if (length(args) == 0) {
    # Default: try to find CSV in sleeper analysis directory
    cat("Fantasy Breakout Weekly Metrics Plotter\n")
    cat("=====================================\n")
    cat("Looking for weekly_metrics.csv in sleeper analysis directory...\n\n")
    plot_from_sleeper_analysis()
  } else if (length(args) == 1) {
    csv_path <- args[1]
    output_path <- "weekly_metrics_plot.png"
    cat("Fantasy Breakout Weekly Metrics Plotter\n")
    cat("=====================================\n")
    cat("CSV file:", csv_path, "\n")
    cat("Output file:", output_path, "\n\n")
    plot_weekly_metrics(csv_path, output_path)
  } else {
    csv_path <- args[1]
    output_path <- args[2]
    cat("Fantasy Breakout Weekly Metrics Plotter\n")
    cat("=====================================\n")
    cat("CSV file:", csv_path, "\n")
    cat("Output file:", output_path, "\n\n")
    plot_weekly_metrics(csv_path, output_path)
  }
} else {
  # Interactive usage
  cat("Weekly metrics plotter loaded.\n")
  cat("Usage:\n")
  cat("  plot_weekly_metrics('path/to/weekly_metrics.csv', 'output_plot.png')\n")
  cat("  plot_from_sleeper_analysis('../sleeper_analysis_2024', 'output_plot.png')\n")
  cat("Default:\n")
  cat("  plot_from_sleeper_analysis() # Looks in ../sleeper_analysis_2024/\n")
}