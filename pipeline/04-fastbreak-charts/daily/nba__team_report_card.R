#!/usr/bin/env Rscript

# NBA Team Report Card — per-team player leaderboards grouped by position group.
# Mirrors mlb__team_report_card.R and nfl__team_report_card.R: every player in a
# group is ranked league-wide on five stats, those ranks average into a player
# composite, and the same stats aggregated over the team's whole group produce
# the group's team composite. The position group and team-unit composites
# average into the team's overall composite.
#
# Position groups and their stats (all rate stats, so bench players and
# starters are comparable):
#  - Guards:   PTS/36 + AST/36 + TS% + 3P% + STL/36
#  - Forwards: PTS/36 + REB/36 + TS% + 3P% + STL+BLK/36
#  - Centers:  PTS/36 + REB/36 + TS% + BLK/36 + FG%
#
# Team units, which have no per-player equivalent:
#  - Offense:  offensive rating + the four factors a team controls
#  - Defense:  defensive rating + the four factors it allows
#
# Everything comes from the ESPN box scores in the hoopR data repository
# rather than stats.nba.com, so the whole card is built from one feed that the
# rest of the NBA pipeline already blends and that is reachable without the
# stats.nba.com allowlist.
#
# Differences from the MLB card:
#  - Four factors stand in for the pitching groups baseball has
#  - Last-10 game ledger and a 10-game recent-form panel, same as MLB
#  - No WAR column: the box score feed carries no all-in-one value metric

library(hoopR)
library(dplyr)
library(tidyr)
library(jsonlite)
library(rvest)
library(httr)

TOP_N <- 10
LAST_TEN_COUNT <- 10
REGULAR_SEASON_GAMES <- 82

# A season is only used for the card once every team has played enough of it to
# rank on; before that the card reports the last completed season instead.
MIN_TEAM_GAMES_FOR_CURRENT_SEASON <- 5

# Qualification. A per-36 rate needs two things: enough games to be a sample,
# and enough minutes per game to be a rotation player. The games bar is a share
# of what the league has actually played so an early-season card still has
# players in every group; the minutes-per-game bar is absolute, because a
# garbage-time per-36 is flattering at any point in the season.
MIN_GROUP_GAMES_SHARE <- 0.4
MIN_MINUTES_PER_GAME <- 15
MIN_THREE_ATTEMPTS_FULL <- 50

cat("=== NBA Team Report Card ===\n")

# ============================================================================
# Team metadata
# ============================================================================
# Keyed by the ESPN abbreviations the box score feed uses (GS/NO/NY/SA/UTAH/WSH),
# which differ from the stats.nba.com codes in utils/nba_utils.R. Doubles as the
# team filter: the feed also carries All-Star rosters (STARS/STRIPES/WORLD).
TEAM_META <- tibble::tribble(
  ~team_code, ~team_name,               ~conference, ~division,
  "ATL",      "Atlanta Hawks",          "East",      "Southeast",
  "BOS",      "Boston Celtics",         "East",      "Atlantic",
  "BKN",      "Brooklyn Nets",          "East",      "Atlantic",
  "CHA",      "Charlotte Hornets",      "East",      "Southeast",
  "CHI",      "Chicago Bulls",          "East",      "Central",
  "CLE",      "Cleveland Cavaliers",    "East",      "Central",
  "DAL",      "Dallas Mavericks",       "West",      "Southwest",
  "DEN",      "Denver Nuggets",         "West",      "Northwest",
  "DET",      "Detroit Pistons",        "East",      "Central",
  "GS",       "Golden State Warriors",  "West",      "Pacific",
  "HOU",      "Houston Rockets",        "West",      "Southwest",
  "IND",      "Indiana Pacers",         "East",      "Central",
  "LAC",      "LA Clippers",            "West",      "Pacific",
  "LAL",      "Los Angeles Lakers",     "West",      "Pacific",
  "MEM",      "Memphis Grizzlies",      "West",      "Southwest",
  "MIA",      "Miami Heat",             "East",      "Southeast",
  "MIL",      "Milwaukee Bucks",        "East",      "Central",
  "MIN",      "Minnesota Timberwolves", "West",      "Northwest",
  "NO",       "New Orleans Pelicans",   "West",      "Southwest",
  "NY",       "New York Knicks",        "East",      "Atlantic",
  "OKC",      "Oklahoma City Thunder",  "West",      "Northwest",
  "ORL",      "Orlando Magic",          "East",      "Southeast",
  "PHI",      "Philadelphia 76ers",     "East",      "Atlantic",
  "PHX",      "Phoenix Suns",           "West",      "Pacific",
  "POR",      "Portland Trail Blazers", "West",      "Northwest",
  "SA",       "San Antonio Spurs",      "West",      "Southwest",
  "SAC",      "Sacramento Kings",       "West",      "Pacific",
  "TOR",      "Toronto Raptors",        "East",      "Atlantic",
  "UTAH",     "Utah Jazz",              "West",      "Northwest",
  "WSH",      "Washington Wizards",     "East",      "Southeast"
) %>% arrange(team_code)

ALL_TEAMS <- TEAM_META$team_code
team_names <- setNames(TEAM_META$team_name, TEAM_META$team_code)
team_divisions <- setNames(TEAM_META$division, TEAM_META$team_code)
team_conferences <- setNames(TEAM_META$conference, TEAM_META$team_code)

cat("Loaded", length(ALL_TEAMS), "NBA teams\n")

# ============================================================================
# Shared helpers (mirrors nfl__team_report_card.R)
# ============================================================================
# Null/blank coalesce. Deliberately only inspects scalars: the ESPN payload
# walk feeds whole lists through this, and is.na() on a list returns a vector
# that would blow up the `if`.
`%||%` <- function(a, b) {
  if (is.null(a) || length(a) == 0) return(b)
  if (is.atomic(a) && length(a) == 1) {
    if (is.na(a)) return(b)
    if (is.character(a) && !nzchar(a)) return(b)
  }
  a
}

tied_rank <- function(x) {
  numeric_ranks <- rank(x, ties.method = "min", na.last = "keep")
  rank_counts <- table(numeric_ranks[!is.na(numeric_ranks)])
  display_ranks <- vapply(numeric_ranks, function(r) {
    if (is.na(r)) return(NA_character_)
    if (rank_counts[as.character(r)] > 1) paste0("T", r) else as.character(r)
  }, character(1))
  list(rank = numeric_ranks, rankDisplay = display_ranks)
}

rank_and_assign <- function(df, col, lower_better = FALSE) {
  vals <- suppressWarnings(as.numeric(df[[col]]))
  rk <- if (lower_better) tied_rank(vals) else tied_rank(-vals)
  df[[paste0(col, "_rank")]] <- as.integer(rk$rank)
  df[[paste0(col, "_rankDisplay")]] <- rk$rankDisplay
  df
}

# Average percentile across a category's stat ranks (100 = best in the league on
# every stat). Surfaces well-rounded players instead of one-stat specialists.
add_composite_score <- function(df, stat_cols) {
  if (nrow(df) == 0) {
    df$composite_score <- numeric(0)
    return(df)
  }
  n <- nrow(df)
  score_matrix <- vapply(stat_cols, function(col) {
    ranks <- df[[paste0(col, "_rank")]]
    pct <- (n - ranks + 1) / n * 100
    pct[is.na(ranks)] <- NA_real_
    pct
  }, numeric(n))
  if (length(stat_cols) == 1) {
    df$composite_score <- as.numeric(score_matrix)
  } else {
    df$composite_score <- rowMeans(score_matrix, na.rm = TRUE)
  }
  df$composite_score[is.nan(df$composite_score)] <- NA_real_
  df
}

stat_entry <- function(row, col, label, digits = NULL, display_value = NULL) {
  if (is.null(row) || !col %in% names(row) || is.na(row[[col]])) {
    return(list(label = label, value = NULL, rank = NULL, rankDisplay = NULL, displayValue = NULL))
  }
  value <- as.numeric(row[[col]])
  if (!is.null(digits)) value <- round(value, digits)
  rank_val <- if (paste0(col, "_rank") %in% names(row)) row[[paste0(col, "_rank")]] else NA
  rank_display <- if (paste0(col, "_rankDisplay") %in% names(row)) row[[paste0(col, "_rankDisplay")]] else NA
  list(
    label = label,
    value = value,
    rank = if (length(rank_val) == 0 || is.na(rank_val)) NULL else as.integer(rank_val[[1]]),
    rankDisplay = if (length(rank_display) == 0 || is.na(rank_display)) NULL else as.character(rank_display[[1]]),
    displayValue = display_value
  )
}

composite_stat_entry <- function(row) {
  composite <- if (!is.null(row) && "composite_score" %in% names(row)) row$composite_score else NA
  if (is.null(row) || length(composite) == 0 || is.na(composite[[1]])) {
    return(list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL))
  }
  list(label = "Composite", value = round(composite[[1]], 1), rank = NULL, rankDisplay = NULL)
}

