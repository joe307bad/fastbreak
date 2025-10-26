#!/usr/bin/env Rscript
# Phase 3: Generate Rankings & Validate
# Create visualizations and final reports

library(tidyverse)

cat("=== Phase 3: Generate Rankings & Validate ===\n\n")

# Load results from Phase 2
cat("Loading Elo results...\n")
rankings <- read_csv("data/nba_elo_rankings.csv", show_col_types = FALSE)
game_results <- read_csv("data/nba_elo_game_predictions.csv", show_col_types = FALSE)

cat("Loaded", nrow(rankings), "teams\n")
cat("Loaded", nrow(game_results), "game predictions\n\n")

# Filter out All-Star teams for display
nba_teams <- rankings %>%
  filter(!str_detect(team, "^Team ")) %>%
  arrange(desc(elo_rating)) %>%
  mutate(rank = row_number())

cat("NBA teams (excluding All-Star):", nrow(nba_teams), "\n\n")

# Display current rankings
cat("=== Current NBA Elo Power Rankings (2025-26 Season) ===\n\n")
cat("Top 15 Teams:\n")
print(head(nba_teams, 15), n = 15)

cat("\nBottom 5 Teams:\n")
print(tail(nba_teams, 5), n = 5)

# Evaluation metrics
cat("\n=== Prediction Accuracy Metrics ===\n")

# Overall accuracy
accuracy <- mean((game_results$home_win_prob > 0.5) == game_results$home_win_actual)
cat("Overall Prediction Accuracy:", round(accuracy * 100, 2), "%\n")

# Brier Score
brier_score <- mean((game_results$home_win_prob - as.numeric(game_results$home_win_actual))^2)
cat("Brier Score:", round(brier_score, 4), "\n")

# Log Loss
log_loss <- -mean(
  as.numeric(game_results$home_win_actual) * log(pmax(game_results$home_win_prob, 1e-15)) +
  (1 - as.numeric(game_results$home_win_actual)) * log(pmax(1 - game_results$home_win_prob, 1e-15))
)
cat("Log Loss:", round(log_loss, 4), "\n\n")

# Accuracy by confidence level
cat("=== Accuracy by Prediction Confidence ===\n")
confidence_analysis <- game_results %>%
  mutate(
    predicted_winner = if_else(home_win_prob > 0.5, "home", "away"),
    actual_winner = if_else(home_win_actual, "home", "away"),
    correct = predicted_winner == actual_winner,
    confidence = abs(home_win_prob - 0.5),
    confidence_bucket = cut(
      confidence,
      breaks = c(0, 0.1, 0.2, 0.3, 0.4, 0.5),
      labels = c("50-60%", "60-70%", "70-80%", "80-90%", "90-100%")
    )
  ) %>%
  group_by(confidence_bucket) %>%
  summarize(
    games = n(),
    accuracy = mean(correct),
    avg_confidence = mean(home_win_prob),
    .groups = "drop"
  )

print(confidence_analysis)

# Season breakdown
cat("\n=== Performance by Season ===\n")
season_stats <- game_results %>%
  group_by(season) %>%
  summarize(
    games = n(),
    accuracy = mean((home_win_prob > 0.5) == home_win_actual),
    brier_score = mean((home_win_prob - as.numeric(home_win_actual))^2),
    avg_elo_change = mean(abs(elo_change)),
    .groups = "drop"
  )

print(season_stats)

# Create visualizations
cat("\n=== Creating Visualizations ===\n")

# 1. Current Elo Rankings Bar Chart
cat("Creating Elo rankings bar chart...\n")
p1 <- ggplot(nba_teams, aes(x = reorder(team, elo_rating), y = elo_rating, fill = elo_rating)) +
  geom_col() +
  coord_flip() +
  scale_fill_gradient2(
    low = "#d73027",
    mid = "#fee08b",
    high = "#1a9850",
    midpoint = 1500,
    name = "Elo Rating"
  ) +
  geom_hline(yintercept = 1500, linetype = "dashed", alpha = 0.5) +
  labs(
    title = "NBA Elo Power Rankings (2025-26 Season)",
    subtitle = paste("Updated:", format(Sys.Date(), "%B %d, %Y")),
    x = "Team",
    y = "Elo Rating"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(size = 16, face = "bold"),
    plot.subtitle = element_text(size = 12),
    axis.text.y = element_text(size = 10)
  )

ggsave("visualizations/elo_rankings_bar.png", p1, width = 10, height = 12, dpi = 300)
cat("✓ Saved: visualizations/elo_rankings_bar.png\n")

# 2. Elo Distribution
cat("Creating Elo distribution histogram...\n")
p2 <- ggplot(nba_teams, aes(x = elo_rating)) +
  geom_histogram(bins = 15, fill = "#4575b4", alpha = 0.7, color = "black") +
  geom_vline(xintercept = 1500, linetype = "dashed", color = "red", size = 1) +
  labs(
    title = "Distribution of NBA Team Elo Ratings",
    subtitle = "Dashed line = Starting Elo (1500)",
    x = "Elo Rating",
    y = "Number of Teams"
  ) +
  theme_minimal()

