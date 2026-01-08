#!/usr/bin/env Rscript

library(nflreadr)
library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)

# Script runs in production mode by default

# Constants
MIN_GAMES_QB <- 8
MIN_GAMES_RB <- 6
MIN_GAMES_WR <- 6
CURRENT_SEASON <- 2025

cat("=== Loading NFL data for", CURRENT_SEASON, "season ===\n")

# ============================================================================
# STEP 1 & 2: Load team stats and calculate season totals with ranks
# ============================================================================
cat("\n1. Loading team stats from nflreadr...\n")

# Load play-by-play data for defensive EPA calculation
pbp_data <- tryCatch({
  load_pbp(seasons = CURRENT_SEASON)
}, error = function(e) {
  cat("Error loading play-by-play data:", e$message, "\n")
  stop(e)
})

cat("Loaded", nrow(pbp_data), "plays from play-by-play data\n")

# Calculate comprehensive offensive stats by week from play-by-play data
offense_weekly <- pbp_data %>%
  filter(!is.na(posteam)) %>%
  group_by(posteam, week) %>%
  summarise(
    # EPA stats (only pass and run plays)
    off_epa_total = sum(epa[play_type %in% c("pass", "run")], na.rm = TRUE),
    off_plays = sum(play_type %in% c("pass", "run"), na.rm = TRUE),

    # Yards stats
    total_yards = sum(yards_gained, na.rm = TRUE),
    pass_yards = sum(yards_gained[play_type == "pass"], na.rm = TRUE),
    rush_yards = sum(yards_gained[play_type == "run"], na.rm = TRUE),

    # Scoring stats
    points_scored = sum(posteam_score_post - posteam_score, na.rm = TRUE),
    touchdowns = sum(touchdown == 1, na.rm = TRUE),

    # Play counts
    total_plays = n(),
    pass_plays = sum(play_type == "pass", na.rm = TRUE),
    rush_plays = sum(play_type == "run", na.rm = TRUE),

    # Efficiency stats
    third_down_conversions = sum(third_down_converted == 1, na.rm = TRUE),
    third_down_attempts = sum(!is.na(third_down_converted), na.rm = TRUE),

    # Turnover stats
    interceptions_thrown = sum(interception == 1, na.rm = TRUE),
    fumbles_lost = sum(fumble_lost == 1, na.rm = TRUE),

    .groups = "drop"
  ) %>%
  rename(team = posteam) %>%
  mutate(team = ifelse(team == "LA", "LAR", team))

# Get detailed offensive stats from player stats
player_stats_weekly <- tryCatch({
  player_data <- load_player_stats(seasons = CURRENT_SEASON)

  player_data %>%
    group_by(team, week) %>%
    summarise(
      passing_epa_total = sum(passing_epa, na.rm = TRUE),
      passing_attempts = sum(attempts, na.rm = TRUE),
      rushing_epa_total = sum(rushing_epa, na.rm = TRUE),
      rushing_carries = sum(carries, na.rm = TRUE),
      receiving_epa_total = sum(receiving_epa, na.rm = TRUE),
      receiving_targets = sum(targets, na.rm = TRUE),
      passing_cpoe = mean(passing_cpoe, na.rm = TRUE),
      pacr = mean(pacr, na.rm = TRUE),
      passing_first_downs = sum(passing_first_downs, na.rm = TRUE),
      sacks_suffered = sum(sacks_suffered, na.rm = TRUE),
      .groups = "drop"
    )
}, error = function(e) {
  cat("Error loading team stats:", e$message, "\n")
  stop(e)
})

# Calculate comprehensive defensive stats by week from play-by-play data
defense_weekly <- pbp_data %>%
  filter(!is.na(defteam)) %>%
  group_by(defteam, week) %>%
  summarise(
    # EPA stats (only pass and run plays)
    def_epa_total = sum(epa[play_type %in% c("pass", "run")], na.rm = TRUE),
    def_plays = sum(play_type %in% c("pass", "run"), na.rm = TRUE),

    # Yards allowed stats
    total_yards_allowed = sum(yards_gained, na.rm = TRUE),
    pass_yards_allowed = sum(yards_gained[play_type == "pass"], na.rm = TRUE),
    rush_yards_allowed = sum(yards_gained[play_type == "run"], na.rm = TRUE),

    # Scoring defense stats
    points_allowed = sum(posteam_score_post - posteam_score, na.rm = TRUE),
    touchdowns_allowed = sum(touchdown == 1, na.rm = TRUE),

    # Defensive playmaking
    sacks_made = sum(sack == 1, na.rm = TRUE),
    interceptions_made = sum(interception == 1, na.rm = TRUE),
    fumbles_forced = sum(fumble == 1, na.rm = TRUE),
    fumbles_recovered = sum(fumble_lost == 1, na.rm = TRUE),

    # Efficiency defense
    third_down_conversions_allowed = sum(third_down_converted == 1, na.rm = TRUE),
    third_down_attempts_def = sum(!is.na(third_down_converted), na.rm = TRUE),

    .groups = "drop"
  ) %>%
  rename(team = defteam) %>%
  mutate(team = ifelse(team == "LA", "LAR", team))

# Join all stats together
team_stats_weekly <- player_stats_weekly %>%
  left_join(offense_weekly, by = c("team", "week")) %>%
  left_join(defense_weekly, by = c("team", "week"))

cat("Loaded weekly team stats:", nrow(team_stats_weekly), "rows\n")