team_composite_stat_entry <- function(row) {
  composite <- if (!is.null(row) && "composite_score" %in% names(row)) as.numeric(row$composite_score) else NA_real_
  if (is.null(row) || length(composite) == 0 || is.na(composite[[1]])) {
    return(list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL))
  }
  rank_val <- if ("composite_score_rank" %in% names(row)) as.integer(row$composite_score_rank[[1]]) else NA_integer_
  rank_display <- if ("composite_score_rankDisplay" %in% names(row)) as.character(row$composite_score_rankDisplay[[1]]) else NA_character_
  list(
    label = "Composite",
    value = round(composite[[1]], 1),
    rank = if (is.na(rank_val)) NULL else rank_val,
    rankDisplay = if (is.na(rank_display)) NULL else rank_display
  )
}

build_player <- function(row, stat_cols, stat_labels, digits_map = list()) {
  stats <- setNames(
    lapply(stat_cols, function(col) stat_entry(row, col, stat_labels[[col]], digits_map[[col]])),
    stat_cols
  )
  stats$aggregate <- composite_stat_entry(row)
  list(
    playerId = as.character(row$player_id),
    name = as.character(row$player_name),
    position = if (!is.null(row$position) && !is.na(row$position)) as.character(row$position) else NULL,
    stats = stats
  )
}

top_team_players <- function(df, team, stat_cols, stat_labels, digits_map = list()) {
  team_df <- df %>%
    filter(team_code == team) %>%
    arrange(desc(composite_score)) %>%
    head(TOP_N)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) build_player(team_df[i, ], stat_cols, stat_labels, digits_map))
}

build_team_category_stats <- function(team_df, team, stat_cols, stat_labels, digits_map = list()) {
  row <- team_df %>% filter(team_code == team)
  stats <- setNames(
    lapply(stat_cols, function(col) {
      if (nrow(row) == 0) {
        list(label = stat_labels[[col]], value = NULL, rank = NULL, rankDisplay = NULL)
      } else {
        stat_entry(row[1, ], col, stat_labels[[col]], digits_map[[col]])
      }
    }),
    stat_cols
  )
  stats$aggregate <- if (nrow(row) == 0) {
    list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL)
  } else {
    team_composite_stat_entry(row[1, ])
  }
  list(stats = stats)
}

build_rankings <- function(team_df, team_col, stat_col, rank_col, rankDisplay_col) {
  valid <- !is.na(team_df[[rank_col]]) & !is.na(team_df[[stat_col]])
  if (!any(valid)) return(list())
  df <- data.frame(
    team = team_df[[team_col]][valid],
    rank = as.integer(team_df[[rank_col]][valid]),
    rankDisplay = as.character(team_df[[rankDisplay_col]][valid]),
    value = round(as.numeric(team_df[[stat_col]][valid]), 4),
    stringsAsFactors = FALSE
  )
  df <- df[order(df$rank), ]
  lapply(seq_len(nrow(df)), function(i) {
    list(rank = df$rank[i], rankDisplay = df$rankDisplay[i], value = df$value[i], team = df$team[i])
  })
}

build_category_stat_rankings <- function(category, team_df, stat_cols) {
  setNames(
    lapply(stat_cols, function(col) {
      build_rankings(team_df, "team_code", col, paste0(col, "_rank"), paste0(col, "_rankDisplay"))
    }),
    paste0(category, ".", stat_cols)
  )
}

build_player_rankings <- function(df, stat_col) {
  rank_col <- paste0(stat_col, "_rank")
  rankDisplay_col <- paste0(stat_col, "_rankDisplay")
  valid <- !is.na(df[[rank_col]]) & !is.na(df[[stat_col]])
  if (!any(valid)) return(list())
  subset <- df[valid, , drop = FALSE]
  subset <- subset[order(subset[[rank_col]]), , drop = FALSE]
  lapply(seq_len(nrow(subset)), function(i) {
    row <- subset[i, , drop = FALSE]
    list(
      rank = as.integer(row[[rank_col]][[1]]),
      rankDisplay = as.character(row[[rankDisplay_col]][[1]]),
      value = round(as.numeric(row[[stat_col]][[1]]), 4),
      team = as.character(row$team_code[[1]]),
      player = as.character(row$player_name[[1]])
    )
  })
}

build_player_pool_rankings <- function(category, df, stat_cols) {
  setNames(
    lapply(stat_cols, function(col) build_player_rankings(df, col)),
    paste0(category, ".player.", stat_cols)
  )
}

safe_div <- function(numerator, denominator) {
  ifelse(is.na(denominator) | denominator == 0, NA_real_, numerator / denominator)
}

# ============================================================================
# Season + phase resolution
# ============================================================================
# hoopR names an NBA season by the calendar year it ends in, so 2027 is 2026-27.
# The card always reports a season every team has played enough of to rank on:
# on opening weekend three teams have a box score and the other 27 do not, so a
# composite built from it would be noise. Until the new season clears
# MIN_TEAM_GAMES_FOR_CURRENT_SEASON the card stays on the completed one, and the
# phase says which situation the reader is looking at.
calendar_season <- {
  y <- as.numeric(format(Sys.Date(), "%Y"))
  m <- as.numeric(format(Sys.Date(), "%m"))
  # The NBA season tips off in October and is named for the year it ends.
  if (m >= 10) y + 1 else y
}

load_season_boxes <- function(season, attempts = 3) {
  for (attempt in seq_len(attempts)) {
    boxes <- tryCatch(
      {
        team_box <- hoopR::load_nba_team_box(seasons = season) %>%
          filter(team_abbreviation %in% ALL_TEAMS)
        player_box <- hoopR::load_nba_player_box(seasons = season) %>%
          filter(team_abbreviation %in% ALL_TEAMS)
        list(team_box = team_box, player_box = player_box)
      },
      error = function(e) {
        cat("Warning: could not load box scores for", season, ":", e$message, "\n")
        NULL
      }
    )
    if (!is.null(boxes)) return(boxes)
    if (attempt < attempts) Sys.sleep(2 * attempt)
  }
  NULL
}

# Games every team has played in a season's regular season, used both to decide
# whether the season is usable and to scale the qualification thresholds. A
# season nobody has finished a fifth of is not rankable, and neither is one the
# feed has only partly caught up on, so every team has to be on the board.
min_team_games <- function(boxes) {
  if (is.null(boxes) || is.null(boxes$team_box)) return(0)
  reg <- boxes$team_box %>% filter(season_type == 2)
  if (nrow(reg) == 0) return(0)
  counts <- reg %>% count(team_abbreviation)
  if (nrow(counts) < length(ALL_TEAMS)) return(0)
  min(counts$n)
}

# Resolve in two steps rather than one loop, so a season that is merely
# unreachable can never be mistaken for one that is over: the phase is decided
# by which branch answered, not by whether playoff games exist in the payload
# (every completed season has those).
resolve_season_context <- function() {
  current_season <- calendar_season
  current_boxes <- load_season_boxes(current_season)
  current_started <- !is.null(current_boxes) && nrow(current_boxes$team_box) > 0

  if (min_team_games(current_boxes) >= MIN_TEAM_GAMES_FOR_CURRENT_SEASON) {
    in_postseason <- any(current_boxes$team_box$season_type == 3)
    return(list(
      stats_season = current_season,
      boxes = current_boxes,
      schedule_season = current_season,
      schedule_boxes = current_boxes,
      phase = if (in_postseason) "POST" else "REG",
      started_season = NULL
    ))
  }

  # Not enough of the current season on the board yet. Report the most recent
  # completed one and say whether the new season has tipped off (EARLY) or not
  # (PRE) — either way the card is not describing the season in progress.
  for (season in (current_season - 1):(current_season - 3)) {
    boxes <- load_season_boxes(season)
    if (min_team_games(boxes) > 0) {
      # Two seasons, deliberately. Ranking players needs a season with enough
      # games behind it, but "how is my team doing" means right now - so the
      # record, the form panel and the game log follow the season in progress
      # as soon as it has a single game, while the player and unit rankings
      # stay on the completed one.
      return(list(
        stats_season = season,
        boxes = boxes,
        schedule_season = if (current_started) current_season else season,
        schedule_boxes = if (current_started) current_boxes else boxes,
        phase = if (current_started) "EARLY" else "PRE",
        started_season = current_season
      ))
    }
  }
  stop("Could not find an NBA season with enough completed games")
}

season_ctx <- resolve_season_context()
nba_season <- season_ctx$stats_season
season_phase <- season_ctx$phase
started_season <- season_ctx$started_season
team_box_all <- season_ctx$boxes$team_box
schedule_season <- season_ctx$schedule_season
schedule_box_all <- season_ctx$schedule_boxes$team_box
schedule_season_label <- paste0(schedule_season - 1, "-", substr(schedule_season, 3, 4))
schedule_is_current <- !identical(schedule_season, nba_season)
player_box_all <- season_ctx$boxes$player_box

season_phase_label <- switch(
  season_phase,
  PRE = "Preseason",
  EARLY = "Early Season",
  REG = "Regular Season",
  POST = "Postseason",
  season_phase
)

