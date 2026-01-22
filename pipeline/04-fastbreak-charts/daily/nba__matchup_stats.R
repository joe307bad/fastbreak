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
    turnovers_per_game = turnovers_total / games_played
  )

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
    turnovers_per_game_rankDisplay = tpg_ranks$rankDisplay
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

  team_stats <- team_stats %>%
    mutate(
      fta_rate_rank = fta_rate_ranks$rank,
      fta_rate_rankDisplay = fta_rate_ranks$rankDisplay,
      opp_efg_pct_rank = opp_efg_pct_ranks$rank,
      opp_efg_pct_rankDisplay = opp_efg_pct_ranks$rankDisplay
    )
}

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
if (!is.null(player_advanced_stats)) {
  player_stats <- player_stats %>%
    left_join(
      player_advanced_stats %>%
        select(PLAYER_ID, OFF_RATING, DEF_RATING, NET_RATING, PIE, TS_PCT, EFG_PCT, AST_PCT, REB_PCT) %>%
        rename(
          athlete_id = PLAYER_ID,
          player_off_rating = OFF_RATING,
          player_def_rating = DEF_RATING,
          player_net_rating = NET_RATING,
          player_pie = PIE,
          player_ts_pct = TS_PCT,
          player_efg_pct = EFG_PCT,
          player_ast_pct = AST_PCT,
          player_reb_pct = REB_PCT
        ) %>%
        mutate(
          athlete_id = as.character(athlete_id),
          player_off_rating = as.numeric(player_off_rating),
          player_def_rating = as.numeric(player_def_rating),
          player_net_rating = as.numeric(player_net_rating),
          player_pie = as.numeric(player_pie),
          player_ts_pct = as.numeric(player_ts_pct),
          player_efg_pct = as.numeric(player_efg_pct),
          player_ast_pct = as.numeric(player_ast_pct),
          player_reb_pct = as.numeric(player_reb_pct)
        ),
      by = "athlete_id"
    )
}