# Calculate season totals for ranking
team_season_totals <- team_stats_weekly %>%
  group_by(team) %>%
  summarise(
    # Player-level aggregates
    passing_epa_total = sum(passing_epa_total, na.rm = TRUE),
    passing_attempts = sum(passing_attempts, na.rm = TRUE),
    rushing_epa_total = sum(rushing_epa_total, na.rm = TRUE),
    rushing_carries = sum(rushing_carries, na.rm = TRUE),
    receiving_epa_total = sum(receiving_epa_total, na.rm = TRUE),
    receiving_targets = sum(receiving_targets, na.rm = TRUE),
    passing_cpoe = mean(passing_cpoe, na.rm = TRUE),
    pacr = mean(pacr, na.rm = TRUE),
    passing_first_downs = sum(passing_first_downs, na.rm = TRUE),
    sacks_suffered = sum(sacks_suffered, na.rm = TRUE),

    # Offensive totals
    off_epa_total = sum(off_epa_total, na.rm = TRUE),
    off_plays = sum(off_plays, na.rm = TRUE),
    total_yards = sum(total_yards, na.rm = TRUE),
    pass_yards = sum(pass_yards, na.rm = TRUE),
    rush_yards = sum(rush_yards, na.rm = TRUE),
    points_scored = sum(points_scored, na.rm = TRUE),
    touchdowns = sum(touchdowns, na.rm = TRUE),
    total_plays = sum(total_plays, na.rm = TRUE),
    third_down_conversions = sum(third_down_conversions, na.rm = TRUE),
    third_down_attempts = sum(third_down_attempts, na.rm = TRUE),
    interceptions_thrown = sum(interceptions_thrown, na.rm = TRUE),
    fumbles_lost = sum(fumbles_lost, na.rm = TRUE),

    # Defensive totals
    def_epa_total = sum(def_epa_total, na.rm = TRUE),
    def_plays = sum(def_plays, na.rm = TRUE),
    total_yards_allowed = sum(total_yards_allowed, na.rm = TRUE),
    pass_yards_allowed = sum(pass_yards_allowed, na.rm = TRUE),
    rush_yards_allowed = sum(rush_yards_allowed, na.rm = TRUE),
    points_allowed = sum(points_allowed, na.rm = TRUE),
    touchdowns_allowed = sum(touchdowns_allowed, na.rm = TRUE),
    sacks_made = sum(sacks_made, na.rm = TRUE),
    interceptions_made = sum(interceptions_made, na.rm = TRUE),
    fumbles_forced = sum(fumbles_forced, na.rm = TRUE),
    third_down_conversions_allowed = sum(third_down_conversions_allowed, na.rm = TRUE),
    third_down_attempts_def = sum(third_down_attempts_def, na.rm = TRUE),

    # Number of games played for per-game calculations
    games_played = n_distinct(week),

    .groups = "drop"
  ) %>%
  mutate(
    # Offensive per-play/per-game stats
    off_epa = if_else(off_plays > 0, off_epa_total / off_plays, NA_real_),
    yards_per_game = if_else(games_played > 0, total_yards / games_played, NA_real_),
    pass_yards_per_game = if_else(games_played > 0, pass_yards / games_played, NA_real_),
    rush_yards_per_game = if_else(games_played > 0, rush_yards / games_played, NA_real_),
    points_per_game = if_else(games_played > 0, points_scored / games_played, NA_real_),
    yards_per_play = if_else(total_plays > 0, total_yards / total_plays, NA_real_),
    third_down_pct = if_else(third_down_attempts > 0, third_down_conversions / third_down_attempts * 100, NA_real_),
    turnover_differential = (interceptions_made + fumbles_forced) - (interceptions_thrown + fumbles_lost),

    # Defensive per-play/per-game stats
    def_epa = if_else(def_plays > 0, def_epa_total / def_plays, NA_real_),
    yards_allowed_per_game = if_else(games_played > 0, total_yards_allowed / games_played, NA_real_),
    pass_yards_allowed_per_game = if_else(games_played > 0, pass_yards_allowed / games_played, NA_real_),
    rush_yards_allowed_per_game = if_else(games_played > 0, rush_yards_allowed / games_played, NA_real_),
    points_allowed_per_game = if_else(games_played > 0, points_allowed / games_played, NA_real_),
    third_down_pct_def = if_else(third_down_attempts_def > 0, third_down_conversions_allowed / third_down_attempts_def * 100, NA_real_),

    # EPA-based stats for player metrics
    rushing_epa = if_else(rushing_carries > 0, rushing_epa_total / rushing_carries, NA_real_),
    receiving_epa = if_else(receiving_targets > 0, receiving_epa_total / receiving_targets, NA_real_),

    # Offensive ranks
    off_epa_rank = rank(-off_epa),
    yards_per_game_rank = rank(-yards_per_game),
    pass_yards_per_game_rank = rank(-pass_yards_per_game),
    rush_yards_per_game_rank = rank(-rush_yards_per_game),
    points_per_game_rank = rank(-points_per_game),
    yards_per_play_rank = rank(-yards_per_play),
    third_down_pct_rank = rank(-third_down_pct),
    rushing_epa_rank = rank(-rushing_epa),
    receiving_epa_rank = rank(-receiving_epa),
    pacr_rank = rank(-pacr),
    passing_first_downs_rank = rank(-passing_first_downs),
    sacks_suffered_rank = rank(sacks_suffered),
    touchdowns_rank = rank(-touchdowns),
    interceptions_thrown_rank = rank(interceptions_thrown),
    fumbles_lost_rank = rank(fumbles_lost),
    turnovers_rank = rank(interceptions_thrown + fumbles_lost),

    # Defensive ranks (lower is better)
    def_epa_rank = rank(def_epa),
    yards_allowed_per_game_rank = rank(yards_allowed_per_game),
    pass_yards_allowed_per_game_rank = rank(pass_yards_allowed_per_game),
    rush_yards_allowed_per_game_rank = rank(rush_yards_allowed_per_game),
    points_allowed_per_game_rank = rank(points_allowed_per_game),
    third_down_pct_def_rank = rank(third_down_pct_def),
    sacks_made_rank = rank(-sacks_made),
    interceptions_made_rank = rank(-interceptions_made),
    fumbles_forced_rank = rank(-fumbles_forced),
    touchdowns_allowed_rank = rank(touchdowns_allowed),
    turnover_differential_rank = rank(-turnover_differential)
  )

cat("Calculated season totals for", nrow(team_season_totals), "teams\n")

# ============================================================================
# STEP 3: Calculate cumulative EPA by week
# ============================================================================
cat("\n3. Calculating cumulative EPA...\n")

cum_epa_by_team <- team_stats_weekly %>%
  group_by(team) %>%
  arrange(week) %>%
  mutate(
    cum_epa = cumsum(passing_epa_total + rushing_epa_total)
  ) %>%
  select(team, week, cum_epa) %>%
  ungroup()

cat("Calculated cumulative EPA for", length(unique(cum_epa_by_team$team)), "teams\n")

# ============================================================================
# STEP 4-7: Load player stats, filter by snaps, rank, and select top N
# ============================================================================
cat("\n4-7. Loading and ranking player stats...\n")

player_stats <- tryCatch({
  load_player_stats(seasons = CURRENT_SEASON)
}, error = function(e) {
  cat("Error loading player stats:", e$message, "\n")
  stop(e)
})

cat("Loaded", nrow(player_stats), "player stat rows\n")