# hoopR names a season by the year it ends in; the card labels it the NBA way.
season_label <- paste0(nba_season - 1, "-", substr(nba_season, 3, 4))

cat("Season:", season_label, "| phase:", season_phase_label, "\n")

team_box <- team_box_all %>% filter(season_type == 2)
player_box <- player_box_all %>% filter(season_type == 2)
# Records, recent form and the game log are built from this one.
schedule_box <- schedule_box_all %>% filter(season_type == 2)

if (schedule_is_current) {
  cat("Records, form and game log come from the season in progress:",
      schedule_season_label, "|", nrow(schedule_box), "team-game rows\n")
}

games_played <- min_team_games(season_ctx$boxes)
season_fraction <- max(min(games_played / REGULAR_SEASON_GAMES, 1), 1 / REGULAR_SEASON_GAMES)

scaled_min <- function(full_threshold) {
  max(1, round(full_threshold * season_fraction))
}

MIN_GROUP_GAMES <- max(3, ceiling(games_played * MIN_GROUP_GAMES_SHARE))
MIN_THREE_ATTEMPTS <- scaled_min(MIN_THREE_ATTEMPTS_FULL)

cat("Regular season games played (fewest by any team):", games_played,
    "| thresholds — group games:", MIN_GROUP_GAMES,
    "| min/game:", MIN_MINUTES_PER_GAME,
    "| 3PA:", MIN_THREE_ATTEMPTS, "\n")
cat("Loaded", nrow(player_box), "player box rows,", nrow(team_box), "team box rows\n")

if (nrow(team_box) == 0) stop("No NBA team box scores available")

# ============================================================================
# Player season totals
# ============================================================================
# ESPN publishes plus-minus as a signed string ("+15", "-4"), so it has to be
# coerced before it can be summed.
player_games <- player_box %>%
  filter(!is.na(minutes), minutes > 0) %>%
  mutate(plus_minus = suppressWarnings(as.numeric(sub("^\\+", "", plus_minus))))

primary_team <- player_games %>%
  group_by(athlete_id, team_abbreviation) %>%
  summarise(team_minutes = sum(minutes), .groups = "drop") %>%
  arrange(athlete_id, desc(team_minutes)) %>%
  distinct(athlete_id, .keep_all = TRUE) %>%
  transmute(athlete_id, team_code = team_abbreviation)

primary_position <- player_games %>%
  filter(!is.na(athlete_position_abbreviation), nzchar(athlete_position_abbreviation)) %>%
  count(athlete_id, athlete_position_abbreviation) %>%
  arrange(athlete_id, desc(n)) %>%
  distinct(athlete_id, .keep_all = TRUE) %>%
  transmute(athlete_id, position = athlete_position_abbreviation)

# ESPN publishes one of G/F/C for nearly every player, with a handful of
# PG/SG/SF/PF rows, so the three groups cover the league.
position_group_for <- function(position) {
  dplyr::case_when(
    position %in% c("G", "PG", "SG") ~ "guards",
    position %in% c("F", "SF", "PF") ~ "forwards",
    position == "C" ~ "centers",
    TRUE ~ NA_character_
  )
}

