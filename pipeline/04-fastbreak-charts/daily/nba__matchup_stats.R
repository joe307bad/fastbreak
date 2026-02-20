#!/usr/bin/env Rscript

library(hoopR)
library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)
library(lubridate)

# Script runs in production mode by default

# Constants
MIN_GAMES_PLAYED <- 10
CURRENT_YEAR <- as.numeric(format(Sys.Date(), "%Y"))
CURRENT_MONTH <- as.numeric(format(Sys.Date(), "%m"))

# NBA season starts in October, so if we're in Jan-Sep, use previous year as season start
# NBA_SEASON represents the ending year (e.g., 2025 for 2024-25 season)
NBA_SEASON <- if (CURRENT_MONTH >= 10) CURRENT_YEAR + 1 else CURRENT_YEAR
NBA_SEASON_STRING <- paste0(NBA_SEASON - 1, "-", substr(NBA_SEASON, 3, 4))

# Number of days to look ahead for matchups
DAYS_AHEAD <- 7

# Helper function to create tied ranks
tied_rank <- function(x) {
  numeric_ranks <- rank(x, ties.method = "min", na.last = "keep")
  rank_counts <- table(numeric_ranks[!is.na(numeric_ranks)])

  display_ranks <- sapply(numeric_ranks, function(r) {
    if (is.na(r)) {
      return(NA_character_)
    }
    if (rank_counts[as.character(r)] > 1) {
      paste0("T", r)
    } else {
      as.character(r)
    }
  })

  return(list(rank = numeric_ranks, rankDisplay = display_ranks))
}

# Helper function for API rate limiting
add_api_delay <- function() {
  Sys.sleep(0.5)
}

cat("=== Loading NBA data for", NBA_SEASON_STRING, "season ===\n")

# ============================================================================
# STEP 1: Load team stats and calculate season totals with ranks
# ============================================================================
cat("\n1. Loading team stats from hoopR...\n")

# Load team box scores for the season
team_box <- tryCatch({
  hoopR::load_nba_team_box(seasons = NBA_SEASON)
}, error = function(e) {
  cat("Error loading team box scores:", e$message, "\n")
  stop(e)
})

cat("Loaded", nrow(team_box), "team box score records\n")

# Calculate team season statistics
team_stats <- team_box %>%
  group_by(team_id, team_display_name, team_short_display_name) %>%
  summarise(
    # Get team abbreviation from first row (all rows for a team have same abbreviation)
    team_abbreviation = first(team_abbreviation),
    games_played = n(),

    # Offensive stats
    points = sum(team_score, na.rm = TRUE),
    field_goals_made = sum(field_goals_made, na.rm = TRUE),
    field_goals_attempted = sum(field_goals_attempted, na.rm = TRUE),
    three_point_field_goals_made = sum(three_point_field_goals_made, na.rm = TRUE),
    three_point_field_goals_attempted = sum(three_point_field_goals_attempted, na.rm = TRUE),
    free_throws_made = sum(free_throws_made, na.rm = TRUE),
    free_throws_attempted = sum(free_throws_attempted, na.rm = TRUE),
    offensive_rebounds_total = sum(offensive_rebounds, na.rm = TRUE),
    defensive_rebounds_total = sum(defensive_rebounds, na.rm = TRUE),
    assists_total = sum(assists, na.rm = TRUE),
    steals_total = sum(steals, na.rm = TRUE),
    blocks_total = sum(blocks, na.rm = TRUE),
    turnovers_total = sum(turnovers, na.rm = TRUE),

    # Defensive stats (what opponents did against this team)
    opp_points = sum(opponent_team_score, na.rm = TRUE),

    .groups = "drop"
  ) %>%
  mutate(
    # Per-game averages
    points_per_game = points / games_played,
    fg_pct = field_goals_made / field_goals_attempted * 100,
    three_pt_pct = three_point_field_goals_made / three_point_field_goals_attempted * 100,
    ft_pct = free_throws_made / free_throws_attempted * 100,
    rebounds_per_game = (offensive_rebounds_total + defensive_rebounds_total) / games_played,
    assists_per_game = assists_total / games_played,
    steals_per_game = steals_total / games_played,
    blocks_per_game = blocks_total / games_played,
    turnovers_per_game = turnovers_total / games_played,

    # Defensive per-game averages
    opp_points_per_game = opp_points / games_played
  )

