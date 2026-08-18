#!/usr/bin/env Rscript

# NFL Team Report Card — per-team player leaderboards grouped by position group.
# Mirrors mlb__team_report_card.R: every player in a group is ranked league-wide
# on 4-5 advanced stats, those ranks average into a player composite, and the
# same stats aggregated to the team level produce the position group's team
# composite. The seven position group composites average into the team's overall
# composite.
#
# Position groups and their stats:
#  - QBs:         EPA/dropback + CPOE + ANY/A + success rate + sack rate
#  - RBs:         EPA/rush + yards per carry + success rate + first down rate + scrimmage yards
#  - Receivers:   EPA/target + yards per target + catch rate + target share + receiving yards
#  - O-Line:      snaps + snap share + games + penalties + penalty yards
#                 (team level uses real line play: sack/pressure/stuff rates and
#                  yards before contact, which have no per-player equivalent in
#                  the nflverse feeds)
#  - D-Line:      sacks + pressures + QB hits + TFL + tackles
#  - Linebackers: tackles + TFL + sacks + passes defensed + missed tackle rate
#  - Secondary:   INTs + passes defensed + completion rate allowed + yards per
#                 target allowed + passer rating allowed
#
# Differences from the MLB card, per product direction:
#  - No playoff picture (no NFL playoff odds source wired up yet)
#  - No 4-week trend
#  - Full season schedule and results instead of a last-10-games ledger

library(nflreadr)
library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)

options(nflreadr.verbose = FALSE)

TOP_N <- 10

# Qualification thresholds. Scaled down for partial seasons so an in-progress
# week 3 report card still has players in every group.
MIN_QB_DROPBACKS_FULL <- 150
MIN_RB_CARRIES_FULL <- 50
MIN_REC_TARGETS_FULL <- 30
MIN_OL_SNAPS_FULL <- 200
MIN_DEF_SNAPS_FULL <- 150
MIN_DB_TARGETS_FULL <- 25
MIN_BELOW_REPLACEMENT_PLAYS_FULL <- 40

REGULAR_SEASON_WEEKS <- 18

# ============================================================================
# Team metadata
# ============================================================================
# nflverse ships the Rams as "LA"; every other Fastbreak NFL chart and the
# nfl__teams.json roster use "LAR", so normalize on the way in.
normalize_nfl_team <- function(team) {
  code <- as.character(team)
  code[is.na(code)] <- NA_character_
  # PFR rolls players who changed teams into 2TM/3TM rows; those are not teams.
  code[code %in% c("2TM", "3TM", "4TM")] <- NA_character_
  ifelse(code == "LA", "LAR", code)
}

cat("=== NFL Team Report Card ===\n")

teams_meta <- nflreadr::load_teams() %>%
  transmute(
    team_code = normalize_nfl_team(team_abbr),
    team_name = team_name,
    conference = team_conf,
    division = team_division
  ) %>%
  filter(!is.na(team_code)) %>%
  distinct(team_code, .keep_all = TRUE) %>%
  arrange(team_code)

ALL_TEAMS <- teams_meta$team_code
team_names <- setNames(teams_meta$team_name, teams_meta$team_code)
team_divisions <- setNames(teams_meta$division, teams_meta$team_code)
team_conferences <- setNames(teams_meta$conference, teams_meta$team_code)

cat("Loaded", length(ALL_TEAMS), "NFL teams\n")

# ============================================================================
# Season + phase resolution
# ============================================================================
# The report card always covers the most recent season that has actually been
# played. The phase describes where the league sits *right now*, which is what
# the "preseason / regular season / postseason" badge is for: once a schedule
# exists for the next season but none of it has kicked off, we are in that
# season's preseason and the card still reports the completed season.
schedule_for_season <- function(season) {
  tryCatch(
    nflreadr::load_schedules(season),
    error = function(e) {
      cat("Warning: could not load schedule for", season, ":", e$message, "\n")
      NULL
    }
  )
}

calendar_season <- {
  y <- as.numeric(format(Sys.Date(), "%Y"))
  m <- as.numeric(format(Sys.Date(), "%m"))
  # The NFL league year flips in March; before that we are still in the
  # postseason of the previous season.
  if (m >= 3) y else y - 1
}

resolve_season_context <- function() {
  season <- calendar_season
  for (attempt in 1:3) {
    sched <- schedule_for_season(season)
    played <- if (is.null(sched)) NULL else sched %>% filter(!is.na(result))
    if (!is.null(played) && nrow(played) > 0) {
      has_post <- any(played$game_type != "REG")
      return(list(
        stats_season = season,
        schedule = sched,
        phase = if (has_post) "POST" else "REG",
        upcoming_season = NULL
      ))
    }
    # Nothing played yet for this season: the league is in its preseason, so
    # fall back to the previous season for the report card body.
    prev <- season - 1
    prev_sched <- schedule_for_season(prev)
    prev_played <- if (is.null(prev_sched)) NULL else prev_sched %>% filter(!is.na(result))
    if (!is.null(prev_played) && nrow(prev_played) > 0) {
      return(list(
        stats_season = prev,
        schedule = prev_sched,
        phase = "PRE",
        upcoming_season = season
      ))
    }
    season <- season - 1
  }
  stop("Could not find an NFL season with completed games")
}

season_ctx <- resolve_season_context()
nfl_season <- season_ctx$stats_season
season_phase <- season_ctx$phase
upcoming_season <- season_ctx$upcoming_season
schedule_all <- season_ctx$schedule

season_phase_label <- switch(
  season_phase,
  PRE = "Preseason",
  REG = "Regular Season",
  POST = "Postseason",
  season_phase
)

# NFL seasons are named by their opening year but end in the following one.
season_label <- paste0(nfl_season, "-", substr(nfl_season + 1, 3, 4))

cat("Season:", season_label, "| phase:", season_phase_label, "\n")
if (!is.null(upcoming_season)) {
  cat("Upcoming season", upcoming_season, "has not kicked off yet\n")
}

regular_season_played <- schedule_all %>%
  filter(!is.na(result), game_type == "REG")
weeks_played <- if (nrow(regular_season_played) > 0) {
  max(regular_season_played$week, na.rm = TRUE)
} else {
  0
}
season_fraction <- max(min(weeks_played / REGULAR_SEASON_WEEKS, 1), 1 / REGULAR_SEASON_WEEKS)

scaled_min <- function(full_threshold) {
  max(1, round(full_threshold * season_fraction))
}

MIN_QB_DROPBACKS <- scaled_min(MIN_QB_DROPBACKS_FULL)
MIN_RB_CARRIES <- scaled_min(MIN_RB_CARRIES_FULL)
MIN_REC_TARGETS <- scaled_min(MIN_REC_TARGETS_FULL)
MIN_OL_SNAPS <- scaled_min(MIN_OL_SNAPS_FULL)
MIN_DEF_SNAPS <- scaled_min(MIN_DEF_SNAPS_FULL)
MIN_DB_TARGETS <- scaled_min(MIN_DB_TARGETS_FULL)
MIN_BELOW_REPLACEMENT_PLAYS <- scaled_min(MIN_BELOW_REPLACEMENT_PLAYS_FULL)

cat("Regular season weeks played:", weeks_played,
    "| qualification thresholds — QB dropbacks:", MIN_QB_DROPBACKS,
    "| RB carries:", MIN_RB_CARRIES,
    "| targets:", MIN_REC_TARGETS,
    "| OL snaps:", MIN_OL_SNAPS, "\n")

# ============================================================================
# Shared helpers (mirrors mlb__team_report_card.R)
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