player_totals <- player_games %>%
  group_by(athlete_id) %>%
  summarise(
    player_name = dplyr::first(athlete_display_name),
    gp = n(),
    minutes = sum(minutes),
    fgm = sum(field_goals_made, na.rm = TRUE),
    fga = sum(field_goals_attempted, na.rm = TRUE),
    tpm = sum(three_point_field_goals_made, na.rm = TRUE),
    tpa = sum(three_point_field_goals_attempted, na.rm = TRUE),
    ftm = sum(free_throws_made, na.rm = TRUE),
    fta = sum(free_throws_attempted, na.rm = TRUE),
    reb = sum(rebounds, na.rm = TRUE),
    ast = sum(assists, na.rm = TRUE),
    stl = sum(steals, na.rm = TRUE),
    blk = sum(blocks, na.rm = TRUE),
    tov = sum(turnovers, na.rm = TRUE),
    pts = sum(points, na.rm = TRUE),
    plus_minus = sum(plus_minus, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  left_join(primary_team, by = "athlete_id") %>%
  left_join(primary_position, by = "athlete_id") %>%
  filter(!is.na(team_code)) %>%
  mutate(
    player_id = as.character(athlete_id),
    position_group = position_group_for(position)
  )

# Rate stats. A 3P% needs a real sample to mean anything, so it is only
# published once a shooter clears the scaled attempt bar; team rows always do.
add_rate_stats <- function(df, min_three_attempts) {
  df %>%
    mutate(
      pts_per_36 = safe_div(pts * 36, minutes),
      reb_per_36 = safe_div(reb * 36, minutes),
      ast_per_36 = safe_div(ast * 36, minutes),
      stl_per_36 = safe_div(stl * 36, minutes),
      blk_per_36 = safe_div(blk * 36, minutes),
      stocks_per_36 = safe_div((stl + blk) * 36, minutes),
      ts_pct = safe_div(pts * 100, 2 * (fga + 0.44 * fta)),
      fg_pct = safe_div(fgm * 100, fga),
      fg3_pct = ifelse(tpa >= min_three_attempts, safe_div(tpm * 100, tpa), NA_real_)
    )
}

player_rates <- add_rate_stats(player_totals, MIN_THREE_ATTEMPTS)

team_minutes <- player_totals %>%
  group_by(team_code) %>%
  summarise(team_total_minutes = sum(minutes), .groups = "drop")

POSITION_GROUP_KEYS <- c("guards", "forwards", "centers")

# The qualified pool a group's players are ranked in.
group_player_pool <- function(group_key) {
  player_rates %>%
    filter(
      position_group == group_key,
      gp >= MIN_GROUP_GAMES,
      safe_div(minutes, gp) >= MIN_MINUTES_PER_GAME
    ) %>%
    select(player_id, player_name, team_code, position, gp, minutes,
           pts_per_36, reb_per_36, ast_per_36, stl_per_36, blk_per_36,
           stocks_per_36, ts_pct, fg_pct, fg3_pct)
}

# The team row aggregates every player in the group, qualified or not, so it
# describes the whole position group rather than just its rotation regulars.
group_team_pool <- function(group_key) {
  totals <- player_totals %>%
    filter(position_group == group_key) %>%
    group_by(team_code) %>%
    summarise(across(c(minutes, fgm, fga, tpm, tpa, ftm, fta, reb, ast, stl, blk, tov, pts),
                     ~ sum(.x, na.rm = TRUE)), .groups = "drop")
  tibble(team_code = ALL_TEAMS) %>%
    left_join(totals, by = "team_code") %>%
    add_rate_stats(0)
}

guards <- group_player_pool("guards")
forwards <- group_player_pool("forwards")
centers <- group_player_pool("centers")

team_guards <- group_team_pool("guards")
team_forwards <- group_team_pool("forwards")
team_centers <- group_team_pool("centers")

cat("Qualified pools — guards:", nrow(guards),
    "| forwards:", nrow(forwards),
    "| centers:", nrow(centers), "\n")

# ============================================================================
# Team units: offense and defense
# ============================================================================
# One row per team per game with the opponent's line attached, so both ends of
# the floor come out of the same source of truth. Built twice: the four factors
# rank the season the player pools come from, while the record, the form panel
# and the game log describe the season in progress.
build_team_games <- function(box) {
  games <- box %>%
    transmute(
      game_id, game_date, team_id, team_code = team_abbreviation,
      opponent_team_id, opponent_code = opponent_team_abbreviation,
      is_home = team_home_away == "home",
      team_score, opponent_team_score, won = team_winner,
      fga = field_goals_attempted, fgm = field_goals_made,
      tpm = three_point_field_goals_made,
      tpa = three_point_field_goals_attempted,
      fta = free_throws_attempted,
      oreb = offensive_rebounds, dreb = defensive_rebounds,
      tov = coalesce(total_turnovers, turnovers),
      pts = team_score
    )

  opponent_lines <- games %>%
    transmute(
      game_id, opponent_of = team_id,
      opp_fga = fga, opp_fgm = fgm, opp_tpm = tpm, opp_tpa = tpa, opp_fta = fta,
      opp_oreb = oreb, opp_dreb = dreb, opp_tov = tov, opp_pts = pts
    )

  games %>%
    left_join(opponent_lines, by = c("game_id", "opponent_team_id" = "opponent_of"))
}

# The four factors, from the same season the rankings use.
stats_team_games <- build_team_games(team_box)

# Everything schedule-shaped, from the season in progress.
team_games <- if (schedule_is_current) build_team_games(schedule_box) else stats_team_games

possessions <- function(fga, fta, oreb, tov) fga + 0.44 * fta - oreb + tov

team_unit_totals <- stats_team_games %>%
  group_by(team_code) %>%
  summarise(
    games = n(),
    across(c(fga, fgm, tpm, tpa, fta, oreb, dreb, tov, pts,
             opp_fga, opp_fgm, opp_tpm, opp_tpa, opp_fta, opp_oreb, opp_dreb, opp_tov, opp_pts),
           ~ sum(.x, na.rm = TRUE)),
    .groups = "drop"
  ) %>%
  mutate(
    poss = possessions(fga, fta, oreb, tov),
    opp_poss = possessions(opp_fga, opp_fta, opp_oreb, opp_tov),
    off_rating = safe_div(pts * 100, poss),
    def_rating = safe_div(opp_pts * 100, opp_poss),
    efg_pct = safe_div((fgm + 0.5 * tpm) * 100, fga),
    opp_efg_pct = safe_div((opp_fgm + 0.5 * opp_tpm) * 100, opp_fga),
    tov_pct = safe_div(tov * 100, poss),
    opp_tov_pct = safe_div(opp_tov * 100, opp_poss),
    oreb_pct = safe_div(oreb * 100, oreb + opp_dreb),
    dreb_pct = safe_div(dreb * 100, dreb + opp_oreb),
    ft_rate = safe_div(fta * 100, fga),
    opp_ft_rate = safe_div(opp_fta * 100, opp_fga)
  )

team_offense <- team_unit_totals %>%
  select(team_code, off_rating, efg_pct, tov_pct, oreb_pct, ft_rate)
team_defense <- team_unit_totals %>%
  select(team_code, def_rating, opp_efg_pct, opp_tov_pct, dreb_pct, opp_ft_rate)

# ============================================================================
# Records
# ============================================================================
team_records <- team_games %>%
  group_by(team_code) %>%
  summarise(
    W = sum(won, na.rm = TRUE),
    L = sum(!won, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(win_pct = safe_div(W, W + L)) %>%
  left_join(TEAM_META %>% select(team_code, division, conference), by = "team_code")

team_records <- rank_and_assign(team_records, "win_pct")
team_records <- team_records %>%
  group_by(division) %>%
  mutate(
    division_rank = rank(-win_pct, ties.method = "min"),
    division_rank_display = ifelse(
      duplicated(division_rank) | duplicated(division_rank, fromLast = TRUE),
      paste0("T", division_rank), as.character(division_rank)
    )
  ) %>%
  ungroup()

cat("Records — teams:", nrow(team_records), "\n")

# ============================================================================
# Recent form + last-10 game ledger
# ============================================================================
recent_games <- team_games %>%
  group_by(team_code) %>%
  arrange(desc(game_date), desc(game_id), .by_group = TRUE) %>%
  slice_head(n = LAST_TEN_COUNT) %>%
  ungroup()

team_recent_trend <- recent_games %>%
  group_by(team_code) %>%
  summarise(
    recent_games_played = n(),
    recent_wins = sum(won, na.rm = TRUE),
    recent_losses = sum(!won, na.rm = TRUE),
    points_per_game = safe_div(sum(pts, na.rm = TRUE), n()),
    points_allowed_per_game = safe_div(sum(opp_pts, na.rm = TRUE), n()),
    fg_pct_recent = safe_div(sum(fgm, na.rm = TRUE) * 100, sum(fga, na.rm = TRUE)),
    fg3_pct_recent = safe_div(sum(tpm, na.rm = TRUE) * 100, sum(tpa, na.rm = TRUE)),
    .groups = "drop"
  )

TREND_STAT_COLS <- c("record", "pointDiffPerGame", "pointsPerGame",
                     "pointsAllowedPerGame", "fg_pct_recent", "fg3_pct_recent")

build_game_log_for_team <- function(team) {
  empty <- list(
    label = paste("Last", LAST_TEN_COUNT, "Games"),
    columns = list("Opponent", "Opp", "Team", "Diff"),
    games = list(),
    record = list(wins = 0L, losses = 0L, display = "0-0"),
    totalDifferential = 0L
  )
  rows <- recent_games %>%
    filter(team_code == team) %>%
    arrange(game_date, game_id)
  if (nrow(rows) == 0) return(empty)

  games <- lapply(seq_len(nrow(rows)), function(i) {
    row <- rows[i, ]
    loc <- if (isTRUE(row$is_home)) "vs" else "@"
    diff <- as.integer(row$team_score - row$opponent_team_score)
    list(
      date = format(as.Date(row$game_date), "%Y-%m-%d"),
      location = loc,
      opponent = as.character(row$opponent_code),
      opponentLabel = paste(loc, row$opponent_code),
      teamScore = as.integer(row$team_score),
      opponentScore = as.integer(row$opponent_team_score),
      differential = diff,
      result = if (isTRUE(row$won)) "W" else "L",
      won = isTRUE(row$won),
      played = TRUE
    )
  })
  wins <- sum(rows$won, na.rm = TRUE)
  losses <- nrow(rows) - wins
  list(
    label = paste("Last", LAST_TEN_COUNT, "Games"),
    columns = list("Opponent", "Opp", "Team", "Diff"),
    games = games,
    record = list(wins = as.integer(wins), losses = as.integer(losses),
                  display = paste0(wins, "-", losses)),
    totalDifferential = as.integer(sum(vapply(games, function(g) g$differential, integer(1))))
  )
}

# ============================================================================
# Below-replacement performers
# ============================================================================
# The box score feed has no all-in-one value metric, so the stand-in for a
# below-replacement bat is a rotation player the team was outscored with.
below_replacement <- player_rates %>%
  filter(
    gp >= MIN_GROUP_GAMES,
    safe_div(minutes, gp) >= MIN_MINUTES_PER_GAME,
    plus_minus < 0
  ) %>%
  select(player_id, player_name, team_code, position, minutes, plus_minus)

team_below_replacement <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    below_replacement %>%
      group_by(team_code) %>%
      summarise(br_minutes = sum(minutes, na.rm = TRUE), .groups = "drop"),
    by = "team_code"
  ) %>%
  left_join(team_minutes, by = "team_code") %>%
  mutate(
    br_minutes = coalesce(br_minutes, 0),
    below_replacement_min_pct = coalesce(safe_div(br_minutes * 100, team_total_minutes), 0)
  ) %>%
  select(team_code, below_replacement_min_pct)

cat("Below-replacement players:", nrow(below_replacement), "\n")

# ============================================================================
# Injury report (ESPN)
# ============================================================================
injury_status_weight <- function(status) {
  st <- tolower(trimws(as.character(status)))
  if (!nzchar(st)) return(NA_real_)
  if (grepl("suspension|suspended", st)) return(NA_real_)
  if (grepl("^active$", st)) return(NA_real_)
  if (grepl("out for season|season-ending", st)) return(1.0)
  if (grepl("^out$", st)) return(0.9)
  if (grepl("doubtful", st)) return(0.75)
  if (grepl("questionable", st)) return(0.4)
  if (grepl("day-to-day|day to day", st)) return(0.3)
  if (grepl("game time decision|gtd", st)) return(0.3)
  0.5
}

fetch_espn_injuries <- function(url, name_lookup) {
  empty <- tibble(
    team_code = character(), entry_id = character(), athlete_id = character(),
    athlete_name = character(), status = character(), position = character(),
    status_weight = numeric()
  )
  safe_scalar <- function(x, default = NA_character_) {
    if (is.null(x) || length(x) == 0) return(default)
    if (length(x) > 1) x <- x[[1]]
    if (is.na(x) || !nzchar(as.character(x))) return(default)
    as.character(x)
  }
  # ESPN 403s R's built-in url-connection User-Agent (what a bare
  # jsonlite::fromJSON(url) sends) but serves the identical request over
  # libcurl. Do not add a browser User-Agent — ESPN rejects those too.
  fetch_payload <- function(attempts = 3) {
    for (attempt in seq_len(attempts)) {
      resp <- tryCatch(GET(url, timeout(60)), error = function(e) NULL)
      if (!is.null(resp) && status_code(resp) == 200) {
        payload <- tryCatch(
          fromJSON(content(resp, as = "text", encoding = "UTF-8"), simplifyVector = FALSE),
          error = function(e) NULL
        )
        if (!is.null(payload)) return(payload)
      }
      if (attempt < attempts) Sys.sleep(2 * attempt)
    }
    NULL
  }
  tryCatch({
    payload <- fetch_payload()
    if (is.null(payload)) stop("ESPN injury endpoint unreachable after retries")
    rows <- list()
    for (group in (payload$injuries %||% list())) {
      display_name <- safe_scalar(group$displayName, "")
      team_code <- name_lookup[[normalize_team_name(display_name)]] %||% NA_character_
      if (is.na(team_code)) {
        cat("Warning: could not map ESPN team:", display_name, "\n")
        next
      }
      for (entry in (group$injuries %||% list())) {
        status <- safe_scalar(entry$status, "")
        weight <- injury_status_weight(status)
        if (is.na(weight)) next
        athlete <- entry$athlete
        if (is.null(athlete)) athlete <- list()
        rows[[length(rows) + 1]] <- list(
          team_code = team_code,
          entry_id = safe_scalar(entry$id),
          athlete_id = safe_scalar(athlete$id),
          athlete_name = safe_scalar(athlete$displayName),
          status = status,
          position = if (is.null(athlete$position)) NA_character_ else safe_scalar(athlete$position$abbreviation),
          status_weight = weight
        )
      }
    }
    if (length(rows) == 0) return(empty)
    bind_rows(rows)
  }, error = function(e) {
    cat("Warning: could not load ESPN injury report:", e$message, "\n")
    empty
  })
}

# Fold both sides of the name match to plain ASCII so an accent or a stray
# punctuation mark on either side does not drop a whole team's injuries.
normalize_team_name <- function(x) {
  plain <- iconv(as.character(x), from = "UTF-8", to = "ASCII//TRANSLIT")
  plain[is.na(plain)] <- as.character(x)[is.na(plain)]
  tolower(trimws(gsub("[^A-Za-z0-9 ]", "", plain)))
}

cat("Loading ESPN NBA injury report...\n")
espn_name_lookup <- as.list(setNames(TEAM_META$team_code,
                                     normalize_team_name(TEAM_META$team_name)))
injury_players_raw <- fetch_espn_injuries(
  "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/injuries",
  espn_name_lookup
)

# Minutes share stands in for MLB's WAR: it is the clearest measure of how much
# of a team's basketball the injured player was actually playing.
player_minute_share <- player_totals %>%
  left_join(team_minutes, by = "team_code") %>%
  transmute(
    athlete_key = tolower(trimws(player_name)),
    minute_share = coalesce(safe_div(minutes * 100, team_total_minutes), 0)
  ) %>%
  group_by(athlete_key) %>%
  summarise(minute_share = max(minute_share, na.rm = TRUE), .groups = "drop")

injury_players <- if (nrow(injury_players_raw) == 0) {
  cat("Warning: injury report unavailable — continuing without injury data\n")
  injury_players_raw %>% mutate(minute_share = numeric(), impact = numeric())
} else {
  injury_players_raw %>%
    mutate(athlete_key = tolower(trimws(athlete_name))) %>%
    left_join(player_minute_share, by = "athlete_key") %>%
    mutate(
      minute_share = coalesce(minute_share, 0),
      impact = minute_share * status_weight
    ) %>%
    select(-athlete_key)
}

team_injuries <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    injury_players %>%
      group_by(team_code) %>%
      summarise(
        injured_count = n(),
        injury_minute_share = sum(impact, na.rm = TRUE),
        .groups = "drop"
      ),
    by = "team_code"
  ) %>%
  mutate(
    injured_count = coalesce(as.integer(injured_count), 0L),
    injury_minute_share = coalesce(injury_minute_share, 0)
  )

cat("Injury report — players:", nrow(injury_players),
    "| teams with injuries:", sum(team_injuries$injured_count > 0), "\n")

# ============================================================================
# Playoff chances (playoffstatus.com)
# ============================================================================
# Short team names as playoffstatus.com prints them, plus the alternates it
# sometimes uses. Mirrors the table in nba__matchup_stats.R.
PLAYOFF_NAME_TO_CODE <- c(
  "76ers" = "PHI", "Sixers" = "PHI",
  "Bucks" = "MIL", "Bulls" = "CHI",
  "Cavaliers" = "CLE", "Cavs" = "CLE",
  "Celtics" = "BOS", "Clippers" = "LAC", "Grizzlies" = "MEM", "Hawks" = "ATL",
  "Heat" = "MIA", "Hornets" = "CHA", "Jazz" = "UTAH", "Kings" = "SAC",
  "Knicks" = "NY", "Lakers" = "LAL", "Magic" = "ORL",
  "Mavericks" = "DAL", "Mavs" = "DAL",
  "Nets" = "BKN", "Nuggets" = "DEN", "Pacers" = "IND",
  "Pelicans" = "NO",
  "Pistons" = "DET", "Raptors" = "TOR", "Rockets" = "HOU", "Spurs" = "SA",
  "Suns" = "PHX", "Thunder" = "OKC",
  "Timberwolves" = "MIN", "Wolves" = "MIN", "T. Wolves" = "MIN",
  "Trail Blazers" = "POR", "Blazers" = "POR",
  "Warriors" = "GS", "Wizards" = "WSH"
)

scrape_playoff_probabilities <- function() {
  url <- "https://www.playoffstatus.com/nba/nbapostseasonprob.html"
  tryCatch({
    cat("Scraping NBA playoff probabilities from playoffstatus.com...\n")
    page <- read_html(url)
    tables <- page %>% html_elements("table")
    if (length(tables) == 0) {
      cat("Warning: no tables found on the playoff probability page\n")
      return(NULL)
    }
    prob_table <- NULL
    for (tbl in tables) {
      if (length(tbl %>% html_elements("tr")) > 20) {
        prob_table <- tbl
        break
      }
    }
    if (is.null(prob_table)) prob_table <- tables[[1]]

    parse_pct <- function(cell) {
      text <- cell %>% html_text(trim = TRUE)
      text <- gsub("[%<>]", "", text)
      text <- gsub("\\s+", "", text)
      val <- suppressWarnings(as.numeric(text))
      if (is.na(val)) 0 else val
    }

    results <- list()
    for (row in (prob_table %>% html_elements("tr"))) {
      cells <- row %>% html_elements("td")
      if (length(cells) < 10) next
      team_link <- cells[[1]] %>% html_element("a")
      team_name <- if (!is.na(team_link)) {
        team_link %>% html_text(trim = TRUE)
      } else {
        cells[[1]] %>% html_text(trim = TRUE)
      }
      team_name <- trimws(team_name)
      if (!nzchar(team_name) || grepl("Team|Conference", team_name, ignore.case = TRUE)) next

      team_code <- PLAYOFF_NAME_TO_CODE[team_name]
      if (is.na(team_code)) {
        for (name in names(PLAYOFF_NAME_TO_CODE)) {
          if (grepl(name, team_name, ignore.case = TRUE)) {
            team_code <- PLAYOFF_NAME_TO_CODE[name]
            break
          }
        }
      }
      if (is.na(team_code)) {
        cat("Warning: could not map playoffstatus team name:", team_name, "\n")
        next
      }

      # Data columns run in reverse round order: champs first, round 1 last.
      results[[unname(team_code)]] <- list(
        champProb = parse_pct(cells[[6]]),
        playoffProb = parse_pct(cells[[10]])
      )
    }
    cat("Scraped playoff probabilities for", length(results), "teams\n")
    results
  }, error = function(e) {
    cat("Warning: could not scrape playoff probabilities:", e$message, "\n")
    NULL
  })
}

# Playoff odds describe the season in progress. When the card has fallen back to
# the completed season (PRE/EARLY) those odds belong to a different season than
# every other number on the page, so they are left off rather than mixed in.
playoff_probs <- if (season_phase %in% c("REG", "POST")) {
  scrape_playoff_probabilities()
} else {
  cat("Skipping playoff odds: card covers the completed", season_label,
      "season, not the one those odds are for\n")
  NULL
}

# The sheet groups teams by conference and then by the division each one sits
# in, which is what standingsSection names. Games back is the standard
# half-game formula against the division leader, computed by the web card.
build_playoff_chances <- function() {
  if (is.null(playoff_probs) || length(playoff_probs) == 0) return(list())
  rows <- team_records %>%
    arrange(conference, division, division_rank) %>%
    filter(team_code %in% names(playoff_probs))
  if (nrow(rows) == 0) return(list())
  lapply(seq_len(nrow(rows)), function(i) {
    row <- rows[i, ]
    probs <- playoff_probs[[row$team_code]]
    list(
      team = row$team_code,
      teamCode = row$team_code,
      teamName = unname(team_names[row$team_code]),
      playoffProb = probs$playoffProb,
      champProb = probs$champProb,
      conference = if (row$conference == "East") "Eastern" else "Western",
      division = row$division,
      divisionRank = as.integer(row$division_rank),
      wins = as.integer(row$W),
      losses = as.integer(row$L),
      winPct = round(row$win_pct, 4),
      standingsSection = row$division
    )
  })
}

playoff_chances <- build_playoff_chances()
cat("Playoff chances entries:", length(playoff_chances), "\n")

# ============================================================================
# Category definitions
# ============================================================================
# One spec per category drives ranking, composites, the per-team payload and the
# league-wide ranking sheets, so a stat is only ever declared once.
CATEGORY_SPECS <- list(
  list(
    key = "guards",
    label = "Guards",
    description = paste0(
      "Guards ranked by points, assists and steals per 36 minutes, true ",
      "shooting percentage, and three-point percentage. Qualified at ",
      MIN_GROUP_GAMES, "+ games and ", MIN_MINUTES_PER_GAME,
      "+ minutes per game, with 3P% held back until ",
      MIN_THREE_ATTEMPTS, "+ attempts."
    ),
    pool = quote(guards),
    team_df = quote(team_guards),
    stat_cols = c("pts_per_36", "ast_per_36", "ts_pct", "fg3_pct", "stl_per_36"),
    lower_better = character(0),
    labels = c(pts_per_36 = "PTS/36", ast_per_36 = "AST/36", ts_pct = "TS%",
               fg3_pct = "3P%", stl_per_36 = "STL/36"),
    digits = list(pts_per_36 = 1, ast_per_36 = 1, ts_pct = 1, fg3_pct = 1, stl_per_36 = 1)
  ),
  list(
    key = "forwards",
    label = "Forwards",
    description = paste0(
      "Forwards ranked by points and rebounds per 36 minutes, true shooting ",
      "percentage, three-point percentage, and steals plus blocks per 36. ",
      "Qualified at ", MIN_GROUP_GAMES, "+ games and ", MIN_MINUTES_PER_GAME, "+ minutes per game."
    ),
    pool = quote(forwards),
    team_df = quote(team_forwards),
    stat_cols = c("pts_per_36", "reb_per_36", "ts_pct", "fg3_pct", "stocks_per_36"),
    lower_better = character(0),
    labels = c(pts_per_36 = "PTS/36", reb_per_36 = "REB/36", ts_pct = "TS%",
               fg3_pct = "3P%", stocks_per_36 = "STK/36"),
    digits = list(pts_per_36 = 1, reb_per_36 = 1, ts_pct = 1, fg3_pct = 1, stocks_per_36 = 1)
  ),
  list(
    key = "centers",
    label = "Centers",
    description = paste0(
      "Centers ranked by points, rebounds and blocks per 36 minutes, true ",
      "shooting percentage, and field goal percentage. Qualified at ",
      "Qualified at ", MIN_GROUP_GAMES, "+ games and ", MIN_MINUTES_PER_GAME, "+ minutes per game."
    ),
    pool = quote(centers),
    team_df = quote(team_centers),
    stat_cols = c("pts_per_36", "reb_per_36", "ts_pct", "blk_per_36", "fg_pct"),
    lower_better = character(0),
    labels = c(pts_per_36 = "PTS/36", reb_per_36 = "REB/36", ts_pct = "TS%",
               blk_per_36 = "BLK/36", fg_pct = "FG%"),
    digits = list(pts_per_36 = 1, reb_per_36 = 1, ts_pct = 1, blk_per_36 = 1, fg_pct = 1)
  )
)

# Team-only units. The box score feed has no per-player rating, so these
# describe the team and carry no player table.
TEAM_UNIT_SPECS <- list(
  list(
    key = "offense",
    label = "Offense",
    description = paste0(
      "Offensive rating (points per 100 possessions) and the four factors a ",
      "team controls: effective field goal percentage, turnover rate (lower is ",
      "better), offensive rebound rate, and free throw rate — free throw ",
      "attempts per 100 field goal attempts."
    ),
    team_df = quote(team_offense),
    stat_cols = c("off_rating", "efg_pct", "tov_pct", "oreb_pct", "ft_rate"),
    lower_better = c("tov_pct"),
    labels = c(off_rating = "ORtg", efg_pct = "eFG%", tov_pct = "TOV%",
               oreb_pct = "OREB%", ft_rate = "FT Rate"),
    digits = list(off_rating = 1, efg_pct = 1, tov_pct = 1, oreb_pct = 1, ft_rate = 1)
  ),
  list(
    key = "defense",
    label = "Defense",
    description = paste0(
      "Defensive rating (points allowed per 100 possessions, lower is better) ",
      "and the four factors a team allows: opponent effective field goal ",
      "percentage (lower is better), the turnover rate it forces (higher is ",
      "better), defensive rebound rate, and the opponent's free throw rate ",
      "(lower is better)."
    ),
    team_df = quote(team_defense),
    stat_cols = c("def_rating", "opp_efg_pct", "opp_tov_pct", "dreb_pct", "opp_ft_rate"),
    lower_better = c("def_rating", "opp_efg_pct", "opp_ft_rate"),
    labels = c(def_rating = "DRtg", opp_efg_pct = "Opp eFG%", opp_tov_pct = "Opp TOV%",
               dreb_pct = "DREB%", opp_ft_rate = "Opp FT Rate"),
    digits = list(def_rating = 1, opp_efg_pct = 1, opp_tov_pct = 1, dreb_pct = 1, opp_ft_rate = 1)
  )
)

TEAM_UNIT_KEYS <- vapply(TEAM_UNIT_SPECS, function(spec) spec$key, character(1))
PLAYER_GROUP_KEYS <- vapply(CATEGORY_SPECS, function(spec) spec$key, character(1))
# The three position groups and the two team units feed the overall composite.
# Recent form, below-replacement minutes and the injury report are diagnostics,
# not quality ratings, so they sit outside it exactly as on the MLB card.
COMPOSITE_KEYS <- c(PLAYER_GROUP_KEYS, TEAM_UNIT_KEYS)

# ============================================================================
# Rank + composite every pool
# ============================================================================
player_pools <- list()
team_pools <- list()

for (spec in CATEGORY_SPECS) {
  pool <- eval(spec$pool)
  team_df <- eval(spec$team_df)

  for (col in spec$stat_cols) {
    pool <- rank_and_assign(pool, col, lower_better = col %in% spec$lower_better)
  }
  pool <- add_composite_score(pool, spec$stat_cols)

  team_df <- team_df %>% filter(team_code %in% ALL_TEAMS)
  for (col in spec$stat_cols) {
    team_df <- rank_and_assign(team_df, col, lower_better = col %in% spec$lower_better)
  }
  team_df <- add_composite_score(team_df, spec$stat_cols)
  team_df <- rank_and_assign(team_df, "composite_score")

  player_pools[[spec$key]] <- pool
  team_pools[[spec$key]] <- team_df
}

for (spec in TEAM_UNIT_SPECS) {
  team_df <- eval(spec$team_df) %>% filter(team_code %in% ALL_TEAMS)
  for (col in spec$stat_cols) {
    team_df <- rank_and_assign(team_df, col, lower_better = col %in% spec$lower_better)
  }
  team_df <- add_composite_score(team_df, spec$stat_cols)
  team_df <- rank_and_assign(team_df, "composite_score")
  team_pools[[spec$key]] <- team_df
}

team_below_replacement <- rank_and_assign(
  team_below_replacement, "below_replacement_min_pct", lower_better = TRUE
)
team_below_replacement <- add_composite_score(team_below_replacement, c("below_replacement_min_pct"))
team_below_replacement <- rank_and_assign(team_below_replacement, "composite_score")

below_replacement <- rank_and_assign(below_replacement, "minutes")
below_replacement <- rank_and_assign(below_replacement, "plus_minus", lower_better = TRUE)

team_injuries <- rank_and_assign(team_injuries, "injured_count")
team_injuries <- rank_and_assign(team_injuries, "injury_minute_share")
team_injuries <- add_composite_score(team_injuries, c("injured_count", "injury_minute_share"))
team_injuries <- rank_and_assign(team_injuries, "composite_score")

# Recent form ranks on its own stats but has no composite: a 10-game sample is
# a form read, not a rating, and it stays out of the overall composite.
team_recent_trend <- tibble(team_code = ALL_TEAMS) %>%
  left_join(team_recent_trend, by = "team_code") %>%
  mutate(
    recent_games_played = coalesce(recent_games_played, 0L),
    recent_wins = coalesce(recent_wins, 0L),
    recent_losses = coalesce(recent_losses, 0L),
    record = safe_div(recent_wins, recent_wins + recent_losses),
    pointDiffPerGame = points_per_game - points_allowed_per_game,
    pointsPerGame = points_per_game,
    pointsAllowedPerGame = points_allowed_per_game
  )

team_recent_trend <- rank_and_assign(team_recent_trend, "record")
team_recent_trend <- rank_and_assign(team_recent_trend, "pointDiffPerGame")
team_recent_trend <- rank_and_assign(team_recent_trend, "pointsPerGame")
team_recent_trend <- rank_and_assign(team_recent_trend, "pointsAllowedPerGame", lower_better = TRUE)
team_recent_trend <- rank_and_assign(team_recent_trend, "fg_pct_recent")
team_recent_trend <- rank_and_assign(team_recent_trend, "fg3_pct_recent")

category_composite <- function(team_df, team) {
  row <- team_df %>% filter(team_code == team)
  if (nrow(row) == 0 || is.na(row$composite_score[1])) NA_real_ else row$composite_score[1]
}

team_overall <- tibble(team_code = ALL_TEAMS) %>%
  rowwise() %>%
  mutate(
    overall_composite = mean(
      vapply(COMPOSITE_KEYS, function(key) category_composite(team_pools[[key]], team_code), numeric(1)),
      na.rm = TRUE
    )
  ) %>%
  ungroup() %>%
  mutate(overall_composite = if_else(is.nan(overall_composite), NA_real_, overall_composite)) %>%
  filter(!is.na(overall_composite))

team_overall <- rank_and_assign(team_overall, "overall_composite")

# ============================================================================
# Extra category payload builders
# ============================================================================
trend_labels <- c(record = "Record", pointDiffPerGame = "Pt Diff/G",
                  pointsPerGame = "PTS/G", pointsAllowedPerGame = "OPP PTS/G",
                  fg_pct_recent = "FG%", fg3_pct_recent = "3P%")
trend_digits <- list(record = 3, pointDiffPerGame = 1, pointsPerGame = 1,
                     pointsAllowedPerGame = 1, fg_pct_recent = 1, fg3_pct_recent = 1)

# No aggregate entry: recent form is a read, not a rating, so the panel shows
# the stat rows and no composite.
build_trend_category_stats <- function(team) {
  row <- team_recent_trend %>% filter(team_code == team)
  stats <- setNames(
    lapply(TREND_STAT_COLS, function(col) {
      if (nrow(row) == 0) {
        return(list(label = trend_labels[[col]], value = NULL, rank = NULL,
                    rankDisplay = NULL, displayValue = NULL))
      }
      display <- if (col == "record") {
        paste0(row$recent_wins[1], "-", row$recent_losses[1])
      } else {
        NULL
      }
      stat_entry(row[1, ], col, trend_labels[[col]], trend_digits[[col]], display)
    }),
    TREND_STAT_COLS
  )
  list(stats = stats)
}

below_replacement_labels <- c(minutes = "MIN", plus_minus = "+/-")
below_replacement_digits <- list(minutes = 0, plus_minus = 0)
below_replacement_team_labels <- c(below_replacement_min_pct = "BR MIN%")
below_replacement_team_digits <- list(below_replacement_min_pct = 1)
injury_labels <- c(injured_count = "Injured", injury_minute_share = "MIN Lost")
injury_digits <- list(injured_count = 0, injury_minute_share = 1)

below_replacement_team_players <- function(team) {
  team_df <- below_replacement %>%
    filter(team_code == team) %>%
    arrange(plus_minus, desc(minutes)) %>%
    head(TOP_N)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) {
    build_player(team_df[i, ], c("minutes", "plus_minus"),
                 below_replacement_labels, below_replacement_digits)
  })
}