# Join usage stats
if (!is.null(player_usage_stats)) {
  player_stats <- player_stats %>%
    left_join(
      player_usage_stats %>%
        select(PLAYER_ID, USG_PCT) %>%
        rename(
          athlete_id = PLAYER_ID,
          player_usg_pct = USG_PCT
        ) %>%
        mutate(
          athlete_id = as.character(athlete_id),
          player_usg_pct = as.numeric(player_usg_pct)
        ),
      by = "athlete_id"
    )
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
    three_pt_pct_rankDisplay = player_three_pt_pct_ranks$rankDisplay
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
# STEP 3: Get upcoming games (next 7 days)
# ============================================================================
cat("\n3. Fetching upcoming NBA games for next", DAYS_AHEAD, "days...\n")

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
        # Ensure proper ISO 8601 format with seconds (YYYY-MM-DDTHH:MM:SSZ)
        game_date <- format(as.POSIXct(event$date, format = "%Y-%m-%dT%H:%M", tz = "UTC"),
                           "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
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
            # Extract odds if available
            odds_data <- NULL
            if (!is.null(competition$odds) && length(competition$odds) > 0) {
              odds <- competition$odds[[1]]
              odds_data <- list(
                provider = if (!is.null(odds$provider)) odds$provider$name else NA,
                spread = if (!is.null(odds$spread)) as.numeric(odds$spread) else NA,
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
# STEP 4: Build matchup data for each game
# ============================================================================
cat("\n4. Building matchup statistics...\n")

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
  home_players <- player_stats %>%
    filter(team_id == game$home_team_id) %>%
    arrange(desc(points_per_game)) %>%
    head(5)

  away_players <- player_stats %>%
    filter(team_id == game$away_team_id) %>%
    arrange(desc(points_per_game)) %>%
    head(5)

  # Build team comparison data
  home_team_data <- list(
    id = game$home_team_id,
    name = game$home_team_name,
    abbreviation = game$home_team_abbrev,
    logo = game$home_team_logo,
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

  away_team_data <- list(
    id = game$away_team_id,
    name = game$away_team_name,
    abbreviation = game$away_team_abbrev,
    logo = game$away_team_logo,
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

  # Build player data
  home_players_data <- lapply(1:nrow(home_players), function(i) {
    p <- home_players[i, ]
    player_data <- list(
      id = p$athlete_id,
      name = p$athlete_display_name,
      position = p$athlete_position_abbreviation,
      stats = list(
        gamesPlayed = p$games_played,
        pointsPerGame = round(p$points_per_game, 1),
        pointsPerGameRank = p$points_per_game_rank,
        pointsPerGameRankDisplay = p$points_per_game_rankDisplay,
        reboundsPerGame = round(p$rebounds_per_game, 1),
        reboundsPerGameRank = p$rebounds_per_game_rank,
        reboundsPerGameRankDisplay = p$rebounds_per_game_rankDisplay,
        assistsPerGame = round(p$assists_per_game, 1),
        assistsPerGameRank = p$assists_per_game_rank,
        assistsPerGameRankDisplay = p$assists_per_game_rankDisplay,
        stealsPerGame = round(p$steals_per_game, 1),
        stealsPerGameRank = p$steals_per_game_rank,
        stealsPerGameRankDisplay = p$steals_per_game_rankDisplay,
        blocksPerGame = round(p$blocks_per_game, 1),
        blocksPerGameRank = p$blocks_per_game_rank,
        blocksPerGameRankDisplay = p$blocks_per_game_rankDisplay,
        fieldGoalPct = round(p$fg_pct, 1),
        fieldGoalPctRank = p$fg_pct_rank,
        fieldGoalPctRankDisplay = p$fg_pct_rankDisplay,
        threePtPct = round(p$three_pt_pct, 1),
        threePtPctRank = p$three_pt_pct_rank,
        threePtPctRankDisplay = p$three_pt_pct_rankDisplay
      )
    )

    # Add advanced player stats if available
    if (!is.null(player_advanced_stats) && !is.na(p$player_ts_pct)) {
      player_data$stats$trueShootingPct <- round(p$player_ts_pct, 1)
      player_data$stats$trueShootingPctRank <- p$player_ts_pct_rank
      player_data$stats$trueShootingPctRankDisplay <- p$player_ts_pct_rankDisplay
      player_data$stats$effectiveFgPct <- round(p$player_efg_pct, 1)
      player_data$stats$effectiveFgPctRank <- p$player_efg_pct_rank
      player_data$stats$effectiveFgPctRankDisplay <- p$player_efg_pct_rankDisplay
      player_data$stats$pie <- round(p$player_pie, 1)
      player_data$stats$pieRank <- p$player_pie_rank
      player_data$stats$pieRankDisplay <- p$player_pie_rankDisplay
    }

    if (!is.null(player_usage_stats) && !is.na(p$player_usg_pct)) {
      player_data$stats$usagePct <- round(p$player_usg_pct, 1)
      player_data$stats$usagePctRank <- p$player_usg_pct_rank
      player_data$stats$usagePctRankDisplay <- p$player_usg_pct_rankDisplay
    }

    player_data
  })

  away_players_data <- lapply(1:nrow(away_players), function(i) {
    p <- away_players[i, ]
    player_data <- list(
      id = p$athlete_id,
      name = p$athlete_display_name,
      position = p$athlete_position_abbreviation,
      stats = list(
        gamesPlayed = p$games_played,
        pointsPerGame = round(p$points_per_game, 1),
        pointsPerGameRank = p$points_per_game_rank,
        pointsPerGameRankDisplay = p$points_per_game_rankDisplay,
        reboundsPerGame = round(p$rebounds_per_game, 1),
        reboundsPerGameRank = p$rebounds_per_game_rank,
        reboundsPerGameRankDisplay = p$rebounds_per_game_rankDisplay,
        assistsPerGame = round(p$assists_per_game, 1),
        assistsPerGameRank = p$assists_per_game_rank,
        assistsPerGameRankDisplay = p$assists_per_game_rankDisplay,
        stealsPerGame = round(p$steals_per_game, 1),
        stealsPerGameRank = p$steals_per_game_rank,
        stealsPerGameRankDisplay = p$steals_per_game_rankDisplay,
        blocksPerGame = round(p$blocks_per_game, 1),
        blocksPerGameRank = p$blocks_per_game_rank,
        blocksPerGameRankDisplay = p$blocks_per_game_rankDisplay,
        fieldGoalPct = round(p$fg_pct, 1),
        fieldGoalPctRank = p$fg_pct_rank,
        fieldGoalPctRankDisplay = p$fg_pct_rankDisplay,
        threePtPct = round(p$three_pt_pct, 1),
        threePtPctRank = p$three_pt_pct_rank,
        threePtPctRankDisplay = p$three_pt_pct_rankDisplay
      )
    )

    # Add advanced player stats if available
    if (!is.null(player_advanced_stats) && !is.na(p$player_ts_pct)) {
      player_data$stats$trueShootingPct <- round(p$player_ts_pct, 1)
      player_data$stats$trueShootingPctRank <- p$player_ts_pct_rank
      player_data$stats$trueShootingPctRankDisplay <- p$player_ts_pct_rankDisplay
      player_data$stats$effectiveFgPct <- round(p$player_efg_pct, 1)
      player_data$stats$effectiveFgPctRank <- p$player_efg_pct_rank
      player_data$stats$effectiveFgPctRankDisplay <- p$player_efg_pct_rankDisplay
      player_data$stats$pie <- round(p$player_pie, 1)
      player_data$stats$pieRank <- p$player_pie_rank
      player_data$stats$pieRankDisplay <- p$player_pie_rankDisplay
    }

    if (!is.null(player_usage_stats) && !is.na(p$player_usg_pct)) {
      player_data$stats$usagePct <- round(p$player_usg_pct, 1)
      player_data$stats$usagePctRank <- p$player_usg_pct_rank
      player_data$stats$usagePctRankDisplay <- p$player_usg_pct_rankDisplay
    }

    player_data
  })

  # Build matchup object
  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = game$game_name,
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    homePlayers = home_players_data,
    awayPlayers = away_players_data
  )

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

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

# ============================================================================
# STEP 5: Generate output JSON
# ============================================================================
cat("\n5. Generating output JSON...\n")

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
    list(label = "player", layout = "left", color = "#2196F3")
  ),
  sortOrder = 0,
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
  # Development mode - just print the output location
  cat("Development mode - output written to:", tmp_file, "\n")
  cat("To upload to S3, set AWS_S3_BUCKET environment variable\n")
}

cat("\n=== NBA Matchup Stats generation complete ===\n")