# Calculate opponent stats by matching what the opponent did in games against each team
# For each team, look at what their opponents scored/did when playing against them
opponent_stats <- team_box %>%
  select(game_id, team_id,
         opp_id = opponent_team_id,
         opp_field_goals_made = field_goals_made,
         opp_field_goals_attempted = field_goals_attempted,
         opp_three_point_made = three_point_field_goals_made,
         opp_three_point_attempted = three_point_field_goals_attempted,
         opp_assists = assists,
         opp_rebounds = offensive_rebounds,  # Offensive rebounds by opponent
         opp_turnovers_forced = turnovers) %>%  # Turnovers by opponent (forced by this team)
  # Flip perspective: this is what opponents did AGAINST the opp_id team
  group_by(team_id = opp_id) %>%
  summarise(
    games = n(),
    opp_fg_made = sum(opp_field_goals_made, na.rm = TRUE),
    opp_fg_attempted = sum(opp_field_goals_attempted, na.rm = TRUE),
    opp_three_pt_made = sum(opp_three_point_made, na.rm = TRUE),
    opp_three_pt_attempted = sum(opp_three_point_attempted, na.rm = TRUE),
    opp_assists_total = sum(opp_assists, na.rm = TRUE),
    opp_oreb_total = sum(opp_rebounds, na.rm = TRUE),
    opp_turnovers_total = sum(opp_turnovers_forced, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    opp_fg_pct = opp_fg_made / opp_fg_attempted * 100,
    opp_three_pt_pct = opp_three_pt_made / opp_three_pt_attempted * 100,
    opp_assists_per_game = opp_assists_total / games,
    opp_oreb_per_game = opp_oreb_total / games,
    opp_turnovers_per_game = opp_turnovers_total / games
  )

# Join opponent stats with team stats
team_stats <- team_stats %>%
  left_join(opponent_stats, by = "team_id")

cat("Calculated stats for", nrow(team_stats), "teams\n")

# Get advanced team stats
advanced_stats <- tryCatch({
  hoopR::nba_leaguedashteamstats(
    season = NBA_SEASON_STRING,
    measure_type = "Advanced"
  )$LeagueDashTeamStats
}, error = function(e) {
  cat("Warning: Could not load advanced stats:", e$message, "\n")
  NULL
})

# Get Four Factors stats
four_factors_stats <- tryCatch({
  hoopR::nba_leaguedashteamstats(
    season = NBA_SEASON_STRING,
    measure_type = "Four Factors"
  )$LeagueDashTeamStats
}, error = function(e) {
  cat("Warning: Could not load four factors stats:", e$message, "\n")
  NULL
})

if (!is.null(advanced_stats)) {
  # Join advanced stats with team stats (join on team_display_name since IDs don't match)
  team_stats <- team_stats %>%
    left_join(
      advanced_stats %>%
        select(TEAM_NAME, OFF_RATING, DEF_RATING, NET_RATING, PACE, PIE, AST_PCT, AST_RATIO,
               OREB_PCT, DREB_PCT, REB_PCT, TM_TOV_PCT, EFG_PCT, TS_PCT) %>%
        rename(
          team_display_name = TEAM_NAME,
          offensive_rating = OFF_RATING,
          defensive_rating = DEF_RATING,
          net_rating = NET_RATING,
          pace = PACE,
          pie = PIE,
          ast_pct = AST_PCT,
          ast_ratio = AST_RATIO,
          oreb_pct = OREB_PCT,
          dreb_pct = DREB_PCT,
          reb_pct = REB_PCT,
          tm_tov_pct = TM_TOV_PCT,
          efg_pct = EFG_PCT,
          ts_pct = TS_PCT
        ) %>%
        mutate(
          offensive_rating = as.numeric(offensive_rating),
          defensive_rating = as.numeric(defensive_rating),
          net_rating = as.numeric(net_rating),
          pace = as.numeric(pace),
          pie = as.numeric(pie),
          ast_pct = as.numeric(ast_pct),
          ast_ratio = as.numeric(ast_ratio),
          oreb_pct = as.numeric(oreb_pct),
          dreb_pct = as.numeric(dreb_pct),
          reb_pct = as.numeric(reb_pct),
          tm_tov_pct = as.numeric(tm_tov_pct),
          efg_pct = as.numeric(efg_pct),
          ts_pct = as.numeric(ts_pct)
        ),
      by = "team_display_name"
    )
}

if (!is.null(four_factors_stats)) {
  # Join four factors stats (join on team_display_name since IDs don't match)
  team_stats <- team_stats %>%
    left_join(
      four_factors_stats %>%
        select(TEAM_NAME, FTA_RATE, OPP_EFG_PCT, OPP_FTA_RATE, OPP_TOV_PCT, OPP_OREB_PCT) %>%
        rename(
          team_display_name = TEAM_NAME,
          fta_rate = FTA_RATE,
          opp_efg_pct = OPP_EFG_PCT,
          opp_fta_rate = OPP_FTA_RATE,
          opp_tov_pct = OPP_TOV_PCT,
          opp_oreb_pct = OPP_OREB_PCT
        ) %>%
        mutate(
          fta_rate = as.numeric(fta_rate),
          opp_efg_pct = as.numeric(opp_efg_pct),
          opp_fta_rate = as.numeric(opp_fta_rate),
          opp_tov_pct = as.numeric(opp_tov_pct),
          opp_oreb_pct = as.numeric(opp_oreb_pct)
        ),
      by = "team_display_name"
    )
}

# Calculate ranks for team stats
cat("Calculating team stat ranks...\n")

ppg_ranks <- tied_rank(-team_stats$points_per_game)
fg_pct_ranks <- tied_rank(-team_stats$fg_pct)
three_pt_pct_ranks <- tied_rank(-team_stats$three_pt_pct)
rpg_ranks <- tied_rank(-team_stats$rebounds_per_game)
apg_ranks <- tied_rank(-team_stats$assists_per_game)
spg_ranks <- tied_rank(-team_stats$steals_per_game)
bpg_ranks <- tied_rank(-team_stats$blocks_per_game)
tpg_ranks <- tied_rank(team_stats$turnovers_per_game)  # Lower is better

# Opponent/defensive stat ranks
opp_ppg_ranks <- tied_rank(team_stats$opp_points_per_game)  # Lower is better (points allowed)
opp_fg_pct_ranks <- tied_rank(team_stats$opp_fg_pct)  # Lower is better
opp_three_pt_pct_ranks <- tied_rank(team_stats$opp_three_pt_pct)  # Lower is better
opp_apg_ranks <- tied_rank(team_stats$opp_assists_per_game)  # Lower is better (assists allowed)
opp_turnovers_pg_ranks <- tied_rank(-team_stats$opp_turnovers_per_game)  # Higher is better (forcing turnovers)

team_stats <- team_stats %>%
  mutate(
    points_per_game_rank = ppg_ranks$rank,
    points_per_game_rankDisplay = ppg_ranks$rankDisplay,
    fg_pct_rank = fg_pct_ranks$rank,
    fg_pct_rankDisplay = fg_pct_ranks$rankDisplay,
    three_pt_pct_rank = three_pt_pct_ranks$rank,
    three_pt_pct_rankDisplay = three_pt_pct_ranks$rankDisplay,
    rebounds_per_game_rank = rpg_ranks$rank,
    rebounds_per_game_rankDisplay = rpg_ranks$rankDisplay,
    assists_per_game_rank = apg_ranks$rank,
    assists_per_game_rankDisplay = apg_ranks$rankDisplay,
    steals_per_game_rank = spg_ranks$rank,
    steals_per_game_rankDisplay = spg_ranks$rankDisplay,
    blocks_per_game_rank = bpg_ranks$rank,
    blocks_per_game_rankDisplay = bpg_ranks$rankDisplay,
    turnovers_per_game_rank = tpg_ranks$rank,
    turnovers_per_game_rankDisplay = tpg_ranks$rankDisplay,

    # Opponent/defensive stat ranks
    opp_points_per_game_rank = opp_ppg_ranks$rank,
    opp_points_per_game_rankDisplay = opp_ppg_ranks$rankDisplay,
    opp_fg_pct_rank = opp_fg_pct_ranks$rank,
    opp_fg_pct_rankDisplay = opp_fg_pct_ranks$rankDisplay,
    opp_three_pt_pct_rank = opp_three_pt_pct_ranks$rank,
    opp_three_pt_pct_rankDisplay = opp_three_pt_pct_ranks$rankDisplay,
    opp_assists_per_game_rank = opp_apg_ranks$rank,
    opp_assists_per_game_rankDisplay = opp_apg_ranks$rankDisplay,
    opp_turnovers_per_game_rank = opp_turnovers_pg_ranks$rank,
    opp_turnovers_per_game_rankDisplay = opp_turnovers_pg_ranks$rankDisplay
  )

if (!is.null(advanced_stats)) {
  off_rating_ranks <- tied_rank(-team_stats$offensive_rating)
  def_rating_ranks <- tied_rank(team_stats$defensive_rating)  # Lower is better
  net_rating_ranks <- tied_rank(-team_stats$net_rating)
  pace_ranks <- tied_rank(-team_stats$pace)
  efg_pct_ranks <- tied_rank(-team_stats$efg_pct)
  ts_pct_ranks <- tied_rank(-team_stats$ts_pct)
  oreb_pct_ranks <- tied_rank(-team_stats$oreb_pct)
  tov_pct_ranks <- tied_rank(team_stats$tm_tov_pct)  # Lower is better

  team_stats <- team_stats %>%
    mutate(
      offensive_rating_rank = off_rating_ranks$rank,
      offensive_rating_rankDisplay = off_rating_ranks$rankDisplay,
      defensive_rating_rank = def_rating_ranks$rank,
      defensive_rating_rankDisplay = def_rating_ranks$rankDisplay,
      net_rating_rank = net_rating_ranks$rank,
      net_rating_rankDisplay = net_rating_ranks$rankDisplay,
      pace_rank = pace_ranks$rank,
      pace_rankDisplay = pace_ranks$rankDisplay,
      efg_pct_rank = efg_pct_ranks$rank,
      efg_pct_rankDisplay = efg_pct_ranks$rankDisplay,
      ts_pct_rank = ts_pct_ranks$rank,
      ts_pct_rankDisplay = ts_pct_ranks$rankDisplay,
      oreb_pct_rank = oreb_pct_ranks$rank,
      oreb_pct_rankDisplay = oreb_pct_ranks$rankDisplay,
      tm_tov_pct_rank = tov_pct_ranks$rank,
      tm_tov_pct_rankDisplay = tov_pct_ranks$rankDisplay
    )
}

if (!is.null(four_factors_stats)) {
  fta_rate_ranks <- tied_rank(-team_stats$fta_rate)
  opp_efg_pct_ranks <- tied_rank(team_stats$opp_efg_pct)  # Lower is better (opponent stat)
  opp_fta_rate_ranks <- tied_rank(team_stats$opp_fta_rate)  # Lower is better (opponent stat)
  opp_tov_pct_ranks <- tied_rank(-team_stats$opp_tov_pct)  # Higher is better (forcing turnovers)
  opp_oreb_pct_ranks <- tied_rank(team_stats$opp_oreb_pct)  # Lower is better (opponent stat)

  team_stats <- team_stats %>%
    mutate(
      fta_rate_rank = fta_rate_ranks$rank,
      fta_rate_rankDisplay = fta_rate_ranks$rankDisplay,
      opp_efg_pct_rank = opp_efg_pct_ranks$rank,
      opp_efg_pct_rankDisplay = opp_efg_pct_ranks$rankDisplay,
      opp_fta_rate_rank = opp_fta_rate_ranks$rank,
      opp_fta_rate_rankDisplay = opp_fta_rate_ranks$rankDisplay,
      opp_tov_pct_rank = opp_tov_pct_ranks$rank,
      opp_tov_pct_rankDisplay = opp_tov_pct_ranks$rankDisplay,
      opp_oreb_pct_rank = opp_oreb_pct_ranks$rank,
      opp_oreb_pct_rankDisplay = opp_oreb_pct_ranks$rankDisplay
    )
}

# ============================================================================
# STEP 1b: Calculate cumulative net rating and weekly efficiency by week
# ============================================================================
cat("\n1b. Calculating cumulative net rating and weekly efficiency...\n")

# Calculate per-game net rating and group by week
# Net Rating = Offensive Rating - Defensive Rating (per 100 possessions)
# Possessions ≈ FGA - ORB + TOV + (0.44 * FTA)
# NBA season starts in October, so we use the season start year as reference
season_start_date <- as.Date(paste0(NBA_SEASON - 1, "-10-01"))

# First, calculate per-game ratings using simple possession estimate
game_ratings <- team_box %>%
  mutate(
    game_date_parsed = as.Date(game_date),
    week_num = as.integer(floor(difftime(game_date_parsed, season_start_date, units = "weeks")) + 1),
    # Simple possession estimate: FGA - ORB + TOV + 0.44*FTA
    possessions = field_goals_attempted - offensive_rebounds + turnovers + (0.44 * free_throws_attempted),
    # Calculate offensive and defensive ratings (per 100 possessions)
    off_rating = (team_score / possessions) * 100,
    def_rating = (opponent_team_score / possessions) * 100,
    # Net rating for this game
    game_net_rating = off_rating - def_rating
  ) %>%
  filter(week_num > 0, possessions > 0)

# Calculate cumulative average net rating by week
cum_net_rating_by_team <- game_ratings %>%
  group_by(team_abbreviation) %>%
  arrange(game_date_parsed) %>%
  mutate(
    # Running average net rating through each game
    cum_avg_net_rating = cummean(game_net_rating)
  ) %>%
  # Get the last game of each week to represent that week's cumulative average
  group_by(team_abbreviation, week_num) %>%
  slice_tail(n = 1) %>%
  ungroup() %>%
  select(team_abbreviation, week_num, cum_net_rating = cum_avg_net_rating)

cat("Calculated cumulative net rating for", length(unique(cum_net_rating_by_team$team_abbreviation)), "teams\n")

# Calculate the #10 ranked cumulative net rating for each week (for reference line on chart)
# This represents the "playoff cutoff" level - top 10 teams have positive ratings generally
tenth_net_rating_by_week <- cum_net_rating_by_team %>%
  group_by(week_num) %>%
  arrange(desc(cum_net_rating)) %>%
  slice(10) %>%  # Get the 10th best team's rating
  ungroup() %>%
  select(week_num, tenth_net_rating = cum_net_rating)

cat("Calculated #10 net rating reference line for", nrow(tenth_net_rating_by_week), "weeks\n")

# Calculate weekly efficiency (off_rating and def_rating per week) - last 10 weeks
# This averages the ratings for all games within each week
weekly_efficiency <- game_ratings %>%
  group_by(team_abbreviation, week_num) %>%
  summarise(
    off_rating = mean(off_rating, na.rm = TRUE),
    def_rating = mean(def_rating, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  # Keep only last 10 weeks per team
  group_by(team_abbreviation) %>%
  arrange(desc(week_num)) %>%
  slice_head(n = 10) %>%
  arrange(week_num) %>%
  ungroup()

cat("Calculated weekly efficiency for", length(unique(weekly_efficiency$team_abbreviation)), "teams\n")

# Calculate league-wide efficiency stats for consistent scatter plot scaling
# This provides: avg off/def rating, min/max for axis bounds across all teams
league_efficiency_stats <- weekly_efficiency %>%
  summarise(
    avg_off_rating = mean(off_rating, na.rm = TRUE),
    avg_def_rating = mean(def_rating, na.rm = TRUE),
    min_off_rating = min(off_rating, na.rm = TRUE),
    max_off_rating = max(off_rating, na.rm = TRUE),
    min_def_rating = min(def_rating, na.rm = TRUE),
    max_def_rating = max(def_rating, na.rm = TRUE)
  )

cat("League avg off rating:", round(league_efficiency_stats$avg_off_rating, 1),
    "def rating:", round(league_efficiency_stats$avg_def_rating, 1), "\n")

# ============================================================================
# STEP 1c: Calculate 1-month trend rankings (last 4 weeks)
# ============================================================================
cat("\n1c. Calculating 1-month trend rankings (last 4 weeks)...\n")

# Get current week number
current_week <- max(game_ratings$week_num, na.rm = TRUE)
month_start_week <- current_week - 3  # Last 4 weeks including current

# Filter to last 4 weeks of games
month_games <- game_ratings %>%
  filter(week_num >= month_start_week)

cat("Month trend: weeks", month_start_week, "to", current_week, "\n")

# Calculate per-team stats for the last 4 weeks
month_trend_stats <- month_games %>%
  group_by(team_abbreviation) %>%
  summarise(
    games_played = n(),
    # Record
    wins = sum(team_score > opponent_team_score, na.rm = TRUE),
    losses = sum(team_score < opponent_team_score, na.rm = TRUE),
    # Ratings (average over the month)
    avg_net_rating = mean(game_net_rating, na.rm = TRUE),
    avg_off_rating = mean(off_rating, na.rm = TRUE),
    avg_def_rating = mean(def_rating, na.rm = TRUE),
    # Per game stats
    points_per_game = mean(team_score, na.rm = TRUE),
    assists_per_game = mean(assists, na.rm = TRUE),
    turnovers_per_game = mean(turnovers, na.rm = TRUE),
    # Turnovers forced (from opponent box scores - need to calculate differently)
    .groups = "drop"
  )

# Get opponent turnovers (turnovers forced by this team) from opponent perspective
opponent_turnovers_month <- month_games %>%
  group_by(opponent_team_abbreviation = team_abbreviation) %>%
  summarise(
    opp_turnovers_committed = mean(turnovers, na.rm = TRUE),
    .groups = "drop"
  )

# Join with month_trend_stats - turnovers forced = opponent's turnovers when playing against us
# We need to flip the perspective: look at what opponents did AGAINST each team
turnovers_forced <- team_box %>%
  mutate(
    game_date_parsed = as.Date(game_date),
    week_num = as.integer(floor(difftime(game_date_parsed, season_start_date, units = "weeks")) + 1)
  ) %>%
  filter(week_num >= month_start_week, week_num <= current_week) %>%
  group_by(defending_team = opponent_team_abbreviation) %>%
  summarise(
    turnovers_forced_pg = mean(turnovers, na.rm = TRUE),  # Turnovers by the opponent = forced by defending_team
    .groups = "drop"
  )

# Join turnovers forced
month_trend_stats <- month_trend_stats %>%
  left_join(turnovers_forced, by = c("team_abbreviation" = "defending_team")) %>%
  mutate(
    # Turnover differential: forced - committed (higher is better)
    turnover_diff = coalesce(turnovers_forced_pg, 0) - turnovers_per_game
  )

# Calculate rankings for each stat (across all 30 teams)
# Net rating (higher is better)
net_rating_month_ranks <- tied_rank(-month_trend_stats$avg_net_rating)
month_trend_stats$net_rating_rank <- net_rating_month_ranks$rank
month_trend_stats$net_rating_rankDisplay <- net_rating_month_ranks$rankDisplay

# Offensive rating (higher is better)
off_rating_month_ranks <- tied_rank(-month_trend_stats$avg_off_rating)
month_trend_stats$off_rating_rank <- off_rating_month_ranks$rank
month_trend_stats$off_rating_rankDisplay <- off_rating_month_ranks$rankDisplay

# Defensive rating (lower is better)
def_rating_month_ranks <- tied_rank(month_trend_stats$avg_def_rating)
month_trend_stats$def_rating_rank <- def_rating_month_ranks$rank
month_trend_stats$def_rating_rankDisplay <- def_rating_month_ranks$rankDisplay

# Points per game (higher is better)
ppg_month_ranks <- tied_rank(-month_trend_stats$points_per_game)
month_trend_stats$ppg_rank <- ppg_month_ranks$rank
month_trend_stats$ppg_rankDisplay <- ppg_month_ranks$rankDisplay

# Assists per game (higher is better)
apg_month_ranks <- tied_rank(-month_trend_stats$assists_per_game)
month_trend_stats$apg_rank <- apg_month_ranks$rank
month_trend_stats$apg_rankDisplay <- apg_month_ranks$rankDisplay

# Turnovers per game (lower is better)
tpg_month_ranks <- tied_rank(month_trend_stats$turnovers_per_game)
month_trend_stats$tpg_rank <- tpg_month_ranks$rank
month_trend_stats$tpg_rankDisplay <- tpg_month_ranks$rankDisplay

# Turnover differential (higher is better)
tov_diff_month_ranks <- tied_rank(-month_trend_stats$turnover_diff)
month_trend_stats$tov_diff_rank <- tov_diff_month_ranks$rank
month_trend_stats$tov_diff_rankDisplay <- tov_diff_month_ranks$rankDisplay

# Win percentage for record ranking (higher is better)
month_trend_stats <- month_trend_stats %>%
  mutate(win_pct = wins / (wins + losses))
record_month_ranks <- tied_rank(-month_trend_stats$win_pct)
month_trend_stats$record_rank <- record_month_ranks$rank
month_trend_stats$record_rankDisplay <- record_month_ranks$rankDisplay

cat("Calculated month trend rankings for", nrow(month_trend_stats), "teams\n")

# ============================================================================
# STEP 2: Load player stats and calculate ranks
# ============================================================================
cat("\n2. Loading player stats...\n")

player_box <- tryCatch({
  hoopR::load_nba_player_box(seasons = NBA_SEASON)
}, error = function(e) {
  cat("Error loading player box scores:", e$message, "\n")
  stop(e)
})

cat("Loaded", nrow(player_box), "player box score records\n")

# Calculate player season statistics
player_stats <- player_box %>%
  group_by(athlete_id, athlete_display_name, team_id, athlete_position_abbreviation) %>%
  summarise(
    games_played = n(),

    # Counting stats (using .data$ to avoid conflicts with base R functions)
    points_total = sum(.data$points, na.rm = TRUE),
    rebounds_total = sum(.data$rebounds, na.rm = TRUE),
    assists_total = sum(.data$assists, na.rm = TRUE),
    steals_total = sum(.data$steals, na.rm = TRUE),
    blocks_total = sum(.data$blocks, na.rm = TRUE),
    turnovers_total = sum(.data$turnovers, na.rm = TRUE),
    field_goals_made = sum(.data$field_goals_made, na.rm = TRUE),
    field_goals_attempted = sum(.data$field_goals_attempted, na.rm = TRUE),
    three_point_field_goals_made = sum(.data$three_point_field_goals_made, na.rm = TRUE),
    three_point_field_goals_attempted = sum(.data$three_point_field_goals_attempted, na.rm = TRUE),
    free_throws_made = sum(.data$free_throws_made, na.rm = TRUE),
    free_throws_attempted = sum(.data$free_throws_attempted, na.rm = TRUE),
    minutes_total = sum(.data$minutes, na.rm = TRUE),

    .groups = "drop"
  ) %>%
  filter(games_played >= MIN_GAMES_PLAYED) %>%
  mutate(
    # Per-game averages
    points_per_game = points_total / games_played,
    rebounds_per_game = rebounds_total / games_played,
    assists_per_game = assists_total / games_played,
    steals_per_game = steals_total / games_played,
    blocks_per_game = blocks_total / games_played,
    turnovers_per_game = turnovers_total / games_played,
    minutes_per_game = minutes_total / games_played,

    # Shooting percentages
    fg_pct = if_else(field_goals_attempted > 0,
                     field_goals_made / field_goals_attempted * 100,
                     NA_real_),
    three_pt_pct = if_else(three_point_field_goals_attempted > 0,
                           three_point_field_goals_made / three_point_field_goals_attempted * 100,
                           NA_real_),
    ft_pct = if_else(free_throws_attempted > 0,
                     free_throws_made / free_throws_attempted * 100,
                     NA_real_)
  )

cat("Calculated stats for", nrow(player_stats), "players with", MIN_GAMES_PLAYED, "+ games\n")

# Convert athlete_id to character for joins
player_stats <- player_stats %>%
  mutate(athlete_id = as.character(athlete_id))

# Get advanced player stats
cat("Loading advanced player stats...\n")
player_advanced_stats <- tryCatch({
  hoopR::nba_leaguedashplayerstats(
    season = NBA_SEASON_STRING,
    measure_type = "Advanced",
    per_mode = "PerGame"
  )$LeagueDashPlayerStats
}, error = function(e) {
  cat("Warning: Could not load advanced player stats:", e$message, "\n")
  NULL
})

# Get player usage stats
player_usage_stats <- tryCatch({
  hoopR::nba_leaguedashplayerstats(
    season = NBA_SEASON_STRING,
    measure_type = "Usage",
    per_mode = "PerGame"
  )$LeagueDashPlayerStats
}, error = function(e) {
  cat("Warning: Could not load player usage stats:", e$message, "\n")
  NULL
})

# Join advanced stats with player stats
# Try to join on player ID first (after converting to character), fall back to name matching
if (!is.null(player_advanced_stats)) {
  # Add debugging to see which players aren't matching
  cat("Players in player_stats:", nrow(player_stats), "\n")
  cat("Players in player_advanced_stats:", nrow(player_advanced_stats), "\n")

  # Debug: Check sample IDs
  cat("Sample athlete_ids from player_stats:", paste(head(player_stats$athlete_id, 3), collapse=", "), "\n")
  cat("Sample PLAYER_IDs from advanced stats:", paste(head(player_advanced_stats$PLAYER_ID, 3), collapse=", "), "\n")

  # Prepare advanced stats with both ID and name for joining
  advanced_for_join <- player_advanced_stats %>%
    select(PLAYER_ID, PLAYER_NAME, OFF_RATING, DEF_RATING, NET_RATING, PIE, TS_PCT, EFG_PCT, AST_PCT, REB_PCT) %>%
    mutate(
      athlete_id = as.character(PLAYER_ID),
      athlete_display_name = PLAYER_NAME,  # Keep name for fallback join
      player_off_rating = as.numeric(OFF_RATING),
      player_def_rating = as.numeric(DEF_RATING),
      player_net_rating = as.numeric(NET_RATING),
      player_pie = as.numeric(PIE),
      player_ts_pct = as.numeric(TS_PCT),
      player_efg_pct = as.numeric(EFG_PCT),
      player_ast_pct = as.numeric(AST_PCT),
      player_reb_pct = as.numeric(REB_PCT)
    ) %>%
    select(athlete_id, athlete_display_name, player_off_rating, player_def_rating, player_net_rating,
           player_pie, player_ts_pct, player_efg_pct, player_ast_pct, player_reb_pct)

  # Try join on player ID first
  player_stats <- player_stats %>%
    left_join(advanced_for_join %>% select(-athlete_display_name), by = "athlete_id", suffix = c("", "_adv"))

  # Check how many players got matched by ID
  matched_count <- sum(!is.na(player_stats$player_pie))
  cat("Players matched with advanced stats by ID:", matched_count, "out of", nrow(player_stats), "\n")

  # If ID join didn't work well, try name-based join for unmatched players
  if (matched_count == 0) {
    cat("ID join failed, falling back to name-based join...\n")
    player_stats <- player_stats %>%
      select(-starts_with("player_off_rating"), -starts_with("player_def_rating"),
             -starts_with("player_net_rating"), -starts_with("player_pie"),
             -starts_with("player_ts_pct"), -starts_with("player_efg_pct"),
             -starts_with("player_ast_pct"), -starts_with("player_reb_pct")) %>%
      left_join(advanced_for_join %>% select(-athlete_id), by = "athlete_display_name")

    matched_count <- sum(!is.na(player_stats$player_pie))
    cat("Players matched with advanced stats by name:", matched_count, "out of", nrow(player_stats), "\n")

    # For remaining unmatched players, try normalized name matching (remove accents/special chars)
    # Create a better normalization function that removes apostrophes and special chars
    normalize_name <- function(name) {
      # Convert accents to ASCII, remove apostrophes/special chars, lowercase
      name %>%
        iconv(to='ASCII//TRANSLIT') %>%
        gsub("[^A-Za-z ]", "", .) %>%  # Remove everything except letters and spaces
        tolower() %>%
        trimws()
    }

    unmatched_players <- player_stats %>%
      filter(is.na(player_pie)) %>%
      select(athlete_id, athlete_display_name) %>%
      mutate(name_normalized = normalize_name(athlete_display_name))

    advanced_normalized <- advanced_for_join %>%
      select(-athlete_id) %>%
      mutate(name_normalized = normalize_name(athlete_display_name))

    # Join on normalized names for unmatched players
    normalized_matches <- unmatched_players %>%
      left_join(advanced_normalized %>% select(-athlete_display_name), by = "name_normalized") %>%
      filter(!is.na(player_pie)) %>%
      select(athlete_id, player_off_rating, player_def_rating, player_net_rating,
             player_pie, player_ts_pct, player_efg_pct, player_ast_pct, player_reb_pct)

    # Update player_stats with normalized matches
    if (nrow(normalized_matches) > 0) {
      # Remove the null values for these players
      player_stats <- player_stats %>%
        left_join(normalized_matches, by = "athlete_id", suffix = c("", "_norm")) %>%
        mutate(
          player_off_rating = coalesce(player_off_rating, player_off_rating_norm),
          player_def_rating = coalesce(player_def_rating, player_def_rating_norm),
          player_net_rating = coalesce(player_net_rating, player_net_rating_norm),
          player_pie = coalesce(player_pie, player_pie_norm),
          player_ts_pct = coalesce(player_ts_pct, player_ts_pct_norm),
          player_efg_pct = coalesce(player_efg_pct, player_efg_pct_norm),
          player_ast_pct = coalesce(player_ast_pct, player_ast_pct_norm),
          player_reb_pct = coalesce(player_reb_pct, player_reb_pct_norm)
        ) %>%
        select(-ends_with("_norm"))

      matched_count <- sum(!is.na(player_stats$player_pie))
      cat("After normalized name matching:", matched_count, "out of", nrow(player_stats), "\n")
    }
  }

  # Print a few examples of unmatched players for debugging
  unmatched <- player_stats %>%
    filter(is.na(player_pie)) %>%
    select(athlete_id, athlete_display_name, games_played) %>%
    head(5)
  if (nrow(unmatched) > 0) {
    cat("Sample unmatched players:\n")
    print(unmatched)
  }
}

# Join usage stats (using player ID for better matching, fallback to name)
if (!is.null(player_usage_stats)) {
  usage_for_join <- player_usage_stats %>%
    select(PLAYER_ID, PLAYER_NAME, USG_PCT) %>%
    mutate(
      athlete_id = as.character(PLAYER_ID),
      athlete_display_name = PLAYER_NAME,
      player_usg_pct = as.numeric(USG_PCT)
    ) %>%
    select(athlete_id, athlete_display_name, player_usg_pct)

  # Try ID join first
  player_stats <- player_stats %>%
    left_join(usage_for_join %>% select(-athlete_display_name), by = "athlete_id", suffix = c("", "_usg"))

  # Check how many players got matched
  matched_count <- sum(!is.na(player_stats$player_usg_pct))
  cat("Players matched with usage stats by ID:", matched_count, "out of", nrow(player_stats), "\n")

  # If ID join didn't work, try name join
  if (matched_count == 0) {
    cat("ID join failed, falling back to name-based join for usage stats...\n")
    player_stats <- player_stats %>%
      select(-player_usg_pct) %>%
      left_join(usage_for_join %>% select(-athlete_id), by = "athlete_display_name")

    matched_count <- sum(!is.na(player_stats$player_usg_pct))
    cat("Players matched with usage stats by name:", matched_count, "out of", nrow(player_stats), "\n")

    # For remaining unmatched players, try normalized name matching
    # Use same normalization function as advanced stats
    normalize_name <- function(name) {
      # Convert accents to ASCII, remove apostrophes/special chars, lowercase
      name %>%
        iconv(to='ASCII//TRANSLIT') %>%
        gsub("[^A-Za-z ]", "", .) %>%  # Remove everything except letters and spaces
        tolower() %>%
        trimws()
    }

    unmatched_players <- player_stats %>%
      filter(is.na(player_usg_pct)) %>%
      select(athlete_id, athlete_display_name) %>%
      mutate(name_normalized = normalize_name(athlete_display_name))

    usage_normalized <- usage_for_join %>%
      select(-athlete_id) %>%
      mutate(name_normalized = normalize_name(athlete_display_name))

    # Join on normalized names for unmatched players
    normalized_matches <- unmatched_players %>%
      left_join(usage_normalized %>% select(-athlete_display_name), by = "name_normalized") %>%
      filter(!is.na(player_usg_pct)) %>%
      select(athlete_id, player_usg_pct)

    # Update player_stats with normalized matches
    if (nrow(normalized_matches) > 0) {
      player_stats <- player_stats %>%
        left_join(normalized_matches, by = "athlete_id", suffix = c("", "_norm")) %>%
        mutate(player_usg_pct = coalesce(player_usg_pct, player_usg_pct_norm)) %>%
        select(-ends_with("_norm"))

      matched_count <- sum(!is.na(player_stats$player_usg_pct))
      cat("After normalized name matching:", matched_count, "out of", nrow(player_stats), "\n")
    }
  }
}

# Calculate player ranks
cat("Calculating player stat ranks...\n")

player_ppg_ranks <- tied_rank(-player_stats$points_per_game)
player_rpg_ranks <- tied_rank(-player_stats$rebounds_per_game)
player_apg_ranks <- tied_rank(-player_stats$assists_per_game)
player_spg_ranks <- tied_rank(-player_stats$steals_per_game)
player_bpg_ranks <- tied_rank(-player_stats$blocks_per_game)
player_fg_pct_ranks <- tied_rank(-player_stats$fg_pct)
player_three_pt_pct_ranks <- tied_rank(-player_stats$three_pt_pct)
player_mpg_ranks <- tied_rank(-player_stats$minutes_per_game)
player_games_ranks <- tied_rank(-player_stats$games_played)

player_stats <- player_stats %>%
  mutate(
    points_per_game_rank = player_ppg_ranks$rank,
    points_per_game_rankDisplay = player_ppg_ranks$rankDisplay,
    rebounds_per_game_rank = player_rpg_ranks$rank,
    rebounds_per_game_rankDisplay = player_rpg_ranks$rankDisplay,
    assists_per_game_rank = player_apg_ranks$rank,
    assists_per_game_rankDisplay = player_apg_ranks$rankDisplay,
    steals_per_game_rank = player_spg_ranks$rank,
    steals_per_game_rankDisplay = player_spg_ranks$rankDisplay,
    blocks_per_game_rank = player_bpg_ranks$rank,
    blocks_per_game_rankDisplay = player_bpg_ranks$rankDisplay,
    fg_pct_rank = player_fg_pct_ranks$rank,
    fg_pct_rankDisplay = player_fg_pct_ranks$rankDisplay,
    three_pt_pct_rank = player_three_pt_pct_ranks$rank,
    three_pt_pct_rankDisplay = player_three_pt_pct_ranks$rankDisplay,
    minutes_per_game_rank = player_mpg_ranks$rank,
    minutes_per_game_rankDisplay = player_mpg_ranks$rankDisplay,
    games_played_rank = player_games_ranks$rank,
    games_played_rankDisplay = player_games_ranks$rankDisplay
  )

# Add ranks for advanced player stats
if (!is.null(player_advanced_stats)) {
  player_ts_pct_ranks <- tied_rank(-player_stats$player_ts_pct)
  player_efg_pct_ranks <- tied_rank(-player_stats$player_efg_pct)
  player_pie_ranks <- tied_rank(-player_stats$player_pie)

  player_stats <- player_stats %>%
    mutate(
      player_ts_pct_rank = player_ts_pct_ranks$rank,
      player_ts_pct_rankDisplay = player_ts_pct_ranks$rankDisplay,
      player_efg_pct_rank = player_efg_pct_ranks$rank,
      player_efg_pct_rankDisplay = player_efg_pct_ranks$rankDisplay,
      player_pie_rank = player_pie_ranks$rank,
      player_pie_rankDisplay = player_pie_ranks$rankDisplay
    )
}

if (!is.null(player_usage_stats)) {
  player_usg_pct_ranks <- tied_rank(-player_stats$player_usg_pct)

  player_stats <- player_stats %>%
    mutate(
      player_usg_pct_rank = player_usg_pct_ranks$rank,
      player_usg_pct_rankDisplay = player_usg_pct_ranks$rankDisplay
    )
}

# ============================================================================
# STEP 3: Get team standings (records, division/conference rankings)
# ============================================================================
cat("\n3. Fetching team standings...\n")

# Fetch standings from ESPN API
standings_url <- "https://site.api.espn.com/apis/v2/sports/basketball/nba/standings"
add_api_delay()

standings_data <- tryCatch({
  standings_resp <- GET(standings_url)
  if (status_code(standings_resp) == 200) {
    content(standings_resp, as = "parsed")
  } else {
    cat("Warning: Failed to fetch standings (status:", status_code(standings_resp), ")\n")
    NULL
  }
}, error = function(e) {
  cat("Warning: Could not load standings:", e$message, "\n")
  NULL
})

# Process standings data
team_standings <- NULL
if (!is.null(standings_data) && !is.null(standings_data$children)) {
  # standings_data$children contains conferences (Eastern, Western)
  all_teams_standings <- list()

  for (conference in standings_data$children) {
    conference_name <- conference$name  # "Eastern Conference" or "Western Conference"
    conference_abbrev <- conference$abbreviation  # "east" or "west"

    if (!is.null(conference$standings) && !is.null(conference$standings$entries)) {
      # Conference rank is the position in the standings
      for (conf_rank_idx in seq_along(conference$standings$entries)) {
        team <- conference$standings$entries[[conf_rank_idx]]

        # Extract stats by name for robustness
        get_stat_value <- function(stats_list, stat_name) {
          for (stat in stats_list) {
            if (!is.null(stat$name) && stat$name == stat_name) {
              return(as.numeric(stat$value))
            }
          }
          return(NA)
        }

        team_info <- list(
          team_id = team$team$id,
          team_name = team$team$displayName,
          team_abbrev = team$team$abbreviation,
          wins = get_stat_value(team$stats, "wins"),
          losses = get_stat_value(team$stats, "losses"),
          win_pct = get_stat_value(team$stats, "winPercent"),
          games_back = get_stat_value(team$stats, "gamesBehind"),
          conference = conference_abbrev
        )
        all_teams_standings[[length(all_teams_standings) + 1]] <- team_info
      }
    }
  }

  # Convert to data frame
  if (length(all_teams_standings) > 0) {
    team_standings <- bind_rows(all_teams_standings)

    # Calculate conference rank based on wins (descending) and losses (ascending)
    team_standings <- team_standings %>%
      arrange(conference, desc(wins), losses) %>%
      group_by(conference) %>%
      mutate(conference_rank = row_number()) %>%
      ungroup()

    cat("Loaded standings for", nrow(team_standings), "teams\n")

    # Join standings with team_stats
    team_stats <- team_stats %>%
      left_join(
        team_standings %>%
          select(team_abbrev, wins, losses, win_pct, games_back, conference_rank, conference),
        by = c("team_abbreviation" = "team_abbrev")
      )

    cat("Merged standings with team stats\n")
  }
} else {
  cat("Warning: No standings data available\n")
}

# ============================================================================
# STEP 4: Get upcoming games (next 7 days)
# ============================================================================
cat("\n4. Fetching upcoming NBA games for next", DAYS_AHEAD, "days...\n")

# Get today's date and calculate date range
today <- Sys.Date()
end_date <- today + days(DAYS_AHEAD)

# Fetch schedule from ESPN API
upcoming_games <- list()
current_date <- today

while (current_date <= end_date) {
  date_string <- format(current_date, "%Y%m%d")

  add_api_delay()
  scoreboard_url <- paste0(
    "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=",
    date_string
  )

  cat("Fetching games for", format(current_date, "%Y-%m-%d"), "...\n")

  scoreboard_resp <- tryCatch({
    GET(scoreboard_url)
  }, error = function(e) {
    cat("Error fetching scoreboard for", date_string, ":", e$message, "\n")
    NULL
  })

  if (!is.null(scoreboard_resp) && status_code(scoreboard_resp) == 200) {
    scoreboard_data <- content(scoreboard_resp, as = "parsed")

    if (!is.null(scoreboard_data$events) && length(scoreboard_data$events) > 0) {
      for (event in scoreboard_data$events) {
        game_id <- event$id
        # event$date is in ISO 8601 format (e.g., "2026-01-23T00:00Z")
        # Need to add seconds component for proper Instant parsing
        game_date <- event$date
        # Check if it has seconds (ends with :SS followed by Z or timezone)
        if (grepl("T\\d{2}:\\d{2}Z$", game_date)) {
          # Has HH:MM but no seconds, insert :00 before Z
          game_date <- sub("Z$", ":00Z", game_date)
        }
        game_name <- event$name

        # Extract teams
        if (!is.null(event$competitions) && length(event$competitions) > 0) {
          competition <- event$competitions[[1]]
          teams <- competition$competitors

          # Find home and away teams
          home_team <- NULL
          away_team <- NULL

          for (team in teams) {
            if (team$homeAway == "home") {
              home_team <- team
            } else {
              away_team <- team
            }
          }

          if (!is.null(home_team) && !is.null(away_team)) {
            # Extract venue/location information
            location_data <- NULL
            if (!is.null(competition$venue)) {
              venue <- competition$venue
              stadium_name <- if (!is.null(venue$fullName)) venue$fullName else NA
              city <- if (!is.null(venue$address) && !is.null(venue$address$city)) venue$address$city else NA
              state <- if (!is.null(venue$address) && !is.null(venue$address$state)) venue$address$state else NA
              country <- if (!is.null(venue$address) && !is.null(venue$address$country)) venue$address$country else NA

              # Build location string components
              location_parts <- c()
              if (!is.na(stadium_name)) location_parts <- c(location_parts, stadium_name)
              if (!is.na(city)) location_parts <- c(location_parts, city)
              if (!is.na(state)) location_parts <- c(location_parts, state)
              if (!is.na(country) && country != "USA") location_parts <- c(location_parts, country)  # Only show country if not USA

              location_string <- paste(location_parts, collapse = ", ")

              location_data <- list(
                stadium = stadium_name,
                city = city,
                state = state,
                country = country,
                fullLocation = if (length(location_parts) > 0) location_string else NA
              )
            }

            # Extract odds if available
            odds_data <- NULL
            if (!is.null(competition$odds) && length(competition$odds) > 0) {
              odds <- competition$odds[[1]]

              # Extract home team spread (preferred method)
              home_spread <- NA
              if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$spreadOdds)) {
                home_spread <- as.numeric(odds$homeTeamOdds$spreadOdds)
              } else if (!is.null(odds$spread)) {
                # Fallback: use generic spread field (assumed to be home team's spread per ESPN convention)
                home_spread <- as.numeric(odds$spread)
              }

              odds_data <- list(
                provider = if (!is.null(odds$provider)) odds$provider$name else NA,
                spread = home_spread,  # This is explicitly the home team's spread
                over_under = if (!is.null(odds$overUnder)) as.numeric(odds$overUnder) else NA,
                home_moneyline = if (!is.null(odds$homeTeamOdds)) {
                  if (!is.null(odds$homeTeamOdds$moneyLine)) as.numeric(odds$homeTeamOdds$moneyLine) else NA
                } else NA,
                away_moneyline = if (!is.null(odds$awayTeamOdds)) {
                  if (!is.null(odds$awayTeamOdds$moneyLine)) as.numeric(odds$awayTeamOdds$moneyLine) else NA
                } else NA
              )
            }

            game_info <- list(
              game_id = game_id,
              game_date = game_date,
              game_name = game_name,
              home_team_id = home_team$team$id,
              home_team_name = home_team$team$displayName,
              home_team_abbrev = home_team$team$abbreviation,
              home_team_logo = if (!is.null(home_team$team$logo)) home_team$team$logo else NA,
              away_team_id = away_team$team$id,
              away_team_name = away_team$team$displayName,
              away_team_abbrev = away_team$team$abbreviation,
              away_team_logo = if (!is.null(away_team$team$logo)) away_team$team$logo else NA,
              location = location_data,
              odds = odds_data
            )

            upcoming_games[[length(upcoming_games) + 1]] <- game_info
          }
        }
      }
    }
  }

  current_date <- current_date + days(1)
}

cat("Found", length(upcoming_games), "upcoming games\n")

if (length(upcoming_games) == 0) {
  cat("No upcoming games found. Exiting.\n")
  quit(status = 0)
}

# ============================================================================
# STEP 5: Build matchup data for each game
# ============================================================================
cat("\n5. Building matchup statistics...\n")

# Helper function to build comparison data
build_nba_comparisons <- function(home_stats, away_stats, home_team, away_team) {
  # Define which stats are offensive vs defensive
  # For NBA, offensive rating, net rating, and most counting stats are offensive
  # Defensive rating and opponent stats are defensive

  # Offensive comparison (side-by-side team offense)
  off_comparison <- list()
  off_stats <- list(
    list(key = "pointsPerGame", label = "Points/Game", value_home = home_stats$points_per_game, rank_home = home_stats$points_per_game_rank, rankDisplay_home = home_stats$points_per_game_rankDisplay, value_away = away_stats$points_per_game, rank_away = away_stats$points_per_game_rank, rankDisplay_away = away_stats$points_per_game_rankDisplay),
    list(key = "fieldGoalPct", label = "Field Goal %", value_home = home_stats$fg_pct, rank_home = home_stats$fg_pct_rank, rankDisplay_home = home_stats$fg_pct_rankDisplay, value_away = away_stats$fg_pct, rank_away = away_stats$fg_pct_rank, rankDisplay_away = away_stats$fg_pct_rankDisplay),
    list(key = "threePtPct", label = "3-Point %", value_home = home_stats$three_pt_pct, rank_home = home_stats$three_pt_pct_rank, rankDisplay_home = home_stats$three_pt_pct_rankDisplay, value_away = away_stats$three_pt_pct, rank_away = away_stats$three_pt_pct_rank, rankDisplay_away = away_stats$three_pt_pct_rankDisplay),
    list(key = "assistsPerGame", label = "Assists/Game", value_home = home_stats$assists_per_game, rank_home = home_stats$assists_per_game_rank, rankDisplay_home = home_stats$assists_per_game_rankDisplay, value_away = away_stats$assists_per_game, rank_away = away_stats$assists_per_game_rank, rankDisplay_away = away_stats$assists_per_game_rankDisplay),
    list(key = "reboundsPerGame", label = "Rebounds/Game", value_home = home_stats$rebounds_per_game, rank_home = home_stats$rebounds_per_game_rank, rankDisplay_home = home_stats$rebounds_per_game_rankDisplay, value_away = away_stats$rebounds_per_game, rank_away = away_stats$rebounds_per_game_rank, rankDisplay_away = away_stats$rebounds_per_game_rankDisplay)
  )

  # Add advanced offensive stats if available
  if (!is.na(home_stats$offensive_rating) && !is.na(away_stats$offensive_rating)) {
    off_stats <- c(off_stats, list(
      list(key = "offensiveRating", label = "Offensive Rating", value_home = home_stats$offensive_rating, rank_home = home_stats$offensive_rating_rank, rankDisplay_home = home_stats$offensive_rating_rankDisplay, value_away = away_stats$offensive_rating, rank_away = away_stats$offensive_rating_rank, rankDisplay_away = away_stats$offensive_rating_rankDisplay),
      list(key = "trueShootingPct", label = "True Shooting %", value_home = home_stats$ts_pct, rank_home = home_stats$ts_pct_rank, rankDisplay_home = home_stats$ts_pct_rankDisplay, value_away = away_stats$ts_pct, rank_away = away_stats$ts_pct_rank, rankDisplay_away = away_stats$ts_pct_rankDisplay),
      list(key = "effectiveFgPct", label = "Effective FG %", value_home = home_stats$efg_pct, rank_home = home_stats$efg_pct_rank, rankDisplay_home = home_stats$efg_pct_rankDisplay, value_away = away_stats$efg_pct, rank_away = away_stats$efg_pct_rank, rankDisplay_away = away_stats$efg_pct_rankDisplay)
    ))
  }

  for (stat in off_stats) {
    off_comparison[[stat$key]] <- list(
      label = stat$label,
      home = list(
        value = if (!is.na(stat$value_home)) round(stat$value_home, 2) else NULL,
        rank = if (!is.na(stat$rank_home)) as.integer(stat$rank_home) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_home)) stat$rankDisplay_home else NULL
      ),
      away = list(
        value = if (!is.na(stat$value_away)) round(stat$value_away, 2) else NULL,
        rank = if (!is.na(stat$rank_away)) as.integer(stat$rank_away) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_away)) stat$rankDisplay_away else NULL
      )
    )
  }

  # Defensive comparison (side-by-side team defense)
  def_comparison <- list()
  def_stats <- list(
    list(key = "oppPointsPerGame", label = "Opp Points/Game", value_home = home_stats$opp_points_per_game, rank_home = home_stats$opp_points_per_game_rank, rankDisplay_home = home_stats$opp_points_per_game_rankDisplay, value_away = away_stats$opp_points_per_game, rank_away = away_stats$opp_points_per_game_rank, rankDisplay_away = away_stats$opp_points_per_game_rankDisplay),
    list(key = "oppFieldGoalPct", label = "Opp Field Goal %", value_home = home_stats$opp_fg_pct, rank_home = home_stats$opp_fg_pct_rank, rankDisplay_home = home_stats$opp_fg_pct_rankDisplay, value_away = away_stats$opp_fg_pct, rank_away = away_stats$opp_fg_pct_rank, rankDisplay_away = away_stats$opp_fg_pct_rankDisplay),
    list(key = "oppThreePtPct", label = "Opp 3-Point %", value_home = home_stats$opp_three_pt_pct, rank_home = home_stats$opp_three_pt_pct_rank, rankDisplay_home = home_stats$opp_three_pt_pct_rankDisplay, value_away = away_stats$opp_three_pt_pct, rank_away = away_stats$opp_three_pt_pct_rank, rankDisplay_away = away_stats$opp_three_pt_pct_rankDisplay),
    list(key = "stealsPerGame", label = "Steals/Game", value_home = home_stats$steals_per_game, rank_home = home_stats$steals_per_game_rank, rankDisplay_home = home_stats$steals_per_game_rankDisplay, value_away = away_stats$steals_per_game, rank_away = away_stats$steals_per_game_rank, rankDisplay_away = away_stats$steals_per_game_rankDisplay),
    list(key = "blocksPerGame", label = "Blocks/Game", value_home = home_stats$blocks_per_game, rank_home = home_stats$blocks_per_game_rank, rankDisplay_home = home_stats$blocks_per_game_rankDisplay, value_away = away_stats$blocks_per_game, rank_away = away_stats$blocks_per_game_rank, rankDisplay_away = away_stats$blocks_per_game_rankDisplay),
    list(key = "oppAssistsPerGame", label = "Opp Assists/Game", value_home = home_stats$opp_assists_per_game, rank_home = home_stats$opp_assists_per_game_rank, rankDisplay_home = home_stats$opp_assists_per_game_rankDisplay, value_away = away_stats$opp_assists_per_game, rank_away = away_stats$opp_assists_per_game_rank, rankDisplay_away = away_stats$opp_assists_per_game_rankDisplay),
    list(key = "oppTurnoversPerGame", label = "Opp Turnovers/Game", value_home = home_stats$opp_turnovers_per_game, rank_home = home_stats$opp_turnovers_per_game_rank, rankDisplay_home = home_stats$opp_turnovers_per_game_rankDisplay, value_away = away_stats$opp_turnovers_per_game, rank_away = away_stats$opp_turnovers_per_game_rank, rankDisplay_away = away_stats$opp_turnovers_per_game_rankDisplay)
  )

  # Add advanced defensive stats if available
  if (!is.na(home_stats$defensive_rating) && !is.na(away_stats$defensive_rating)) {
    def_stats <- c(def_stats, list(
      list(key = "defensiveRating", label = "Defensive Rating", value_home = home_stats$defensive_rating, rank_home = home_stats$defensive_rating_rank, rankDisplay_home = home_stats$defensive_rating_rankDisplay, value_away = away_stats$defensive_rating, rank_away = away_stats$defensive_rating_rank, rankDisplay_away = away_stats$defensive_rating_rankDisplay)
    ))
  }

  if (!is.na(home_stats$opp_efg_pct) && !is.na(away_stats$opp_efg_pct)) {
    def_stats <- c(def_stats, list(
      list(key = "oppEffectiveFgPct", label = "Opp Effective FG %", value_home = home_stats$opp_efg_pct, rank_home = home_stats$opp_efg_pct_rank, rankDisplay_home = home_stats$opp_efg_pct_rankDisplay, value_away = away_stats$opp_efg_pct, rank_away = away_stats$opp_efg_pct_rank, rankDisplay_away = away_stats$opp_efg_pct_rankDisplay)
    ))
  }

  for (stat in def_stats) {
    def_comparison[[stat$key]] <- list(
      label = stat$label,
      home = list(
        value = if (!is.na(stat$value_home)) round(stat$value_home, 2) else NULL,
        rank = if (!is.na(stat$rank_home)) as.integer(stat$rank_home) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_home)) stat$rankDisplay_home else NULL
      ),
      away = list(
        value = if (!is.na(stat$value_away)) round(stat$value_away, 2) else NULL,
        rank = if (!is.na(stat$rank_away)) as.integer(stat$rank_away) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_away)) stat$rankDisplay_away else NULL
      )
    )
  }

  # For NBA, offense vs defense matchups are more straightforward
  # Home offense vs Away defense
  home_off_vs_away_def <- list()

  # Define offense vs defense stat matchups
  off_vs_def_matchups <- list(
    list(
      key = "pointsPerGame",
      off_label = "Points/Game",
      def_label = "Points Allowed/Game",
      off_stat = "points_per_game",
      def_stat = "opp_points_per_game",
      off_rank = "points_per_game_rank",
      def_rank = "opp_points_per_game_rank",
      off_rankDisplay = "points_per_game_rankDisplay",
      def_rankDisplay = "opp_points_per_game_rankDisplay"
    ),
    list(
      key = "fieldGoalPct",
      off_label = "Field Goal %",
      def_label = "Opp Field Goal %",
      off_stat = "fg_pct",
      def_stat = "opp_fg_pct",
      off_rank = "fg_pct_rank",
      def_rank = "opp_fg_pct_rank",
      off_rankDisplay = "fg_pct_rankDisplay",
      def_rankDisplay = "opp_fg_pct_rankDisplay"
    ),
    list(
      key = "threePtPct",
      off_label = "3-Point %",
      def_label = "Opp 3-Point %",
      off_stat = "three_pt_pct",
      def_stat = "opp_three_pt_pct",
      off_rank = "three_pt_pct_rank",
      def_rank = "opp_three_pt_pct_rank",
      off_rankDisplay = "three_pt_pct_rankDisplay",
      def_rankDisplay = "opp_three_pt_pct_rankDisplay"
    ),
    list(
      key = "effectiveFgPct",
      off_label = "Effective FG %",
      def_label = "Opp Effective FG %",
      off_stat = "efg_pct",
      def_stat = "opp_efg_pct",
      off_rank = "efg_pct_rank",
      def_rank = "opp_efg_pct_rank",
      off_rankDisplay = "efg_pct_rankDisplay",
      def_rankDisplay = "opp_efg_pct_rankDisplay"
    ),
    list(
      key = "assistsPerGame",
      off_label = "Assists/Game",
      def_label = "Assists Allowed/Game",
      off_stat = "assists_per_game",
      def_stat = "opp_assists_per_game",
      off_rank = "assists_per_game_rank",
      def_rank = "opp_assists_per_game_rank",
      off_rankDisplay = "assists_per_game_rankDisplay",
      def_rankDisplay = "opp_assists_per_game_rankDisplay"
    ),
    list(
      key = "turnoversPerGame",
      off_label = "Turnovers/Game",
      def_label = "Turnovers Forced/Game",
      off_stat = "turnovers_per_game",
      def_stat = "opp_turnovers_per_game",
      off_rank = "turnovers_per_game_rank",
      def_rank = "opp_turnovers_per_game_rank",
      off_rankDisplay = "turnovers_per_game_rankDisplay",
      def_rankDisplay = "opp_turnovers_per_game_rankDisplay"
    ),
    list(
      key = "rating",
      off_label = "Rating",
      def_label = "Defensive Rating",
      off_stat = "offensive_rating",
      def_stat = "defensive_rating",
      off_rank = "offensive_rating_rank",
      def_rank = "defensive_rating_rank",
      off_rankDisplay = "offensive_rating_rankDisplay",
      def_rankDisplay = "defensive_rating_rankDisplay"
    )
  )

  # Build home offense vs away defense comparisons
  for (matchup in off_vs_def_matchups) {
    off_val <- home_stats[[matchup$off_stat]]
    def_val <- away_stats[[matchup$def_stat]]
    off_rank <- home_stats[[matchup$off_rank]]
    def_rank <- away_stats[[matchup$def_rank]]

    if (!is.na(off_val) && !is.na(def_val)) {
      advantage <- 0
      if (!is.na(off_rank) && !is.na(def_rank)) {
        if (off_rank < def_rank) {
          advantage <- -1  # Offense advantage
        } else if (off_rank > def_rank) {
          advantage <- 1   # Defense advantage
        }
      }

      home_off_vs_away_def[[matchup$key]] <- list(
        statKey = matchup$key,
        offLabel = matchup$off_label,
        defLabel = matchup$def_label,
        offense = list(
          team = home_team,
          value = round(off_val, 2),
          rank = as.integer(off_rank),
          rankDisplay = home_stats[[matchup$off_rankDisplay]]
        ),
        defense = list(
          team = away_team,
          value = round(def_val, 2),
          rank = as.integer(def_rank),
          rankDisplay = away_stats[[matchup$def_rankDisplay]]
        ),
        advantage = advantage
      )
    }
  }

  # Away offense vs Home defense
  away_off_vs_home_def <- list()

  # Build away offense vs home defense comparisons
  for (matchup in off_vs_def_matchups) {
    off_val <- away_stats[[matchup$off_stat]]
    def_val <- home_stats[[matchup$def_stat]]
    off_rank <- away_stats[[matchup$off_rank]]
    def_rank <- home_stats[[matchup$def_rank]]

    if (!is.na(off_val) && !is.na(def_val)) {
      advantage <- 0
      if (!is.na(off_rank) && !is.na(def_rank)) {
        if (off_rank < def_rank) {
          advantage <- -1  # Offense advantage
        } else if (off_rank > def_rank) {
          advantage <- 1   # Defense advantage
        }
      }

      away_off_vs_home_def[[matchup$key]] <- list(
        statKey = matchup$key,
        offLabel = matchup$off_label,
        defLabel = matchup$def_label,
        offense = list(
          team = away_team,
          value = round(off_val, 2),
          rank = as.integer(off_rank),
          rankDisplay = away_stats[[matchup$off_rankDisplay]]
        ),
        defense = list(
          team = home_team,
          value = round(def_val, 2),
          rank = as.integer(def_rank),
          rankDisplay = home_stats[[matchup$def_rankDisplay]]
        ),
        advantage = advantage
      )
    }
  }

  return(list(
    sideBySide = list(
      offense = off_comparison,
      defense = def_comparison
    ),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  ))
}

