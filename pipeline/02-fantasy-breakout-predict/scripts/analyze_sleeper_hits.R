#!/usr/bin/env Rscript

# Analyze sleeper hits for a specific year
# Usage: Rscript scripts/analyze_sleeper_hits.R [year]
# Example: Rscript scripts/analyze_sleeper_hits.R 2024

suppressPackageStartupMessages({
  library(dplyr)
  library(readr)
  library(ggplot2)
  library(tidyr)
  library(stringr)
})

# Parse command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Check if year argument provided
if (length(args) != 1) {
  cat("Usage: Rscript scripts/analyze_sleeper_hits.R [year]\n")
  cat("Example: Rscript scripts/analyze_sleeper_hits.R 2024\n")
  quit(status = 1)
}

target_year <- as.integer(args[1])

# Validate year
current_year <- as.integer(format(Sys.Date(), "%Y"))
if (is.na(target_year) || target_year < 2020 || target_year > current_year + 1) {
  cat("Error: Please provide a valid year between 2020 and", current_year + 1, "\n")
  quit(status = 1)
}

# Ensure we're in the correct directory
current_dir <- getwd()
cat("Current working directory:", current_dir, "\n")

# If we're in the scripts directory, move up one level
if (basename(current_dir) == "scripts") {
  setwd("..")
  cat("Moved up to:", getwd(), "\n")
}

cat("Analyzing sleeper hits for year", target_year, "...\n")

# Find sleeper CSV files for the target year
sleeper_files <- list.files(
  pattern = paste0("^second_year_sleepers_", target_year, "_w\\d{1,2}\\.csv$"),
  full.names = TRUE
)

cat("Found", length(sleeper_files), "sleeper files for", target_year, "\n")

if (length(sleeper_files) == 0) {
  cat("No sleeper CSV files found for year", target_year, "\n")
  cat("Make sure files follow the pattern: second_year_sleepers_", target_year, "_w{week}.csv\n")
  quit(status = 1)
}

# Initialize results dataframe
all_results <- data.frame()

# Process each file
for (file_path in sleeper_files) {
  # Extract year and week from filename
  file_name <- basename(file_path)
  matches <- str_match(file_name, "second_year_sleepers_(\\d{4})_w(\\d{1,2})\\.csv")

  if (is.na(matches[1])) {
    cat("Skipping file with unexpected format:", file_name, "\n")
    next
  }

  year <- as.integer(matches[2])
  week <- as.integer(matches[3])

  cat("Processing", file_name, "- Year:", year, "Week:", week, "\n")

  # Read the CSV file
  tryCatch({
    sleeper_data <- read_csv(file_path, show_col_types = FALSE)

    # Check if required columns exist
    if (!"sleeper_rank" %in% names(sleeper_data)) {
      cat("Warning: Missing sleeper_rank column in", file_name, "\n")
      next
    }

    # Check if this is a historical file (no hit_status column)
    if (!"hit_status" %in% names(sleeper_data)) {
      cat("  - Skipping historical file (no hit_status column):", file_name, "\n")
      next
    }

    # Calculate hits for top 10 and top 3
    total_players <- nrow(sleeper_data)

    # Top 10 hits
    top_10 <- sleeper_data %>%
      filter(sleeper_rank <= 10) %>%
      nrow()

    top_10_hits <- sleeper_data %>%
      filter(sleeper_rank <= 10, hit_status == "HIT") %>%
      nrow()

    # Top 3 hits
    top_3 <- sleeper_data %>%
      filter(sleeper_rank <= 3) %>%
      nrow()

    top_3_hits <- sleeper_data %>%
      filter(sleeper_rank <= 3, hit_status == "HIT") %>%
      nrow()

    # Calculate hit rates
    top_10_hit_rate <- if (top_10 > 0) round((top_10_hits / top_10) * 100, 1) else 0
    top_3_hit_rate <- if (top_3 > 0) round((top_3_hits / top_3) * 100, 1) else 0

    # Add to results
    result_row <- data.frame(
      year = year,
      week = week,
      total_players = total_players,
      top_10_candidates = top_10,
      top_10_hits = top_10_hits,
      top_10_hit_rate = top_10_hit_rate,
      top_3_candidates = top_3,
      top_3_hits = top_3_hits,
      top_3_hit_rate = top_3_hit_rate
    )

    all_results <- rbind(all_results, result_row)

    cat("  - Top 10 hits:", top_10_hits, "/", top_10, "(", top_10_hit_rate, "%)\n")
    cat("  - Top 3 hits:", top_3_hits, "/", top_3, "(", top_3_hit_rate, "%)\n")

  }, error = function(e) {
    cat("Error processing", file_name, ":", e$message, "\n")
  })
}

