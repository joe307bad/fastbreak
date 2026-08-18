#!/usr/bin/env Rscript

# NFL Matchup Stats generator — the NFL counterpart to mlb__matchup_stats.R.
#
# Emits NFL_MATCHUP: one entry per game in a window around today (recently
# completed games plus the upcoming slate), each carrying full season stat
# comparisons for both teams, head-to-head history, chart series, and — for
# completed games — a team box score, player highlights, and a game-vs-season
# comparison.
#
# Everything comes from the nflverse feeds rather than a per-game ESPN call:
# nflreadr::load_schedules already carries scores, odds, and venue, so this
# script makes no rate-limited HTTP requests and cannot be throttled mid-run.

library(nflreadr)
library(dplyr)
library(tidyr)
library(jsonlite)

options(nflreadr.verbose = FALSE)

# ============================================================================
# Constants
# ============================================================================
DAYS_BEHIND <- 10
DAYS_AHEAD <- 10

# NFL teams meet once or twice a season, so a single season of head-to-head is
# usually one game. Three seasons is enough to be worth reading.
H2H_SEASONS <- 3

# Recent form window, in games. The NFL plays once a week, so the MLB card's
# one-month trend is four games here.
RECENT_FORM_GAMES <- 4

REGULAR_SEASON_WEEKS <- 18
EXPLOSIVE_PASS_YARDS <- 20
EXPLOSIVE_RUSH_YARDS <- 12

# ============================================================================
# Helpers
# ============================================================================
`%||%` <- function(a, b) {
  if (is.null(a) || length(a) == 0) return(b)
  if (is.atomic(a) && length(a) == 1) {
    if (is.na(a)) return(b)
    if (is.character(a) && !nzchar(a)) return(b)
  }
  a
}

safe_num <- function(x) {
  if (is.null(x) || length(x) == 0) return(NA_real_)
  v <- suppressWarnings(as.numeric(x[[1]]))
  if (is.na(v)) NA_real_ else v
}

null_if_na <- function(x, digits = NULL) {
  v <- safe_num(x)
  if (is.na(v)) return(NULL)
  if (!is.null(digits)) v <- round(v, digits)
  v
}