format_ordinal <- function(n) {
  if (is.na(n)) return(NA_character_)
  suffix <- if (n %% 100 %in% 11:13) {
    "th"
  } else if (n %% 10 == 1) {
    "st"
  } else if (n %% 10 == 2) {
    "nd"
  } else if (n %% 10 == 3) {
    "rd"
  } else {
    "th"
  }
  paste0(n, suffix)
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
# Load nflverse data
# ============================================================================
load_or_stop <- function(label, expr) {
  tryCatch(expr, error = function(e) {
    cat("Error loading", label, ":", e$message, "\n")
    stop(e)
  })
}

load_or_warn <- function(label, expr, fallback) {
  tryCatch(expr, error = function(e) {
    cat("Warning: could not load", label, ":", e$message, "\n")
    fallback
  })
}

# Player and team stats are regular-season only so rate stats stay comparable
# across teams; the postseason sample is both tiny and available to only a third
# of the league. The schedule section below still carries postseason results.
player_stats_raw <- load_or_stop(
  "player stats",
  nflreadr::load_player_stats(nfl_season, summary_level = "reg")
)
team_stats_raw <- load_or_stop(
  "team stats",
  nflreadr::load_team_stats(nfl_season, summary_level = "reg")
)
pbp <- load_or_stop(
  "play-by-play",
  nflreadr::load_pbp(nfl_season) %>% filter(season_type == "REG")
)
snap_counts <- load_or_warn(
  "snap counts",
  nflreadr::load_snap_counts(nfl_season) %>% filter(game_type == "REG"),
  NULL
)
pfr_def <- load_or_warn(
  "PFR defensive advanced stats",
  nflreadr::load_pfr_advstats(nfl_season, stat_type = "def", summary_level = "season"),
  NULL
)
pfr_pass <- load_or_warn(
  "PFR passing advanced stats",
  nflreadr::load_pfr_advstats(nfl_season, stat_type = "pass", summary_level = "season"),
  NULL
)
pfr_rush <- load_or_warn(
  "PFR rushing advanced stats",
  nflreadr::load_pfr_advstats(nfl_season, stat_type = "rush", summary_level = "season"),
  NULL
)
players_ids <- load_or_warn(
  "player id crosswalk",
  # Distinct on both ids: this crosswalk is joined in both directions below, and
  # a duplicate on either side would silently multiply a player's rows.
  nflreadr::load_players() %>%
    filter(!is.na(gsis_id), !is.na(pfr_id)) %>%
    distinct(gsis_id, .keep_all = TRUE) %>%
    distinct(pfr_id, .keep_all = TRUE) %>%
    select(gsis_id, pfr_id),
  NULL
)

cat("Loaded", nrow(player_stats_raw), "player stat rows,",
    nrow(pbp), "regular season plays,",
    if (is.null(snap_counts)) 0 else nrow(snap_counts), "snap count rows\n")

player_stats <- player_stats_raw %>%
  mutate(
    team_code = normalize_nfl_team(recent_team),
    player_name = coalesce(player_display_name, player_name)
  ) %>%
  filter(!is.na(team_code), team_code %in% ALL_TEAMS)

team_stats <- team_stats_raw %>%
  mutate(team_code = normalize_nfl_team(team)) %>%
  filter(!is.na(team_code), team_code %in% ALL_TEAMS)

# PFR advanced stats keyed by gsis id so they can be joined onto the nflverse
# player rows. Multi-team (2TM/3TM) rows are dropped by normalize_nfl_team.
join_pfr <- function(pfr_df, team_col) {
  if (is.null(pfr_df) || is.null(players_ids)) return(NULL)
  pfr_df %>%
    mutate(team_code = normalize_nfl_team(.data[[team_col]])) %>%
    filter(!is.na(team_code)) %>%
    inner_join(players_ids, by = "pfr_id") %>%
    distinct(gsis_id, .keep_all = TRUE)
}

pfr_def_joined <- join_pfr(pfr_def, "tm")
pfr_rush_joined <- join_pfr(pfr_rush, "tm")

# ============================================================================
# Play-by-play derived rates
# ============================================================================
qb_success <- pbp %>%
  filter(qb_dropback == 1, !is.na(passer_player_id), !is.na(success)) %>%
  group_by(player_id = passer_player_id) %>%
  summarise(pbp_dropbacks = n(), success_rate = mean(success) * 100, .groups = "drop")

rush_success <- pbp %>%
  filter(rush == 1, !is.na(rusher_player_id), !is.na(success)) %>%
  group_by(player_id = rusher_player_id) %>%
  summarise(pbp_carries = n(), success_rate = mean(success) * 100, .groups = "drop")

team_qb_success <- pbp %>%
  filter(qb_dropback == 1, !is.na(posteam), !is.na(success)) %>%
  mutate(team_code = normalize_nfl_team(posteam)) %>%
  filter(!is.na(team_code)) %>%
  group_by(team_code) %>%
  summarise(success_rate = mean(success) * 100, .groups = "drop")

team_rush_success <- pbp %>%
  filter(rush == 1, !is.na(posteam), !is.na(success)) %>%
  mutate(team_code = normalize_nfl_team(posteam)) %>%
  filter(!is.na(team_code)) %>%
  group_by(team_code) %>%
  summarise(success_rate = mean(success) * 100, .groups = "drop")

# Run "stuffs" — carries gaining nothing — are the cleanest publicly available
# proxy for interior run blocking, so they anchor the O-line team card.
team_run_block <- pbp %>%
  filter(rush == 1, !is.na(posteam), !is.na(yards_gained)) %>%
  mutate(team_code = normalize_nfl_team(posteam)) %>%
  filter(!is.na(team_code)) %>%
  group_by(team_code) %>%
  summarise(
    rush_attempts = n(),
    stuff_rate = mean(yards_gained <= 0) * 100,
    .groups = "drop"
  )

# Offensive line penalties: nflverse tags the penalized player, so blocking
# fouls can be attributed to the line without a separate charting feed.
OL_PENALTY_TYPES <- c(
  "Offensive Holding", "False Start", "Illegal Use of Hands",
  "Illegal Formation", "Ineligible Downfield Pass", "Illegal Block Above the Waist",
  "Chop Block", "Tripping", "Illegal Blindside Block", "Offensive Offside",
  "Illegal Shift", "Illegal Motion", "Ineligible Downfield Kick"
)

team_ol_penalties <- pbp %>%
  filter(penalty == 1, penalty_type %in% OL_PENALTY_TYPES, !is.na(penalty_team)) %>%
  mutate(team_code = normalize_nfl_team(penalty_team)) %>%
  filter(!is.na(team_code)) %>%
  group_by(team_code) %>%
  summarise(ol_penalties = n(), .groups = "drop")

player_ol_penalties <- pbp %>%
  filter(penalty == 1, penalty_type %in% OL_PENALTY_TYPES, !is.na(penalty_player_id)) %>%
  group_by(player_id = penalty_player_id) %>%
  summarise(
    ol_penalties = n(),
    ol_penalty_yards = sum(abs(penalty_yards), na.rm = TRUE),
    .groups = "drop"
  )

# ============================================================================
# Position group player pools
# ============================================================================
qbs <- player_stats %>%
  filter(position_group == "QB") %>%
  left_join(qb_success %>% select(player_id, success_rate), by = "player_id") %>%
  mutate(
    dropbacks = coalesce(attempts, 0) + coalesce(sacks_suffered, 0),
    epa_per_db = safe_div(passing_epa, dropbacks),
    cpoe = as.numeric(passing_cpoe),
    # sack_yards_lost arrives negative from nflverse, so it adds straight in.
    any_a = safe_div(
      passing_yards + 20 * passing_tds - 45 * passing_interceptions + coalesce(sack_yards_lost, 0),
      dropbacks
    ),
    sack_pct = safe_div(sacks_suffered, dropbacks) * 100
  ) %>%
  filter(
    dropbacks >= MIN_QB_DROPBACKS,
    !is.na(epa_per_db), !is.na(cpoe), !is.na(any_a), !is.na(success_rate), !is.na(sack_pct)
  )

rbs <- player_stats %>%
  filter(position_group == "RB") %>%
  left_join(rush_success %>% select(player_id, success_rate), by = "player_id") %>%
  mutate(
    epa_per_rush = safe_div(rushing_epa, carries),
    ypc = safe_div(rushing_yards, carries),
    first_down_pct = safe_div(rushing_first_downs, carries) * 100,
    scrimmage_yards = coalesce(rushing_yards, 0) + coalesce(receiving_yards, 0)
  ) %>%
  filter(
    carries >= MIN_RB_CARRIES,
    !is.na(epa_per_rush), !is.na(ypc), !is.na(success_rate),
    !is.na(first_down_pct), !is.na(scrimmage_yards)
  )

receivers <- player_stats %>%
  filter(position_group %in% c("WR", "TE")) %>%
  mutate(
    epa_per_target = safe_div(receiving_epa, targets),
    yards_per_target = safe_div(receiving_yards, targets),
    catch_pct = safe_div(receptions, targets) * 100,
    target_share_pct = as.numeric(target_share) * 100,
    rec_yards = as.numeric(receiving_yards)
  ) %>%
  filter(
    targets >= MIN_REC_TARGETS,
    !is.na(epa_per_target), !is.na(yards_per_target), !is.na(catch_pct),
    !is.na(target_share_pct), !is.na(rec_yards)
  )

# Offensive linemen never show up in the box score, so the pool comes from snap
# counts (which cover every lineman who played) rather than the stats feed.
build_ol_pool <- function() {
  empty <- tibble(
    player_id = character(), player_name = character(), position = character(),
    team_code = character(), off_snaps = numeric(), snap_pct = numeric(),
    games = numeric(), penalties = numeric(), penalty_yards = numeric()
  )
  if (is.null(snap_counts)) {
    cat("Warning: snap counts unavailable — offensive line group will be empty\n")
    return(empty)
  }
  ol <- snap_counts %>%
    filter(position %in% c("T", "OT", "G", "C", "OL"), !is.na(pfr_player_id)) %>%
    mutate(team_code = normalize_nfl_team(team)) %>%
    filter(!is.na(team_code), team_code %in% ALL_TEAMS)
  if (nrow(ol) == 0) return(empty)

  primary_team <- ol %>%
    group_by(pfr_player_id, team_code) %>%
    summarise(team_snaps = sum(offense_snaps, na.rm = TRUE), .groups = "drop") %>%
    group_by(pfr_player_id) %>%
    slice_max(team_snaps, n = 1, with_ties = FALSE) %>%
    ungroup() %>%
    select(pfr_player_id, team_code)

  pool <- ol %>%
    group_by(pfr_player_id) %>%
    summarise(
      player_name = last(player),
      position = last(position),
      off_snaps = sum(offense_snaps, na.rm = TRUE),
      snap_pct = mean(offense_pct, na.rm = TRUE) * 100,
      games = sum(offense_snaps > 0, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    inner_join(primary_team, by = "pfr_player_id")

  pool <- if (is.null(players_ids)) {
    pool %>% mutate(player_id = pfr_player_id)
  } else {
    pool %>%
      left_join(players_ids, by = c("pfr_player_id" = "pfr_id")) %>%
      mutate(player_id = coalesce(gsis_id, pfr_player_id)) %>%
      select(-gsis_id)
  }

  pool %>%
    left_join(player_ol_penalties, by = "player_id") %>%
    mutate(
      penalties = coalesce(as.numeric(ol_penalties), 0),
      penalty_yards = coalesce(as.numeric(ol_penalty_yards), 0)
    ) %>%
    filter(off_snaps >= MIN_OL_SNAPS, !is.na(snap_pct)) %>%
    select(-any_of(c("ol_penalties", "ol_penalty_yards")))
}

offensive_line <- build_ol_pool()

# Defensive snap totals gate the front seven and secondary pools the same way
# innings gate MLB fielders: enough playing time to make the rates meaningful.
defensive_snaps <- if (is.null(snap_counts) || is.null(players_ids)) {
  tibble(player_id = character(), def_snaps = numeric())
} else {
  snap_counts %>%
    filter(!is.na(pfr_player_id)) %>%
    group_by(pfr_player_id) %>%
    summarise(def_snaps = sum(defense_snaps, na.rm = TRUE), .groups = "drop") %>%
    left_join(players_ids, by = c("pfr_player_id" = "pfr_id")) %>%
    filter(!is.na(gsis_id)) %>%
    transmute(player_id = gsis_id, def_snaps)
}

# The empty fallback still declares every column: the join below is what puts
# these on `defense_base`, and the pools filter on them, so a missing PFR feed
# has to degrade to all-NA rather than to absent columns.
pfr_def_stats <- if (is.null(pfr_def_joined)) {
  cat("Warning: PFR defensive stats unavailable — front seven and secondary pools will be empty\n")
  tibble(
    player_id = character(),
    pressures = numeric(),
    missed_tackle_pct = numeric(),
    cmp_pct_allowed = numeric(),
    yards_per_target_allowed = numeric(),
    passer_rating_allowed = numeric(),
    coverage_targets = numeric()
  )
} else {
  pfr_def_joined %>%
    transmute(
      player_id = gsis_id,
      pressures = as.numeric(prss),
      missed_tackle_pct = as.numeric(m_tkl_percent),
      cmp_pct_allowed = as.numeric(cmp_percent) * 100,
      yards_per_target_allowed = as.numeric(yds_tgt),
      passer_rating_allowed = as.numeric(rat),
      coverage_targets = as.numeric(tgt)
    )
}

defense_base <- player_stats %>%
  filter(position_group %in% c("DL", "LB", "DB")) %>%
  left_join(defensive_snaps, by = "player_id") %>%
  left_join(pfr_def_stats, by = "player_id") %>%
  mutate(
    def_snaps = coalesce(def_snaps, 0),
    tackles = coalesce(def_tackles_solo, 0) + coalesce(def_tackle_assists, 0),
    sacks = as.numeric(def_sacks),
    qb_hits = as.numeric(def_qb_hits),
    tfl = as.numeric(def_tackles_for_loss),
    passes_defensed = as.numeric(def_pass_defended),
    interceptions = as.numeric(def_interceptions)
  )

defensive_line <- defense_base %>%
  filter(position_group == "DL", def_snaps >= MIN_DEF_SNAPS) %>%
  filter(!is.na(sacks), !is.na(qb_hits), !is.na(tfl), !is.na(tackles), !is.na(pressures))

linebackers <- defense_base %>%
  filter(position_group == "LB", def_snaps >= MIN_DEF_SNAPS) %>%
  filter(!is.na(tackles), !is.na(tfl), !is.na(sacks), !is.na(passes_defensed), !is.na(missed_tackle_pct))

secondary <- defense_base %>%
  filter(position_group == "DB", def_snaps >= MIN_DEF_SNAPS) %>%
  filter(
    !is.na(interceptions), !is.na(passes_defensed), !is.na(cmp_pct_allowed),
    !is.na(yards_per_target_allowed), !is.na(passer_rating_allowed),
    coverage_targets >= MIN_DB_TARGETS
  )

cat("Qualified pools — QBs:", nrow(qbs),
    "| RBs:", nrow(rbs),
    "| receivers:", nrow(receivers),
    "| O-line:", nrow(offensive_line),
    "| D-line:", nrow(defensive_line),
    "| linebackers:", nrow(linebackers),
    "| secondary:", nrow(secondary), "\n")

# ============================================================================
# Team-level position group stats
# ============================================================================
# Team cards aggregate the whole position group, not just the qualified players
# shown in the table, so a team is not credited or penalized for roster churn.
player_position_group <- player_stats %>%
  select(player_id, position_group) %>%
  distinct(player_id, .keep_all = TRUE)

group_rush_success <- function(groups) {
  ids <- player_position_group %>% filter(position_group %in% groups) %>% pull(player_id)
  pbp %>%
    filter(rush == 1, rusher_player_id %in% ids, !is.na(posteam), !is.na(success)) %>%
    mutate(team_code = normalize_nfl_team(posteam)) %>%
    filter(!is.na(team_code)) %>%
    group_by(team_code) %>%
    summarise(success_rate = mean(success) * 100, .groups = "drop")
}

team_qbs <- team_stats %>%
  mutate(
    dropbacks = coalesce(attempts, 0) + coalesce(sacks_suffered, 0),
    epa_per_db = safe_div(passing_epa, dropbacks),
    cpoe = as.numeric(passing_cpoe),
    any_a = safe_div(
      passing_yards + 20 * passing_tds - 45 * passing_interceptions + coalesce(sack_yards_lost, 0),
      dropbacks
    ),
    sack_pct = safe_div(sacks_suffered, dropbacks) * 100
  ) %>%
  select(team_code, epa_per_db, cpoe, any_a, sack_pct) %>%
  left_join(team_qb_success, by = "team_code")

team_rbs <- player_stats %>%
  filter(position_group == "RB") %>%
  group_by(team_code) %>%
  summarise(
    rushing_epa = sum(rushing_epa, na.rm = TRUE),
    carries = sum(carries, na.rm = TRUE),
    rushing_yards = sum(rushing_yards, na.rm = TRUE),
    rushing_first_downs = sum(rushing_first_downs, na.rm = TRUE),
    scrimmage_yards = sum(coalesce(rushing_yards, 0) + coalesce(receiving_yards, 0), na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    epa_per_rush = safe_div(rushing_epa, carries),
    ypc = safe_div(rushing_yards, carries),
    first_down_pct = safe_div(rushing_first_downs, carries) * 100
  ) %>%
  left_join(group_rush_success("RB"), by = "team_code") %>%
  select(team_code, epa_per_rush, ypc, success_rate, first_down_pct, scrimmage_yards)

team_receivers <- player_stats %>%
  filter(position_group %in% c("WR", "TE")) %>%
  group_by(team_code) %>%
  summarise(
    receiving_epa = sum(receiving_epa, na.rm = TRUE),
    targets = sum(targets, na.rm = TRUE),
    receptions = sum(receptions, na.rm = TRUE),
    rec_yards = sum(receiving_yards, na.rm = TRUE),
    target_share_pct = sum(target_share, na.rm = TRUE) * 100,
    .groups = "drop"
  ) %>%
  mutate(
    epa_per_target = safe_div(receiving_epa, targets),
    yards_per_target = safe_div(rec_yards, targets),
    catch_pct = safe_div(receptions, targets) * 100
  ) %>%
  select(team_code, epa_per_target, yards_per_target, catch_pct, target_share_pct, rec_yards)

# The line's team card measures actual line play. Individual linemen have no
# public per-snap grade in the nflverse feeds, so their table is availability
# plus penalties while the team row carries protection and run-blocking rates.
team_pressure_allowed <- if (is.null(pfr_pass)) {
  tibble(team_code = character(), pressure_rate_allowed = numeric())
} else {
  pfr_pass %>%
    mutate(team_code = normalize_nfl_team(team)) %>%
    filter(!is.na(team_code)) %>%
    group_by(team_code) %>%
    summarise(
      pressure_rate_allowed = safe_div(
        sum(times_pressured, na.rm = TRUE),
        sum(pass_attempts, na.rm = TRUE)
      ) * 100,
      .groups = "drop"
    )
}

team_ybc <- if (is.null(pfr_rush)) {
  tibble(team_code = character(), ybc_per_att = numeric())
} else {
  pfr_rush %>%
    mutate(team_code = normalize_nfl_team(tm)) %>%
    filter(!is.na(team_code)) %>%
    group_by(team_code) %>%
    summarise(
      ybc_per_att = safe_div(sum(ybc, na.rm = TRUE), sum(att, na.rm = TRUE)),
      .groups = "drop"
    )
}

team_offensive_line <- team_stats %>%
  mutate(
    dropbacks = coalesce(attempts, 0) + coalesce(sacks_suffered, 0),
    sack_rate_allowed = safe_div(sacks_suffered, dropbacks) * 100
  ) %>%
  select(team_code, sack_rate_allowed) %>%
  left_join(team_pressure_allowed, by = "team_code") %>%
  left_join(team_run_block %>% select(team_code, stuff_rate), by = "team_code") %>%
  left_join(team_ybc, by = "team_code") %>%
  left_join(team_ol_penalties, by = "team_code") %>%
  mutate(ol_penalties = coalesce(as.numeric(ol_penalties), 0))

summarise_defense_group <- function(group) {
  rows <- defense_base %>% filter(position_group == group)

  totals <- rows %>%
    group_by(team_code) %>%
    summarise(
      tackles = sum(tackles, na.rm = TRUE),
      sacks = sum(sacks, na.rm = TRUE),
      qb_hits = sum(qb_hits, na.rm = TRUE),
      tfl = sum(tfl, na.rm = TRUE),
      passes_defensed = sum(passes_defensed, na.rm = TRUE),
      interceptions = sum(interceptions, na.rm = TRUE),
      pressures = sum(pressures, na.rm = TRUE),
      .groups = "drop"
    )

  # Rate stats weight by the volume they are measured over, so a rotational
  # player's small sample cannot swing the team number. Kept in a separate pass
  # because the totals above shadow the per-player columns they weight by.
  rates <- rows %>%
    group_by(team_code) %>%
    summarise(
      missed_tackle_pct = weighted.mean(missed_tackle_pct, tackles, na.rm = TRUE),
      cmp_pct_allowed = weighted.mean(cmp_pct_allowed, coverage_targets, na.rm = TRUE),
      yards_per_target_allowed = weighted.mean(yards_per_target_allowed, coverage_targets, na.rm = TRUE),
      passer_rating_allowed = weighted.mean(passer_rating_allowed, coverage_targets, na.rm = TRUE),
      .groups = "drop"
    )

  totals %>%
    left_join(rates, by = "team_code") %>%
    mutate(across(where(is.numeric), ~ ifelse(is.nan(.x), NA_real_, .x)))
}

team_defensive_line <- summarise_defense_group("DL") %>%
  select(team_code, sacks, pressures, qb_hits, tfl, tackles)
team_linebackers <- summarise_defense_group("LB") %>%
  select(team_code, tackles, tfl, sacks, passes_defensed, missed_tackle_pct)
team_secondary <- summarise_defense_group("DB") %>%
  select(team_code, interceptions, passes_defensed, cmp_pct_allowed,
         yards_per_target_allowed, passer_rating_allowed)

# ============================================================================
# Below-replacement performers
# ============================================================================
# The NFL analogue of MLB's negative-WAR regulars: skill players who cost their
# offense expected points across a real workload. Team share is the fraction of
# offensive touches those players absorbed.
skill_plays <- player_stats %>%
  filter(position_group %in% c("QB", "RB", "WR", "TE")) %>%
  mutate(
    plays = coalesce(attempts, 0) + coalesce(sacks_suffered, 0) +
      coalesce(carries, 0) + coalesce(targets, 0),
    total_epa = coalesce(passing_epa, 0) + coalesce(rushing_epa, 0) + coalesce(receiving_epa, 0)
  )

below_replacement <- skill_plays %>%
  filter(plays >= MIN_BELOW_REPLACEMENT_PLAYS, total_epa < 0)

team_skill_plays <- skill_plays %>%
  group_by(team_code) %>%
  summarise(team_plays = sum(plays, na.rm = TRUE), .groups = "drop")

team_below_replacement <- tibble(team_code = ALL_TEAMS) %>%
  left_join(team_skill_plays, by = "team_code") %>%
  left_join(
    below_replacement %>%
      group_by(team_code) %>%
      summarise(below_replacement_plays = sum(plays, na.rm = TRUE), .groups = "drop"),
    by = "team_code"
  ) %>%
  mutate(
    team_plays = coalesce(team_plays, 0),
    below_replacement_plays = coalesce(below_replacement_plays, 0),
    below_replacement_play_pct = if_else(
      team_plays > 0,
      below_replacement_plays / team_plays * 100,
      0
    )
  )

# ============================================================================
# Injury report (ESPN)
# ============================================================================
# ESPN reports "Active" for players who are simply on the injury wire with no
# game-status designation; those carry no expected absence, so they are dropped.
injury_status_weight <- function(status) {
  st <- tolower(trimws(as.character(status)))
  if (!nzchar(st)) return(NA_real_)
  if (grepl("suspension", st)) return(NA_real_)
  if (grepl("^active$", st)) return(NA_real_)
  if (grepl("injured reserve|^ir$", st)) return(1.0)
  if (grepl("physically unable|^pup$", st)) return(1.0)
  if (grepl("non.?football", st)) return(1.0)
  if (grepl("^out$", st)) return(0.9)
  if (grepl("doubtful", st)) return(0.75)
  if (grepl("questionable", st)) return(0.4)
  if (grepl("day-to-day|day to day", st)) return(0.3)
  0.5
}

espn_nfl_team_to_code <- function(display_name, lookup) {
  name <- trimws(as.character(display_name))
  if (!nzchar(name)) return(NA_character_)
  hit <- lookup[[name]]
  if (!is.null(hit)) return(hit)
  for (full_name in names(lookup)) {
    if (identical(full_name, name)) return(lookup[[full_name]])
  }
  NA_character_
}

fetch_espn_nfl_injuries <- function() {
  url <- "https://site.api.espn.com/apis/site/v2/sports/football/nfl/injuries"
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
    lookup <- as.list(setNames(teams_meta$team_code, teams_meta$team_name))
    rows <- list()
    for (group in (payload$injuries %||% list())) {
      team_code <- espn_nfl_team_to_code(safe_scalar(group$displayName, ""), lookup)
      if (is.na(team_code)) {
        cat("Warning: could not map ESPN NFL team:", safe_scalar(group$displayName, ""), "\n")
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

cat("Loading ESPN NFL injury report...\n")
injury_players_raw <- fetch_espn_nfl_injuries()

# Snap share stands in for MLB's WAR here: it is the best public measure of how
# much of a team's football an injured player was actually playing.
player_snap_share <- if (is.null(snap_counts)) {
  tibble(athlete_key = character(), snap_share = numeric())
} else {
  snap_counts %>%
    filter(!is.na(player)) %>%
    group_by(player) %>%
    summarise(
      snap_share = mean(pmax(offense_pct, defense_pct, na.rm = TRUE), na.rm = TRUE) * 100,
      .groups = "drop"
    ) %>%
    transmute(athlete_key = tolower(trimws(player)), snap_share) %>%
    filter(!is.na(snap_share)) %>%
    distinct(athlete_key, .keep_all = TRUE)
}

injury_players <- if (nrow(injury_players_raw) == 0) {
  cat("Warning: injury report unavailable — continuing without injury data\n")
  injury_players_raw %>% mutate(snap_share = numeric(), impact = numeric())
} else {
  injury_players_raw %>%
    mutate(athlete_key = tolower(trimws(athlete_name))) %>%
    left_join(player_snap_share, by = "athlete_key") %>%
    mutate(
      snap_share = coalesce(snap_share, 0),
      impact = snap_share * status_weight
    ) %>%
    select(-athlete_key)
}

team_injuries <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    injury_players %>%
      group_by(team_code) %>%
      summarise(
        injured_count = n(),
        injury_snap_share = sum(impact, na.rm = TRUE),
        .groups = "drop"
      ),
    by = "team_code"
  ) %>%
  mutate(
    injured_count = coalesce(as.integer(injured_count), 0L),
    injury_snap_share = coalesce(injury_snap_share, 0)
  )

cat("Injury report — players:", nrow(injury_players),
    "| teams with injuries:", sum(team_injuries$injured_count > 0), "\n")

# ============================================================================
# Records + full schedule with results
# ============================================================================
# One row per team per game, so the schedule ledger and the standings both come
# out of the same source of truth.
team_games <- schedule_all %>%
  filter(!is.na(home_team), !is.na(away_team)) %>%
  mutate(
    home_code = normalize_nfl_team(home_team),
    away_code = normalize_nfl_team(away_team)
  ) %>%
  filter(!is.na(home_code), !is.na(away_code))

team_game_rows <- bind_rows(
  team_games %>%
    transmute(
      team_code = home_code, opponent_code = away_code, is_home = TRUE,
      week, game_type, gameday, gametime,
      team_score = home_score, opponent_score = away_score
    ),
  team_games %>%
    transmute(
      team_code = away_code, opponent_code = home_code, is_home = FALSE,
      week, game_type, gameday, gametime,
      team_score = away_score, opponent_score = home_score
    )
) %>%
  mutate(
    played = !is.na(team_score) & !is.na(opponent_score),
    differential = if_else(played, team_score - opponent_score, NA_integer_),
    result = case_when(
      !played ~ NA_character_,
      team_score > opponent_score ~ "W",
      team_score < opponent_score ~ "L",
      TRUE ~ "T"
    ),
    # Sorting on the ISO date plus kickoff keeps same-day games in order and
    # keeps postseason rounds after the regular season finale.
    sort_key = paste(coalesce(gameday, "9999-99-99"), coalesce(gametime, "00:00"))
  ) %>%
  arrange(team_code, sort_key)

team_records <- team_game_rows %>%
  filter(played, game_type == "REG") %>%
  group_by(team_code) %>%
  summarise(
    W = sum(result == "W"),
    L = sum(result == "L"),
    T = sum(result == "T"),
    .groups = "drop"
  ) %>%
  right_join(tibble(team_code = ALL_TEAMS), by = "team_code") %>%
  mutate(
    W = coalesce(as.integer(W), 0L),
    L = coalesce(as.integer(L), 0L),
    T = coalesce(as.integer(T), 0L),
    games = W + L + T,
    # NFL win percentage counts a tie as half a win.
    win_pct = if_else(games > 0, (W + 0.5 * T) / games, NA_real_),
    division = unname(team_divisions[team_code]),
    conference = unname(team_conferences[team_code])
  )

team_records <- rank_and_assign(team_records, "win_pct")

team_records <- team_records %>%
  group_by(division) %>%
  mutate(
    division_rank = as.integer(rank(-coalesce(win_pct, -1), ties.method = "min")),
    division_rank_display = vapply(division_rank, format_ordinal, character(1))
  ) %>%
  ungroup()

format_record <- function(w, l, t) {
  if (t > 0) paste0(w, "-", l, "-", t) else paste0(w, "-", l)
}

build_schedule_for_team <- function(team) {
  rows <- team_game_rows %>% filter(team_code == team)
  label <- paste0(nfl_season, " Schedule & Results")
  if (nrow(rows) == 0) {
    return(list(
      label = label,
      columns = list("Wk", "Opponent", "Opp", "Team", "Diff"),
      games = list(),
      record = list(wins = 0L, losses = 0L, ties = 0L, display = "0-0"),
      totalDifferential = 0L
    ))
  }

  games <- lapply(seq_len(nrow(rows)), function(i) {
    r <- rows[i, ]
    loc <- if (isTRUE(r$is_home)) "vs" else "@"
    played <- isTRUE(r$played)
    list(
      date = if (!is.na(r$gameday)) as.character(r$gameday) else NULL,
      week = if (!is.na(r$week)) as.integer(r$week) else NULL,
      seasonType = if (!is.na(r$game_type)) as.character(r$game_type) else NULL,
      location = loc,
      opponent = r$opponent_code,
      opponentLabel = paste(loc, r$opponent_code),
      teamScore = if (played) as.integer(r$team_score) else NULL,
      opponentScore = if (played) as.integer(r$opponent_score) else NULL,
      differential = if (played) as.integer(r$differential) else NULL,
      result = if (played) r$result else NULL,
      won = if (played) identical(r$result, "W") else NULL,
      played = played
    )
  })

  played_rows <- rows %>% filter(played)
  wins <- sum(played_rows$result == "W")
  losses <- sum(played_rows$result == "L")
  ties <- sum(played_rows$result == "T")
  total_diff <- sum(played_rows$differential, na.rm = TRUE)

  list(
    label = label,
    columns = list("Wk", "Opponent", "Opp", "Team", "Diff"),
    games = games,
    record = list(
      wins = as.integer(wins),
      losses = as.integer(losses),
      ties = as.integer(ties),
      display = format_record(wins, losses, ties)
    ),
    totalDifferential = as.integer(total_diff)
  )
}

cat("Schedule ledger — games:", nrow(team_game_rows),
    "| played:", sum(team_game_rows$played), "\n")

# ============================================================================
# Category definitions
# ============================================================================
# One spec per position group drives ranking, composites, the per-team payload
# and the league-wide ranking sheets, so a stat is only ever declared once.
CATEGORY_SPECS <- list(
  list(
    key = "qbs",
    label = "Quarterbacks",
    description = paste0(
      "Passers ranked by EPA per dropback, completion percentage over expected, ",
      "adjusted net yards per attempt, dropback success rate, and sack rate. ",
      "Qualified at ", MIN_QB_DROPBACKS, "+ dropbacks."
    ),
    pool = quote(qbs),
    team_df = quote(team_qbs),
    stat_cols = c("epa_per_db", "cpoe", "any_a", "success_rate", "sack_pct"),
    lower_better = c("sack_pct"),
    labels = c(epa_per_db = "EPA/DB", cpoe = "CPOE", any_a = "ANY/A",
               success_rate = "Succ%", sack_pct = "Sack%"),
    digits = list(epa_per_db = 3, cpoe = 1, any_a = 2, success_rate = 1, sack_pct = 1)
  ),
  list(
    key = "rbs",
    label = "Running Backs",
    description = paste0(
      "Ball carriers ranked by EPA per rush, yards per carry, rush success rate, ",
      "first down rate, and yards from scrimmage. Qualified at ",
      MIN_RB_CARRIES, "+ carries."
    ),
    pool = quote(rbs),
    team_df = quote(team_rbs),
    stat_cols = c("epa_per_rush", "ypc", "success_rate", "first_down_pct", "scrimmage_yards"),
    lower_better = character(0),
    labels = c(epa_per_rush = "EPA/Rush", ypc = "YPC", success_rate = "Succ%",
               first_down_pct = "1D%", scrimmage_yards = "Scrim Yds"),
    digits = list(epa_per_rush = 3, ypc = 2, success_rate = 1, first_down_pct = 1, scrimmage_yards = 0)
  ),
  list(
    key = "receivers",
    label = "Receivers",
    description = paste0(
      "Wide receivers and tight ends ranked by EPA per target, yards per target, ",
      "catch rate, target share, and receiving yards. Qualified at ",
      MIN_REC_TARGETS, "+ targets."
    ),
    pool = quote(receivers),
    team_df = quote(team_receivers),
    stat_cols = c("epa_per_target", "yards_per_target", "catch_pct", "target_share_pct", "rec_yards"),
    lower_better = character(0),
    labels = c(epa_per_target = "EPA/Tgt", yards_per_target = "Y/Tgt", catch_pct = "Catch%",
               target_share_pct = "Tgt%", rec_yards = "Rec Yds"),
    digits = list(epa_per_target = 3, yards_per_target = 2, catch_pct = 1,
                  target_share_pct = 1, rec_yards = 0)
  ),
  list(
    key = "offensiveLine",
    label = "Offensive Line",
    description = paste0(
      "Linemen ranked by snaps played, snap share, games, and blocking penalties ",
      "(fewer is better) — the nflverse feeds carry no per-snap blocking grade. ",
      "The team row uses actual line play: sack rate and pressure rate allowed, ",
      "stuff rate, yards before contact per rush, and blocking penalties. ",
      "Qualified at ", MIN_OL_SNAPS, "+ offensive snaps."
    ),
    pool = quote(offensive_line),
    team_df = quote(team_offensive_line),
    stat_cols = c("off_snaps", "snap_pct", "games", "penalties", "penalty_yards"),
    lower_better = c("penalties", "penalty_yards"),
    labels = c(off_snaps = "Snaps", snap_pct = "Snap%", games = "G",
               penalties = "Pen", penalty_yards = "Pen Yds"),
    digits = list(off_snaps = 0, snap_pct = 1, games = 0, penalties = 0, penalty_yards = 0),
    team_stat_cols = c("sack_rate_allowed", "pressure_rate_allowed", "stuff_rate",
                       "ybc_per_att", "ol_penalties"),
    team_lower_better = c("sack_rate_allowed", "pressure_rate_allowed", "stuff_rate", "ol_penalties"),
    team_labels = c(sack_rate_allowed = "Sack%", pressure_rate_allowed = "Prss%",
                    stuff_rate = "Stuff%", ybc_per_att = "YBC/Att", ol_penalties = "Pen"),
    team_digits = list(sack_rate_allowed = 1, pressure_rate_allowed = 1, stuff_rate = 1,
                       ybc_per_att = 2, ol_penalties = 0)
  ),
  list(
    key = "defensiveLine",
    label = "Defensive Line",
    description = paste0(
      "Interior and edge defenders ranked by sacks, pressures, quarterback hits, ",
      "tackles for loss, and total tackles. Qualified at ", MIN_DEF_SNAPS,
      "+ defensive snaps."
    ),
    pool = quote(defensive_line),
    team_df = quote(team_defensive_line),
    stat_cols = c("sacks", "pressures", "qb_hits", "tfl", "tackles"),
    lower_better = character(0),
    labels = c(sacks = "Sacks", pressures = "Prss", qb_hits = "QB Hits",
               tfl = "TFL", tackles = "Tkl"),
    digits = list(sacks = 1, pressures = 0, qb_hits = 0, tfl = 0, tackles = 0)
  ),
  list(
    key = "linebackers",
    label = "Linebackers",
    description = paste0(
      "Linebackers ranked by tackles, tackles for loss, sacks, passes defensed, ",
      "and missed tackle rate (lower is better). Qualified at ", MIN_DEF_SNAPS,
      "+ defensive snaps."
    ),
    pool = quote(linebackers),
    team_df = quote(team_linebackers),
    stat_cols = c("tackles", "tfl", "sacks", "passes_defensed", "missed_tackle_pct"),
    lower_better = c("missed_tackle_pct"),
    labels = c(tackles = "Tkl", tfl = "TFL", sacks = "Sacks",
               passes_defensed = "PD", missed_tackle_pct = "MT%"),
    digits = list(tackles = 0, tfl = 0, sacks = 1, passes_defensed = 0, missed_tackle_pct = 1)
  ),
  list(
    key = "secondary",
    label = "Secondary",
    description = paste0(
      "Corners and safeties ranked by interceptions, passes defensed, and the ",
      "coverage they allowed — completion rate, yards per target, and passer ",
      "rating against (all lower is better). Qualified at ", MIN_DEF_SNAPS,
      "+ defensive snaps and ", MIN_DB_TARGETS, "+ targets in coverage."
    ),
    pool = quote(secondary),
    team_df = quote(team_secondary),
    stat_cols = c("interceptions", "passes_defensed", "cmp_pct_allowed",
                  "yards_per_target_allowed", "passer_rating_allowed"),
    lower_better = c("cmp_pct_allowed", "yards_per_target_allowed", "passer_rating_allowed"),
    labels = c(interceptions = "INT", passes_defensed = "PD", cmp_pct_allowed = "Cmp%",
               yards_per_target_allowed = "Y/Tgt", passer_rating_allowed = "Rtg"),
    digits = list(interceptions = 0, passes_defensed = 0, cmp_pct_allowed = 1,
                  yards_per_target_allowed = 1, passer_rating_allowed = 1)
  )
)

# The seven position groups feed the overall composite. Below-replacement and
# the injury report are diagnostics, not quality ratings, so they sit outside it
# exactly as they do on the MLB card.
POSITION_GROUP_KEYS <- vapply(CATEGORY_SPECS, function(spec) spec$key, character(1))

spec_team_stat_cols <- function(spec) spec$team_stat_cols %||% spec$stat_cols
spec_team_lower_better <- function(spec) {
  if (!is.null(spec$team_stat_cols)) spec$team_lower_better %||% character(0) else spec$lower_better
}
spec_team_labels <- function(spec) {
  if (!is.null(spec$team_stat_cols)) spec$team_labels else spec$labels
}
spec_team_digits <- function(spec) {
  if (!is.null(spec$team_stat_cols)) spec$team_digits else spec$digits
}

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

  team_cols <- spec_team_stat_cols(spec)
  team_lower <- spec_team_lower_better(spec)
  team_df <- team_df %>% filter(team_code %in% ALL_TEAMS)
  for (col in team_cols) {
    team_df <- rank_and_assign(team_df, col, lower_better = col %in% team_lower)
  }
  team_df <- add_composite_score(team_df, team_cols)
  team_df <- rank_and_assign(team_df, "composite_score")

  player_pools[[spec$key]] <- pool
  team_pools[[spec$key]] <- team_df
}

team_below_replacement <- rank_and_assign(
  team_below_replacement, "below_replacement_play_pct", lower_better = TRUE
)
team_below_replacement <- add_composite_score(team_below_replacement, c("below_replacement_play_pct"))
team_below_replacement <- rank_and_assign(team_below_replacement, "composite_score")

below_replacement <- rank_and_assign(below_replacement, "plays")
below_replacement <- rank_and_assign(below_replacement, "total_epa", lower_better = TRUE)

team_injuries <- rank_and_assign(team_injuries, "injured_count")
team_injuries <- rank_and_assign(team_injuries, "injury_snap_share")
team_injuries <- add_composite_score(team_injuries, c("injured_count", "injury_snap_share"))
team_injuries <- rank_and_assign(team_injuries, "composite_score")

category_composite <- function(team_df, team) {
  row <- team_df %>% filter(team_code == team)
  if (nrow(row) == 0 || is.na(row$composite_score[1])) NA_real_ else row$composite_score[1]
}

team_overall <- tibble(team_code = ALL_TEAMS) %>%
  rowwise() %>%
  mutate(
    overall_composite = mean(
      vapply(POSITION_GROUP_KEYS, function(key) category_composite(team_pools[[key]], team_code), numeric(1)),
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
below_replacement_labels <- c(plays = "Plays", total_epa = "EPA")
below_replacement_digits <- list(plays = 0, total_epa = 1)
below_replacement_team_labels <- c(below_replacement_play_pct = "BR Play%")
below_replacement_team_digits <- list(below_replacement_play_pct = 1)
injury_labels <- c(injured_count = "Injured", injury_snap_share = "Snaps Lost")
injury_digits <- list(injured_count = 0, injury_snap_share = 1)

below_replacement_team_players <- function(team) {
  team_df <- below_replacement %>%
    filter(team_code == team) %>%
    arrange(total_epa, desc(plays)) %>%
    head(TOP_N)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) {
    build_player(team_df[i, ], c("plays", "total_epa"),
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
CATEGORY_ORDER <- c(POSITION_GROUP_KEYS, "belowReplacement", "injuries")

build_position_category <- function(spec, team) {
  team_cols <- spec_team_stat_cols(spec)
  list(
    label = spec$label,
    description = spec$description,
    statKeys = as.list(team_cols),
    playerStatKeys = as.list(spec$stat_cols),
    compositeRankingKey = paste0(spec$key, "Composite"),
    team = build_team_category_stats(
      team_pools[[spec$key]], team, team_cols,
      spec_team_labels(spec), spec_team_digits(spec)
    ),
    players = top_team_players(
      player_pools[[spec$key]], team, spec$stat_cols, spec$labels, spec$digits
    )
  )
}

teams_json <- lapply(ALL_TEAMS, function(team) {
  categories <- setNames(
    lapply(CATEGORY_SPECS, function(spec) build_position_category(spec, team)),
    POSITION_GROUP_KEYS
  )

  categories$belowReplacement <- list(
    label = "Below-replacement performers",
    description = paste0(
      "Skill players with negative total EPA on at least ",
      MIN_BELOW_REPLACEMENT_PLAYS, " offensive plays, worst EPA first. ",
      "BR Play% is the share of the team's dropbacks, carries and targets those ",
      "players absorbed. Higher composite = fewer below-replacement snaps. Rank 1 = best."
    ),
    statKeys = list("below_replacement_play_pct"),
    playerStatKeys = list("plays", "total_epa"),
    showPlayerRankAndComposite = FALSE,
    showTeamComposite = FALSE,
    team = build_team_category_stats(
      team_below_replacement, team, c("below_replacement_play_pct"),
      below_replacement_team_labels, below_replacement_team_digits
    ),
    players = below_replacement_team_players(team)
  )

  categories$injuries <- list(
    label = "Injury Report",
    description = paste0(
      "Current ESPN injury designations weighted by severity (IR/PUP = 100%, ",
      "out = 90%, doubtful = 75%, questionable = 40%) and by the player's season ",
      "snap share. Higher composite = more injury impact. Rank 1 = most injured."
    ),
    statKeys = list("injured_count", "injury_snap_share"),
    playerStatKeys = list("impact"),
    showPlayerRankAndComposite = FALSE,
    showStatusColumn = TRUE,
    compositeRankingKey = "injuriesComposite",
    team = build_team_category_stats(
      team_injuries, team, c("injured_count", "injury_snap_share"),
      injury_labels, injury_digits
    ),
    players = team_injured_players(team)
  )

  record_row <- team_records %>% filter(team_code == team)
  overall_row <- team_overall %>% filter(team_code == team)

  list(
    teamCode = team,
    teamName = unname(team_names[team]),
    division = unname(team_divisions[team]),
    league = unname(team_conferences[team]),
    wins = if (nrow(record_row) > 0) as.integer(record_row$W[1]) else NULL,
    losses = if (nrow(record_row) > 0) as.integer(record_row$L[1]) else NULL,
    ties = if (nrow(record_row) > 0) as.integer(record_row$T[1]) else NULL,
    recordRank = if (nrow(record_row) > 0 && !is.na(record_row$win_pct_rank[1])) as.integer(record_row$win_pct_rank[1]) else NULL,
    recordRankDisplay = if (nrow(record_row) > 0 && !is.na(record_row$win_pct_rankDisplay[1])) record_row$win_pct_rankDisplay[1] else NULL,
    divisionRank = if (nrow(record_row) > 0) as.integer(record_row$division_rank[1]) else NULL,
    divisionRankDisplay = if (nrow(record_row) > 0) record_row$division_rank_display[1] else NULL,
    overallComposite = if (nrow(overall_row) > 0) round(overall_row$overall_composite[1], 1) else NULL,
    overallCompositeRank = if (nrow(overall_row) > 0) as.integer(overall_row$overall_composite_rank[1]) else NULL,
    overallCompositeRankDisplay = if (nrow(overall_row) > 0) overall_row$overall_composite_rankDisplay[1] else NULL,
    gameLog = build_schedule_for_team(team),
    categoryOrder = as.list(CATEGORY_ORDER),
    categories = categories
  )
})

names(teams_json) <- ALL_TEAMS

# ============================================================================
# League-wide ranking sheets
# ============================================================================
composite_rankings <- setNames(
  lapply(POSITION_GROUP_KEYS, function(key) {
    build_rankings(team_pools[[key]], "team_code", "composite_score",
                   "composite_score_rank", "composite_score_rankDisplay")
  }),
  paste0(POSITION_GROUP_KEYS, "Composite")
)

team_stat_rankings <- do.call(c, lapply(CATEGORY_SPECS, function(spec) {
  build_category_stat_rankings(spec$key, team_pools[[spec$key]], spec_team_stat_cols(spec))
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
  build_category_stat_rankings("injuries", team_injuries, c("injured_count", "injury_snap_share")),
  build_category_stat_rankings("belowReplacement", team_below_replacement, c("below_replacement_play_pct")),
  player_stat_rankings,
  build_player_pool_rankings("belowReplacement", below_replacement, c("plays", "total_epa"))
)

# ============================================================================
# Output JSON
# ============================================================================
phase_tag_label <- switch(
  season_phase,
  PRE = "preseason",
  REG = "regular season",
  POST = "postseason",
  "regular season"
)

phase_note <- switch(
  season_phase,
  PRE = paste0(
    "The ", upcoming_season %||% (nfl_season + 1),
    " season has not kicked off yet, so this card covers the completed ",
    nfl_season, " season."
  ),
  REG = paste0("The ", nfl_season, " regular season is in progress."),
  POST = paste0("The ", nfl_season, " postseason has begun."),
  ""
)

output_data <- list(
  sport = "NFL",
  visualizationType = "NFL_TEAM_REPORT_CARD",
  title = paste0("NFL Team Report Cards - ", season_label),
  subtitle = paste0(
    "Top ", TOP_N, " players per team by position group (sorted by composite score) · ",
    season_phase_label
  ),
  description = paste0(
    "Per-team player report cards from nflverse play-by-play, player stats and ",
    "Pro Football Reference advanced stats. Each team surfaces its top ", TOP_N,
    " players in seven position groups ranked by a composite of 4-5 stats, with ",
    "NFL-wide rank badges and a team row for the whole group.\n\n",
    phase_note, "\n\n",
    "STATS:\n\n",
    " • Composite: Average percentile across a group's stats (100 = best in the ",
    "league on every one). Team composites rank the same stats aggregated over ",
    "the whole position group; the overall composite averages the seven group ",
    "composites. Higher is better.\n\n",
    " • EPA/DB, EPA/Rush, EPA/Tgt: Expected points added per dropback, rush, or ",
    "target. Higher is better.\n\n",
    " • CPOE: Completion percentage over expected. Higher is better.\n\n",
    " • ANY/A: Adjusted net yards per attempt — passing yards plus 20 per ",
    "touchdown, minus 45 per interception and sack yardage, over dropbacks. ",
    "Higher is better.\n\n",
    " • Succ%: Share of plays with positive EPA. Higher is better.\n\n",
    " • Sack%: Share of dropbacks ending in a sack. Lower is better.\n\n",
    " • YPC / 1D% / Scrim Yds: Yards per carry, share of carries producing a ",
    "first down, and total yards from scrimmage. Higher is better.\n\n",
    " • Y/Tgt / Catch% / Tgt%: Receiving yards per target, catch rate, and share ",
    "of team targets. Higher is better.\n\n",
    " • Offensive line: Individual linemen have no public per-snap blocking grade ",
    "in the nflverse feeds, so their table ranks snaps, snap share, games, and ",
    "blocking penalties (fewer is better). The team row measures real line play — ",
    "sack rate and pressure rate allowed, stuff rate (carries gaining nothing), ",
    "yards before contact per rush, and blocking penalties.\n\n",
    " • Prss / QB Hits / TFL / Tkl: Pressures, quarterback hits, tackles for loss, ",
    "and combined tackles. Higher is better.\n\n",
    " • MT%: Missed tackle rate. Lower is better.\n\n",
    " • PD / INT: Passes defensed and interceptions. Higher is better.\n\n",
    " • Cmp% / Y/Tgt / Rtg (secondary): Completion rate, yards per target, and ",
    "passer rating allowed in coverage. Lower is better.\n\n",
    " • Below-replacement performers: Skill players with negative total EPA on at ",
    "least ", MIN_BELOW_REPLACEMENT_PLAYS, " plays. BR Play% is the share of the ",
    "team's offensive plays they absorbed — rank 1 = fewest.\n\n",
    " • Injury Report: ESPN injury designations weighted by severity and by the ",
    "player's season snap share. Rank 1 is the most injured team.\n\n",
    " • Schedule & Results: Every ", nfl_season, " game including the postseason, ",
    "in order. Each row shows the week, the opponent (vs = home, @ = away), both ",
    "scores, and the point differential.\n\n",
    "Player and team stats cover the regular season so rates stay comparable ",
    "across the league."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "nflverse (nflfastR / nflreadr) • Pro Football Reference • ESPN",
  season = nfl_season,
  seasonLabel = season_label,
  seasonPhase = season_phase,
  seasonPhaseLabel = season_phase_label,
  topN = TOP_N,
  categoryOrder = as.list(CATEGORY_ORDER),
  rankings = rankings_json,
  playoffChances = list(),
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
sample_team <- if ("KC" %in% ALL_TEAMS) "KC" else ALL_TEAMS[1]
sample <- output_data$teams[[sample_team]]
cat("\nSample —", sample_team, "record:", sample$wins, "-", sample$losses,
    "| overall composite:", sample$overallComposite,
    "rank:", sample$overallCompositeRank, "\n")
for (key in POSITION_GROUP_KEYS) {
  cat_data <- sample$categories[[key]]
  top <- if (length(cat_data$players) > 0) cat_data$players[[1]]$name else "(none)"
  cat("  ", cat_data$label, "— players:", length(cat_data$players),
      "| top:", top,
      "| team composite:", cat_data$team$stats$aggregate$value %||% NA, "\n")
}
cat("  Schedule games:", length(sample$gameLog$games),
    "| record:", sample$gameLog$record$display,
    "| total diff:", sample$gameLog$totalDifferential, "\n")
cat("Teams with player data:", sum(vapply(teams_json, function(t) {
  any(vapply(t$categories, function(c) length(c$players) > 0, logical(1)))
}, logical(1))), "/", length(teams_json), "\n")
cat("Teams with schedule data:", sum(vapply(teams_json, function(t) {
  length(t$gameLog$games) > 0
}, logical(1))), "/", length(teams_json), "\n")

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
  "prod/nfl__team_report_card.json"
} else {
  "dev/nfl__team_report_card.json"
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

cat("\n=== NFL Team Report Card generation complete ===\n")