build_injury_player <- function(row) {
  impact_val <- if (!is.na(row$impact)) round(as.numeric(row$impact), 1) else NULL
  player_id <- if (!is.na(row$athlete_id)) {
    as.character(row$athlete_id)
  } else if (!is.na(row$entry_id)) {
    as.character(row$entry_id)
  } else {
    as.character(row$athlete_name)
  }
  list(
    playerId = player_id,
    name = as.character(row$athlete_name),
    position = if (!is.na(row$position) && nzchar(row$position)) as.character(row$position) else NULL,
    status = if (!is.na(row$status) && nzchar(row$status)) as.character(row$status) else NULL,
    stats = list(
      impact = list(label = "Impact", value = impact_val, rank = NULL, rankDisplay = NULL),
      aggregate = list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL)
    )
  )
}

team_injured_players <- function(team) {
  team_df <- injury_players %>%
    filter(team_code == team) %>%
    arrange(desc(impact), athlete_name)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) build_injury_player(team_df[i, ]))
}

# ============================================================================
# Build per-team report cards
# ============================================================================
CATEGORY_ORDER <- c("recentTrend", PLAYER_GROUP_KEYS, TEAM_UNIT_KEYS,
                    "belowReplacement", "injuries")

build_position_category <- function(spec, team) {
  list(
    label = spec$label,
    description = spec$description,
    statKeys = as.list(spec$stat_cols),
    playerStatKeys = as.list(spec$stat_cols),
    compositeRankingKey = paste0(spec$key, "Composite"),
    team = build_team_category_stats(
      team_pools[[spec$key]], team, spec$stat_cols, spec$labels, spec$digits
    ),
    players = top_team_players(
      player_pools[[spec$key]], team, spec$stat_cols, spec$labels, spec$digits
    )
  )
}