int_or_null <- function(x) {
  v <- safe_num(x)
  if (is.na(v)) return(NULL)
  as.integer(round(v))
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

safe_div <- function(numerator, denominator) {
  ifelse(is.na(denominator) | denominator == 0, NA_real_, numerator / denominator)
}

# nflverse ships the Rams as "LA"; the team roster and every other NFL chart
# here use "LAR", and the app matches pinned teams by code.
normalize_nfl_team <- function(team) {
  code <- as.character(team)
  ifelse(is.na(code), NA_character_, ifelse(code == "LA", "LAR", code))
}

cat("=== NFL Matchup Stats Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")

# ============================================================================
# Team metadata
# ============================================================================
teams_meta <- nflreadr::load_teams() %>%
  transmute(
    team_code = normalize_nfl_team(team_abbr),
    team_id = as.character(team_id),
    team_name = team_name,
    conference = team_conf,
    division = team_division,
    logo = team_logo_espn
  ) %>%
  filter(!is.na(team_code)) %>%
  distinct(team_code, .keep_all = TRUE)

ALL_TEAMS <- teams_meta$team_code
team_meta_row <- function(code) {
  row <- teams_meta %>% filter(team_code == code)
  if (nrow(row) == 0) NULL else row[1, ]
}

cat("Loaded", length(ALL_TEAMS), "NFL teams\n")

# ============================================================================
# Season resolution
# ============================================================================
# Stats always come from the most recent season that has been played. The game
# window may reach into the next season's schedule once it is published, which
# is what keeps this chart populated through the offseason.
load_schedule <- function(season) {
  tryCatch(nflreadr::load_schedules(season), error = function(e) NULL)
}

calendar_season <- {
  y <- as.numeric(format(Sys.Date(), "%Y"))
  m <- as.numeric(format(Sys.Date(), "%m"))
  if (m >= 3) y else y - 1
}

resolve_stats_season <- function() {
  for (season in c(calendar_season, calendar_season - 1, calendar_season - 2)) {
    sched <- load_schedule(season)
    if (!is.null(sched) && any(!is.na(sched$result))) return(season)
  }
  stop("Could not find an NFL season with completed games")
}

stats_season <- resolve_stats_season()
cat("Stats season:", stats_season, "\n")

# Every schedule we might draw games from: the stats season, the one after it
# (published well before kickoff), and enough history for head-to-head.
schedule_seasons <- sort(unique(c(
  seq(stats_season - H2H_SEASONS + 1, stats_season),
  stats_season + 1
)))

schedules_all <- bind_rows(lapply(schedule_seasons, function(season) {
  sched <- load_schedule(season)
  if (is.null(sched)) return(NULL)
  sched %>% mutate(season = season)
})) %>%
  filter(!is.na(home_team), !is.na(away_team)) %>%
  mutate(
    home_code = normalize_nfl_team(home_team),
    away_code = normalize_nfl_team(away_team),
    game_day = suppressWarnings(as.Date(gameday)),
    played = !is.na(result)
  ) %>%
  filter(!is.na(home_code), !is.na(away_code), !is.na(game_day))

cat("Loaded", nrow(schedules_all), "scheduled games across seasons",
    paste(range(schedule_seasons), collapse = "-"), "\n")

# ============================================================================
# Season data used for stats, charts and box scores
# ============================================================================
# Loaded whole so postseason games still get a box score; season rates below
# are computed from the regular season slice only, so they stay comparable.
pbp_all <- tryCatch(
  nflreadr::load_pbp(stats_season),
  error = function(e) {
    cat("Error loading play-by-play:", e$message, "\n")
    stop(e)
  }
)
pbp <- pbp_all %>% filter(season_type == "REG")

weekly_player_stats <- tryCatch(
  nflreadr::load_player_stats(stats_season),
  error = function(e) {
    cat("Warning: could not load weekly player stats:", e$message, "\n")
    NULL
  }
)

cat("Loaded", nrow(pbp), "regular season plays\n")

season_schedule <- schedules_all %>% filter(season == stats_season)

# One row per team per completed regular season game — the spine for records,
# recent form, the chart series, and season highs.
team_game_rows_for <- function(sched) {
  bind_rows(
    sched %>%
      transmute(
        game_id, week, game_day, game_type,
        team = home_code, opponent = away_code, is_home = TRUE,
        points = home_score, points_allowed = away_score
      ),
    sched %>%
      transmute(
        game_id, week, game_day, game_type,
        team = away_code, opponent = home_code, is_home = FALSE,
        points = away_score, points_allowed = home_score
      )
  )
}

team_game_results_all <- team_game_rows_for(season_schedule %>% filter(played)) %>%
  mutate(
    point_diff = points - points_allowed,
    result = case_when(
      points > points_allowed ~ "W",
      points < points_allowed ~ "L",
      TRUE ~ "T"
    )
  )

team_game_results <- team_game_results_all %>%
  filter(game_type == "REG") %>%
  select(-game_type) %>%
  arrange(team, week)

cat("Completed team-game rows:", nrow(team_game_results),
    "(all game types:", nrow(team_game_results_all), ")\n")

# ============================================================================
# Per-game team box scores from play-by-play
# ============================================================================
# Offensive production is attributed to posteam; the same rows are the defensive
# line for defteam, so one aggregation feeds both sides of every matchup.
offense_by_game <- pbp_all %>%
  filter(!is.na(posteam), play_type %in% c("pass", "run")) %>%
  mutate(team = normalize_nfl_team(posteam)) %>%
  group_by(game_id, team) %>%
  summarise(
    plays = n(),
    total_yards = sum(yards_gained, na.rm = TRUE),
    pass_yards = sum(yards_gained[play_type == "pass"], na.rm = TRUE),
    rush_yards = sum(yards_gained[play_type == "run"], na.rm = TRUE),
    epa_total = sum(epa, na.rm = TRUE),
    success_rate = mean(success, na.rm = TRUE) * 100,
    explosive_plays = sum(
      (play_type == "pass" & yards_gained >= EXPLOSIVE_PASS_YARDS) |
        (play_type == "run" & yards_gained >= EXPLOSIVE_RUSH_YARDS),
      na.rm = TRUE
    ),
    sacks_allowed = sum(sack, na.rm = TRUE),
    turnovers = sum(interception, na.rm = TRUE) + sum(fumble_lost, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(yards_per_play = safe_div(total_yards, plays))

# First downs, downs conversions and drive-level context come from every play,
# not just pass/run, so they are aggregated separately.
drives_by_game <- pbp_all %>%
  filter(!is.na(posteam)) %>%
  mutate(team = normalize_nfl_team(posteam)) %>%
  group_by(game_id, team) %>%
  summarise(
    first_downs = sum(first_down, na.rm = TRUE),
    third_down_att = sum(third_down_converted, na.rm = TRUE) + sum(third_down_failed, na.rm = TRUE),
    third_down_conv = sum(third_down_converted, na.rm = TRUE),
    fourth_down_att = sum(fourth_down_converted, na.rm = TRUE) + sum(fourth_down_failed, na.rm = TRUE),
    fourth_down_conv = sum(fourth_down_converted, na.rm = TRUE),
    red_zone_trips = n_distinct(series[yardline_100 <= 20 & !is.na(series)]),
    red_zone_tds = sum(touchdown == 1 & yardline_100 <= 20, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    third_down_pct = safe_div(third_down_conv, third_down_att) * 100,
    red_zone_td_pct = safe_div(red_zone_tds, red_zone_trips) * 100
  )

# Time of possession is a drive attribute; sum one value per drive.
top_by_game <- pbp_all %>%
  filter(!is.na(posteam), !is.na(drive), !is.na(drive_time_of_possession)) %>%
  mutate(team = normalize_nfl_team(posteam)) %>%
  distinct(game_id, team, drive, .keep_all = TRUE) %>%
  mutate(
    top_seconds = {
      parts <- strsplit(as.character(drive_time_of_possession), ":", fixed = TRUE)
      vapply(parts, function(p) {
        if (length(p) < 2) return(NA_real_)
        m <- suppressWarnings(as.numeric(p[1]))
        s <- suppressWarnings(as.numeric(p[2]))
        if (is.na(m) || is.na(s)) NA_real_ else m * 60 + s
      }, numeric(1))
    }
  ) %>%
  group_by(game_id, team) %>%
  summarise(top_seconds = sum(top_seconds, na.rm = TRUE), .groups = "drop")

# Penalties are charged to the penalized team regardless of who had the ball.
penalties_by_game <- pbp_all %>%
  filter(penalty == 1, !is.na(penalty_team)) %>%
  mutate(team = normalize_nfl_team(penalty_team)) %>%
  group_by(game_id, team) %>%
  summarise(
    penalties = n(),
    penalty_yards = sum(abs(penalty_yards), na.rm = TRUE),
    .groups = "drop"
  )

# Sacks recorded by the defense, keyed to the defending team.
defense_by_game <- pbp_all %>%
  filter(!is.na(defteam), play_type %in% c("pass", "run")) %>%
  mutate(team = normalize_nfl_team(defteam)) %>%
  group_by(game_id, team) %>%
  summarise(
    sacks = sum(sack, na.rm = TRUE),
    takeaways = sum(interception, na.rm = TRUE) + sum(fumble_lost, na.rm = TRUE),
    .groups = "drop"
  )

game_box_all <- team_game_results_all %>%
  select(game_id, week, game_type, team, opponent, is_home, points, points_allowed, point_diff, result) %>%
  left_join(offense_by_game, by = c("game_id", "team")) %>%
  left_join(drives_by_game, by = c("game_id", "team")) %>%
  left_join(top_by_game, by = c("game_id", "team")) %>%
  left_join(penalties_by_game, by = c("game_id", "team")) %>%
  left_join(defense_by_game, by = c("game_id", "team")) %>%
  mutate(
    penalties = coalesce(penalties, 0),
    penalty_yards = coalesce(penalty_yards, 0),
    sacks = coalesce(sacks, 0),
    takeaways = coalesce(takeaways, 0)
  )

# Season rates and season highs read the regular season slice; per-game box
# score lookups read every completed game, postseason included.
game_box <- game_box_all %>% filter(game_type == "REG") %>% select(-game_type)

cat("Built per-game box scores for", nrow(game_box_all), "team-games (",
    nrow(game_box), "regular season )\n")

# ============================================================================
# Season team stats + league ranks
# ============================================================================
# Season rates are the sum of the per-game box scores rather than a separate
# feed, so a team's season line and its game lines can never disagree.
team_season <- game_box %>%
  group_by(team) %>%
  summarise(
    games_played = n(),
    wins = sum(result == "W"),
    losses = sum(result == "L"),
    ties = sum(result == "T"),
    points_per_game = mean(points, na.rm = TRUE),
    points_allowed_per_game = mean(points_allowed, na.rm = TRUE),
    point_diff_per_game = mean(point_diff, na.rm = TRUE),
    yards_per_game = mean(total_yards, na.rm = TRUE),
    pass_yards_per_game = mean(pass_yards, na.rm = TRUE),
    rush_yards_per_game = mean(rush_yards, na.rm = TRUE),
    yards_per_play = safe_div(sum(total_yards, na.rm = TRUE), sum(plays, na.rm = TRUE)),
    off_epa_per_play = safe_div(sum(epa_total, na.rm = TRUE), sum(plays, na.rm = TRUE)),
    off_success_rate = mean(success_rate, na.rm = TRUE),
    explosive_per_game = mean(explosive_plays, na.rm = TRUE),
    first_downs_per_game = mean(first_downs, na.rm = TRUE),
    third_down_pct = safe_div(sum(third_down_conv, na.rm = TRUE), sum(third_down_att, na.rm = TRUE)) * 100,
    fourth_down_pct = safe_div(sum(fourth_down_conv, na.rm = TRUE), sum(fourth_down_att, na.rm = TRUE)) * 100,
    red_zone_td_pct = safe_div(sum(red_zone_tds, na.rm = TRUE), sum(red_zone_trips, na.rm = TRUE)) * 100,
    turnovers_per_game = mean(turnovers, na.rm = TRUE),
    takeaways_per_game = mean(takeaways, na.rm = TRUE),
    sacks_allowed_per_game = mean(sacks_allowed, na.rm = TRUE),
    sacks_per_game = mean(sacks, na.rm = TRUE),
    penalties_per_game = mean(penalties, na.rm = TRUE),
    penalty_yards_per_game = mean(penalty_yards, na.rm = TRUE),
    time_of_possession = mean(top_seconds, na.rm = TRUE) / 60,
    .groups = "drop"
  ) %>%
  mutate(
    win_pct = safe_div(wins + 0.5 * ties, games_played),
    turnover_diff_per_game = takeaways_per_game - turnovers_per_game
  )

# The defensive side of the ledger is the opponents' offensive line, so it is
# derived by joining each team's games to what the other team did in them.
opponent_season <- game_box %>%
  select(game_id, team = opponent, opp_team = team, plays, total_yards,
         pass_yards, rush_yards, epa_total, success_rate, third_down_conv,
         third_down_att, red_zone_tds, red_zone_trips) %>%
  group_by(team) %>%
  summarise(
    yards_allowed_per_game = mean(total_yards, na.rm = TRUE),
    pass_yards_allowed_per_game = mean(pass_yards, na.rm = TRUE),
    rush_yards_allowed_per_game = mean(rush_yards, na.rm = TRUE),
    yards_allowed_per_play = safe_div(sum(total_yards, na.rm = TRUE), sum(plays, na.rm = TRUE)),
    def_epa_per_play = safe_div(sum(epa_total, na.rm = TRUE), sum(plays, na.rm = TRUE)),
    def_success_rate_allowed = mean(success_rate, na.rm = TRUE),
    third_down_pct_allowed = safe_div(sum(third_down_conv, na.rm = TRUE), sum(third_down_att, na.rm = TRUE)) * 100,
    red_zone_td_pct_allowed = safe_div(sum(red_zone_tds, na.rm = TRUE), sum(red_zone_trips, na.rm = TRUE)) * 100,
    .groups = "drop"
  )

team_stats <- team_season %>%
  left_join(opponent_season, by = "team") %>%
  left_join(
    teams_meta %>% select(team = team_code, division, conference),
    by = "team"
  ) %>%
  mutate(net_epa_per_play = off_epa_per_play - def_epa_per_play)

HIGHER_BETTER_STATS <- c(
  "points_per_game", "point_diff_per_game", "yards_per_game", "pass_yards_per_game",
  "rush_yards_per_game", "yards_per_play", "off_epa_per_play", "off_success_rate",
  "explosive_per_game", "first_downs_per_game", "third_down_pct", "fourth_down_pct",
  "red_zone_td_pct", "takeaways_per_game", "sacks_per_game", "time_of_possession",
  "turnover_diff_per_game", "net_epa_per_play", "win_pct"
)

LOWER_BETTER_STATS <- c(
  "points_allowed_per_game", "yards_allowed_per_game", "pass_yards_allowed_per_game",
  "rush_yards_allowed_per_game", "yards_allowed_per_play", "def_epa_per_play",
  "def_success_rate_allowed", "third_down_pct_allowed", "red_zone_td_pct_allowed",
  "turnovers_per_game", "sacks_allowed_per_game", "penalties_per_game",
  "penalty_yards_per_game"
)

for (stat in HIGHER_BETTER_STATS) team_stats <- rank_and_assign(team_stats, stat)
for (stat in LOWER_BETTER_STATS) team_stats <- rank_and_assign(team_stats, stat, lower_better = TRUE)

cat("Computed season stats and ranks for", nrow(team_stats), "teams\n")

# ============================================================================
# Recent form (last N games)
# ============================================================================
recent_form <- game_box %>%
  group_by(team) %>%
  arrange(week, .by_group = TRUE) %>%
  slice_tail(n = RECENT_FORM_GAMES) %>%
  summarise(
    games_played = n(),
    wins = sum(result == "W"),
    losses = sum(result == "L"),
    ties = sum(result == "T"),
    points_per_game = mean(points, na.rm = TRUE),
    points_allowed_per_game = mean(points_allowed, na.rm = TRUE),
    point_diff_per_game = mean(point_diff, na.rm = TRUE),
    yards_per_game = mean(total_yards, na.rm = TRUE),
    turnover_diff_per_game = mean(takeaways - turnovers, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(win_pct = safe_div(wins + 0.5 * ties, games_played))

for (stat in c("win_pct", "points_per_game", "point_diff_per_game",
               "yards_per_game", "turnover_diff_per_game")) {
  recent_form <- rank_and_assign(recent_form, stat)
}
recent_form <- rank_and_assign(recent_form, "points_allowed_per_game", lower_better = TRUE)

cat("Computed", RECENT_FORM_GAMES, "game recent form for", nrow(recent_form), "teams\n")

# ============================================================================
# Chart series: cumulative point differential + weekly performance
# ============================================================================
cum_point_diff <- team_game_results %>%
  arrange(team, week) %>%
  group_by(team) %>%
  mutate(cum_point_diff = cumsum(point_diff)) %>%
  ungroup() %>%
  group_by(team, week) %>%
  summarise(cum_point_diff = last(cum_point_diff), .groups = "drop")

weekly_performance <- team_game_results %>%
  group_by(team, week) %>%
  summarise(
    points_scored = mean(points, na.rm = TRUE),
    points_allowed = mean(points_allowed, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  arrange(week)

league_cum_point_diff_stats <- list(minCumPointDiff = NULL, maxCumPointDiff = NULL)
if (nrow(cum_point_diff) > 0) {
  # The 10th-best cumulative differential each week draws the "top 10" reference
  # line on the chart, the same way the MLB card marks the playoff-pace cutoff.
  top10_by_week <- cum_point_diff %>%
    group_by(week) %>%
    summarise(
      top10_threshold = if (n() >= 10) sort(cum_point_diff, decreasing = TRUE)[10] else NA_real_,
      .groups = "drop"
    ) %>%
    filter(!is.na(top10_threshold))

  league_cum_point_diff_stats <- list(
    minCumPointDiff = round(min(cum_point_diff$cum_point_diff, na.rm = TRUE)),
    maxCumPointDiff = round(max(cum_point_diff$cum_point_diff, na.rm = TRUE)),
    # Omitted rather than emitted empty: jsonlite writes a zero-length named
    # list as [] and the client field is typed as an object.
    top10ByWeek = if (nrow(top10_by_week) > 0) {
      setNames(as.list(round(top10_by_week$top10_threshold)), paste0("week-", top10_by_week$week))
    } else {
      NULL
    }
  )
}

league_weekly_stats <- list()
if (nrow(weekly_performance) > 0) {
  league_weekly_stats <- list(
    avgPointsScored = round(mean(weekly_performance$points_scored, na.rm = TRUE), 2),
    avgPointsAllowed = round(mean(weekly_performance$points_allowed, na.rm = TRUE), 2),
    minPointsScored = round(min(weekly_performance$points_scored, na.rm = TRUE), 2),
    maxPointsScored = round(max(weekly_performance$points_scored, na.rm = TRUE), 2),
    minPointsAllowed = round(min(weekly_performance$points_allowed, na.rm = TRUE), 2),
    maxPointsAllowed = round(max(weekly_performance$points_allowed, na.rm = TRUE), 2)
  )
}

cat("Built chart series for", n_distinct(cum_point_diff$team), "teams\n")

# ============================================================================
# Head-to-head across the last H2H_SEASONS seasons
# ============================================================================
h2h_pool <- schedules_all %>%
  filter(played, season >= stats_season - H2H_SEASONS + 1) %>%
  transmute(
    season, week, game_type,
    date = format(game_day, "%Y-%m-%d"),
    home_team = home_code, away_team = away_code,
    home_score = as.integer(home_score), away_score = as.integer(away_score)
  ) %>%
  mutate(
    winner = case_when(
      home_score > away_score ~ home_team,
      away_score > home_score ~ away_team,
      TRUE ~ NA_character_
    )
  ) %>%
  arrange(date)

# Grouped by season rather than by MLB's date-adjacent series: NFL opponents meet
# once or twice a year, so the season is the natural unit.
build_h2h <- function(team_a, team_b) {
  games <- h2h_pool %>%
    filter(
      (home_team == team_a & away_team == team_b) |
        (home_team == team_b & away_team == team_a)
    )
  if (nrow(games) == 0) return(NULL)

  a_wins <- sum(games$winner == team_a, na.rm = TRUE)
  b_wins <- sum(games$winner == team_b, na.rm = TRUE)

  series_list <- lapply(sort(unique(games$season)), function(season) {
    sg <- games %>% filter(season == !!season)
    list(
      dateRange = paste0(season, " season"),
      startDate = min(sg$date),
      endDate = max(sg$date),
      teamAWins = as.integer(sum(sg$winner == team_a, na.rm = TRUE)),
      teamBWins = as.integer(sum(sg$winner == team_b, na.rm = TRUE)),
      games = lapply(seq_len(nrow(sg)), function(i) {
        row <- sg[i, ]
        list(
          date = row$date,
          week = as.integer(row$week),
          seasonType = row$game_type,
          homeTeam = row$home_team,
          awayTeam = row$away_team,
          homeScore = row$home_score,
          awayScore = row$away_score,
          winner = row$winner %||% "TIE"
        )
      })
    )
  })

  list(
    teamA = team_a,
    teamB = team_b,
    teamAWins = as.integer(a_wins),
    teamBWins = as.integer(b_wins),
    totalGames = as.integer(nrow(games)),
    series = series_list
  )
}

# ============================================================================
# Season highs (prior-best before each game, so a game can set a new one)
# ============================================================================
season_high_cols <- c(
  points = "points", totalYards = "total_yards",
  passYards = "pass_yards", rushYards = "rush_yards"
)

season_high_table <- game_box %>% arrange(team, week)

check_season_highs <- function(team_code, game_id) {
  rows <- season_high_table %>% filter(team == team_code)
  if (nrow(rows) == 0) return(NULL)
  idx <- which(rows$game_id == game_id)
  if (length(idx) == 0) return(NULL)
  i <- idx[1]
  if (i == 1) return(NULL)

  highs <- list()
  for (out_key in names(season_high_cols)) {
    col <- season_high_cols[[out_key]]
    value <- safe_num(rows[[col]][i])
    prior <- suppressWarnings(max(rows[[col]][seq_len(i - 1)], na.rm = TRUE))
    if (is.na(value) || !is.finite(prior)) next
    if (value > prior) {
      highs[[out_key]] <- list(
        previousHigh = round(prior, 1),
        differential = round(value - prior, 1)
      )
    }
  }
  if (length(highs) == 0) NULL else highs
}

# ============================================================================
# Player highlights for completed games
# ============================================================================
# Postseason weeks are kept: week numbers do not collide with the regular
# season (19-22), so a playoff game still gets its player highlights.
player_week_stats <- if (is.null(weekly_player_stats)) {
  NULL
} else {
  weekly_player_stats %>%
    mutate(
      team_code = normalize_nfl_team(team),
      player_name = coalesce(player_display_name, player_name)
    ) %>%
    filter(!is.na(team_code))
}

build_player_highlights <- function(team_code, week_num) {
  if (is.null(player_week_stats)) return(NULL)
  rows <- player_week_stats %>% filter(team_code == !!team_code, week == week_num)
  if (nrow(rows) == 0) return(NULL)

  qb_row <- rows %>%
    filter(coalesce(attempts, 0) > 0) %>%
    arrange(desc(attempts)) %>%
    head(1)

  quarterback <- if (nrow(qb_row) == 0) NULL else list(
    playerId = as.character(qb_row$player_id[1]),
    name = as.character(qb_row$player_name[1]),
    position = as.character(qb_row$position[1]),
    completions = int_or_null(qb_row$completions[1]),
    attempts = int_or_null(qb_row$attempts[1]),
    passYards = int_or_null(qb_row$passing_yards[1]),
    passTds = int_or_null(qb_row$passing_tds[1]),
    interceptions = int_or_null(qb_row$passing_interceptions[1]),
    sacksTaken = int_or_null(qb_row$sacks_suffered[1]),
    rushYards = int_or_null(qb_row$rushing_yards[1]),
    rushTds = int_or_null(qb_row$rushing_tds[1]),
    epa = null_if_na(qb_row$passing_epa[1], 1),
    cpoe = null_if_na(qb_row$passing_cpoe[1], 1)
  )

  # Quarterbacks are excluded here — they already have their own block, and a
  # scrambling QB otherwise pushes the actual lead back off the list.
  rusher_rows <- rows %>%
    filter(coalesce(carries, 0) > 0, position != "QB") %>%
    arrange(desc(rushing_yards), desc(carries)) %>%
    head(2)
  rushers <- lapply(seq_len(nrow(rusher_rows)), function(i) {
    r <- rusher_rows[i, ]
    list(
      playerId = as.character(r$player_id),
      name = as.character(r$player_name),
      position = as.character(r$position),
      carries = int_or_null(r$carries),
      rushYards = int_or_null(r$rushing_yards),
      rushTds = int_or_null(r$rushing_tds),
      yardsPerCarry = null_if_na(safe_div(r$rushing_yards, r$carries), 1),
      receptions = int_or_null(r$receptions),
      recYards = int_or_null(r$receiving_yards),
      epa = null_if_na(r$rushing_epa, 1)
    )
  })

  receiver_rows <- rows %>%
    filter(coalesce(targets, 0) > 0) %>%
    arrange(desc(receiving_yards), desc(receptions)) %>%
    head(3)
  receivers <- lapply(seq_len(nrow(receiver_rows)), function(i) {
    r <- receiver_rows[i, ]
    list(
      playerId = as.character(r$player_id),
      name = as.character(r$player_name),
      position = as.character(r$position),
      targets = int_or_null(r$targets),
      receptions = int_or_null(r$receptions),
      recYards = int_or_null(r$receiving_yards),
      recTds = int_or_null(r$receiving_tds),
      yardsPerReception = null_if_na(safe_div(r$receiving_yards, r$receptions), 1),
      epa = null_if_na(r$receiving_epa, 1)
    )
  })

  # Weighted so a sack or a takeaway outranks a pile of routine tackles.
  defender_rows <- rows %>%
    mutate(
      tackles = coalesce(def_tackles_solo, 0) + coalesce(def_tackle_assists, 0),
      def_score = coalesce(def_sacks, 0) * 3 +
        coalesce(def_tackles_for_loss, 0) * 2 +
        coalesce(def_interceptions, 0) * 4 +
        coalesce(def_pass_defended, 0) * 1.5 +
        coalesce(def_fumbles_forced, 0) * 3 +
        tackles * 0.5
    ) %>%
    filter(def_score > 0) %>%
    arrange(desc(def_score)) %>%
    head(3)
  defenders <- lapply(seq_len(nrow(defender_rows)), function(i) {
    r <- defender_rows[i, ]
    list(
      playerId = as.character(r$player_id),
      name = as.character(r$player_name),
      position = as.character(r$position),
      tackles = int_or_null(r$tackles),
      sacks = null_if_na(r$def_sacks, 1),
      tacklesForLoss = int_or_null(r$def_tackles_for_loss),
      interceptions = int_or_null(r$def_interceptions),
      passesDefensed = int_or_null(r$def_pass_defended),
      forcedFumbles = int_or_null(r$def_fumbles_forced),
      qbHits = int_or_null(r$def_qb_hits)
    )
  })

  list(
    quarterback = quarterback,
    rushers = rushers,
    receivers = receivers,
    defenders = defenders
  )
}

# ============================================================================
# Game window
# ============================================================================
today <- Sys.Date()
window_start <- today - DAYS_BEHIND
window_end <- today + DAYS_AHEAD

selected_games <- schedules_all %>%
  filter(game_day >= window_start, game_day <= window_end) %>%
  arrange(game_day, gametime)

window_mode <- "window"

# In the offseason the live window is empty. Rather than publish an empty chart
# for months, fall back to the last week that was played plus the next week that
# is scheduled — the results a reader still cares about, and the slate ahead.
if (nrow(selected_games) == 0) {
  window_mode <- "fallback"
  last_played <- schedules_all %>%
    filter(played) %>%
    arrange(desc(game_day)) %>%
    head(1)
  next_scheduled <- schedules_all %>%
    filter(!played, game_day >= today) %>%
    arrange(game_day) %>%
    head(1)

  parts <- list()
  if (nrow(last_played) > 0) {
    parts[[length(parts) + 1]] <- schedules_all %>%
      filter(season == last_played$season[1], week == last_played$week[1],
             game_type == last_played$game_type[1])
  }
  if (nrow(next_scheduled) > 0) {
    parts[[length(parts) + 1]] <- schedules_all %>%
      filter(season == next_scheduled$season[1], week == next_scheduled$week[1],
             game_type == next_scheduled$game_type[1])
  }
  selected_games <- bind_rows(parts) %>%
    distinct(game_id, .keep_all = TRUE) %>%
    arrange(game_day, gametime)
}

cat("Selected", nrow(selected_games), "games (", window_mode, "mode )\n")

if (nrow(selected_games) == 0) {
  cat("No games to publish. Exiting.\n")
  quit(save = "no", status = 0)
}

# ============================================================================
# Stat catalog — drives both the team stat block and the side-by-side view
# ============================================================================
OFFENSE_STATS <- list(
  list(key = "pointsPerGame", col = "points_per_game", label = "Points/Game", digits = 1),
  list(key = "yardsPerGame", col = "yards_per_game", label = "Yards/Game", digits = 1),
  list(key = "passYardsPerGame", col = "pass_yards_per_game", label = "Pass Yards/Game", digits = 1),
  list(key = "rushYardsPerGame", col = "rush_yards_per_game", label = "Rush Yards/Game", digits = 1),
  list(key = "yardsPerPlay", col = "yards_per_play", label = "Yards/Play", digits = 2),
  list(key = "offEpaPerPlay", col = "off_epa_per_play", label = "Off EPA/Play", digits = 3),
  list(key = "offSuccessRate", col = "off_success_rate", label = "Off Success %", digits = 1),
  list(key = "firstDownsPerGame", col = "first_downs_per_game", label = "1st Downs/Game", digits = 1),
  list(key = "thirdDownPct", col = "third_down_pct", label = "3rd Down %", digits = 1),
  list(key = "fourthDownPct", col = "fourth_down_pct", label = "4th Down %", digits = 1),
  list(key = "redZoneTdPct", col = "red_zone_td_pct", label = "Red Zone TD %", digits = 1),
  list(key = "explosivePerGame", col = "explosive_per_game", label = "Explosive/Game", digits = 1),
  list(key = "turnoversPerGame", col = "turnovers_per_game", label = "Turnovers/Game", digits = 2),
  list(key = "sacksAllowedPerGame", col = "sacks_allowed_per_game", label = "Sacks Allowed/Game", digits = 2),
  list(key = "timeOfPossession", col = "time_of_possession", label = "Time of Poss (min)", digits = 1)
)

DEFENSE_STATS <- list(
  list(key = "pointsAllowedPerGame", col = "points_allowed_per_game", label = "Points Allowed/Game", digits = 1),
  list(key = "yardsAllowedPerGame", col = "yards_allowed_per_game", label = "Yards Allowed/Game", digits = 1),
  list(key = "passYardsAllowedPerGame", col = "pass_yards_allowed_per_game", label = "Pass Yards Allowed/Game", digits = 1),
  list(key = "rushYardsAllowedPerGame", col = "rush_yards_allowed_per_game", label = "Rush Yards Allowed/Game", digits = 1),
  list(key = "yardsAllowedPerPlay", col = "yards_allowed_per_play", label = "Yards Allowed/Play", digits = 2),
  list(key = "defEpaPerPlay", col = "def_epa_per_play", label = "Def EPA/Play", digits = 3),
  list(key = "defSuccessRateAllowed", col = "def_success_rate_allowed", label = "Success % Allowed", digits = 1),
  list(key = "thirdDownPctAllowed", col = "third_down_pct_allowed", label = "3rd Down % Allowed", digits = 1),
  list(key = "redZoneTdPctAllowed", col = "red_zone_td_pct_allowed", label = "Red Zone TD % Allowed", digits = 1),
  list(key = "takeawaysPerGame", col = "takeaways_per_game", label = "Takeaways/Game", digits = 2),
  list(key = "sacksPerGame", col = "sacks_per_game", label = "Sacks/Game", digits = 2)
)

OVERALL_STATS <- list(
  list(key = "pointDiffPerGame", col = "point_diff_per_game", label = "Point Diff/Game", digits = 1),
  list(key = "netEpaPerPlay", col = "net_epa_per_play", label = "Net EPA/Play", digits = 3),
  list(key = "turnoverDiffPerGame", col = "turnover_diff_per_game", label = "Turnover Diff/Game", digits = 2),
  list(key = "penaltiesPerGame", col = "penalties_per_game", label = "Penalties/Game", digits = 1),
  list(key = "penaltyYardsPerGame", col = "penalty_yards_per_game", label = "Penalty Yards/Game", digits = 1)
)

ALL_STAT_SPECS <- c(OFFENSE_STATS, DEFENSE_STATS, OVERALL_STATS)

stat_val <- function(row, col, digits = 3) {
  if (is.null(row) || !col %in% names(row)) {
    return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  }
  value <- safe_num(row[[col]])
  if (is.na(value)) return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  rank_val <- if (paste0(col, "_rank") %in% names(row)) row[[paste0(col, "_rank")]] else NA
  rank_display <- if (paste0(col, "_rankDisplay") %in% names(row)) row[[paste0(col, "_rankDisplay")]] else NA
  list(
    value = round(value, digits),
    rank = if (length(rank_val) == 0 || is.na(rank_val)) NULL else as.integer(rank_val[[1]]),
    rankDisplay = if (length(rank_display) == 0 || is.na(rank_display)) NULL else as.character(rank_display[[1]])
  )
}

build_stat_block <- function(row) {
  if (is.null(row)) return(NULL)
  stats <- list(gamesPlayed = as.integer(row$games_played[1]))
  for (spec in ALL_STAT_SPECS) {
    stats[[spec$key]] <- stat_val(row, spec$col, spec$digits)
  }
  stats
}

build_side_by_side <- function(home_row, away_row) {
  make_group <- function(specs) {
    setNames(
      lapply(specs, function(spec) {
        list(
          label = spec$label,
          home = stat_val(home_row, spec$col, spec$digits),
          away = stat_val(away_row, spec$col, spec$digits)
        )
      }),
      vapply(specs, function(spec) spec$key, character(1))
    )
  }
  list(
    offense = make_group(OFFENSE_STATS),
    defense = make_group(DEFENSE_STATS),
    overall = make_group(OVERALL_STATS)
  )
}

# Offense-vs-defense pairings: each maps one team's offensive rate against the
# stat the opponent's defense gives up, so the advantage is a real comparison
# rather than two unrelated league ranks.
OFF_VS_DEF_PAIRS <- list(
  list(key = "points", off = "points_per_game", def = "points_allowed_per_game",
       offLabel = "Points/Game", defLabel = "Points Allowed/Game", digits = 1),
  list(key = "yards", off = "yards_per_game", def = "yards_allowed_per_game",
       offLabel = "Yards/Game", defLabel = "Yards Allowed/Game", digits = 1),
  list(key = "pass", off = "pass_yards_per_game", def = "pass_yards_allowed_per_game",
       offLabel = "Pass Yards/Game", defLabel = "Pass Yards Allowed/Game", digits = 1),
  list(key = "rush", off = "rush_yards_per_game", def = "rush_yards_allowed_per_game",
       offLabel = "Rush Yards/Game", defLabel = "Rush Yards Allowed/Game", digits = 1),
  list(key = "epa", off = "off_epa_per_play", def = "def_epa_per_play",
       offLabel = "Off EPA/Play", defLabel = "Def EPA/Play", digits = 3),
  list(key = "success", off = "off_success_rate", def = "def_success_rate_allowed",
       offLabel = "Off Success %", defLabel = "Success % Allowed", digits = 1),
  list(key = "thirdDown", off = "third_down_pct", def = "third_down_pct_allowed",
       offLabel = "3rd Down %", defLabel = "3rd Down % Allowed", digits = 1),
  list(key = "redZone", off = "red_zone_td_pct", def = "red_zone_td_pct_allowed",
       offLabel = "Red Zone TD %", defLabel = "Red Zone TD % Allowed", digits = 1),
  list(key = "protection", off = "sacks_allowed_per_game", def = "sacks_per_game",
       offLabel = "Sacks Allowed/Game", defLabel = "Sacks/Game", digits = 2),
  list(key = "ballSecurity", off = "turnovers_per_game", def = "takeaways_per_game",
       offLabel = "Turnovers/Game", defLabel = "Takeaways/Game", digits = 2)
)

# Both sides are already ranked best-to-worst within their own stat, so the
# better rank wins regardless of whether the underlying stat is high- or
# low-is-better. -1 = offense has the edge, 1 = defense does.
calc_advantage <- function(off_rank, def_rank) {
  if (is.null(off_rank) || is.null(def_rank)) return(0L)
  if (off_rank < def_rank) return(-1L)
  if (off_rank > def_rank) return(1L)
  0L
}

build_off_vs_def <- function(off_team, def_team, off_row, def_row) {
  setNames(
    lapply(OFF_VS_DEF_PAIRS, function(pair) {
      off_v <- stat_val(off_row, pair$off, pair$digits)
      def_v <- stat_val(def_row, pair$def, pair$digits)
      list(
        statKey = pair$key,
        offLabel = pair$offLabel,
        defLabel = pair$defLabel,
        offense = list(team = off_team, value = off_v$value, rank = off_v$rank, rankDisplay = off_v$rankDisplay),
        defense = list(team = def_team, value = def_v$value, rank = def_v$rank, rankDisplay = def_v$rankDisplay),
        advantage = calc_advantage(off_v$rank, def_v$rank)
      )
    }),
    vapply(OFF_VS_DEF_PAIRS, function(pair) pair$key, character(1))
  )
}

# ============================================================================
# Per-team payload pieces
# ============================================================================
build_recent_form <- function(team_code) {
  row <- recent_form %>% filter(team == team_code)
  if (nrow(row) == 0) return(setNames(list(), character(0)))
  row <- row[1, ]
  entry <- function(col, digits) {
    list(
      value = null_if_na(row[[col]], digits),
      rank = int_or_null(row[[paste0(col, "_rank")]]),
      rankDisplay = as.character(row[[paste0(col, "_rankDisplay")]])
    )
  }
  list(
    gamesPlayed = as.integer(row$games_played),
    record = list(
      wins = as.integer(row$wins),
      losses = as.integer(row$losses),
      ties = as.integer(row$ties),
      rank = int_or_null(row$win_pct_rank),
      rankDisplay = as.character(row$win_pct_rankDisplay)
    ),
    pointsPerGame = entry("points_per_game", 1),
    pointsAllowedPerGame = entry("points_allowed_per_game", 1),
    pointDiffPerGame = entry("point_diff_per_game", 1),
    yardsPerGame = entry("yards_per_game", 1),
    turnoverDiffPerGame = entry("turnover_diff_per_game", 2)
  )
}

build_cum_point_diff <- function(team_code) {
  rows <- cum_point_diff %>% filter(team == team_code) %>% arrange(week)
  if (nrow(rows) == 0) return(setNames(list(), character(0)))
  setNames(as.list(round(rows$cum_point_diff)), paste0("week-", rows$week))
}

build_performance_by_week <- function(team_code) {
  rows <- weekly_performance %>% filter(team == team_code) %>% arrange(week)
  if (nrow(rows) == 0) return(setNames(list(), character(0)))
  setNames(
    lapply(seq_len(nrow(rows)), function(i) {
      list(
        pointsScored = round(rows$points_scored[i], 2),
        pointsAllowed = round(rows$points_allowed[i], 2)
      )
    }),
    paste0("week-", rows$week)
  )
}

format_record <- function(w, l, t) {
  if (is.na(w) || is.na(l)) return(NULL)
  if (!is.na(t) && t > 0) paste0(w, "-", l, "-", t) else paste0(w, "-", l)
}

team_record_string <- function(team_code) {
  row <- team_stats %>% filter(team == team_code)
  if (nrow(row) == 0) return(NULL)
  format_record(row$wins[1], row$losses[1], row$ties[1])
}

build_team_info <- function(team_code, stats_row) {
  meta <- team_meta_row(team_code)
  stats <- build_stat_block(stats_row)
  if (!is.null(stats)) {
    stats$recentForm <- build_recent_form(team_code)
    stats$cumPointDiffByWeek <- build_cum_point_diff(team_code)
    stats$performanceByWeek <- build_performance_by_week(team_code)
  }
  list(
    id = if (is.null(meta)) team_code else meta$team_id,
    name = if (is.null(meta)) team_code else meta$team_name,
    abbreviation = team_code,
    logo = if (is.null(meta)) NULL else meta$logo,
    record = team_record_string(team_code),
    division = if (is.null(meta)) NULL else meta$division,
    conference = if (is.null(meta)) NULL else meta$conference,
    stats = stats
  )
}

# ============================================================================
# Completed game results
# ============================================================================
BOX_SCORE_FIELDS <- list(
  points = list(col = "points", digits = 0),
  totalYards = list(col = "total_yards", digits = 0),
  passYards = list(col = "pass_yards", digits = 0),
  rushYards = list(col = "rush_yards", digits = 0),
  plays = list(col = "plays", digits = 0),
  yardsPerPlay = list(col = "yards_per_play", digits = 2),
  firstDowns = list(col = "first_downs", digits = 0),
  thirdDownConv = list(col = "third_down_conv", digits = 0),
  thirdDownAtt = list(col = "third_down_att", digits = 0),
  thirdDownPct = list(col = "third_down_pct", digits = 1),
  fourthDownConv = list(col = "fourth_down_conv", digits = 0),
  fourthDownAtt = list(col = "fourth_down_att", digits = 0),
  redZoneTrips = list(col = "red_zone_trips", digits = 0),
  redZoneTds = list(col = "red_zone_tds", digits = 0),
  turnovers = list(col = "turnovers", digits = 0),
  takeaways = list(col = "takeaways", digits = 0),
  sacksAllowed = list(col = "sacks_allowed", digits = 0),
  sacks = list(col = "sacks", digits = 0),
  penalties = list(col = "penalties", digits = 0),
  penaltyYards = list(col = "penalty_yards", digits = 0),
  explosivePlays = list(col = "explosive_plays", digits = 0),
  epa = list(col = "epa_total", digits = 2),
  successRate = list(col = "success_rate", digits = 1)
)

build_box_score <- function(team_code, game_id) {
  row <- game_box_all %>% filter(team == team_code, game_id == !!game_id)
  if (nrow(row) == 0) return(NULL)
  row <- row[1, ]
  box <- lapply(BOX_SCORE_FIELDS, function(spec) null_if_na(row[[spec$col]], spec$digits))
  box$timeOfPossession <- null_if_na(row$top_seconds / 60, 1)
  box
}

# Game line vs the team's season per-game rate for the same stat.
VS_AVG_FIELDS <- list(
  points = list(game = "points", season = "points_per_game", digits = 1),
  totalYards = list(game = "total_yards", season = "yards_per_game", digits = 1),
  passYards = list(game = "pass_yards", season = "pass_yards_per_game", digits = 1),
  rushYards = list(game = "rush_yards", season = "rush_yards_per_game", digits = 1),
  yardsPerPlay = list(game = "yards_per_play", season = "yards_per_play", digits = 2),
  firstDowns = list(game = "first_downs", season = "first_downs_per_game", digits = 1),
  thirdDownPct = list(game = "third_down_pct", season = "third_down_pct", digits = 1),
  turnovers = list(game = "turnovers", season = "turnovers_per_game", digits = 2),
  takeaways = list(game = "takeaways", season = "takeaways_per_game", digits = 2),
  sacksAllowed = list(game = "sacks_allowed", season = "sacks_allowed_per_game", digits = 2),
  sacks = list(game = "sacks", season = "sacks_per_game", digits = 2),
  penalties = list(game = "penalties", season = "penalties_per_game", digits = 1),
  penaltyYards = list(game = "penalty_yards", season = "penalty_yards_per_game", digits = 1),
  explosivePlays = list(game = "explosive_plays", season = "explosive_per_game", digits = 1),
  successRate = list(game = "success_rate", season = "off_success_rate", digits = 1),
  timeOfPossession = list(game = "top_minutes", season = "time_of_possession", digits = 1)
)

build_vs_season_avg <- function(team_code, game_id) {
  game_row <- game_box_all %>% filter(team == team_code, game_id == !!game_id)
  season_row <- team_stats %>% filter(team == team_code)
  if (nrow(game_row) == 0 || nrow(season_row) == 0) return(NULL)
  game_row <- game_row[1, ]
  game_row$top_minutes <- game_row$top_seconds / 60
  season_row <- season_row[1, ]

  out <- lapply(VS_AVG_FIELDS, function(spec) {
    game_val <- safe_num(game_row[[spec$game]])
    season_val <- safe_num(season_row[[spec$season]])
    if (is.na(game_val) || is.na(season_val)) return(NULL)
    list(
      gameValue = round(game_val, spec$digits),
      seasonAvg = round(season_val, spec$digits),
      difference = round(game_val - season_val, spec$digits)
    )
  })
  if (all(vapply(out, is.null, logical(1)))) NULL else out
}

build_results <- function(game) {
  home_score <- as.integer(game$home_score)
  away_score <- as.integer(game$away_score)
  home_won <- home_score > away_score
  winner <- if (home_score == away_score) {
    "TIE"
  } else if (home_won) {
    game$home_code
  } else {
    game$away_code
  }

  results <- list(
    homeScore = home_score,
    awayScore = away_score,
    winner = winner,
    margin = as.integer(abs(home_score - away_score)),
    homeWon = home_won
  )

  # Box scores are built from the stats season's play-by-play, so a game from a
  # different season (the offseason fallback can span two) has no line here.
  home_box <- build_box_score(game$home_code, game$game_id)
  away_box <- build_box_score(game$away_code, game$game_id)
  if (!is.null(home_box) || !is.null(away_box)) {
    results$teamBoxScore <- list(home = home_box, away = away_box)
    results$vsSeasonAvg <- list(
      home = build_vs_season_avg(game$home_code, game$game_id),
      away = build_vs_season_avg(game$away_code, game$game_id)
    )
    home_highs <- check_season_highs(game$home_code, game$game_id)
    away_highs <- check_season_highs(game$away_code, game$game_id)
    if (!is.null(home_highs) || !is.null(away_highs)) {
      results$seasonHighs <- list(home = home_highs, away = away_highs)
    }
  }

  # Player highlights come from the stats season's weekly feed, so a game from
  # a different season (the offseason fallback can span two) has none.
  # Compared numerically: load_schedules ships `season` as an integer while
  # stats_season is a double, and identical() would never match.
  if (isTRUE(as.integer(game$season) == as.integer(stats_season))) {
    home_players <- build_player_highlights(game$home_code, game$week)
    away_players <- build_player_highlights(game$away_code, game$week)
    if (!is.null(home_players) || !is.null(away_players)) {
      results$playerHighlights <- list(home = home_players, away = away_players)
    }
  }

  results
}

# ============================================================================
# Build matchups
# ============================================================================
cat("\nBuilding matchups...\n")

SEASON_TYPE_LABELS <- c(
  REG = "Regular Season", WC = "Wild Card", DIV = "Divisional Round",
  CON = "Conference Championship", SB = "Super Bowl"
)

game_status_name <- function(game) {
  if (isTRUE(game$played)) "STATUS_FINAL" else "STATUS_SCHEDULED"
}

# nflverse gives the kickoff as a local date plus a wall-clock time. The app
# parses gameDate as an instant, so emit it in UTC using Eastern kickoff time,
# which is how the league schedules and how every other matchup chart reads.
game_date_iso <- function(game) {
  time_str <- game$gametime
  if (is.na(time_str) || !nzchar(time_str)) time_str <- "13:00"
  local <- tryCatch(
    as.POSIXct(paste(format(game$game_day, "%Y-%m-%d"), time_str),
               tz = "America/New_York", format = "%Y-%m-%d %H:%M"),
    error = function(e) NA
  )
  if (is.na(local)) {
    return(paste0(format(game$game_day, "%Y-%m-%d"), "T18:00:00Z"))
  }
  format(as.POSIXct(local, tz = "UTC"), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
}

build_odds <- function(game) {
  spread <- safe_num(game$spread_line)
  total <- safe_num(game$total_line)
  home_ml <- safe_num(game$home_moneyline)
  away_ml <- safe_num(game$away_moneyline)
  if (is.na(spread) && is.na(total) && is.na(home_ml) && is.na(away_ml)) return(NULL)

  # nflverse spread_line is home-relative and positive when the home team is
  # favored; the app's details string names the favorite with a negative number.
  details <- NULL
  if (!is.na(spread) && spread != 0) {
    favorite <- if (spread > 0) game$home_code else game$away_code
    details <- sprintf("%s -%s", favorite, format(abs(spread), nsmall = 1))
  } else if (!is.na(spread)) {
    details <- "PK"
  }

  list(
    provider = "nflverse",
    spread = if (is.na(spread)) NULL else round(spread, 1),
    overUnder = if (is.na(total)) NULL else round(total, 1),
    homeMoneyline = int_or_null(home_ml),
    awayMoneyline = int_or_null(away_ml),
    details = details
  )
}

build_location <- function(game) {
  stadium <- game$stadium
  if ((is.na(stadium) || !nzchar(stadium)) && is.na(game$roof)) return(NULL)
  list(
    stadium = if (is.na(stadium) || !nzchar(stadium)) NULL else as.character(stadium),
    roof = if (is.na(game$roof)) NULL else as.character(game$roof),
    surface = if (is.na(game$surface)) NULL else as.character(game$surface)
  )
}

matchups_json <- list()

for (i in seq_len(nrow(selected_games))) {
  game <- selected_games[i, ]

  home_row <- team_stats %>% filter(team == game$home_code)
  away_row <- team_stats %>% filter(team == game$away_code)
  home_s <- if (nrow(home_row) > 0) home_row[1, ] else NULL
  away_s <- if (nrow(away_row) > 0) away_row[1, ] else NULL

  home_meta <- team_meta_row(game$home_code)
  away_meta <- team_meta_row(game$away_code)
  game_name <- paste(
    if (is.null(away_meta)) game$away_code else away_meta$team_name,
    "at",
    if (is.null(home_meta)) game$home_code else home_meta$team_name
  )

  comparisons <- if (!is.null(home_s) && !is.null(away_s)) {
    list(
      sideBySide = build_side_by_side(home_s, away_s),
      homeOffVsAwayDef = build_off_vs_def(game$home_code, game$away_code, home_s, away_s),
      awayOffVsHomeDef = build_off_vs_def(game$away_code, game$home_code, away_s, home_s)
    )
  } else {
    NULL
  }

  matchup <- list(
    gameId = as.character(game$game_id),
    gameDate = game_date_iso(game),
    gameName = game_name,
    gameStatus = game_status_name(game),
    gameCompleted = isTRUE(game$played),
    season = as.integer(game$season),
    week = as.integer(game$week),
    seasonType = as.character(game$game_type),
    seasonTypeLabel = unname(SEASON_TYPE_LABELS[as.character(game$game_type)]) %||% as.character(game$game_type),
    homeTeam = build_team_info(game$home_code, home_s),
    awayTeam = build_team_info(game$away_code, away_s),
    location = build_location(game),
    odds = build_odds(game),
    comparisons = comparisons,
    # Away team is teamA so the H2H block reads in the same order as the
    # matchup header (away at home).
    h2h = build_h2h(game$away_code, game$home_code)
  )

  if (isTRUE(game$played) && !is.na(game$home_score) && !is.na(game$away_score)) {
    matchup$results <- build_results(game)
  }

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

cat("Built", length(matchups_json), "matchups\n")

# ============================================================================
# Output JSON
# ============================================================================
window_label <- paste0(
  format(min(selected_games$game_day), "%b %d"), " - ",
  format(max(selected_games$game_day), "%b %d")
)
chart_title <- paste0("NFL Matchups - ", window_label)

weeks_covered <- selected_games %>%
  distinct(season, week, game_type) %>%
  arrange(season, week)
week_summary <- paste(
  apply(weeks_covered, 1, function(r) {
    label <- unname(SEASON_TYPE_LABELS[trimws(r[["game_type"]])]) %||% trimws(r[["game_type"]])
    if (identical(trimws(r[["game_type"]]), "REG")) {
      paste0(trimws(r[["season"]]), " Week ", trimws(r[["week"]]))
    } else {
      paste0(trimws(r[["season"]]), " ", label)
    }
  }),
  collapse = " · "
)

subtitle <- if (identical(window_mode, "window")) {
  paste0("Games from the past ", DAYS_BEHIND, " days and next ", DAYS_AHEAD, " days · ", week_summary)
} else {
  paste0("Most recent results and the next scheduled slate · ", week_summary)
}

output_data <- list(
  sport = "NFL",
  visualizationType = "NFL_MATCHUP",
  title = chart_title,
  subtitle = subtitle,
  description = paste0(
    "NFL matchup worksheets with full-season team comparisons on offense, ",
    "defense and overall, plus offense-versus-defense pairings that rank each ",
    "team's attack against what the opponent's defense actually allows. ",
    "Completed games add a team box score, a game-versus-season-average ",
    "breakdown, season highs, and player highlights.\n\n",
    "Season stats cover the ", stats_season, " regular season and come from ",
    "nflverse play-by-play, so a team's season line and its per-game lines are ",
    "always the same numbers.\n\n",
    "OFFENSE:\n\n",
    " • Points/Game, Yards/Game, Pass Yards/Game, Rush Yards/Game: Season ",
    "per-game production. Higher is better.\n\n",
    " • Yards/Play: Total yards divided by offensive plays. Higher is better.\n\n",
    " • Off EPA/Play: Expected points added per offensive play — the single ",
    "best measure of offensive efficiency. Higher is better.\n\n",
    " • Off Success %: Share of plays with positive EPA. Higher is better.\n\n",
    " • 3rd Down % / 4th Down %: Conversion rate on third and fourth down. ",
    "Higher is better.\n\n",
    " • Red Zone TD %: Touchdowns per trip inside the 20. Higher is better.\n\n",
    " • Explosive/Game: Plays of 20+ passing yards or 12+ rushing yards. ",
    "Higher is better.\n\n",
    " • Turnovers/Game, Sacks Allowed/Game: Lower is better.\n\n",
    " • Time of Poss: Average minutes of possession per game.\n\n",
    "DEFENSE:\n\n",
    " • Points Allowed/Game, Yards Allowed/Game, Pass and Rush Yards Allowed: ",
    "What opponents produce against this defense. Lower is better.\n\n",
    " • Def EPA/Play, Success % Allowed: Efficiency surrendered per play. ",
    "Lower is better.\n\n",
    " • 3rd Down % Allowed, Red Zone TD % Allowed: Lower is better.\n\n",
    " • Takeaways/Game, Sacks/Game: Higher is better.\n\n",
    "OVERALL:\n\n",
    " • Point Diff/Game, Net EPA/Play, Turnover Diff/Game: Higher is better.\n\n",
    " • Penalties/Game, Penalty Yards/Game: Lower is better.\n\n",
    "OFFENSE VS DEFENSE:\n\n",
    "Each pairing puts one team's offensive rank beside the rank of the stat ",
    "the opposing defense gives up. The better league rank holds the edge.\n\n",
    "CHARTS:\n\n",
    " • Cumulative Point Differential: Each team's running point differential ",
    "by week, against the league's 10th-best pace.\n\n",
    " • Weekly Performance: Points scored versus points allowed per week — the ",
    "upper-left quadrant is scoring more while allowing less.\n\n",
    "RECENT FORM:\n\n",
    "The last ", RECENT_FORM_GAMES, " games for each team, with league ranks ",
    "over that same window.\n\n",
    "HEAD TO HEAD:\n\n",
    "Every meeting between the two teams over the last ", H2H_SEASONS,
    " seasons, grouped by season."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "nflverse (nflfastR / nflreadr)",
  season = stats_season,
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  leagueCumPointDiffStats = league_cum_point_diff_stats,
  leagueWeeklyStats = league_weekly_stats,
  dataPoints = matchups_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

completed <- sum(vapply(matchups_json, function(m) isTRUE(m$gameCompleted), logical(1)))
cat("\nSummary — matchups:", length(matchups_json),
    "| completed:", completed,
    "| upcoming:", length(matchups_json) - completed, "\n")
if (length(matchups_json) > 0) {
  sample <- matchups_json[[1]]
  cat("Sample —", sample$gameName, "|", sample$gameDate,
      "| week", sample$week, sample$seasonType, "\n")
  cat("  comparisons:", if (is.null(sample$comparisons)) "none" else
        paste(length(sample$comparisons$sideBySide$offense), "offense /",
              length(sample$comparisons$sideBySide$defense), "defense /",
              length(sample$comparisons$sideBySide$overall), "overall stats"), "\n")
  cat("  h2h games:", if (is.null(sample$h2h)) 0 else sample$h2h$totalGames, "\n")
  cat("  results:", if (is.null(sample$results)) "none" else
        paste0(sample$results$awayScore, "-", sample$results$homeScore), "\n")
}

if (nzchar(Sys.getenv("FASTBREAK_LOCAL_JSON"))) {
  file.copy(tmp_file, Sys.getenv("FASTBREAK_LOCAL_JSON"), overwrite = TRUE)
  cat("Wrote local JSON copy:", Sys.getenv("FASTBREAK_LOCAL_JSON"), "\n")
}

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (!nzchar(s3_bucket)) stop("AWS_S3_BUCKET environment variable is not set")

env <- toupper(Sys.getenv("ENV", "DEV"))
s3_key <- if (env == "PROD") "prod/nfl__matchup_stats.json" else "dev/nfl__matchup_stats.json"

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
if (system(cmd) != 0) stop("Failed to upload to S3")
cat("Uploaded to S3:", s3_path, "\n")

dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
dynamodb_item <- sprintf(
  '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
  s3_key, utc_timestamp, chart_title
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

cat("\n=== NFL Matchup Stats generation complete ===\n")
