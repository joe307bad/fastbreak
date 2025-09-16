#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(dplyr)
  library(ggplot2)
  library(gridExtra)
  library(grid)
  library(png)
})

# Get command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Set year (default to 2024 if not provided)
if (length(args) > 0) {
  year <- as.integer(args[1])
} else {
  year <- 2024
}

# Read the CSV file
csv_file <- sprintf("qb_college_starts_%d.csv", year)

if (!file.exists(csv_file)) {
  stop(sprintf("Error: File %s not found. Run get_college_starts.R first.", csv_file))
}

# Read data
qb_data <- read.csv(csv_file, stringsAsFactors = FALSE)

# Clean and prepare data
qb_table <- qb_data %>%
  select(Team, Starting_QB, College, College_Starts) %>%
  arrange(desc(College_Starts)) %>%
  mutate(
    College_Starts = ifelse(is.na(College_Starts), "N/A", as.character(College_Starts)),
    College = ifelse(is.na(College) | College == "", "Unknown", College),
    Rank = row_number()
  ) %>%
  select(Rank, Team, Starting_QB, College, College_Starts)

# Create a theme for the table
table_theme <- ttheme_default(
  core = list(
    fg_params = list(
      col = "black",
      fontsize = 10,
      fontface = "plain"
    ),
    bg_params = list(
      fill = c("white", "#f0f0f0"),
      col = "#cccccc",
      lwd = 0.5
    )
  ),
  colhead = list(
    fg_params = list(
      col = "white",
      fontsize = 11,
      fontface = "bold"
    ),
    bg_params = list(
      fill = "#2c3e50",
      col = "#2c3e50"
    )
  )
)

# Create the table
table_grob <- tableGrob(
  qb_table,
  rows = NULL,
  theme = table_theme
)

# Add title
title_grob <- textGrob(
  sprintf("%d NFL Starting QBs - College Starts", year),
  gp = gpar(fontsize = 16, fontface = "bold", col = "#2c3e50"),
  x = 0.5,
  y = 0.95
)

# Add subtitle with timestamp
subtitle_grob <- textGrob(
  sprintf("Generated: %s", format(Sys.time(), "%B %d, %Y at %I:%M %p")),
  gp = gpar(fontsize = 10, fontface = "italic", col = "#7f8c8d"),
  x = 0.5,
  y = 0.90
)

# Calculate dimensions based on content
n_rows <- nrow(qb_table)
height <- 2 + (n_rows * 0.25)  # Base height + row height
width <- 12  # Fixed width

# Create the plot
png_file <- sprintf("qb_college_starts_table_%d.png", year)
png(png_file, width = width, height = height, units = "in", res = 150)

# Create layout
grid.newpage()
pushViewport(viewport(layout = grid.layout(3, 1, heights = unit(c(0.15, 0.8, 0.05), "npc"))))

# Add title and subtitle
pushViewport(viewport(layout.pos.row = 1))
grid.draw(title_grob)
grid.draw(subtitle_grob)
popViewport()

# Add table
pushViewport(viewport(layout.pos.row = 2))
grid.draw(table_grob)
popViewport()

# Add footer
pushViewport(viewport(layout.pos.row = 3))
footer_grob <- textGrob(
  "Data source: Sports Reference College Football",
  gp = gpar(fontsize = 8, col = "#95a5a6"),
  x = 0.5,
  y = 0.5
)
grid.draw(footer_grob)
popViewport()

dev.off()

# Print summary
cat("\n")
cat("=====================================\n")
cat(sprintf("    QB College Starts Table - %d\n", year))
cat("=====================================\n\n")

# Summary statistics
valid_starts <- qb_data$College_Starts[!is.na(qb_data$College_Starts)]
cat(sprintf("Total QBs: %d\n", nrow(qb_data)))
cat(sprintf("QBs with data: %d\n", length(valid_starts)))
cat(sprintf("Average starts: %.1f\n", mean(valid_starts)))
cat(sprintf("Median starts: %.1f\n", median(valid_starts)))
cat(sprintf("Max starts: %d\n", max(valid_starts)))
cat(sprintf("Min starts: %d\n", min(valid_starts)))

cat("\nTop 5 QBs by College Starts:\n")
cat("-----------------------------\n")
top_5 <- head(qb_table, 5)
for (i in 1:nrow(top_5)) {
  cat(sprintf("%d. %s (%s) - %s starts\n",
              i,
              top_5$Starting_QB[i],
              top_5$Team[i],
              top_5$College_Starts[i]))
}

cat("\n")
cat(sprintf("Table saved to: %s\n", png_file))
cat(sprintf("Dimensions: %d x %d inches\n", width, height))
cat("\n")