build_team_unit_category <- function(spec, team) {
  list(
    label = spec$label,
    description = spec$description,
    statKeys = as.list(spec$stat_cols),
    playerStatKeys = list(),
    compositeRankingKey = paste0(spec$key, "Composite"),
    team = build_team_category_stats(
      team_pools[[spec$key]], team, spec$stat_cols, spec$labels, spec$digits
    ),
    players = list()
  )
}

teams_json <- lapply(ALL_TEAMS, function(team) {
  categories <- c(
    setNames(
      lapply(CATEGORY_SPECS, function(spec) build_position_category(spec, team)),
      PLAYER_GROUP_KEYS
    ),
    setNames(
      lapply(TEAM_UNIT_SPECS, function(spec) build_team_unit_category(spec, team)),
      TEAM_UNIT_KEYS
    )
  )

  categories$recentTrend <- list(
    label = paste(LAST_TEN_COUNT, "Game Trend"),
    description = paste0(
      "The team's last ", LAST_TEN_COUNT, " games of the ", schedule_season_label,
      " season: record, point differential per game, points scored and allowed ",
      "per game, and shooting from the field and from three. Ranked against ",
      "every other team's last ", LAST_TEN_COUNT,
      if (schedule_is_current) {
        paste0(". Early in a season this covers however many games have been ",
               "played, which is fewer than ", LAST_TEN_COUNT, ".")
      } else {
        "."
      }
    ),
    statKeys = as.list(TREND_STAT_COLS),
    playerStatKeys = list(),
    showPlayerRankAndComposite = FALSE,
    showTeamComposite = FALSE,
    team = build_trend_category_stats(team),
    players = list()
  )

  categories$belowReplacement <- list(
    label = "Below-replacement performers",
    description = paste0(
      "Rotation players the team was outscored with: a negative plus-minus, ",
      "worst first, among players clearing the same bar as the position groups (",
      MIN_GROUP_GAMES, "+ games at ", MIN_MINUTES_PER_GAME,
      "+ minutes a game). BR MIN% is the share of the team's minutes those ",
      "players absorbed. Rank 1 = smallest share."
    ),
    statKeys = list("below_replacement_min_pct"),
    playerStatKeys = list("minutes", "plus_minus"),
    showPlayerRankAndComposite = FALSE,
    # WAR is MLB-only; the shared layout would otherwise render an empty column.
    showWarColumn = FALSE,
    showTeamComposite = FALSE,
    team = build_team_category_stats(
      team_below_replacement, team, c("below_replacement_min_pct"),
      below_replacement_team_labels, below_replacement_team_digits
    ),
    players = below_replacement_team_players(team)
  )

  categories$injuries <- list(
    label = "Injury Report",
    description = paste0(
      "Current ESPN injury designations weighted by severity (out for the ",
      "season = 100%, out = 90%, doubtful = 75%, questionable = 40%, ",
      "day-to-day = 30%) and by the player's share of the team's minutes. ",
      "Higher composite = more injury impact. Rank 1 = most injured."
    ),
    statKeys = list("injured_count", "injury_minute_share"),
    playerStatKeys = list("impact"),
    showPlayerRankAndComposite = FALSE,
    showStatusColumn = TRUE,
    compositeRankingKey = "injuriesComposite",
    team = build_team_category_stats(
      team_injuries, team, c("injured_count", "injury_minute_share"),
      injury_labels, injury_digits
    ),
    players = team_injured_players(team)
  )

  record_row <- team_records %>% filter(team_code == team)
  overall_row <- team_overall %>% filter(team_code == team)
  playoff_prob <- if (!is.null(playoff_probs) && !is.null(playoff_probs[[team]])) {
    playoff_probs[[team]]$playoffProb
  } else {
    NULL
  }

  list(
    teamCode = team,
    teamName = unname(team_names[team]),
    division = unname(team_divisions[team]),
    league = if (unname(team_conferences[team]) == "East") "Eastern" else "Western",
    wins = if (nrow(record_row) > 0) as.integer(record_row$W[1]) else NULL,
    losses = if (nrow(record_row) > 0) as.integer(record_row$L[1]) else NULL,
    recordRank = if (nrow(record_row) > 0 && !is.na(record_row$win_pct_rank[1])) as.integer(record_row$win_pct_rank[1]) else NULL,
    recordRankDisplay = if (nrow(record_row) > 0 && !is.na(record_row$win_pct_rankDisplay[1])) record_row$win_pct_rankDisplay[1] else NULL,
    divisionRank = if (nrow(record_row) > 0) as.integer(record_row$division_rank[1]) else NULL,
    divisionRankDisplay = if (nrow(record_row) > 0) record_row$division_rank_display[1] else NULL,
    overallComposite = if (nrow(overall_row) > 0) round(overall_row$overall_composite[1], 1) else NULL,
    overallCompositeRank = if (nrow(overall_row) > 0) as.integer(overall_row$overall_composite_rank[1]) else NULL,
    overallCompositeRankDisplay = if (nrow(overall_row) > 0) overall_row$overall_composite_rankDisplay[1] else NULL,
    playoffProb = playoff_prob,
    gameLog = build_game_log_for_team(team),
    categoryOrder = as.list(CATEGORY_ORDER),
    categories = categories
  )
})