if (nrow(all_results) == 0) {
  cat("No valid data found to analyze\n")
  quit(status = 1)
}

# Sort results by week
all_results <- all_results %>%
  arrange(week)

cat("\nAnalysis complete. Found data for", nrow(all_results), "weeks in", target_year, "\n")

# Calculate additional statistics
weeks_with_top3_hit <- sum(all_results$top_3_hits > 0)
pct_weeks_top3_hit <- round((weeks_with_top3_hit / nrow(all_results)) * 100, 1)

weeks_with_3plus_top10_hits <- sum(all_results$top_10_hits > 3)
pct_weeks_3plus_top10 <- round((weeks_with_3plus_top10_hits / nrow(all_results)) * 100, 1)

weeks_with_5plus_top10_hits <- sum(all_results$top_10_hits > 5)
pct_weeks_5plus_top10 <- round((weeks_with_5plus_top10_hits / nrow(all_results)) * 100, 1)

# Save results to CSV
output_csv <- paste0("sleeper_hits_analysis_", target_year, ".csv")
write_csv(all_results, output_csv)
cat("Results saved to:", output_csv, "\n")

# Create visualization
cat("Creating visualization...\n")

# Prepare data for plotting
plot_data <- all_results %>%
  select(week, top_10_hits, top_3_hits) %>%
  pivot_longer(
    cols = c(top_10_hits, top_3_hits),
    names_to = "category",
    values_to = "hits"
  ) %>%
  mutate(
    category = case_when(
      category == "top_10_hits" ~ "Top 10 Sleepers",
      category == "top_3_hits" ~ "Top 3 Sleepers",
      TRUE ~ category
    )
  )

# Create the line plot with y-axis set to 10
p <- ggplot(plot_data, aes(x = week, y = hits, color = category)) +
  geom_line(linewidth = 1.5, alpha = 0.9) +
  geom_point(size = 3) +
  geom_text(aes(label = hits), vjust = -0.8, size = 3.5, fontface = "bold") +
  labs(
    title = paste("Second-Year Sleeper Hits -", target_year, "Season"),
    subtitle = "Number of successful 'HIT' predictions for top-ranked sleepers",
    x = "Week",
    y = "Number of Hits",
    color = "Sleeper Category"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(size = 18, face = "bold", hjust = 0.5),
    plot.subtitle = element_text(size = 14, hjust = 0.5, color = "gray60"),
    legend.position = "bottom",
    legend.title = element_text(face = "bold", size = 12),
    legend.text = element_text(size = 11),
    panel.grid.minor = element_blank(),
    axis.text = element_text(size = 11),
    axis.title = element_text(size = 12, face = "bold"),
    plot.margin = margin(20, 20, 120, 20)
  ) +
  scale_color_manual(values = c("Top 10 Sleepers" = "#2E8B57", "Top 3 Sleepers" = "#FF6347")) +
  scale_y_continuous(limits = c(0, 10), breaks = seq(0, 10, by = 1)) +
  scale_x_continuous(breaks = all_results$week)

