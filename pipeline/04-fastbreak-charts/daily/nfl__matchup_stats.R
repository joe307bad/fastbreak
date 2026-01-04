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

# Calculate offensive EPA by week from play-by-play data
# Filter to only pass and run plays (standard EPA calculation)
offense_epa_weekly <- pbp_data %>%
  filter(!is.na(epa), !is.na(posteam), play_type %in% c("pass", "run")) %>%
  group_by(posteam, week) %>%
  summarise(
    off_epa_total = sum(epa, na.rm = TRUE),
    off_plays = n(),
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

# Calculate defensive EPA per play by week from play-by-play data
# Filter to only pass and run plays (standard EPA calculation)
defense_epa_weekly <- pbp_data %>%
  filter(!is.na(epa), !is.na(defteam), play_type %in% c("pass", "run")) %>%
  group_by(defteam, week) %>%
  summarise(
    def_epa_total = sum(epa, na.rm = TRUE),
    def_plays = n(),
    .groups = "drop"
  ) %>%
  rename(team = defteam) %>%
  mutate(team = ifelse(team == "LA", "LAR", team))

# Join all stats together
team_stats_weekly <- player_stats_weekly %>%
  left_join(offense_epa_weekly, by = c("team", "week")) %>%
  left_join(defense_epa_weekly, by = c("team", "week"))

cat("Loaded weekly team stats:", nrow(team_stats_weekly), "rows\n")

# Calculate season totals for ranking
team_season_totals <- team_stats_weekly %>%
  group_by(team) %>%
  summarise(
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
    off_epa_total = sum(off_epa_total, na.rm = TRUE),
    off_plays = sum(off_plays, na.rm = TRUE),
    def_epa_total = sum(def_epa_total, na.rm = TRUE),
    def_plays = sum(def_plays, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    # Calculate EPA per play for offense and defense
    off_epa = if_else(off_plays > 0, off_epa_total / off_plays, NA_real_),
    def_epa = if_else(def_plays > 0, def_epa_total / def_plays, NA_real_),
    # Calculate EPA per attempt/carry/target for passing/rushing/receiving
    passing_epa = if_else(passing_attempts > 0, passing_epa_total / passing_attempts, NA_real_),
    rushing_epa = if_else(rushing_carries > 0, rushing_epa_total / rushing_carries, NA_real_),
    receiving_epa = if_else(receiving_targets > 0, receiving_epa_total / receiving_targets, NA_real_),
    passing_epa_rank = rank(-passing_epa),
    rushing_epa_rank = rank(-rushing_epa),
    receiving_epa_rank = rank(-receiving_epa),
    passing_cpoe_rank = rank(-passing_cpoe),
    pacr_rank = rank(-pacr),
    passing_first_downs_rank = rank(-passing_first_downs),
    sacks_suffered_rank = rank(sacks_suffered),
    off_epa_rank = rank(-off_epa),
    def_epa_rank = rank(def_epa)  # Lower defensive EPA is better
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
    # QB stats (EPA per attempt/carry - includes passing and rushing)
    passing_epa_total = sum(passing_epa, na.rm = TRUE),
    passing_attempts = sum(attempts, na.rm = TRUE),
    passing_cpoe = mean(passing_cpoe, na.rm = TRUE),
    pacr = mean(pacr, na.rm = TRUE),
    passing_air_yards = sum(passing_air_yards, na.rm = TRUE),
    # RB stats (EPA per carry/target)
    rushing_epa_total = sum(rushing_epa, na.rm = TRUE),
    rushing_first_downs = sum(rushing_first_downs, na.rm = TRUE),
    carries = sum(carries, na.rm = TRUE),
    receiving_epa_total = sum(receiving_epa, na.rm = TRUE),
    targets = sum(targets, na.rm = TRUE),
    # WR/TE stats
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
    # QB ranks
    qb_epa_per_play_rank = if_else(position == "QB", rank(-qb_epa_per_play), NA_real_),
    passing_epa_rank = if_else(position == "QB", rank(-passing_epa), NA_real_),
    passing_cpoe_rank = if_else(position == "QB", rank(-passing_cpoe), NA_real_),
    pacr_rank = if_else(position == "QB", rank(-pacr), NA_real_),
    passing_air_yards_rank = if_else(position == "QB", rank(-passing_air_yards), NA_real_),
    # RB ranks
    rushing_epa_rank = if_else(position == "RB", rank(-rushing_epa), NA_real_),
    rushing_first_downs_rank = if_else(position == "RB", rank(-rushing_first_downs), NA_real_),
    carries_rank = if_else(position == "RB", rank(-carries), NA_real_),
    rb_receiving_epa_rank = if_else(position == "RB", rank(-receiving_epa), NA_real_),
    targets_rank = if_else(position == "RB", rank(-targets), NA_real_),
    # WR/TE ranks
    receiving_epa_rank = if_else(position %in% c("WR", "TE"), rank(-receiving_epa), NA_real_),
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

# Get current week (upcoming games)
current_week_games <- schedules %>%
  filter(
    week == max(week[game_type == "REG" & is.na(result)], na.rm = TRUE),
    game_type == "REG",
    is.na(result)
  )

if (nrow(current_week_games) == 0) {
  cat("No upcoming games found, using most recent week\n")
  current_week_games <- schedules %>%
    filter(week == max(week), game_type == "REG")
}

cat("Found", nrow(current_week_games), "games for current week\n")

# Process all matchups

# ============================================================================
# STEP 9: Fetch odds from ESPN API
# ============================================================================
cat("\n9. Fetching odds from ESPN API...\n")

odds_data <- tryCatch({
  resp <- GET("https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard")
  content(resp, as = "parsed")
}, error = function(e) {
  cat("Warning: Could not fetch odds:", e$message, "\n")
  list(events = list())
})

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

# Helper function to extract odds for a game
get_odds_for_game <- function(home_team, away_team) {
  default_odds <- list(
    home_spread = NULL,
    home_moneyline = NULL,
    away_spread = NULL,
    away_moneyline = NULL,
    over_under = NULL
  )

  if (is.null(odds_data$events)) return(default_odds)

  # Normalize team abbreviations for ESPN API comparison
  home_team_normalized <- normalize_team_abbr(home_team)
  away_team_normalized <- normalize_team_abbr(away_team)

  for (event in odds_data$events) {
    if (is.null(event$competitions)) next
    comp <- event$competitions[[1]]
    if (is.null(comp$competitors)) next

    teams <- sapply(comp$competitors, function(x) x$team$abbreviation)
    if (home_team_normalized %in% teams && away_team_normalized %in% teams) {
      if (!is.null(comp$odds) && length(comp$odds) > 0) {
        odds <- comp$odds[[1]]

        # Extract home team spread from pointSpread
        home_spread <- NULL
        if (!is.null(odds$pointSpread) && !is.null(odds$pointSpread$home) &&
            !is.null(odds$pointSpread$home$close) && !is.null(odds$pointSpread$home$close$line)) {
          home_spread <- odds$pointSpread$home$close$line
        }

        # Extract away team spread from pointSpread
        away_spread <- NULL
        if (!is.null(odds$pointSpread) && !is.null(odds$pointSpread$away) &&
            !is.null(odds$pointSpread$away$close) && !is.null(odds$pointSpread$away$close$line)) {
          away_spread <- odds$pointSpread$away$close$line
        }

        # Extract home team moneyline
        home_moneyline <- NULL
        if (!is.null(odds$moneyline) && !is.null(odds$moneyline$home) &&
            !is.null(odds$moneyline$home$close) && !is.null(odds$moneyline$home$close$odds)) {
          home_moneyline <- odds$moneyline$home$close$odds
        }

        # Extract away team moneyline
        away_moneyline <- NULL
        if (!is.null(odds$moneyline) && !is.null(odds$moneyline$away) &&
            !is.null(odds$moneyline$away$close) && !is.null(odds$moneyline$away$close$odds)) {
          away_moneyline <- odds$moneyline$away$close$odds
        }

        return(list(
          home_spread = home_spread,
          home_moneyline = home_moneyline,
          away_spread = away_spread,
          away_moneyline = away_moneyline,
          over_under = if (!is.null(odds$overUnder)) odds$overUnder else NULL
        ))
      }
    }
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
    def_epa = list(value = round(team_totals$def_epa[1], 3), rank = as.integer(team_totals$def_epa_rank[1])),
    off_epa = list(value = round(team_totals$off_epa[1], 3), rank = as.integer(team_totals$off_epa_rank[1])),
    passing_epa = list(value = round(team_totals$passing_epa[1], 3), rank = as.integer(team_totals$passing_epa_rank[1])),
    rushing_epa = list(value = round(team_totals$rushing_epa[1], 3), rank = as.integer(team_totals$rushing_epa_rank[1])),
    passing_cpoe = list(value = round(team_totals$passing_cpoe[1], 3), rank = as.integer(team_totals$passing_cpoe_rank[1])),
    receiving_epa = list(value = round(team_totals$receiving_epa[1], 3), rank = as.integer(team_totals$receiving_epa_rank[1])),
    pacr = list(value = round(team_totals$pacr[1], 2), rank = as.integer(team_totals$pacr_rank[1])),
    passing_first_downs = list(value = as.integer(team_totals$passing_first_downs[1]), rank = as.integer(team_totals$passing_first_downs_rank[1])),
    sacks_suffered = list(value = as.integer(team_totals$sacks_suffered[1]), rank = as.integer(team_totals$sacks_suffered_rank[1]))
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
      passing_epa = list(value = na_to_null(round(qb$passing_epa[1], 2)), rank = na_to_null(as.integer(qb$passing_epa_rank[1]))),
      passing_cpoe = list(value = na_to_null(round(qb$passing_cpoe[1], 3)), rank = na_to_null(as.integer(qb$passing_cpoe_rank[1]))),
      pacr = list(value = na_to_null(round(qb$pacr[1], 2)), rank = na_to_null(as.integer(qb$pacr_rank[1]))),
      passing_air_yards = list(value = na_to_null(as.integer(qb$passing_air_yards[1])), rank = na_to_null(as.integer(qb$passing_air_yards_rank[1])))
    )
  } else NULL

  rbs_json <- lapply(1:nrow(rbs), function(i) {
    list(
      name = rbs$player_name[i],
      rushing_epa = list(value = na_to_null(round(rbs$rushing_epa[i], 2)), rank = na_to_null(as.integer(rbs$rushing_epa_rank[i]))),
      rushing_first_downs = list(value = na_to_null(as.integer(rbs$rushing_first_downs[i])), rank = na_to_null(as.integer(rbs$rushing_first_downs_rank[i]))),
      carries = list(value = na_to_null(as.integer(rbs$carries[i])), rank = na_to_null(as.integer(rbs$carries_rank[i]))),
      receiving_epa = list(value = na_to_null(round(rbs$receiving_epa[i], 2)), rank = na_to_null(as.integer(rbs$rb_receiving_epa_rank[i]))),
      targets = list(value = na_to_null(as.integer(rbs$targets[i])), rank = na_to_null(as.integer(rbs$targets_rank[i]))),
      target_share = list(value = na_to_null(round(rbs$target_share[i], 3)), rank = na_to_null(as.integer(rbs$target_share_rank[i])))
    )
  })

  receivers_json <- lapply(1:nrow(receivers), function(i) {
    list(
      name = receivers$player_name[i],
      wopr = list(value = na_to_null(round(receivers$wopr[i], 2)), rank = na_to_null(as.integer(receivers$wopr_rank[i]))),
      receiving_epa = list(value = na_to_null(round(receivers$receiving_epa[i], 2)), rank = na_to_null(as.integer(receivers$receiving_epa_rank[i]))),
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
  matchup_key <- paste(tolower(away_team), tolower(home_team), sep = "-")

  cat("Processing matchup:", matchup_key, "\n")

  # Get odds
  odds <- get_odds_for_game(home_team, away_team)

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
output_data <- list(
  sport = "NFL",
  visualizationType = "MATCHUP_V2",
  title = paste0("Week ", current_week, " Matchup Worksheets"),
  subtitle = "Comprehensive statistical analysis for all matchups",
  description = "Detailed matchup statistics including team performance metrics, player stats, head-to-head records, and common opponent results.\n\nQUARTERBACK STATS:\n\n • Total EPA: Expected Points Added - total offensive value generated across all plays\n\n • Passing EPA: Expected Points Added on passing plays only\n\n • Pass CPOE: Completion Percentage Over Expected - accuracy beyond what's expected based on throw difficulty\n\n • PACR: Pass Air Conversion Ratio - measures QB efficiency converting air yards to actual yards\n\n • Air Yards: Total passing yards in the air before the catch\n\nRUNNING BACK STATS:\n\n • Rush EPA: Expected Points Added on rushing plays\n\n • Rush 1st Downs: First downs gained on rushing attempts\n\n • Carries: Total rushing attempts\n\n • Rec EPA: Expected Points Added on receptions\n\n • Targets: Total times targeted by the QB\n\n • Target Share: Percentage of team's total targets\n\nRECEIVER STATS:\n\n • WOPR: Weighted Opportunity Rating - combines targets and air yards to measure receiving opportunity\n\n • Rec EPA: Expected Points Added on receptions\n\n • RACR: Receiver Air Conversion Ratio - receiving yards per air yard (measures efficiency converting targets to yards)\n\n • Target Share: Percentage of team's total targets\n\n • Air Yards %: Percentage of team's total air yards\n\nAll EPA stats are per play through the current week.",
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
  chart_title <- paste0("NFL Matchup Worksheets - Week ", current_week_games$week[1])
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