names(teams_json) <- ALL_TEAMS

# ============================================================================
# League-wide ranking sheets
# ============================================================================
composite_rankings <- setNames(
  lapply(COMPOSITE_KEYS, function(key) {
    build_rankings(team_pools[[key]], "team_code", "composite_score",
                   "composite_score_rank", "composite_score_rankDisplay")
  }),
  paste0(COMPOSITE_KEYS, "Composite")
)

team_stat_rankings <- do.call(c, lapply(c(CATEGORY_SPECS, TEAM_UNIT_SPECS), function(spec) {
  build_category_stat_rankings(spec$key, team_pools[[spec$key]], spec$stat_cols)
}))

player_stat_rankings <- do.call(c, lapply(CATEGORY_SPECS, function(spec) {
  build_player_pool_rankings(spec$key, player_pools[[spec$key]], spec$stat_cols)
}))

rankings_json <- c(
  list(
    record = build_rankings(team_records, "team_code", "win_pct", "win_pct_rank", "win_pct_rankDisplay"),
    overallComposite = build_rankings(
      team_overall, "team_code", "overall_composite",
      "overall_composite_rank", "overall_composite_rankDisplay"
    ),
    injuriesComposite = build_rankings(
      team_injuries, "team_code", "composite_score",
      "composite_score_rank", "composite_score_rankDisplay"
    ),
    belowReplacementComposite = build_rankings(
      team_below_replacement, "team_code", "composite_score",
      "composite_score_rank", "composite_score_rankDisplay"
    )
  ),
  composite_rankings,
  team_stat_rankings,
  build_category_stat_rankings("recentTrend", team_recent_trend, TREND_STAT_COLS),
  build_category_stat_rankings("injuries", team_injuries, c("injured_count", "injury_minute_share")),
  build_category_stat_rankings("belowReplacement", team_below_replacement, c("below_replacement_min_pct")),
  player_stat_rankings,
  build_player_pool_rankings("belowReplacement", below_replacement, c("minutes", "plus_minus"))
)