matchups_json <- list()

for (game in upcoming_games) {
  cat("Processing matchup:", game$away_team_abbrev, "@", game$home_team_abbrev, "\n")

  # Get team stats for both teams
  home_stats <- team_stats %>% filter(team_id == game$home_team_id)
  away_stats <- team_stats %>% filter(team_id == game$away_team_id)

  if (nrow(home_stats) == 0 || nrow(away_stats) == 0) {
    cat("Skipping game - missing team stats\n")
    next
  }

  # Get top players for both teams
  # Sort by minutes per game (best indicator of who's actually playing), then games played, then PIE
  # This ensures we get the most active and impactful players
  home_players <- player_stats %>%
    filter(team_id == game$home_team_id) %>%
    arrange(desc(minutes_per_game), desc(games_played), desc(if_else(!is.na(player_pie), player_pie, 0))) %>%
    head(7)

  away_players <- player_stats %>%
    filter(team_id == game$away_team_id) %>%
    arrange(desc(minutes_per_game), desc(games_played), desc(if_else(!is.na(player_pie), player_pie, 0))) %>%
    head(7)

  # Build team comparison data
  home_team_data <- list(
    id = game$home_team_id,
    name = game$home_team_name,
    abbreviation = game$home_team_abbrev,
    logo = game$home_team_logo,
    wins = if (!is.na(home_stats$wins)) as.integer(home_stats$wins) else NULL,
    losses = if (!is.na(home_stats$losses)) as.integer(home_stats$losses) else NULL,
    conferenceRank = if (!is.na(home_stats$conference_rank)) as.integer(home_stats$conference_rank) else NULL,
    conference = if (!is.na(home_stats$conference)) as.character(home_stats$conference) else NULL,
    stats = list(
      gamesPlayed = home_stats$games_played,
      pointsPerGame = round(home_stats$points_per_game, 1),
      pointsPerGameRank = home_stats$points_per_game_rank,
      pointsPerGameRankDisplay = home_stats$points_per_game_rankDisplay,
      fieldGoalPct = round(home_stats$fg_pct, 1),
      fieldGoalPctRank = home_stats$fg_pct_rank,
      fieldGoalPctRankDisplay = home_stats$fg_pct_rankDisplay,
      threePtPct = round(home_stats$three_pt_pct, 1),
      threePtPctRank = home_stats$three_pt_pct_rank,
      threePtPctRankDisplay = home_stats$three_pt_pct_rankDisplay,
      reboundsPerGame = round(home_stats$rebounds_per_game, 1),
      reboundsPerGameRank = home_stats$rebounds_per_game_rank,
      reboundsPerGameRankDisplay = home_stats$rebounds_per_game_rankDisplay,
      assistsPerGame = round(home_stats$assists_per_game, 1),
      assistsPerGameRank = home_stats$assists_per_game_rank,
      assistsPerGameRankDisplay = home_stats$assists_per_game_rankDisplay,
      stealsPerGame = round(home_stats$steals_per_game, 1),
      stealsPerGameRank = home_stats$steals_per_game_rank,
      stealsPerGameRankDisplay = home_stats$steals_per_game_rankDisplay,
      blocksPerGame = round(home_stats$blocks_per_game, 1),
      blocksPerGameRank = home_stats$blocks_per_game_rank,
      blocksPerGameRankDisplay = home_stats$blocks_per_game_rankDisplay,
      turnoversPerGame = round(home_stats$turnovers_per_game, 1),
      turnoversPerGameRank = home_stats$turnovers_per_game_rank,
      turnoversPerGameRankDisplay = home_stats$turnovers_per_game_rankDisplay
    )
  )

  # Add advanced stats if available
  if (!is.null(advanced_stats) && !is.na(home_stats$offensive_rating)) {
    home_team_data$stats$offensiveRating <- round(home_stats$offensive_rating, 1)
    home_team_data$stats$offensiveRatingRank <- home_stats$offensive_rating_rank
    home_team_data$stats$offensiveRatingRankDisplay <- home_stats$offensive_rating_rankDisplay
    home_team_data$stats$defensiveRating <- round(home_stats$defensive_rating, 1)
    home_team_data$stats$defensiveRatingRank <- home_stats$defensive_rating_rank
    home_team_data$stats$defensiveRatingRankDisplay <- home_stats$defensive_rating_rankDisplay
    home_team_data$stats$netRating <- round(home_stats$net_rating, 1)
    home_team_data$stats$netRatingRank <- home_stats$net_rating_rank
    home_team_data$stats$netRatingRankDisplay <- home_stats$net_rating_rankDisplay
    home_team_data$stats$pace <- round(home_stats$pace, 1)
    home_team_data$stats$paceRank <- home_stats$pace_rank
    home_team_data$stats$paceRankDisplay <- home_stats$pace_rankDisplay
    home_team_data$stats$trueShootingPct <- round(home_stats$ts_pct, 1)
    home_team_data$stats$trueShootingPctRank <- home_stats$ts_pct_rank
    home_team_data$stats$trueShootingPctRankDisplay <- home_stats$ts_pct_rankDisplay
    home_team_data$stats$effectiveFgPct <- round(home_stats$efg_pct, 1)
    home_team_data$stats$effectiveFgPctRank <- home_stats$efg_pct_rank
    home_team_data$stats$effectiveFgPctRankDisplay <- home_stats$efg_pct_rankDisplay
    home_team_data$stats$offensiveRebPct <- round(home_stats$oreb_pct, 1)
    home_team_data$stats$offensiveRebPctRank <- home_stats$oreb_pct_rank
    home_team_data$stats$offensiveRebPctRankDisplay <- home_stats$oreb_pct_rankDisplay
    home_team_data$stats$turnoverPct <- round(home_stats$tm_tov_pct, 1)
    home_team_data$stats$turnoverPctRank <- home_stats$tm_tov_pct_rank
    home_team_data$stats$turnoverPctRankDisplay <- home_stats$tm_tov_pct_rankDisplay
  }

  # Add four factors if available
  if (!is.null(four_factors_stats) && !is.na(home_stats$fta_rate)) {
    home_team_data$stats$ftaRate <- round(home_stats$fta_rate, 1)
    home_team_data$stats$ftaRateRank <- home_stats$fta_rate_rank
    home_team_data$stats$ftaRateRankDisplay <- home_stats$fta_rate_rankDisplay
    home_team_data$stats$oppEffectiveFgPct <- round(home_stats$opp_efg_pct, 1)
    home_team_data$stats$oppEffectiveFgPctRank <- home_stats$opp_efg_pct_rank
    home_team_data$stats$oppEffectiveFgPctRankDisplay <- home_stats$opp_efg_pct_rankDisplay
  }

  # Add cumulative net rating by week for home team
  home_cum_net_rating <- cum_net_rating_by_team %>%
    filter(team_abbreviation == game$home_team_abbrev) %>%
    select(week_num, cum_net_rating)

  home_team_data$stats$cumNetRatingByWeek <- if (nrow(home_cum_net_rating) > 0) {
    setNames(
      as.list(round(home_cum_net_rating$cum_net_rating, 1)),
      paste0("week-", home_cum_net_rating$week_num)
    )
  } else {
    list()
  }

  # Add weekly efficiency (off/def rating) for last 10 weeks - for scatter plot
  home_weekly_eff <- weekly_efficiency %>%
    filter(team_abbreviation == game$home_team_abbrev) %>%
    select(week_num, off_rating, def_rating)

  home_team_data$stats$efficiencyByWeek <- if (nrow(home_weekly_eff) > 0) {
    setNames(
      lapply(1:nrow(home_weekly_eff), function(i) {
        list(
          offRating = round(home_weekly_eff$off_rating[i], 1),
          defRating = round(home_weekly_eff$def_rating[i], 1)
        )
      }),
      paste0("week-", home_weekly_eff$week_num)
    )
  } else {
    list()
  }

  # Add 1-month trend rankings for home team
  home_month_trend <- month_trend_stats %>%
    filter(team_abbreviation == game$home_team_abbrev)

  home_team_data$stats$monthTrend <- if (nrow(home_month_trend) > 0) {
    list(
      gamesPlayed = as.integer(home_month_trend$games_played),
      record = list(
        wins = as.integer(home_month_trend$wins),
        losses = as.integer(home_month_trend$losses),
        rank = as.integer(home_month_trend$record_rank),
        rankDisplay = home_month_trend$record_rankDisplay
      ),
      netRating = list(
        value = round(home_month_trend$avg_net_rating, 1),
        rank = as.integer(home_month_trend$net_rating_rank),
        rankDisplay = home_month_trend$net_rating_rankDisplay
      ),
      offensiveRating = list(
        value = round(home_month_trend$avg_off_rating, 1),
        rank = as.integer(home_month_trend$off_rating_rank),
        rankDisplay = home_month_trend$off_rating_rankDisplay
      ),
      defensiveRating = list(
        value = round(home_month_trend$avg_def_rating, 1),
        rank = as.integer(home_month_trend$def_rating_rank),
        rankDisplay = home_month_trend$def_rating_rankDisplay
      ),
      pointsPerGame = list(
        value = round(home_month_trend$points_per_game, 1),
        rank = as.integer(home_month_trend$ppg_rank),
        rankDisplay = home_month_trend$ppg_rankDisplay
      ),
      assistsPerGame = list(
        value = round(home_month_trend$assists_per_game, 1),
        rank = as.integer(home_month_trend$apg_rank),
        rankDisplay = home_month_trend$apg_rankDisplay
      ),
      turnoversPerGame = list(
        value = round(home_month_trend$turnovers_per_game, 1),
        rank = as.integer(home_month_trend$tpg_rank),
        rankDisplay = home_month_trend$tpg_rankDisplay
      ),
      turnoverDiff = list(
        value = round(home_month_trend$turnover_diff, 1),
        rank = as.integer(home_month_trend$tov_diff_rank),
        rankDisplay = home_month_trend$tov_diff_rankDisplay
      )
    )
  } else {
    list()
  }

  away_team_data <- list(
    id = game$away_team_id,
    name = game$away_team_name,
    abbreviation = game$away_team_abbrev,
    logo = game$away_team_logo,
    wins = if (!is.na(away_stats$wins)) as.integer(away_stats$wins) else NULL,
    losses = if (!is.na(away_stats$losses)) as.integer(away_stats$losses) else NULL,
    conferenceRank = if (!is.na(away_stats$conference_rank)) as.integer(away_stats$conference_rank) else NULL,
    conference = if (!is.na(away_stats$conference)) as.character(away_stats$conference) else NULL,
    stats = list(
      gamesPlayed = away_stats$games_played,
      pointsPerGame = round(away_stats$points_per_game, 1),
      pointsPerGameRank = away_stats$points_per_game_rank,
      pointsPerGameRankDisplay = away_stats$points_per_game_rankDisplay,
      fieldGoalPct = round(away_stats$fg_pct, 1),
      fieldGoalPctRank = away_stats$fg_pct_rank,
      fieldGoalPctRankDisplay = away_stats$fg_pct_rankDisplay,
      threePtPct = round(away_stats$three_pt_pct, 1),
      threePtPctRank = away_stats$three_pt_pct_rank,
      threePtPctRankDisplay = away_stats$three_pt_pct_rankDisplay,
      reboundsPerGame = round(away_stats$rebounds_per_game, 1),
      reboundsPerGameRank = away_stats$rebounds_per_game_rank,
      reboundsPerGameRankDisplay = away_stats$rebounds_per_game_rankDisplay,
      assistsPerGame = round(away_stats$assists_per_game, 1),
      assistsPerGameRank = away_stats$assists_per_game_rank,
      assistsPerGameRankDisplay = away_stats$assists_per_game_rankDisplay,
      stealsPerGame = round(away_stats$steals_per_game, 1),
      stealsPerGameRank = away_stats$steals_per_game_rank,
      stealsPerGameRankDisplay = away_stats$steals_per_game_rankDisplay,
      blocksPerGame = round(away_stats$blocks_per_game, 1),
      blocksPerGameRank = away_stats$blocks_per_game_rank,
      blocksPerGameRankDisplay = away_stats$blocks_per_game_rankDisplay,
      turnoversPerGame = round(away_stats$turnovers_per_game, 1),
      turnoversPerGameRank = away_stats$turnovers_per_game_rank,
      turnoversPerGameRankDisplay = away_stats$turnovers_per_game_rankDisplay
    )
  )

  # Add advanced stats if available
  if (!is.null(advanced_stats) && !is.na(away_stats$offensive_rating)) {
    away_team_data$stats$offensiveRating <- round(away_stats$offensive_rating, 1)
    away_team_data$stats$offensiveRatingRank <- away_stats$offensive_rating_rank
    away_team_data$stats$offensiveRatingRankDisplay <- away_stats$offensive_rating_rankDisplay
    away_team_data$stats$defensiveRating <- round(away_stats$defensive_rating, 1)
    away_team_data$stats$defensiveRatingRank <- away_stats$defensive_rating_rank
    away_team_data$stats$defensiveRatingRankDisplay <- away_stats$defensive_rating_rankDisplay
    away_team_data$stats$netRating <- round(away_stats$net_rating, 1)
    away_team_data$stats$netRatingRank <- away_stats$net_rating_rank
    away_team_data$stats$netRatingRankDisplay <- away_stats$net_rating_rankDisplay
    away_team_data$stats$pace <- round(away_stats$pace, 1)
    away_team_data$stats$paceRank <- away_stats$pace_rank
    away_team_data$stats$paceRankDisplay <- away_stats$pace_rankDisplay
    away_team_data$stats$trueShootingPct <- round(away_stats$ts_pct, 1)
    away_team_data$stats$trueShootingPctRank <- away_stats$ts_pct_rank
    away_team_data$stats$trueShootingPctRankDisplay <- away_stats$ts_pct_rankDisplay
    away_team_data$stats$effectiveFgPct <- round(away_stats$efg_pct, 1)
    away_team_data$stats$effectiveFgPctRank <- away_stats$efg_pct_rank
    away_team_data$stats$effectiveFgPctRankDisplay <- away_stats$efg_pct_rankDisplay
    away_team_data$stats$offensiveRebPct <- round(away_stats$oreb_pct, 1)
    away_team_data$stats$offensiveRebPctRank <- away_stats$oreb_pct_rank
    away_team_data$stats$offensiveRebPctRankDisplay <- away_stats$oreb_pct_rankDisplay
    away_team_data$stats$turnoverPct <- round(away_stats$tm_tov_pct, 1)
    away_team_data$stats$turnoverPctRank <- away_stats$tm_tov_pct_rank
    away_team_data$stats$turnoverPctRankDisplay <- away_stats$tm_tov_pct_rankDisplay
  }

  # Add four factors if available
  if (!is.null(four_factors_stats) && !is.na(away_stats$fta_rate)) {
    away_team_data$stats$ftaRate <- round(away_stats$fta_rate, 1)
    away_team_data$stats$ftaRateRank <- away_stats$fta_rate_rank
    away_team_data$stats$ftaRateRankDisplay <- away_stats$fta_rate_rankDisplay
    away_team_data$stats$oppEffectiveFgPct <- round(away_stats$opp_efg_pct, 1)
    away_team_data$stats$oppEffectiveFgPctRank <- away_stats$opp_efg_pct_rank
    away_team_data$stats$oppEffectiveFgPctRankDisplay <- away_stats$opp_efg_pct_rankDisplay
  }

  # Add cumulative net rating by week for away team
  away_cum_net_rating <- cum_net_rating_by_team %>%
    filter(team_abbreviation == game$away_team_abbrev) %>%
    select(week_num, cum_net_rating)

  away_team_data$stats$cumNetRatingByWeek <- if (nrow(away_cum_net_rating) > 0) {
    setNames(
      as.list(round(away_cum_net_rating$cum_net_rating, 1)),
      paste0("week-", away_cum_net_rating$week_num)
    )
  } else {
    list()
  }

  # Add weekly efficiency (off/def rating) for last 10 weeks - for scatter plot
  away_weekly_eff <- weekly_efficiency %>%
    filter(team_abbreviation == game$away_team_abbrev) %>%
    select(week_num, off_rating, def_rating)

  away_team_data$stats$efficiencyByWeek <- if (nrow(away_weekly_eff) > 0) {
    setNames(
      lapply(1:nrow(away_weekly_eff), function(i) {
        list(
          offRating = round(away_weekly_eff$off_rating[i], 1),
          defRating = round(away_weekly_eff$def_rating[i], 1)
        )
      }),
      paste0("week-", away_weekly_eff$week_num)
    )
  } else {
    list()
  }

  # Add 1-month trend rankings for away team
  away_month_trend <- month_trend_stats %>%
    filter(team_abbreviation == game$away_team_abbrev)

  away_team_data$stats$monthTrend <- if (nrow(away_month_trend) > 0) {
    list(
      gamesPlayed = as.integer(away_month_trend$games_played),
      record = list(
        wins = as.integer(away_month_trend$wins),
        losses = as.integer(away_month_trend$losses),
        rank = as.integer(away_month_trend$record_rank),
        rankDisplay = away_month_trend$record_rankDisplay
      ),
      netRating = list(
        value = round(away_month_trend$avg_net_rating, 1),
        rank = as.integer(away_month_trend$net_rating_rank),
        rankDisplay = away_month_trend$net_rating_rankDisplay
      ),
      offensiveRating = list(
        value = round(away_month_trend$avg_off_rating, 1),
        rank = as.integer(away_month_trend$off_rating_rank),
        rankDisplay = away_month_trend$off_rating_rankDisplay
      ),
      defensiveRating = list(
        value = round(away_month_trend$avg_def_rating, 1),
        rank = as.integer(away_month_trend$def_rating_rank),
        rankDisplay = away_month_trend$def_rating_rankDisplay
      ),
      pointsPerGame = list(
        value = round(away_month_trend$points_per_game, 1),
        rank = as.integer(away_month_trend$ppg_rank),
        rankDisplay = away_month_trend$ppg_rankDisplay
      ),
      assistsPerGame = list(
        value = round(away_month_trend$assists_per_game, 1),
        rank = as.integer(away_month_trend$apg_rank),
        rankDisplay = away_month_trend$apg_rankDisplay
      ),
      turnoversPerGame = list(
        value = round(away_month_trend$turnovers_per_game, 1),
        rank = as.integer(away_month_trend$tpg_rank),
        rankDisplay = away_month_trend$tpg_rankDisplay
      ),
      turnoverDiff = list(
        value = round(away_month_trend$turnover_diff, 1),
        rank = as.integer(away_month_trend$tov_diff_rank),
        rankDisplay = away_month_trend$tov_diff_rankDisplay
      )
    )
  } else {
    list()
  }

  # Build player data
  home_players_data <- lapply(1:nrow(home_players), function(i) {
    p <- home_players[i, ]

    # Helper to create NULL if value is NA
    na_to_null <- function(x) if (is.na(x)) NULL else x

    list(
      name = p$athlete_display_name,
      position = p$athlete_position_abbreviation,
      points_per_game = list(
        value = na_to_null(round(p$points_per_game, 2)),
        rank = na_to_null(as.integer(p$points_per_game_rank)),
        rankDisplay = na_to_null(p$points_per_game_rankDisplay)
      ),
      rebounds_per_game = list(
        value = na_to_null(round(p$rebounds_per_game, 2)),
        rank = na_to_null(as.integer(p$rebounds_per_game_rank)),
        rankDisplay = na_to_null(p$rebounds_per_game_rankDisplay)
      ),
      assists_per_game = list(
        value = na_to_null(round(p$assists_per_game, 2)),
        rank = na_to_null(as.integer(p$assists_per_game_rank)),
        rankDisplay = na_to_null(p$assists_per_game_rankDisplay)
      ),
      steals_per_game = list(
        value = na_to_null(round(p$steals_per_game, 2)),
        rank = na_to_null(as.integer(p$steals_per_game_rank)),
        rankDisplay = na_to_null(p$steals_per_game_rankDisplay)
      ),
      blocks_per_game = list(
        value = na_to_null(round(p$blocks_per_game, 2)),
        rank = na_to_null(as.integer(p$blocks_per_game_rank)),
        rankDisplay = na_to_null(p$blocks_per_game_rankDisplay)
      ),
      field_goal_pct = list(
        value = na_to_null(round(p$fg_pct, 2)),
        rank = na_to_null(as.integer(p$fg_pct_rank)),
        rankDisplay = na_to_null(p$fg_pct_rankDisplay)
      ),
      three_pt_pct = list(
        value = na_to_null(round(p$three_pt_pct, 2)),
        rank = na_to_null(as.integer(p$three_pt_pct_rank)),
        rankDisplay = na_to_null(p$three_pt_pct_rankDisplay)
      ),
      true_shooting_pct = if ("player_ts_pct" %in% names(p) && !is.na(p$player_ts_pct)) {
        list(
          value = na_to_null(round(p$player_ts_pct, 2)),
          rank = na_to_null(as.integer(p$player_ts_pct_rank)),
          rankDisplay = na_to_null(p$player_ts_pct_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      effective_fg_pct = if ("player_efg_pct" %in% names(p) && !is.na(p$player_efg_pct)) {
        list(
          value = na_to_null(round(p$player_efg_pct, 2)),
          rank = na_to_null(as.integer(p$player_efg_pct_rank)),
          rankDisplay = na_to_null(p$player_efg_pct_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      pie = if ("player_pie" %in% names(p) && !is.na(p$player_pie)) {
        list(
          value = na_to_null(round(p$player_pie, 2)),
          rank = na_to_null(as.integer(p$player_pie_rank)),
          rankDisplay = na_to_null(p$player_pie_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      usage_pct = if ("player_usg_pct" %in% names(p) && !is.na(p$player_usg_pct)) {
        list(
          value = na_to_null(round(p$player_usg_pct, 2)),
          rank = na_to_null(as.integer(p$player_usg_pct_rank)),
          rankDisplay = na_to_null(p$player_usg_pct_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      minutes_per_game = list(
        value = na_to_null(round(p$minutes_per_game, 1)),
        rank = na_to_null(as.integer(p$minutes_per_game_rank)),
        rankDisplay = na_to_null(p$minutes_per_game_rankDisplay)
      ),
      games_played = list(
        value = na_to_null(as.integer(p$games_played)),
        rank = na_to_null(as.integer(p$games_played_rank)),
        rankDisplay = na_to_null(p$games_played_rankDisplay)
      )
    )
  })

  away_players_data <- lapply(1:nrow(away_players), function(i) {
    p <- away_players[i, ]

    # Helper to create NULL if value is NA
    na_to_null <- function(x) if (is.na(x)) NULL else x

    list(
      name = p$athlete_display_name,
      position = p$athlete_position_abbreviation,
      points_per_game = list(
        value = na_to_null(round(p$points_per_game, 2)),
        rank = na_to_null(as.integer(p$points_per_game_rank)),
        rankDisplay = na_to_null(p$points_per_game_rankDisplay)
      ),
      rebounds_per_game = list(
        value = na_to_null(round(p$rebounds_per_game, 2)),
        rank = na_to_null(as.integer(p$rebounds_per_game_rank)),
        rankDisplay = na_to_null(p$rebounds_per_game_rankDisplay)
      ),
      assists_per_game = list(
        value = na_to_null(round(p$assists_per_game, 2)),
        rank = na_to_null(as.integer(p$assists_per_game_rank)),
        rankDisplay = na_to_null(p$assists_per_game_rankDisplay)
      ),
      steals_per_game = list(
        value = na_to_null(round(p$steals_per_game, 2)),
        rank = na_to_null(as.integer(p$steals_per_game_rank)),
        rankDisplay = na_to_null(p$steals_per_game_rankDisplay)
      ),
      blocks_per_game = list(
        value = na_to_null(round(p$blocks_per_game, 2)),
        rank = na_to_null(as.integer(p$blocks_per_game_rank)),
        rankDisplay = na_to_null(p$blocks_per_game_rankDisplay)
      ),
      field_goal_pct = list(
        value = na_to_null(round(p$fg_pct, 2)),
        rank = na_to_null(as.integer(p$fg_pct_rank)),
        rankDisplay = na_to_null(p$fg_pct_rankDisplay)
      ),
      three_pt_pct = list(
        value = na_to_null(round(p$three_pt_pct, 2)),
        rank = na_to_null(as.integer(p$three_pt_pct_rank)),
        rankDisplay = na_to_null(p$three_pt_pct_rankDisplay)
      ),
      true_shooting_pct = if ("player_ts_pct" %in% names(p) && !is.na(p$player_ts_pct)) {
        list(
          value = na_to_null(round(p$player_ts_pct, 2)),
          rank = na_to_null(as.integer(p$player_ts_pct_rank)),
          rankDisplay = na_to_null(p$player_ts_pct_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      effective_fg_pct = if ("player_efg_pct" %in% names(p) && !is.na(p$player_efg_pct)) {
        list(
          value = na_to_null(round(p$player_efg_pct, 2)),
          rank = na_to_null(as.integer(p$player_efg_pct_rank)),
          rankDisplay = na_to_null(p$player_efg_pct_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      pie = if ("player_pie" %in% names(p) && !is.na(p$player_pie)) {
        list(
          value = na_to_null(round(p$player_pie, 2)),
          rank = na_to_null(as.integer(p$player_pie_rank)),
          rankDisplay = na_to_null(p$player_pie_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      usage_pct = if ("player_usg_pct" %in% names(p) && !is.na(p$player_usg_pct)) {
        list(
          value = na_to_null(round(p$player_usg_pct, 2)),
          rank = na_to_null(as.integer(p$player_usg_pct_rank)),
          rankDisplay = na_to_null(p$player_usg_pct_rankDisplay)
        )
      } else {
        list(value = NULL, rank = NULL, rankDisplay = NULL)
      },
      minutes_per_game = list(
        value = na_to_null(round(p$minutes_per_game, 1)),
        rank = na_to_null(as.integer(p$minutes_per_game_rank)),
        rankDisplay = na_to_null(p$minutes_per_game_rankDisplay)
      ),
      games_played = list(
        value = na_to_null(as.integer(p$games_played)),
        rank = na_to_null(as.integer(p$games_played_rank)),
        rankDisplay = na_to_null(p$games_played_rankDisplay)
      )
    )
  })

  # Build comparison data
  comparisons <- build_nba_comparisons(
    home_stats,
    away_stats,
    game$home_team_abbrev,
    game$away_team_abbrev
  )

  # Build matchup object
  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = game$game_name,
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    homePlayers = home_players_data,
    awayPlayers = away_players_data,
    comparisons = comparisons
  )

  # Add location if available
  if (!is.null(game$location)) {
    matchup$location <- list(
      stadium = game$location$stadium,
      city = game$location$city,
      state = game$location$state,
      country = game$location$country,
      fullLocation = game$location$fullLocation
    )
  }

  # Add odds if available
  if (!is.null(game$odds)) {
    matchup$odds <- list(
      provider = game$odds$provider,
      spread = game$odds$spread,
      overUnder = game$odds$over_under,
      homeMoneyline = game$odds$home_moneyline,
      awayMoneyline = game$odds$away_moneyline
    )
  }

  # Add #10 net rating reference line (league-wide data for chart overlay)
  matchup$tenthNetRatingByWeek <- if (nrow(tenth_net_rating_by_week) > 0) {
    setNames(
      as.list(round(tenth_net_rating_by_week$tenth_net_rating, 1)),
      paste0("week-", tenth_net_rating_by_week$week_num)
    )
  } else {
    list()
  }

  # Add league-wide efficiency stats for consistent scatter plot scaling
  matchup$leagueEfficiencyStats <- list(
    avgOffRating = round(league_efficiency_stats$avg_off_rating, 1),
    avgDefRating = round(league_efficiency_stats$avg_def_rating, 1),
    minOffRating = round(league_efficiency_stats$min_off_rating, 1),
    maxOffRating = round(league_efficiency_stats$max_off_rating, 1),
    minDefRating = round(league_efficiency_stats$min_def_rating, 1),
    maxDefRating = round(league_efficiency_stats$max_def_rating, 1)
  )

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

# ============================================================================
# STEP 6: Generate output JSON
# ============================================================================
cat("\n6. Generating output JSON...\n")

output_data <- list(
  sport = "NBA",
  visualizationType = "NBA_MATCHUP",
  title = paste0("NBA Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("Upcoming games and comprehensive statistical analysis for the next", DAYS_AHEAD, "days"),
  description = paste0("Detailed matchup statistics including team performance metrics, player stats, and betting odds.\n\nTEAM STATS:\n\n • Points Per Game: Average points scored per game\n\n • Field Goal %: Percentage of field goals made\n\n • 3-Point %: Percentage of three-point shots made\n\n • Rebounds Per Game: Average rebounds per game (offensive + defensive)\n\n • Assists Per Game: Average assists per game\n\n • Steals Per Game: Average steals per game\n\n • Blocks Per Game: Average blocks per game\n\n • Turnovers Per Game: Average turnovers per game (lower is better)\n\n • Offensive Rating: Points scored per 100 possessions (higher is better)\n\n • Defensive Rating: Points allowed per 100 possessions (lower is better)\n\n • Net Rating: Offensive Rating - Defensive Rating (higher is better)\n\nPLAYER STATS:\n\n • Points Per Game: Average points scored per game\n\n • Rebounds Per Game: Average rebounds per game\n\n • Assists Per Game: Average assists per game\n\n • Steals Per Game: Average steals per game\n\n • Blocks Per Game: Average blocks per game\n\n • Field Goal %: Percentage of field goals made\n\n • 3-Point %: Percentage of three-point shots made\n\nAll stats are season totals through the current date. Players must have played at least ", MIN_GAMES_PLAYED, " games to be included."),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "hoopR / ESPN",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  # Scatter plot quadrant configuration for Off vs Def Rating chart
  # With inverted Y axis: top = good defense (low rating), bottom = poor defense
  # X axis: right = good offense (high rating), left = poor offense
  scatterPlotQuadrants = list(
    topRight = list(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
    topLeft = list(label = "Defensive", color = "#2196F3", lightModeColor = "#2196F3"),
    bottomLeft = list(label = "Struggling", color = "#F44336", lightModeColor = "#F44336"),
    bottomRight = list(label = "Offensive", color = "#FF9800", lightModeColor = "#FF9800")
  ),
  dataPoints = matchups_json
)

# Write to temp file first
tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

cat("Generated stats for", length(matchups_json), "matchup(s)\n")

# Upload to S3 if in production
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/nba__matchup_stats.json"

  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)

  if (result != 0) {
    stop("Failed to upload to S3")
  }

  cat("Uploaded to S3:", s3_path, "\n")

  # Update DynamoDB with metadata
  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  chart_title <- paste0("NBA Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d"))
  chart_interval <- "daily"

  dynamodb_item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "%s"}}',
    s3_key, utc_timestamp, chart_title, chart_interval
  )
  dynamodb_cmd <- sprintf(
    'aws dynamodb put-item --table-name %s --item %s',
    shQuote(dynamodb_table),
    shQuote(dynamodb_item)
  )

  dynamodb_result <- system(dynamodb_cmd)

  if (dynamodb_result != 0) {
    cat("Warning: Failed to update DynamoDB\n")
  } else {
    cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
  }
} else {
  # Development mode - just print the output location and copy to persistent location
  dev_output <- "/tmp/nba_matchup_test.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
  cat("To upload to S3, set AWS_S3_BUCKET environment variable\n")
}

cat("\n=== NBA Matchup Stats generation complete ===\n")