ggsave("visualizations/elo_distribution.png", p2, width = 10, height = 6, dpi = 300)
cat("✓ Saved: visualizations/elo_distribution.png\n")

# 3. Prediction Calibration Curve
cat("Creating calibration curve...\n")
calibration_data <- game_results %>%
  mutate(prob_bin = cut(home_win_prob, breaks = seq(0, 1, 0.1))) %>%
  group_by(prob_bin) %>%
  summarize(
    predicted = mean(home_win_prob),
    actual = mean(as.numeric(home_win_actual)),
    n_games = n(),
    .groups = "drop"
  ) %>%
  filter(!is.na(prob_bin))

p3 <- ggplot(calibration_data, aes(x = predicted, y = actual)) +
  geom_point(aes(size = n_games), alpha = 0.6, color = "#4575b4") +
  geom_abline(slope = 1, intercept = 0, linetype = "dashed", color = "red") +
  scale_size_continuous(name = "Games") +
  labs(
    title = "Elo Prediction Calibration Curve",
    subtitle = "Perfect calibration = points on dashed line",
    x = "Predicted Win Probability",
    y = "Actual Win Rate"
  ) +
  theme_minimal() +
  coord_fixed(ratio = 1, xlim = c(0, 1), ylim = c(0, 1))

ggsave("visualizations/calibration_curve.png", p3, width = 8, height = 8, dpi = 300)
cat("✓ Saved: visualizations/calibration_curve.png\n")

# 4. Top 10 Teams Comparison
cat("Creating top 10 teams comparison...\n")
top10 <- head(nba_teams, 10)

p4 <- ggplot(top10, aes(x = reorder(team, elo_rating), y = elo_rating)) +
  geom_col(fill = "#1a9850", alpha = 0.8) +
  geom_text(aes(label = round(elo_rating, 0)), hjust = -0.2, size = 4) +
  coord_flip() +
  labs(
    title = "Top 10 NBA Teams by Elo Rating",
    x = "Team",
    y = "Elo Rating"
  ) +
  theme_minimal() +
  theme(plot.title = element_text(size = 14, face = "bold"))

ggsave("visualizations/top10_teams.png", p4, width = 10, height = 6, dpi = 300)
cat("✓ Saved: visualizations/top10_teams.png\n")

# Output final CSVs (already done in Phase 2, but verify)
cat("\n=== Verifying Output Files ===\n")

# Ensure NBA-only rankings are saved
nba_rankings_file <- "data/nba_elo_rankings_final.csv"
write_csv(nba_teams, nba_rankings_file)
cat("✓ NBA rankings (excluding All-Star) saved to:", nba_rankings_file, "\n")

# Create a summary report
cat("\n=== Creating Final Summary Report ===\n")
summary_file <- "data/final_summary.txt"
sink(summary_file)
cat("=== NBA Elo Power Rankings - Final Report ===\n")
cat("Generated:", format(Sys.time(), "%Y-%m-%d %H:%M:%S"), "\n\n")

cat("--- MODEL PERFORMANCE ---\n")
cat("Prediction Accuracy:", round(accuracy * 100, 2), "%\n")
cat("Brier Score:", round(brier_score, 4), "\n")
cat("Log Loss:", round(log_loss, 4), "\n")
cat("Total Games Analyzed:", nrow(game_results), "\n\n")

cat("--- TOP 10 TEAMS (2025-26 Season) ---\n")
for (i in 1:10) {
  cat(sprintf("%2d. %-30s Elo: %.1f\n",
              top10$rank[i],
              top10$team[i],
              top10$elo_rating[i]))
}

cat("\n--- BOTTOM 5 TEAMS ---\n")
bottom5 <- tail(nba_teams, 5)
for (i in 1:5) {
  cat(sprintf("%2d. %-30s Elo: %.1f\n",
              bottom5$rank[i],
              bottom5$team[i],
              bottom5$elo_rating[i]))
}

cat("\n--- SEASON BREAKDOWN ---\n")
print(season_stats)

cat("\n--- TEAM STATISTICS ---\n")
cat("Total NBA Teams:", nrow(nba_teams), "\n")
cat("Highest Elo:", round(max(nba_teams$elo_rating), 1), "(", nba_teams$team[1], ")\n")
cat("Lowest Elo:", round(min(nba_teams$elo_rating), 1), "(", tail(nba_teams$team, 1), ")\n")
cat("Average Elo:", round(mean(nba_teams$elo_rating), 1), "\n")
cat("Elo Range:", round(max(nba_teams$elo_rating) - min(nba_teams$elo_rating), 1), "\n")

sink()

cat("✓ Final summary saved to:", summary_file, "\n")

cat("\n=== Phase 3 Complete ===\n")
cat("\nAll outputs saved to:\n")
cat("  - data/nba_elo_rankings_final.csv (NBA teams only)\n")
cat("  - data/nba_elo_rankings.csv (all teams)\n")
cat("  - data/nba_elo_game_predictions.csv\n")
cat("  - data/final_summary.txt\n")
cat("  - visualizations/*.png (4 charts)\n")