# ============================================================================
# Output JSON
# ============================================================================
phase_tag_label <- switch(
  season_phase,
  PRE = "preseason",
  EARLY = "early season",
  REG = "regular season",
  POST = "postseason",
  "regular season"
)

phase_note <- switch(
  season_phase,
  PRE = paste0(
    "The ", started_season %||% (nba_season + 1),
    " season has not tipped off yet, so this card covers the completed ",
    season_label, " season."
  ),
  EARLY = paste0(
    "Two seasons are on this page. The record, the ", LAST_TEN_COUNT,
    " game trend and the game log are the ", schedule_season_label,
    " season now in progress. The player groups, the offense and defense ",
    "panels and every composite are the completed ", season_label,
    " season, because no team has yet played the ",
    MIN_TEAM_GAMES_FOR_CURRENT_SEASON,
    " games a league-wide ranking needs."
  ),
  REG = paste0("The ", season_label, " regular season is in progress."),
  POST = paste0("The ", season_label, " postseason has begun."),
  ""
)

output_data <- list(
  sport = "NBA",
  visualizationType = "NBA_TEAM_REPORT_CARD",
  title = paste0("NBA Team Report Cards - ", season_label),
  subtitle = "Each team's top players graded by position group.",
  description = paste0(
    "Per-team player report cards from the ESPN box scores in the hoopR data ",
    "repository. Each team surfaces its top ", TOP_N, " players in three ",
    "position groups ranked by a composite of five stats, with NBA-wide rank ",
    "badges and a team row for the whole group, plus team rows for the ",
    "offense and defense.\n\n",
    phase_note, "\n\n",
    "STATS:\n\n",
    " • Composite: Average percentile across a group's stats (100 = best in ",
    "the league on every one). Team composites rank the same stats aggregated ",
    "over the whole position group; the overall composite averages the three ",
    "group composites and the two team units. Higher is better.\n\n",
    " • PTS/36, REB/36, AST/36, STL/36, BLK/36, STK/36: Per-36-minute rates, ",
    "so a sixth man and a starter are measured the same way. STK is steals ",
    "plus blocks. Higher is better.\n\n",
    " • TS%: True shooting percentage — points per shooting possession, which ",
    "counts threes and free throws at their real value. Higher is better.\n\n",
    " • 3P% / FG%: Three-point and field goal percentage. A player's 3P% is ",
    "only published once they clear ", MIN_THREE_ATTEMPTS, " attempts, so a ",
    "2-for-3 night does not top the league. Higher is better.\n\n",
    " • ORtg / DRtg: Points scored and allowed per 100 possessions, where a ",
    "possession is field goal attempts plus 0.44 free throw attempts minus ",
    "offensive rebounds plus turnovers. Higher ORtg and lower DRtg are ",
    "better.\n\n",
    " • Four factors: eFG% counts a three as 1.5 field goals; TOV% is ",
    "turnovers per 100 possessions (lower is better); OREB% and DREB% are the ",
    "share of available rebounds a team takes; FT Rate is free throw attempts ",
    "per 100 field goal attempts, or how often a team gets to the line. The ",
    "defense panel shows the same four as allowed, so a high ",
    "Opp TOV% (turnovers forced) is good and a high Opp eFG% is not.\n\n",
    " • ", LAST_TEN_COUNT, " Game Trend: The team's last ", LAST_TEN_COUNT,
    " games of the ", schedule_season_label, " season, ranked against every ",
    "other team's last ", LAST_TEN_COUNT,
    ". A form read, so it stays out of the overall composite.\n\n",
    " • Below-replacement performers: Rotation players the team was outscored ",
    "with — a negative plus-minus among players clearing the same bar as the ",
    "position groups. BR MIN% is the share of the team's minutes they ",
    "absorbed — rank 1 = smallest.\n\n",
    " • Injury Report: ESPN injury designations weighted by severity and by ",
    "the player's share of the team's minutes. Rank 1 is the most injured ",
    "team.\n\n",
    " • Last ", LAST_TEN_COUNT, " Games: The most recent ", LAST_TEN_COUNT,
    " results of the ", schedule_season_label, " season, in order. Each row ",
    "shows the opponent (vs = home, @ = away), both scores, and the point ",
    "differential.\n\n",
    "Player and team stats cover the regular season so rates stay comparable ",
    "across the league."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN (via hoopR) • PlayoffStatus.com • ESPN injuries",
  season = nba_season,
  seasonLabel = season_label,
  seasonPhase = season_phase,
  seasonPhaseLabel = season_phase_label,
  topN = TOP_N,
  categoryOrder = as.list(CATEGORY_ORDER),
  rankings = rankings_json,
  playoffChances = playoff_chances,
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = phase_tag_label, layout = "right", color = "#9C27B0")
  ),
  sortOrder = 1,
  teams = teams_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

# ============================================================================
# Sanity sample
# ============================================================================
sample_team <- if ("BOS" %in% ALL_TEAMS) "BOS" else ALL_TEAMS[1]
sample <- output_data$teams[[sample_team]]
cat("\nSample —", sample_team, "record:", sample$wins, "-", sample$losses,
    "| overall composite:", sample$overallComposite,
    "rank:", sample$overallCompositeRank, "\n")
for (key in CATEGORY_ORDER) {
  cat_data <- sample$categories[[key]]
  top <- if (length(cat_data$players) > 0) cat_data$players[[1]]$name else "(none)"
  cat("  ", cat_data$label, "— players:", length(cat_data$players),
      "| top:", top,
      "| team composite:", cat_data$team$stats$aggregate$value %||% NA, "\n")
}
cat("  Game log:", length(sample$gameLog$games), "games | record:",
    sample$gameLog$record$display, "| total diff:", sample$gameLog$totalDifferential, "\n")
cat("Teams with player data:", sum(vapply(teams_json, function(t) {
  any(vapply(t$categories, function(c) length(c$players) > 0, logical(1)))
}, logical(1))), "/", length(teams_json), "\n")
cat("Ranking sheets:", length(rankings_json), "\n")

if (nzchar(Sys.getenv("FASTBREAK_LOCAL_JSON"))) {
  file.copy(tmp_file, Sys.getenv("FASTBREAK_LOCAL_JSON"), overwrite = TRUE)
  cat("Wrote local JSON copy:", Sys.getenv("FASTBREAK_LOCAL_JSON"), "\n")
}

# ============================================================================
# Upload
# ============================================================================
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (!nzchar(s3_bucket)) stop("AWS_S3_BUCKET environment variable is not set")

env <- toupper(Sys.getenv("ENV", "DEV"))
s3_key <- if (env == "PROD") {
  "prod/nba__team_report_card.json"
} else {
  "dev/nba__team_report_card.json"
}

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
if (system(cmd) != 0) stop("Failed to upload to S3")
cat("\nUploaded to S3:", s3_path, "\n")

dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")

dynamodb_item <- sprintf(
  '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
  s3_key, utc_timestamp, output_data$title
)
ddb_cmd <- sprintf(
  "aws dynamodb put-item --table-name %s --item %s",
  shQuote(dynamodb_table), shQuote(dynamodb_item)
)
if (system(ddb_cmd) != 0) {
  warning("Failed to update DynamoDB timestamp (non-fatal)")
} else {
  cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
}

cat("\n=== NBA Team Report Card generation complete ===\n")
