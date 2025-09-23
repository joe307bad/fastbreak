library(jsonlite)
library(ggplot2)
library(dplyr)
library(gridExtra)
library(grid)

# Get command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Read the JSON file
json_data <- fromJSON("weekly-predictions.json")

# Extract the week number from the JSON
week_number <- json_data$week

# Extract the ML top 10 predictions
ml_predictions <- json_data$mlTop10Predictions

# Create a data frame from JSON predictions
df <- data.frame(
  Rank = 1:length(ml_predictions$player),
  Player = ml_predictions$player,
  Position = ml_predictions$position,
  ML_Confidence = round(ml_predictions$mlConfidence, 3),
  Sleeper_Score = ml_predictions$sleeperScore,
  stringsAsFactors = FALSE
)

# Check if CSV file was provided as argument
if(length(args) > 0) {
  # Read the CSV file to get hit status and fantasy points
  csv_data <- read.csv(args[1], stringsAsFactors = FALSE)

  # Merge with CSV data to get hit status and fantasy points
  df <- df %>%
    left_join(csv_data %>% select(player, hit_status, prev_week_fp, current_week_fp, fp_delta),
              by = c("Player" = "player")) %>%
    mutate(
      Hit = ifelse(!is.na(hit_status) & hit_status == "HIT", "✓", ""),
      Prev_FP = round(ifelse(is.na(prev_week_fp), 0, prev_week_fp), 1),
      Curr_FP = round(ifelse(is.na(current_week_fp), 0, current_week_fp), 1),
      FP_Delta = round(ifelse(is.na(fp_delta), 0, fp_delta), 1)
    )

  # Sort by ML confidence and select columns with Hit and FP data
  df <- df %>%
    arrange(desc(ML_Confidence)) %>%
    mutate(Rank = 1:n()) %>%
    select(Rank, Player, Position, ML_Confidence, Sleeper_Score, Prev_FP, Curr_FP, FP_Delta, Hit)

  # Rename columns for display
  names(df) <- c("Rank", "Player", "Pos", "ML Conf", "Sleeper", "Prev FP", "Curr FP", "FP Δ", "Hit")

  # Create row colors - green for hits, light green for non-hits
  row_colors <- ifelse(df$Hit == "✓", "#4CAF50", "#E8F5E9")
} else {
  # No CSV file provided, don't include Hit column
  df <- df %>%
    arrange(desc(ML_Confidence)) %>%
    mutate(Rank = 1:n()) %>%
    select(Rank, Player, Position, ML_Confidence, Sleeper_Score)

  # Rename columns for display
  names(df) <- c("Rank", "Player", "Position", "ML Confidence", "Sleeper Score")

  # Create row colors - top 10 in green, rest in white
  row_colors <- ifelse(df$Rank <= 10, "#90EE90", "white")
}

# Create the table plot with full width
table_plot <- tableGrob(df,
                       rows = NULL,
                       theme = ttheme_default(
                         core = list(
                           bg_params = list(fill = row_colors),
                           fg_params = list(fontsize = 16)
                         ),
                         colhead = list(
                           bg_params = list(fill = "#4CAF50"),
                           fg_params = list(col = "white", fontsize = 18, fontface = "bold")
                         )
                       ))

# Make table fill full width
table_plot$widths <- unit(rep(1/ncol(table_plot), ncol(table_plot)), "npc")

# Create the plot with full width table
p <- ggplot() +
  annotation_custom(table_plot, xmin = -Inf, xmax = Inf,
                   ymin = -Inf, ymax = Inf) +
  labs(title = paste("Fantasy Football Breakout Predictions - Week",
                    week_number),
       subtitle = "Players sorted by ML Confidence (Top 10 rows highlighted)") +
  theme_void() +
  theme(
    plot.title = element_text(size = 24, face = "bold", hjust = 0.5,
                             margin = margin(b = 15)),
    plot.subtitle = element_text(size = 18, hjust = 0.5,
                                margin = margin(b = 25)),
    plot.margin = margin(10, 10, 10, 10)
  ) +
  coord_cartesian(clip = "off")

# Calculate dynamic dimensions based on content
# Adjust width based on number of columns (more columns when CSV provided)
table_width <- if(length(args) > 0) ncol(df) * 2.2 else ncol(df) * 2.5
table_height <- nrow(df) * 0.3 + 3  # Height per row plus title space

# Save as PNG sized to content
ggsave("fantasy_predictions_table.png", plot = p,
       width = table_width, height = table_height, dpi = 300, bg = "white")

# Print summary
cat("Table visualization saved as 'fantasy_predictions_table.png'\n")
cat("Top 10 players by ML Confidence (highlighted in green):\n")
print(df[1:10, ])