# Create statistics text
stats_text <- paste0(
  "Statistics for ", target_year, " Season:\n",
  "• ", pct_weeks_top3_hit, "% of weeks with ≥1 top 3 hit (", weeks_with_top3_hit, "/", nrow(all_results), " weeks)\n",
  "• ", pct_weeks_3plus_top10, "% of weeks with >3 top 10 hits (", weeks_with_3plus_top10_hits, "/", nrow(all_results), " weeks)\n",
  "• ", pct_weeks_5plus_top10, "% of weeks with >5 top 10 hits (", weeks_with_5plus_top10_hits, "/", nrow(all_results), " weeks)"
)

# Add the statistics as a text annotation
p_final <- p +
  labs(caption = stats_text) +
  theme(
    plot.caption = element_text(size = 12, hjust = 0.5, color = "black",
                               margin = margin(t = 20), face = "bold")
  )

# Save the plot
output_png <- paste0("sleeper_hits_analysis_", target_year, ".png")
ggsave(output_png, plot = p_final, width = 14, height = 10, dpi = 300, bg = "white")
cat("Visualization saved to:", output_png, "\n")

# Print summary statistics
cat("\n=== SUMMARY STATISTICS FOR", target_year, "SEASON ===\n")
cat("Total weeks analyzed:", nrow(all_results), "\n")
cat("Week range:", min(all_results$week), "to", max(all_results$week), "\n")

# Overall hit rates
overall_top_10_hits <- sum(all_results$top_10_hits)
overall_top_10_candidates <- sum(all_results$top_10_candidates)
overall_top_10_rate <- round((overall_top_10_hits / overall_top_10_candidates) * 100, 1)

overall_top_3_hits <- sum(all_results$top_3_hits)
overall_top_3_candidates <- sum(all_results$top_3_candidates)
overall_top_3_rate <- round((overall_top_3_hits / overall_top_3_candidates) * 100, 1)

cat("\nOVERALL HIT RATES:\n")
cat("Top 10 Sleepers:", overall_top_10_hits, "/", overall_top_10_candidates,
    "(", overall_top_10_rate, "%)\n")
cat("Top 3 Sleepers:", overall_top_3_hits, "/", overall_top_3_candidates,
    "(", overall_top_3_rate, "%)\n")

cat("\nKEY METRICS:\n")
cat("• Weeks with ≥1 top 3 hit:", pct_weeks_top3_hit, "% (", weeks_with_top3_hit, "/", nrow(all_results), " weeks)\n")
cat("• Weeks with >3 top 10 hits:", pct_weeks_3plus_top10, "% (", weeks_with_3plus_top10_hits, "/", nrow(all_results), " weeks)\n")
cat("• Weeks with >5 top 10 hits:", pct_weeks_5plus_top10, "% (", weeks_with_5plus_top10_hits, "/", nrow(all_results), " weeks)\n")

# Best and worst weeks
best_top_10_week <- all_results[which.max(all_results$top_10_hit_rate), ]
worst_top_10_week <- all_results[which.min(all_results$top_10_hit_rate), ]
best_top_3_week <- all_results[which.max(all_results$top_3_hit_rate), ]
worst_top_3_week <- all_results[which.min(all_results$top_3_hit_rate), ]

cat("\nBEST/WORST WEEKS:\n")
cat("Best Top 10 week: Week", best_top_10_week$week,
    "(", best_top_10_week$top_10_hit_rate, "% hit rate,", best_top_10_week$top_10_hits, "hits )\n")
cat("Worst Top 10 week: Week", worst_top_10_week$week,
    "(", worst_top_10_week$top_10_hit_rate, "% hit rate,", worst_top_10_week$top_10_hits, "hits )\n")
cat("Best Top 3 week: Week", best_top_3_week$week,
    "(", best_top_3_week$top_3_hit_rate, "% hit rate,", best_top_3_week$top_3_hits, "hits )\n")
cat("Worst Top 3 week: Week", worst_top_3_week$week,
    "(", worst_top_3_week$top_3_hit_rate, "% hit rate,", worst_top_3_week$top_3_hits, "hits )\n")

cat("\nAnalysis complete! Check", output_csv, "and", output_png, "for results.\n")