# Calculate season totals and filter by games played
player_stats_filtered <- player_stats %>%
  group_by(player_id, player_name, team, position) %>%
  summarise(
    games = n_distinct(week),

    # QB stats - comprehensive passing stats
    passing_epa_total = sum(passing_epa, na.rm = TRUE),
    passing_attempts = sum(attempts, na.rm = TRUE),
    completions = sum(completions, na.rm = TRUE),
    passing_yards = sum(passing_yards, na.rm = TRUE),
    passing_tds = sum(passing_tds, na.rm = TRUE),
    interceptions = sum(passing_interceptions, na.rm = TRUE),
    passing_cpoe = mean(passing_cpoe, na.rm = TRUE),
    pacr = mean(pacr, na.rm = TRUE),
    passing_air_yards = sum(passing_air_yards, na.rm = TRUE),

    # RB stats - comprehensive rushing and receiving stats
    rushing_epa_total = sum(rushing_epa, na.rm = TRUE),
    rushing_yards = sum(rushing_yards, na.rm = TRUE),
    rushing_tds = sum(rushing_tds, na.rm = TRUE),
    rushing_first_downs = sum(rushing_first_downs, na.rm = TRUE),
    carries = sum(carries, na.rm = TRUE),
    receiving_epa_total = sum(receiving_epa, na.rm = TRUE),
    receptions = sum(receptions, na.rm = TRUE),
    receiving_yards = sum(receiving_yards, na.rm = TRUE),
    receiving_tds = sum(receiving_tds, na.rm = TRUE),
    targets = sum(targets, na.rm = TRUE),

    # WR/TE stats - comprehensive receiving stats
    wopr = mean(wopr, na.rm = TRUE),
    racr = mean(racr, na.rm = TRUE),
    air_yards_share = mean(air_yards_share, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    # Calculate EPA per play
    passing_epa = if_else(passing_attempts > 0, passing_epa_total / passing_attempts, NA_real_),
    rushing_epa = if_else(carries > 0, rushing_epa_total / carries, NA_real_),
    receiving_epa = if_else(targets > 0, receiving_epa_total / targets, NA_real_),

    # QB per-game and efficiency stats
    completion_pct = if_else(passing_attempts > 0, completions / passing_attempts * 100, NA_real_),
    passing_yards_per_game = if_else(games > 0, passing_yards / games, NA_real_),
    passing_tds_per_game = if_else(games > 0, passing_tds / games, NA_real_),

    # RB per-game and efficiency stats
    rushing_yards_per_carry = if_else(carries > 0, rushing_yards / carries, NA_real_),
    rushing_yards_per_game = if_else(games > 0, rushing_yards / games, NA_real_),
    rushing_tds_per_game = if_else(games > 0, rushing_tds / games, NA_real_),
    receiving_yards_per_game = if_else(games > 0, receiving_yards / games, NA_real_),

    # WR/TE per-game and efficiency stats
    receiving_yards_per_reception = if_else(receptions > 0, receiving_yards / receptions, NA_real_),
    catch_pct = if_else(targets > 0, receptions / targets * 100, NA_real_),

    # For QBs: total EPA per play (passing + rushing combined)
    qb_total_plays = if_else(position == "QB", passing_attempts + carries, NA_real_),
    qb_total_epa = if_else(position == "QB", passing_epa_total + rushing_epa_total, NA_real_),
    qb_epa_per_play = if_else(position == "QB" & qb_total_plays > 0, qb_total_epa / qb_total_plays, NA_real_)
  ) %>%
  filter(
    (position == "QB" & games >= MIN_GAMES_QB) |
    (position == "RB" & games >= MIN_GAMES_RB) |
    (position %in% c("WR", "TE") & games >= MIN_GAMES_WR)
  )

cat("Filtered to", nrow(player_stats_filtered), "players meeting snap thresholds\n")

# Rank players by position for each stat
player_stats_ranked <- player_stats_filtered %>%
  group_by(position) %>%
  mutate(
    # QB ranks - comprehensive stats
    qb_epa_per_play_rank = if_else(position == "QB", rank(-qb_epa_per_play), NA_real_),
    passing_epa_rank = if_else(position == "QB", rank(-passing_epa), NA_real_),
    passing_cpoe_rank = if_else(position == "QB", rank(-passing_cpoe), NA_real_),
    pacr_rank = if_else(position == "QB", rank(-pacr), NA_real_),
    passing_yards_rank = if_else(position == "QB", rank(-passing_yards), NA_real_),
    passing_tds_rank = if_else(position == "QB", rank(-passing_tds), NA_real_),
    completion_pct_rank = if_else(position == "QB", rank(-completion_pct), NA_real_),
    passing_yards_per_game_rank = if_else(position == "QB", rank(-passing_yards_per_game), NA_real_),
    interceptions_rank = if_else(position == "QB", rank(interceptions), NA_real_),

    # RB ranks - comprehensive rushing and receiving stats
    rushing_epa_rank = if_else(position == "RB", rank(-rushing_epa), NA_real_),
    rushing_yards_rank = if_else(position == "RB", rank(-rushing_yards), NA_real_),
    rushing_tds_rank = if_else(position == "RB", rank(-rushing_tds), NA_real_),
    rushing_yards_per_carry_rank = if_else(position == "RB", rank(-rushing_yards_per_carry), NA_real_),
    rushing_yards_per_game_rank = if_else(position == "RB", rank(-rushing_yards_per_game), NA_real_),
    carries_rank = if_else(position == "RB", rank(-carries), NA_real_),
    rb_receiving_epa_rank = if_else(position == "RB", rank(-receiving_epa), NA_real_),
    rb_receiving_yards_rank = if_else(position == "RB", rank(-receiving_yards), NA_real_),
    rb_receiving_tds_rank = if_else(position == "RB", rank(-receiving_tds), NA_real_),
    rb_receptions_rank = if_else(position == "RB", rank(-receptions), NA_real_),
    rb_receiving_yards_per_game_rank = if_else(position == "RB", rank(-receiving_yards_per_game), NA_real_),

    # WR/TE ranks - comprehensive receiving stats
    receiving_epa_rank = if_else(position %in% c("WR", "TE"), rank(-receiving_epa), NA_real_),
    receiving_yards_rank = if_else(position %in% c("WR", "TE"), rank(-receiving_yards), NA_real_),
    receiving_tds_rank = if_else(position %in% c("WR", "TE"), rank(-receiving_tds), NA_real_),
    receptions_rank = if_else(position %in% c("WR", "TE"), rank(-receptions), NA_real_),
    receiving_yards_per_reception_rank = if_else(position %in% c("WR", "TE"), rank(-receiving_yards_per_reception), NA_real_),
    receiving_yards_per_game_rank = if_else(position %in% c("WR", "TE"), rank(-receiving_yards_per_game), NA_real_),
    catch_pct_rank = if_else(position %in% c("WR", "TE"), rank(-catch_pct), NA_real_),
    wopr_rank = if_else(position %in% c("WR", "TE"), rank(-wopr), NA_real_),
    racr_rank = if_else(position %in% c("WR", "TE"), rank(-racr), NA_real_),
    air_yards_share_rank = if_else(position %in% c("WR", "TE"), rank(-air_yards_share), NA_real_)
  ) %>%
  ungroup()

# Calculate target share per team
player_stats_ranked <- player_stats_ranked %>%
  group_by(team) %>%
  mutate(
    team_targets = sum(targets, na.rm = TRUE)
  ) %>%
  ungroup() %>%
  mutate(
    target_share = if_else(team_targets > 0, targets / team_targets, 0),
    target_share_rank = rank(-target_share)
  )

# Select top players per team
top_players <- player_stats_ranked %>%
  group_by(team, position) %>%
  arrange(desc(case_when(
    position == "QB" ~ passing_epa,
    position == "RB" ~ rushing_epa,
    position %in% c("WR", "TE") ~ wopr,
    TRUE ~ 0
  ))) %>%
  mutate(player_rank = row_number()) %>%
  filter(
    (position == "QB" & player_rank <= 1) |
    (position == "RB" & player_rank <= 2) |
    (position %in% c("WR", "TE") & player_rank <= 3)
  ) %>%
  ungroup()

cat("Selected top players:", nrow(top_players), "total\n")

# ============================================================================
# STEP 8: Load schedule data for matchups, h2h, and common opponents
# ============================================================================
cat("\n8. Loading schedule data...\n")

schedules <- tryCatch({
  load_schedules(seasons = CURRENT_SEASON)
}, error = function(e) {
  cat("Error loading schedules:", e$message, "\n")
  stop(e)
})

cat("Loaded", nrow(schedules), "games from schedule\n")

# Helper function to add random delay between API calls (3-5 seconds)
add_api_delay <- function() {
  delay <- runif(1, 3, 5)
  cat(sprintf("Waiting %.2f seconds to avoid rate limiting...\n", delay))
  Sys.sleep(delay)
}

# Helper function to determine current week and season type
get_current_week_info <- function(schedules_df) {
  # Get scoreboard to determine current week
  add_api_delay()
  resp <- GET("https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard")
  scoreboard <- content(resp, as = "parsed")

  if (!is.null(scoreboard$week)) {
    espn_week <- scoreboard$week$number
    espn_season_type <- scoreboard$season$type

    cat(sprintf("ESPN current week: %d, Season type: %d (%s)\n",
                espn_week, espn_season_type,
                ifelse(espn_season_type == 2, "Regular", ifelse(espn_season_type == 3, "Playoffs", "Other"))))

    # Check if we should advance to next week based on schedule
    # Strategy: Look for the first week with upcoming games (no results yet)
    # but allow Tuesday rollover by checking if all games in current week are complete

    current_day_of_week <- as.POSIXlt(Sys.Date())$wday  # 0=Sunday, 2=Tuesday, etc.

    # For regular season
    if (espn_season_type == 2) {
      # Find the first upcoming week (has games but no results yet)
      upcoming_weeks <- schedules_df %>%
        filter(game_type == "REG", is.na(result)) %>%
        pull(week) %>%
        unique() %>%
        sort()

      # Check if ESPN's current week has all games completed
      espn_week_games <- schedules_df %>%
        filter(game_type == "REG", week == espn_week)

      espn_week_all_complete <- nrow(espn_week_games) > 0 &&
                                all(!is.na(espn_week_games$result))

      # If it's Tuesday (2) or later and ESPN's current week is complete,
      # advance to the next week
      if (current_day_of_week >= 2 && espn_week_all_complete) {
        if (length(upcoming_weeks) > 0) {
          target_week <- upcoming_weeks[1]
          cat(sprintf("Tuesday rollover: Advancing from week %d to week %d\n",
                      espn_week, target_week))
          return(list(week = target_week, season_type = 2))
        } else {
          # No more regular season games - regular season is over
          # Check if nflreadr has playoff data
          playoff_games <- schedules_df %>% filter(game_type == "POST")
          if (nrow(playoff_games) > 0) {
            cat("Regular season complete, advancing to playoffs (week 1)\n")
            return(list(week = 1, season_type = 3))
          } else {
            # No playoff data in nflreadr yet, but regular season is done
            # Wait for ESPN to update to playoffs, or stay on last week
            cat("Regular season complete but no playoff data available yet\n")
            cat("Staying on week", espn_week, "until playoffs begin\n")
            return(list(week = espn_week, season_type = 2))
          }
        }
      }

      # Otherwise use ESPN's week if there are upcoming games
      if (espn_week %in% upcoming_weeks) {
        return(list(week = espn_week, season_type = espn_season_type))
      } else if (length(upcoming_weeks) > 0) {
        return(list(week = upcoming_weeks[1], season_type = 2))
      } else {
        # Use ESPN's week as fallback
        return(list(week = espn_week, season_type = espn_season_type))
      }
    }

    # For playoffs
    if (espn_season_type == 3) {
      # Find upcoming playoff games
      upcoming_playoff_weeks <- schedules_df %>%
        filter(game_type == "POST", is.na(result)) %>%
        pull(week) %>%
        unique() %>%
        sort()

      # If it's Tuesday or later and we have upcoming playoff games
      if (current_day_of_week >= 2 && length(upcoming_playoff_weeks) > 0) {
        target_week <- upcoming_playoff_weeks[1]
        cat(sprintf("Tuesday rollover (playoffs): Advancing to playoff week %d\n", target_week))
        return(list(week = target_week, season_type = 3))
      }

      # Use ESPN's week if valid
      if (espn_week %in% upcoming_playoff_weeks) {
        return(list(week = espn_week, season_type = espn_season_type))
      } else if (length(upcoming_playoff_weeks) > 0) {
        return(list(week = upcoming_playoff_weeks[1], season_type = 3))
      }
    }

    # Fallback to ESPN's values
    return(list(week = espn_week, season_type = espn_season_type))
  }

  # Fallback to regular season week 1 if we can't determine
  return(list(week = 1, season_type = 2))
}

# Get current week info (pass schedules for Tuesday rollover logic)
week_info <- get_current_week_info(schedules)
current_week <- week_info$week
season_type <- week_info$season_type

# Determine if we're in playoffs (season_type 3) or regular season (season_type 2)
is_playoffs <- (season_type == 3)

# Fetch all games for the current week from ESPN API
# This ensures we get ALL games regardless of their completion status
cat(sprintf("\nFetching games for week %d from ESPN API (%s)...\n",
            current_week, ifelse(is_playoffs, "playoffs", "regular season")))

add_api_delay()
events_url <- sprintf(
  "https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/seasons/%d/types/%d/weeks/%d/events?limit=50",
  CURRENT_SEASON, season_type, current_week
)

events_resp <- tryCatch({
  GET(events_url)
}, error = function(e) {
  cat("Error fetching events:", e$message, "\n")
  stop(e)
})

events_data <- content(events_resp, as = "parsed")

if (is.null(events_data$items) || length(events_data$items) == 0) {
  cat("No games found for current week, using nflreadr schedules as fallback\n")
  current_week_games <- schedules %>%
    filter(
      week == max(week[game_type == "REG" & is.na(result)], na.rm = TRUE),
      game_type == "REG",
      is.na(result)
    )

  if (nrow(current_week_games) == 0) {
    current_week_games <- schedules %>%
      filter(week == max(week), game_type == "REG")
  }
} else {
  # Extract event IDs from the ESPN API response
  event_ids <- sapply(events_data$items, function(item) {
    # Extract event ID from the $ref URL
    ref_url <- item$`$ref`
    id <- sub(".*events/([0-9]+)\\?.*", "\\1", ref_url)
    return(id)
  })

  cat(sprintf("Found %d event IDs from ESPN API\n", length(event_ids)))

  # Match these events with our schedule data
  current_week_games <- schedules %>%
    filter(
      game_id %in% event_ids |
      (week == current_week & game_type == ifelse(is_playoffs, "POST", "REG"))
    )

  # If we don't get matches, fall back to week-based filtering
  if (nrow(current_week_games) == 0) {
    cat("No matches found by event ID, falling back to week filtering\n")
    current_week_games <- schedules %>%
      filter(
        week == current_week,
        game_type == ifelse(is_playoffs, "POST", "REG")
      )
  }
}

cat("Found", nrow(current_week_games), "games for current week\n")

# Safety check: if we have no games, we need to create placeholder games from ESPN data
if (nrow(current_week_games) == 0 && exists("event_ids") && length(event_ids) > 0) {
  cat("WARNING: nflreadr has no game data for this week, but ESPN has events\n")
  cat("This likely means playoff data isn't available in nflreadr yet\n")
  cat("Creating placeholder game records from ESPN event IDs...\n")

  # We'll fetch the team info from the scoreboard and create minimal game records
  # This will be done in the team mapping section below
}

# Check if we have any games at all - if not, exit gracefully
if (nrow(current_week_games) == 0 && (!exists("event_ids") || length(event_ids) == 0)) {
  cat("\nERROR: No games found for current week from either nflreadr or ESPN\n")
  cat("This likely means:\n")
  cat("  - The season is over, or\n")
  cat("  - There's a data availability issue\n")
  cat("Exiting without generating output.\n")
  quit(status = 1)
}

# Store event IDs for odds fetching
espn_event_ids <- if (exists("event_ids")) event_ids else c()

# Build a mapping from team matchups to ESPN event IDs
# Use the scoreboard endpoint which includes teams and event IDs in one call
cat("Building team-to-event mapping for odds lookup...\n")
team_to_event_map <- list()

add_api_delay()
scoreboard_url <- sprintf(
  "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?seasontype=%d&week=%d",
  season_type, current_week
)

scoreboard_resp <- tryCatch({
  resp <- GET(scoreboard_url)
  if (status_code(resp) == 200) {
    content(resp, as = "parsed")
  } else {
    NULL
  }
}, error = function(e) {
  cat("Warning: Could not fetch scoreboard for mapping\n")
  NULL
})

if (!is.null(scoreboard_resp) && !is.null(scoreboard_resp$events)) {
  # If we have no games from nflreadr, we'll build them from ESPN data
  needs_placeholder_games <- nrow(current_week_games) == 0
  placeholder_games_list <- list()

  for (event in scoreboard_resp$events) {
    event_id <- event$id

    if (!is.null(event$competitions) && length(event$competitions) > 0) {
      comp <- event$competitions[[1]]

      if (!is.null(comp$competitors) && length(comp$competitors) >= 2) {
        home_abbr <- NULL
        away_abbr <- NULL

        for (competitor in comp$competitors) {
          abbr <- competitor$team$abbreviation
          home_away <- competitor$homeAway

          if (home_away == "home") {
            home_abbr <- abbr
          } else if (home_away == "away") {
            away_abbr <- abbr
          }
        }

        if (!is.null(home_abbr) && !is.null(away_abbr)) {
          # Normalize team abbreviations (ESPN -> nflreadr format)
          home_abbr_norm <- case_when(
            home_abbr == "WSH" ~ "WAS",
            home_abbr == "LAR" ~ "LA",
            TRUE ~ home_abbr
          )
          away_abbr_norm <- case_when(
            away_abbr == "WSH" ~ "WAS",
            away_abbr == "LAR" ~ "LA",
            TRUE ~ away_abbr
          )

          matchup_key <- paste(away_abbr_norm, home_abbr_norm, sep = "-")
          team_to_event_map[[matchup_key]] <- event_id
          cat(sprintf("  Mapped %s to event %s\n", matchup_key, event_id))

          # If we need placeholder games, create them
          if (needs_placeholder_games) {
            placeholder_game <- data.frame(
              game_id = event_id,
              week = current_week,
              game_type = ifelse(is_playoffs, "POST", "REG"),
              home_team = home_abbr_norm,
              away_team = away_abbr_norm,
              result = NA_real_,
              home_score = NA_real_,
              away_score = NA_real_,
              stringsAsFactors = FALSE
            )
            placeholder_games_list[[length(placeholder_games_list) + 1]] <- placeholder_game
          }
        }
      }
    }
  }

  # If we created placeholder games, combine them into current_week_games
  if (needs_placeholder_games && length(placeholder_games_list) > 0) {
    current_week_games <- do.call(rbind, placeholder_games_list)
    cat(sprintf("Created %d placeholder games from ESPN data\n", nrow(current_week_games)))
  }
}

cat(sprintf("Built mapping for %d matchups\n", length(team_to_event_map)))
cat(sprintf("Final game count: %d games to process\n", nrow(current_week_games)))

# Process all matchups

# ============================================================================
# STEP 9: Fetch odds from ESPN API using dedicated odds endpoint
# ============================================================================
cat("\n9. Fetching odds from ESPN API...\n")

# Helper function to normalize team abbreviations for ESPN API
normalize_team_abbr <- function(abbr) {
  # ESPN uses different abbreviations for some teams
  # WSH instead of WAS for Washington
  # LAR instead of LA for Los Angeles Rams
  case_when(
    abbr == "WAS" ~ "WSH",
    abbr == "LA" ~ "LAR",
    TRUE ~ abbr
  )
}

# Cache odds data by event ID to avoid redundant API calls
odds_cache <- list()

# Helper function to fetch odds for a specific event ID
# This uses the ESPN core API which provides odds even after games start/complete
fetch_event_odds <- function(event_id) {
  # Check cache first
  if (!is.null(odds_cache[[event_id]])) {
    return(odds_cache[[event_id]])
  }

  add_api_delay()

  odds_url <- sprintf(
    "http://sports.core.api.espn.com/v2/sports/football/leagues/nfl/events/%s/competitions/%s/odds?lang=en&region=us",
    event_id, event_id
  )

  odds_resp <- tryCatch({
    resp <- GET(odds_url)
    if (status_code(resp) == 200) {
      content(resp, as = "parsed")
    } else {
      NULL
    }
  }, error = function(e) {
    cat("Warning: Could not fetch odds for event", event_id, ":", e$message, "\n")
    NULL
  })

  # Cache the result
  odds_cache[[event_id]] <<- odds_resp
  return(odds_resp)
}

# Helper function to extract odds from ESPN odds API response
extract_odds_from_response <- function(odds_response) {
  default_odds <- list(
    home_spread = NULL,
    home_moneyline = NULL,
    away_spread = NULL,
    away_moneyline = NULL,
    over_under = NULL
  )

  if (is.null(odds_response) || is.null(odds_response$items) || length(odds_response$items) == 0) {
    return(default_odds)
  }

  # Get the first odds provider (usually DraftKings with priority 1)
  odds_item <- odds_response$items[[1]]

  # Extract spread and over/under from top level
  over_under <- odds_item$overUnder
  spread <- odds_item$spread

  # Extract away team odds
  away_spread <- NULL
  away_moneyline <- NULL
  if (!is.null(odds_item$awayTeamOdds)) {
    away_odds <- odds_item$awayTeamOdds

    # Try current first, then close, then open
    if (!is.null(away_odds$current) && !is.null(away_odds$current$pointSpread)) {
      away_spread <- as.numeric(sub("\\+", "", away_odds$current$pointSpread$american))
    } else if (!is.null(away_odds$close) && !is.null(away_odds$close$pointSpread)) {
      away_spread <- as.numeric(sub("\\+", "", away_odds$close$pointSpread$american))
    } else if (!is.null(away_odds$open) && !is.null(away_odds$open$pointSpread)) {
      away_spread <- as.numeric(sub("\\+", "", away_odds$open$pointSpread$american))
    }

    # Get moneyline
    if (!is.null(away_odds$current) && !is.null(away_odds$current$moneyLine)) {
      away_moneyline <- away_odds$current$moneyLine$american
    } else if (!is.null(away_odds$close) && !is.null(away_odds$close$moneyLine)) {
      away_moneyline <- away_odds$close$moneyLine$american
    } else if (!is.null(away_odds$open) && !is.null(away_odds$open$moneyLine)) {
      away_moneyline <- away_odds$open$moneyLine$american
    } else if (!is.null(away_odds$moneyLine)) {
      away_moneyline <- away_odds$moneyLine
    }
  }

  # Extract home team odds
  home_spread <- NULL
  home_moneyline <- NULL
  if (!is.null(odds_item$homeTeamOdds)) {
    home_odds <- odds_item$homeTeamOdds

    # Try current first, then close, then open
    if (!is.null(home_odds$current) && !is.null(home_odds$current$pointSpread)) {
      home_spread <- as.numeric(sub("\\+", "", home_odds$current$pointSpread$american))
    } else if (!is.null(home_odds$close) && !is.null(home_odds$close$pointSpread)) {
      home_spread <- as.numeric(sub("\\+", "", home_odds$close$pointSpread$american))
    } else if (!is.null(home_odds$open) && !is.null(home_odds$open$pointSpread)) {
      home_spread <- as.numeric(sub("\\+", "", home_odds$open$pointSpread$american))
    }

    # Get moneyline
    if (!is.null(home_odds$current) && !is.null(home_odds$current$moneyLine)) {
      home_moneyline <- home_odds$current$moneyLine$american
    } else if (!is.null(home_odds$close) && !is.null(home_odds$close$moneyLine)) {
      home_moneyline <- home_odds$close$moneyLine$american
    } else if (!is.null(home_odds$open) && !is.null(home_odds$open$moneyLine)) {
      home_moneyline <- home_odds$open$moneyLine$american
    } else if (!is.null(home_odds$moneyLine)) {
      home_moneyline <- home_odds$moneyLine
    }
  }

  return(list(
    home_spread = home_spread,
    home_moneyline = home_moneyline,
    away_spread = away_spread,
    away_moneyline = away_moneyline,
    over_under = over_under
  ))
}

# Helper function to extract odds for a game by finding its event ID
get_odds_for_game <- function(home_team, away_team, game_id = NULL) {
  default_odds <- list(
    home_spread = NULL,
    home_moneyline = NULL,
    away_spread = NULL,
    away_moneyline = NULL,
    over_under = NULL
  )

  # Create matchup key
  matchup_key <- paste(away_team, home_team, sep = "-")

  # Look up event ID from our team mapping
  event_id <- team_to_event_map[[matchup_key]]

  if (!is.null(event_id)) {
    odds_response <- fetch_event_odds(event_id)
    if (!is.null(odds_response)) {
      return(extract_odds_from_response(odds_response))
    }
  } else {
    cat("Warning: No event ID found for matchup", matchup_key, "\n")
  }

  return(default_odds)
}

# Helper function to calculate head-to-head record
get_h2h_record <- function(team1, team2, schedules_df) {
  # Find completed games between these two teams
  h2h_games <- schedules_df %>%
    filter(
      game_type == "REG",
      !is.na(home_score),
      !is.na(away_score),
      (home_team == team1 & away_team == team2) |
      (home_team == team2 & away_team == team1)
    )

  if (nrow(h2h_games) == 0) {
    return(list())
  }

  # Build array of matchup objects
  h2h_array <- lapply(seq_len(nrow(h2h_games)), function(i) {
    game <- h2h_games[i, ]

    # Determine winner
    winner <- if (game$home_score > game$away_score) {
      game$home_team
    } else if (game$away_score > game$home_score) {
      game$away_team
    } else {
      "TIE"
    }

    # Build final score string
    final_score <- paste0(
      game$away_team, " ", game$away_score, " - ",
      game$home_team, " ", game$home_score
    )

    list(
      week = as.integer(game$week),
      finalScore = final_score,
      winner = winner
    )
  })

  return(h2h_array)
}

# Helper function to find common opponents
get_common_opponents <- function(team1, team2, schedules_df) {
  # Get completed games only
  completed <- schedules_df %>%
    filter(game_type == "REG", !is.na(home_score), !is.na(away_score))

  # Find all opponents for team1 (excluding team2)
  team1_games <- completed %>%
    filter(home_team == team1 | away_team == team1) %>%
    mutate(
      opponent = ifelse(home_team == team1, away_team, home_team),
      team1_score = ifelse(home_team == team1, home_score, away_score),
      opp_score = ifelse(home_team == team1, away_score, home_score),
      result = case_when(
        team1_score > opp_score ~ "W",
        team1_score < opp_score ~ "L",
        TRUE ~ "T"
      )
    ) %>%
    filter(opponent != team2) %>%
    select(week, opponent, result, team1_score, opp_score)

  # Find all opponents for team2 (excluding team1)
  team2_games <- completed %>%
    filter(home_team == team2 | away_team == team2) %>%
    mutate(
      opponent = ifelse(home_team == team2, away_team, home_team),
      team2_score = ifelse(home_team == team2, home_score, away_score),
      opp_score = ifelse(home_team == team2, away_score, home_score),
      result = case_when(
        team2_score > opp_score ~ "W",
        team2_score < opp_score ~ "L",
        TRUE ~ "T"
      )
    ) %>%
    filter(opponent != team1) %>%
    select(week, opponent, result, team2_score, opp_score)

  # Find common opponents (allow many-to-many for division games)
  common_opps <- inner_join(
    team1_games,
    team2_games,
    by = "opponent",
    suffix = c("_team1", "_team2"),
    relationship = "many-to-many"
  )

  if (nrow(common_opps) == 0) {
    return(list())
  }

  # Build dictionary structure grouped by opponent
  result_dict <- list()
  unique_opponents <- unique(common_opps$opponent)

  for (opp in unique_opponents) {
    opp_rows <- common_opps %>% filter(opponent == opp)

    # Get unique team1 games (remove duplicates from many-to-many join)
    team1_unique <- opp_rows %>%
      select(week_team1, result_team1, team1_score, opp_score_team1) %>%
      distinct()

    # Get unique team2 games
    team2_unique <- opp_rows %>%
      select(week_team2, result_team2, team2_score, opp_score_team2) %>%
      distinct()

    # Build list of results for team1
    team1_results <- lapply(1:nrow(team1_unique), function(i) {
      list(
        week = as.integer(team1_unique$week_team1[i]),
        result = team1_unique$result_team1[i],
        score = paste0(team1_unique$team1_score[i], "-", team1_unique$opp_score_team1[i])
      )
    })

    # Build list of results for team2
    team2_results <- lapply(1:nrow(team2_unique), function(i) {
      list(
        week = as.integer(team2_unique$week_team2[i]),
        result = team2_unique$result_team2[i],
        score = paste0(team2_unique$team2_score[i], "-", team2_unique$opp_score_team2[i])
      )
    })

    # Create the opponent entry
    result_dict[[opp]] <- list()
    result_dict[[opp]][[tolower(team1)]] <- team1_results
    result_dict[[opp]][[tolower(team2)]] <- team2_results
  }

  return(result_dict)
}

# ============================================================================
# STEP 10: Build matchup JSON
# ============================================================================
cat("\n10. Building matchup JSON...\n")

build_team_json <- function(team_abbr, cum_epa_df, season_totals, top_players_df, weekly_stats) {
  # Helper to convert NA to NULL
  na_to_null <- function(x) if (is.na(x)) NULL else x

  # Get cumulative EPA by week
  team_cum_epa <- cum_epa_df %>%
    filter(team == team_abbr) %>%
    select(week, cum_epa)

  cum_epa_list <- setNames(
    as.list(round(team_cum_epa$cum_epa, 2)),
    paste0("week-", team_cum_epa$week)
  )

  # Get EPA per play by week (off and def for each week)
  team_weekly <- weekly_stats %>%
    filter(team == team_abbr)

  epa_by_week_list <- lapply(1:nrow(team_weekly), function(i) {
    # Calculate EPA per play
    off_epa_per_play <- if (!is.na(team_weekly$off_epa_total[i]) && !is.na(team_weekly$off_plays[i]) && team_weekly$off_plays[i] > 0) {
      round(team_weekly$off_epa_total[i] / team_weekly$off_plays[i], 4)
    } else NULL

    def_epa_per_play <- if (!is.na(team_weekly$def_epa_total[i]) && !is.na(team_weekly$def_plays[i]) && team_weekly$def_plays[i] > 0) {
      round(team_weekly$def_epa_total[i] / team_weekly$def_plays[i], 4)
    } else NULL

    list(
      off = off_epa_per_play,
      def = def_epa_per_play
    )
  })
  names(epa_by_week_list) <- paste0("week-", team_weekly$week)

  # Get current season stats
  team_totals <- season_totals %>%
    filter(team == team_abbr)

  if (nrow(team_totals) == 0) {
    cat("Warning: No team stats found for", team_abbr, "\n")
    return(NULL)
  }

  current_stats <- list(
    offense = list(
      off_epa = list(value = round(team_totals$off_epa[1], 3), rank = as.integer(team_totals$off_epa_rank[1])),
      yards_per_game = list(value = round(team_totals$yards_per_game[1], 1), rank = as.integer(team_totals$yards_per_game_rank[1])),
      pass_yards_per_game = list(value = round(team_totals$pass_yards_per_game[1], 1), rank = as.integer(team_totals$pass_yards_per_game_rank[1])),
      rush_yards_per_game = list(value = round(team_totals$rush_yards_per_game[1], 1), rank = as.integer(team_totals$rush_yards_per_game_rank[1])),
      points_per_game = list(value = round(team_totals$points_per_game[1], 1), rank = as.integer(team_totals$points_per_game_rank[1])),
      yards_per_play = list(value = round(team_totals$yards_per_play[1], 2), rank = as.integer(team_totals$yards_per_play_rank[1])),
      third_down_pct = list(value = round(team_totals$third_down_pct[1], 1), rank = as.integer(team_totals$third_down_pct_rank[1])),
      rushing_epa = list(value = round(team_totals$rushing_epa[1], 3), rank = as.integer(team_totals$rushing_epa_rank[1])),
      receiving_epa = list(value = round(team_totals$receiving_epa[1], 3), rank = as.integer(team_totals$receiving_epa_rank[1])),
      pacr = list(value = round(team_totals$pacr[1], 2), rank = as.integer(team_totals$pacr_rank[1])),
      passing_first_downs = list(value = as.integer(team_totals$passing_first_downs[1]), rank = as.integer(team_totals$passing_first_downs_rank[1])),
      sacks_suffered = list(value = as.integer(team_totals$sacks_suffered[1]), rank = as.integer(team_totals$sacks_suffered_rank[1])),
      touchdowns = list(value = as.integer(team_totals$touchdowns[1]), rank = as.integer(team_totals$touchdowns_rank[1])),
      interceptions_thrown = list(value = as.integer(team_totals$interceptions_thrown[1]), rank = as.integer(team_totals$interceptions_thrown_rank[1])),
      fumbles_lost = list(value = as.integer(team_totals$fumbles_lost[1]), rank = as.integer(team_totals$fumbles_lost_rank[1]))
    ),
    defense = list(
      def_epa = list(value = round(team_totals$def_epa[1], 3), rank = as.integer(team_totals$def_epa_rank[1])),
      yards_allowed_per_game = list(value = round(team_totals$yards_allowed_per_game[1], 1), rank = as.integer(team_totals$yards_allowed_per_game_rank[1])),
      pass_yards_allowed_per_game = list(value = round(team_totals$pass_yards_allowed_per_game[1], 1), rank = as.integer(team_totals$pass_yards_allowed_per_game_rank[1])),
      rush_yards_allowed_per_game = list(value = round(team_totals$rush_yards_allowed_per_game[1], 1), rank = as.integer(team_totals$rush_yards_allowed_per_game_rank[1])),
      points_allowed_per_game = list(value = round(team_totals$points_allowed_per_game[1], 1), rank = as.integer(team_totals$points_allowed_per_game_rank[1])),
      third_down_pct_def = list(value = round(team_totals$third_down_pct_def[1], 1), rank = as.integer(team_totals$third_down_pct_def_rank[1])),
      sacks_made = list(value = as.integer(team_totals$sacks_made[1]), rank = as.integer(team_totals$sacks_made_rank[1])),
      interceptions_made = list(value = as.integer(team_totals$interceptions_made[1]), rank = as.integer(team_totals$interceptions_made_rank[1])),
      fumbles_forced = list(value = as.integer(team_totals$fumbles_forced[1]), rank = as.integer(team_totals$fumbles_forced_rank[1])),
      touchdowns_allowed = list(value = as.integer(team_totals$touchdowns_allowed[1]), rank = as.integer(team_totals$touchdowns_allowed_rank[1])),
      turnover_differential = list(value = as.integer(team_totals$turnover_differential[1]), rank = as.integer(team_totals$turnover_differential_rank[1]))
    )
  )

  # Get top players
  team_players <- top_players_df %>%
    filter(team == team_abbr)

  qb <- team_players %>% filter(position == "QB")
  rbs <- team_players %>% filter(position == "RB")
  receivers <- team_players %>% filter(position %in% c("WR", "TE"))

  qb_json <- if (nrow(qb) > 0) {
    list(
      name = qb$player_name[1],
      total_epa = list(value = na_to_null(round(qb$qb_epa_per_play[1], 2)), rank = na_to_null(as.integer(qb$qb_epa_per_play_rank[1]))),
      passing_yards = list(value = na_to_null(as.integer(qb$passing_yards[1])), rank = na_to_null(as.integer(qb$passing_yards_rank[1]))),
      passing_tds = list(value = na_to_null(as.integer(qb$passing_tds[1])), rank = na_to_null(as.integer(qb$passing_tds_rank[1]))),
      completion_pct = list(value = na_to_null(round(qb$completion_pct[1], 1)), rank = na_to_null(as.integer(qb$completion_pct_rank[1]))),
      passing_cpoe = list(value = na_to_null(round(qb$passing_cpoe[1], 3)), rank = na_to_null(as.integer(qb$passing_cpoe_rank[1]))),
      pacr = list(value = na_to_null(round(qb$pacr[1], 2)), rank = na_to_null(as.integer(qb$pacr_rank[1]))),
      passing_yards_per_game = list(value = na_to_null(round(qb$passing_yards_per_game[1], 1)), rank = na_to_null(as.integer(qb$passing_yards_per_game_rank[1]))),
      interceptions = list(value = na_to_null(as.integer(qb$interceptions[1])), rank = na_to_null(as.integer(qb$interceptions_rank[1])))
    )
  } else NULL

  rbs_json <- lapply(1:nrow(rbs), function(i) {
    list(
      name = rbs$player_name[i],
      rushing_epa = list(value = na_to_null(round(rbs$rushing_epa[i], 2)), rank = na_to_null(as.integer(rbs$rushing_epa_rank[i]))),
      rushing_yards = list(value = na_to_null(as.integer(rbs$rushing_yards[i])), rank = na_to_null(as.integer(rbs$rushing_yards_rank[i]))),
      rushing_tds = list(value = na_to_null(as.integer(rbs$rushing_tds[i])), rank = na_to_null(as.integer(rbs$rushing_tds_rank[i]))),
      yards_per_carry = list(value = na_to_null(round(rbs$rushing_yards_per_carry[i], 2)), rank = na_to_null(as.integer(rbs$rushing_yards_per_carry_rank[i]))),
      rushing_yards_per_game = list(value = na_to_null(round(rbs$rushing_yards_per_game[i], 1)), rank = na_to_null(as.integer(rbs$rushing_yards_per_game_rank[i]))),
      receptions = list(value = na_to_null(as.integer(rbs$receptions[i])), rank = na_to_null(as.integer(rbs$rb_receptions_rank[i]))),
      receiving_yards = list(value = na_to_null(as.integer(rbs$receiving_yards[i])), rank = na_to_null(as.integer(rbs$rb_receiving_yards_rank[i]))),
      receiving_tds = list(value = na_to_null(as.integer(rbs$receiving_tds[i])), rank = na_to_null(as.integer(rbs$rb_receiving_tds_rank[i]))),
      receiving_yards_per_game = list(value = na_to_null(round(rbs$receiving_yards_per_game[i], 1)), rank = na_to_null(as.integer(rbs$rb_receiving_yards_per_game_rank[i]))),
      target_share = list(value = na_to_null(round(rbs$target_share[i], 3)), rank = na_to_null(as.integer(rbs$target_share_rank[i])))
    )
  })

  receivers_json <- lapply(1:nrow(receivers), function(i) {
    list(
      name = receivers$player_name[i],
      receiving_epa = list(value = na_to_null(round(receivers$receiving_epa[i], 2)), rank = na_to_null(as.integer(receivers$receiving_epa_rank[i]))),
      receiving_yards = list(value = na_to_null(as.integer(receivers$receiving_yards[i])), rank = na_to_null(as.integer(receivers$receiving_yards_rank[i]))),
      receiving_tds = list(value = na_to_null(as.integer(receivers$receiving_tds[i])), rank = na_to_null(as.integer(receivers$receiving_tds_rank[i]))),
      receptions = list(value = na_to_null(as.integer(receivers$receptions[i])), rank = na_to_null(as.integer(receivers$receptions_rank[i]))),
      yards_per_reception = list(value = na_to_null(round(receivers$receiving_yards_per_reception[i], 2)), rank = na_to_null(as.integer(receivers$receiving_yards_per_reception_rank[i]))),
      receiving_yards_per_game = list(value = na_to_null(round(receivers$receiving_yards_per_game[i], 1)), rank = na_to_null(as.integer(receivers$receiving_yards_per_game_rank[i]))),
      catch_pct = list(value = na_to_null(round(receivers$catch_pct[i], 1)), rank = na_to_null(as.integer(receivers$catch_pct_rank[i]))),
      wopr = list(value = na_to_null(round(receivers$wopr[i], 2)), rank = na_to_null(as.integer(receivers$wopr_rank[i]))),
      racr = list(value = na_to_null(round(receivers$racr[i], 2)), rank = na_to_null(as.integer(receivers$racr_rank[i]))),
      target_share = list(value = na_to_null(round(receivers$target_share[i], 3)), rank = na_to_null(as.integer(receivers$target_share_rank[i]))),
      air_yards_share = list(value = na_to_null(round(receivers$air_yards_share[i], 2)), rank = na_to_null(as.integer(receivers$air_yards_share_rank[i])))
    )
  })

  list(
    team_stats = list(
      cum_epa_by_week = cum_epa_list,
      epa_by_week = epa_by_week_list,
      current = current_stats
    ),
    players = list(
      qb = qb_json,
      rbs = rbs_json,
      receivers = receivers_json
    )
  )
}

# Build JSON for all matchups
matchups_json <- list()

for (i in 1:nrow(current_week_games)) {
  game <- current_week_games[i, ]
  home_team <- game$home_team
  away_team <- game$away_team
  game_id <- game$game_id
  matchup_key <- paste(tolower(away_team), tolower(home_team), sep = "-")

  cat("Processing matchup:", matchup_key, "\n")

  # Get odds (pass game_id to use ESPN odds API)
  odds <- get_odds_for_game(home_team, away_team, game_id)

  # Get head-to-head record (from home team's perspective)
  h2h <- get_h2h_record(home_team, away_team, schedules)

  # Get common opponents
  common_opps <- get_common_opponents(home_team, away_team, schedules)

  # Build team JSONs
  home_json <- build_team_json(home_team, cum_epa_by_team, team_season_totals, top_players, team_stats_weekly)
  away_json <- build_team_json(away_team, cum_epa_by_team, team_season_totals, top_players, team_stats_weekly)

  # Build matchup JSON
  matchup <- list(
    odds = odds,
    h2h_record = I(h2h),  # Use I() to prevent auto_unbox from converting empty list to null
    common_opponents = common_opps
  )
  matchup[[tolower(home_team)]] <- home_json
  matchup[[tolower(away_team)]] <- away_json

  matchups_json[[matchup_key]] <- matchup
}

# ============================================================================
# Write output JSON and upload to S3
# ============================================================================
cat("\n11. Writing output and uploading to S3...\n")

# Wrap matchups in metadata structure
current_week <- current_week_games$week[1]

# Determine title based on season type
title_text <- if (is_playoffs) {
  week_label <- case_when(
    current_week == 1 ~ "Wild Card",
    current_week == 2 ~ "Divisional Round",
    current_week == 3 ~ "Conference Championships",
    current_week == 4 ~ "Super Bowl",
    TRUE ~ paste0("Playoff Week ", current_week)
  )
  paste0(week_label, " Matchup Worksheets")
} else {
  paste0("Week ", current_week, " Matchup Worksheets")
}

output_data <- list(
  sport = "NFL",
  visualizationType = "MATCHUP_V2",
  title = title_text,
  subtitle = "Comprehensive statistical analysis for all matchups",
  description = "Detailed matchup statistics including team performance metrics, player stats, head-to-head records, and common opponent results.\n\nQUARTERBACK STATS:\n\n • Total EPA: Expected Points Added - total offensive value generated across all plays\n\n • Passing Yards: Total passing yards thrown\n\n • Passing TDs: Total touchdown passes thrown\n\n • Completion %: Completion percentage (completions / attempts × 100)\n\n • Pass CPOE: Completion Percentage Over Expected - accuracy beyond what's expected based on throw difficulty\n\n • PACR: Pass Air Conversion Ratio - measures QB efficiency converting air yards to actual yards (Formula: Passing Yards / Air Yards). Higher PACR indicates more yards after catch.\n\n • Yards/Game: Average passing yards per game\n\n • Interceptions: Total interceptions thrown\n\nRUNNING BACK STATS:\n\n • Rush EPA: Expected Points Added on rushing plays\n\n • Rushing Yards: Total rushing yards gained\n\n • Rushing TDs: Total rushing touchdowns\n\n • Yards/Carry: Average yards per rushing attempt\n\n • Rush Yards/Game: Average rushing yards per game\n\n • Receptions: Total receptions\n\n • Receiving Yards: Total receiving yards\n\n • Receiving TDs: Total receiving touchdowns\n\n • Rec Yards/Game: Average receiving yards per game\n\n • Target Share: Percentage of team's total targets\n\nRECEIVER STATS:\n\n • Rec EPA: Expected Points Added on receptions\n\n • Receiving Yards: Total receiving yards gained\n\n • Receiving TDs: Total receiving touchdowns\n\n • Receptions: Total receptions\n\n • Yards/Reception: Average yards per reception\n\n • Rec Yards/Game: Average receiving yards per game\n\n • Catch %: Catch percentage (receptions / targets × 100)\n\n • WOPR: Weighted Opportunity Rating - combines targets and air yards to measure receiving opportunity\n\n • RACR: Receiver Air Conversion Ratio - receiving yards per air yard (measures efficiency converting targets to yards)\n\n • Target Share: Percentage of team's total targets\n\n • Air Yards %: Percentage of team's total air yards\n\nAll EPA stats are per play through the current week.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "nflfastR / nflreadr / ESPN",
  week = as.integer(current_week),
  dataPoints = matchups_json
)

# Write to temp file first
tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

cat("Generated stats for", length(matchups_json), "matchup(s)\n")

# Upload to S3 if in production
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/nfl__matchup_stats.json"

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
  chart_title <- paste0("NFL Matchup Worksheets - ", title_text)
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
    warning("Failed to update DynamoDB timestamp (non-fatal)")
  } else {
    cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
  }

  # Insert pipeline execution record
  pipeline_file_key <- "dev/nfl__matchup_stats.json"
  pipeline_item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "%s"}}',
    pipeline_file_key, utc_timestamp, chart_title, chart_interval
  )
  pipeline_cmd <- sprintf(
    'aws dynamodb put-item --table-name %s --item %s',
    shQuote(dynamodb_table),
    shQuote(pipeline_item)
  )

  pipeline_result <- system(pipeline_cmd)

  if (pipeline_result != 0) {
    warning("Failed to update DynamoDB pipeline record (non-fatal)")
  } else {
    cat("Updated DynamoDB pipeline record:", pipeline_file_key, "\n")
  }
} else {
  cat("AWS_S3_BUCKET not set, skipping S3 upload\n")
  # Write to local file for testing
  local_file <- "nfl_matchup_stats.json"
  write_json(output_data, local_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")
  cat("Wrote output to local file:", local_file, "\n")
}

cat("\n=== COMPLETE ===\n")
