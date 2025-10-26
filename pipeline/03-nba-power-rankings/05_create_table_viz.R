#!/usr/bin/env Rscript
# Create table visualization of NBA Elo rankings

library(tidyverse)
library(gridExtra)
library(grid)

cat("=== Creating Table Visualization ===\n\n")

# Load enhanced rankings
cat("Loading rankings data...\n")
rankings <- read_csv("data/nba_elo_rankings_formatted.csv", show_col_types = FALSE)

cat("Loaded", nrow(rankings), "teams\n\n")

# Prepare data for table
table_data <- rankings %>%
  mutate(
    Rank = rank,
    Team = team,
    `Elo Rating` = elo_rating,
    `2025-26` = record_2025_26,
    PPG = ppg_2025_26,
    `PA/G` = pa_2025_26,
    `2024-25` = record_2024_25
  ) %>%
  select(Rank, Team, `Elo Rating`, `2025-26`, PPG, `PA/G`, `2024-25`)

cat("Creating table visualization...\n")

# Create a nice table theme with tighter spacing
table_theme <- ttheme_default(
  core = list(
    fg_params = list(hjust = 0, x = 0.05, fontsize = 11),
    bg_params = list(fill = c(rep(c("#f0f0f0", "#ffffff"), 15)))
  ),
  colhead = list(
    fg_params = list(fontsize = 12, fontface = "bold"),
    bg_params = list(fill = "#4575b4", col = "#ffffff")
  ),
  rowhead = list(
    fg_params = list(fontsize = 11)
  )
)

# Create the table
table_grob <- tableGrob(
  table_data,
  rows = NULL,
  theme = table_theme
)

# Make table full width
table_grob$widths <- unit(rep(1/ncol(table_data), ncol(table_data)), "npc")

# Add title with minimal spacing
title <- textGrob(
  "2025-2026 Elo Ratings",
  gp = gpar(fontsize = 22, fontface = "bold")
)

subtitle <- textGrob(
  paste("Updated:", format(Sys.Date(), "%B %d, %Y")),
  gp = gpar(fontsize = 13)
)

# Combine title and table with minimal spacing
final_plot <- arrangeGrob(
  title,
  subtitle,
  table_grob,
  heights = unit(c(1.2, 0.8, 1), c("cm", "cm", "null")),
  ncol = 1
)

# Calculate auto height based on table dimensions
# Each row is approximately 0.6 cm, header is 0.8 cm, title area is 4 cm
num_rows <- nrow(table_data)
table_height <- (num_rows * 0.6) + 0.8  # rows + header
total_height <- table_height + 4 + 0.5  # add title area with bottom padding
total_height_inches <- total_height / 2.54  # convert cm to inches

# Save as PNG with auto-calculated height
output_file <- "visualizations/elo_rankings_table.png"
ggsave(
  output_file,
  final_plot,
  width = 11,
  height = total_height_inches,
  dpi = 300
)

cat("✓ Table visualization saved to:", output_file, "\n")

cat("\n=== Complete ===\n")
