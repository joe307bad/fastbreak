#!/usr/bin/env Rscript

# MLB Playoff Bracket generator.
# Builds a bracket for the current MLB postseason with matchup stats and writes
# JSON for the KMP app (visualizationType = "MLB_PLAYOFF_BRACKET").
#
# Pre-postseason -> projected bracket from current standings (top 6 per league).
# During/after   -> live bracket from ESPN postseason scoreboard, with series scores.
#
# Ported from nba__playoff_bracket.R. The structural difference is the bracket
# shape: MLB seeds 1 and 2 get a bye through the Wild Card round, so each league
# has two R1 series instead of four, and every round has its own series length
# (Wild Card best-of-3, Division best-of-5, LCS / World Series best-of-7).
#
# Team stats, comparisons and chart series reuse the logic in mlb__matchup_stats.

library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)
library(lubridate)

# ============================================================================
# Constants
# ============================================================================
MLB_SEASON <- as.numeric(format(Sys.Date(), "%Y"))

# Window used when scanning the ESPN scoreboard. The regular season runs late
# March through late September; the postseason runs through early November.
SEASON_START    <- as.Date(paste0(MLB_SEASON, "-03-15"))
POSTSEASON_START <- as.Date(paste0(MLB_SEASON, "-09-28"))
POSTSEASON_END   <- as.Date(paste0(MLB_SEASON, "-11-15"))

TREND_DAYS <- 30

# Environment and S3 prefix
ENV <- toupper(Sys.getenv("ENV", "DEV"))
S3_PREFIX <- if (ENV == "PROD") "prod" else "dev"

# S3 key for persisting bracket history (so completed series survive restarts)
HISTORY_S3_KEY <- paste0(S3_PREFIX, "/mlb__bracket_history.json")

LEAGUE_COLORS <- list(
  AL = "#C62828",
  NL = "#1565C0"
)

LEAGUE_NAMES <- list(AL = "American League", NL = "National League")

# Round definitions per league. Unlike the NBA every round has its own length.
LEAGUE_ROUNDS <- list(
  list(roundNumber = 1, roundName = "Wild Card Series",          games = 2, bestOf = 3),
  list(roundNumber = 2, roundName = "Division Series",           games = 2, bestOf = 5),
  list(roundNumber = 3, roundName = "League Championship Series", games = 1, bestOf = 7)
)

WORLD_SERIES_BEST_OF <- 7

# Wild Card seed pairings, in bracket-slot order. Slot 1 feeds the #1 seed's
# Division Series, slot 2 feeds the #2 seed's.
WILD_CARD_SEEDS <- list(c(4, 5), c(3, 6))
# Division Series: the bye seed that waits in each slot.
DIVISION_BYE_SEEDS <- c(1, 2)

TEAM_LEAGUES <- c(
  "ARI" = "NL", "ATL" = "NL", "BAL" = "AL", "BOS" = "AL",
  "CHC" = "NL", "CWS" = "AL", "CIN" = "NL", "CLE" = "AL",
  "COL" = "NL", "DET" = "AL", "HOU" = "AL", "KC" = "AL",
  "LAA" = "AL", "LAD" = "NL", "MIA" = "NL", "MIL" = "NL",
  "MIN" = "AL", "NYM" = "NL", "NYY" = "AL", "ATH" = "AL",
  "PHI" = "NL", "PIT" = "NL", "SD" = "NL", "SF" = "NL",
  "SEA" = "AL", "STL" = "NL", "TB" = "AL", "TEX" = "AL",
  "TOR" = "AL", "WSH" = "NL"
)

# ============================================================================
# Helpers
# ============================================================================
is_valid_value <- function(x) {
  !is.null(x) && length(x) > 0 && !is.na(x[1])
}

`%||%` <- function(a, b) if (!is.null(a) && length(a) > 0 && !is.na(a[1])) a else b

tied_rank <- function(x) {
  numeric_ranks <- rank(x, ties.method = "min", na.last = "keep")
  rank_counts <- table(numeric_ranks[!is.na(numeric_ranks)])
  display_ranks <- sapply(numeric_ranks, function(r) {
    if (is.na(r)) return(NA_character_)
    if (rank_counts[as.character(r)] > 1) paste0("T", r) else as.character(r)
  })
  list(rank = numeric_ranks, rankDisplay = display_ranks)
}

add_api_delay <- function() Sys.sleep(0.25)

safe_num <- function(x) if (is_valid_value(x)) as.numeric(x) else NA_real_

# Parse ESPN playoff-note headlines into a league + round identifier.
#
# ESPN writes these two ways and both show up in the same postseason:
#   abbreviated  "ALWC - Game 2", "NLDS - Game 3", "ALCS - Game 1", "WS - Game 7"
#   spelled out  "American League Wild Card Series - Game 1", "World Series - Game 7"
parse_playoff_round <- function(headline) {
  if (is.null(headline) || headline == "") {
    return(list(league = NA, roundNumber = NA, roundName = NA, bestOf = NA))
  }
  hl <- tolower(headline)
  # Drop the trailing "- Game n" so the abbreviated form is left as a bare token.
  token <- trimws(sub("-.*$", "", hl))

  league <- NA
  if (grepl("american", hl) || grepl("^al(wc|ds|cs)?$", token) || grepl("\\bal\\b", hl)) {
    league <- "AL"
  } else if (grepl("national", hl) || grepl("^nl(wc|ds|cs)?$", token) || grepl("\\bnl\\b", hl)) {
    league <- "NL"
  }

  # Order matters. The World Series is checked first because it is the only
  # league-less round, and the championship series last because "American
  # League Championship Series" also contains the word "league".
  if (grepl("world series", hl) || identical(token, "ws")) {
    return(list(league = "Finals", roundNumber = 4,
                roundName = "World Series", bestOf = WORLD_SERIES_BEST_OF))
  }
  if (grepl("wild ?card", hl) || grepl("wc$", token)) {
    return(list(league = league, roundNumber = 1,
                roundName = "Wild Card Series", bestOf = 3))
  }
  if (grepl("division series", hl) || grepl("ds$", token)) {
    return(list(league = league, roundNumber = 2,
                roundName = "Division Series", bestOf = 5))
  }
  if (grepl("championship series", hl) || grepl("cs$", token)) {
    return(list(league = league, roundNumber = 3,
                roundName = "League Championship Series", bestOf = 7))
  }

  list(league = league, roundNumber = NA, roundName = NA, bestOf = NA)
}

round_best_of <- function(round_number) {
  switch(as.character(round_number), "1" = 3L, "2" = 5L, "3" = 7L, "4" = 7L, 7L)
}

# ============================================================================
# History persistence (same pattern as nba__playoff_bracket)
# ============================================================================
load_bracket_history <- function() {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
  if (!nzchar(s3_bucket)) {
    local_history <- "/tmp/mlb_bracket_history.json"
    if (file.exists(local_history)) {
      cat("Loading history from local file:", local_history, "\n")
      return(tryCatch(fromJSON(local_history, simplifyVector = FALSE), error = function(e) NULL))
    }
    return(NULL)
  }
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  tmp_file <- tempfile(fileext = ".json")
  cmd <- paste("aws s3 cp", shQuote(s3_path), shQuote(tmp_file), "2>/dev/null")
  result <- system(cmd, ignore.stdout = TRUE, ignore.stderr = TRUE)
  if (result == 0 && file.exists(tmp_file)) {
    history <- tryCatch(fromJSON(tmp_file, simplifyVector = FALSE), error = function(e) NULL)
    unlink(tmp_file)
    return(history)
  }
  cat("No existing bracket history found\n")
  NULL
}

save_bracket_history <- function(history) {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
  tmp_file <- tempfile(fileext = ".json")
  write_json(history, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")
  if (!nzchar(s3_bucket)) {
    file.copy(tmp_file, "/tmp/mlb_bracket_history.json", overwrite = TRUE)
    unlink(tmp_file)
    return(TRUE)
  }
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  unlink(tmp_file)
  result == 0
}

cat("=== MLB Playoff Bracket Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")
cat("Season:", MLB_SEASON, "\n")

# ============================================================================
# STEP 1: Season team stats (mirrors mlb__matchup_stats)
# ============================================================================
cat("\n1. Loading MLB team stats from ESPN...\n")

all_teams_resp <- tryCatch(
  GET("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/teams"),
  error = function(e) NULL
)
team_abbrevs <- c()
if (!is.null(all_teams_resp) && status_code(all_teams_resp) == 200) {
  teams_data <- content(all_teams_resp, as = "parsed")
  for (t in teams_data$sports[[1]]$leagues[[1]]$teams) {
    team_abbrevs <- c(team_abbrevs, t$team$abbreviation)
  }
}
cat("Found", length(team_abbrevs), "teams\n")

if (length(team_abbrevs) == 0) {
  stop("Could not load MLB teams from ESPN")
}

team_stats_list <- list()
for (abbrev in team_abbrevs) {
  add_api_delay()
  # seasontype=2 is required: once the postseason starts this endpoint defaults
  # to postseason-only totals, which would make every team's season line read
  # off a two-game sample.
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/teams/",
                abbrev, "/statistics?season=", MLB_SEASON, "&seasontype=2")
  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (is.null(resp) || status_code(resp) != 200) next

  data <- content(resp, as = "parsed")
  cats <- data$results$stats$categories
  if (is.null(cats)) next

  get_stat <- function(category, stat_name) {
    for (cat_entry in cats) {
      if (identical(cat_entry$name, category)) {
        for (s in cat_entry$stats) {
          if (identical(s$name, stat_name)) return(safe_num(s$value))
        }
      }
    }
    NA_real_
  }

  gp <- get_stat("batting", "teamGamesPlayed")
  if (is.na(gp) || gp == 0) next

  team_stats_list[[abbrev]] <- list(
    team_abbreviation = abbrev,
    games_played = gp,
    runs = get_stat("batting", "runs"),
    hits = get_stat("batting", "hits"),
    home_runs = get_stat("batting", "homeRuns"),
    rbis = get_stat("batting", "RBIs"),
    stolen_bases = get_stat("batting", "stolenBases"),
    batting_avg = get_stat("batting", "avg"),
    on_base_pct = get_stat("batting", "onBasePct"),
    slugging_pct = get_stat("batting", "slugAvg"),
    ops = get_stat("batting", "OPS"),
    era = get_stat("pitching", "ERA"),
    whip = get_stat("pitching", "WHIP"),
    strikeouts_pitching = get_stat("pitching", "strikeouts"),
    walks_pitching = get_stat("pitching", "walks"),
    innings_pitched = get_stat("pitching", "innings"),
    hits_allowed = get_stat("pitching", "hits"),
    runs_allowed = get_stat("pitching", "runs"),
    home_runs_allowed = get_stat("pitching", "homeRuns"),
    errors = get_stat("fielding", "errors"),
    fielding_pct = get_stat("fielding", "fieldingPct")
  )
}

cat("Loaded stats for", length(team_stats_list), "teams\n")

team_stats <- bind_rows(team_stats_list) %>%
  mutate(
    runs_per_game = runs / games_played,
    hits_per_game = hits / games_played,
    hr_per_game = home_runs / games_played,
    rbi_per_game = rbis / games_played,
    sb_per_game = stolen_bases / games_played,
    k_per_9 = strikeouts_pitching / (innings_pitched / 9),
    bb_per_9 = walks_pitching / (innings_pitched / 9),
    runs_allowed_per_game = runs_allowed / games_played,
    hits_allowed_per_game = hits_allowed / games_played,
    hr_allowed_per_game = home_runs_allowed / games_played,
    errors_per_game = errors / games_played,
    league = TEAM_LEAGUES[team_abbreviation]
  )

rank_and_assign <- function(df, col, lower_better = FALSE) {
  vals <- df[[col]]
  rk <- if (lower_better) tied_rank(vals) else tied_rank(-vals)
  df[[paste0(col, "_rank")]] <- rk$rank
  df[[paste0(col, "_rankDisplay")]] <- rk$rankDisplay
  df
}

for (stat in c("runs_per_game", "batting_avg", "on_base_pct", "slugging_pct", "ops",
               "hr_per_game", "rbi_per_game", "sb_per_game", "hits_per_game",
               "k_per_9", "fielding_pct")) {
  team_stats <- rank_and_assign(team_stats, stat)
}
for (stat in c("era", "whip", "runs_allowed_per_game", "bb_per_9",
               "hits_allowed_per_game", "hr_allowed_per_game", "errors_per_game")) {
  team_stats <- rank_and_assign(team_stats, stat, lower_better = TRUE)
}

cat("Computed ranks for", nrow(team_stats), "teams\n")

# ============================================================================
# STEP 1b: Season + postseason game results from the ESPN scoreboard
#
# One pass over the scoreboard feeds everything downstream: head-to-head
# history, the weekly chart series, the one-month trend, the playoff trend, and
# the postseason series themselves.
# ============================================================================
cat("\n1b. Fetching season game results...\n")

extract_team_box <- function(competitor) {
  stats <- list()
  if (!is.null(competitor$statistics)) {
    for (s in competitor$statistics) {
      if (!is.null(s$name) && !is.null(s$displayValue)) {
        stats[[s$name]] <- safe_num(s$displayValue)
      }
    }
  }
  stats
}

season_game_results <- list()   # one row per completed game (regular season)
trend_games <- list()           # one row per team-game, last TREND_DAYS of regular season
playoff_team_games <- list()    # one row per team-game, postseason only
playoff_events <- list()        # raw ESPN postseason events

fetch_date <- SEASON_START
fetch_end <- min(Sys.Date() + 7, POSTSEASON_END)

while (fetch_date <= fetch_end) {
  date_str <- format(fetch_date, "%Y%m%d")
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard?dates=", date_str)
  add_api_delay()

  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (!is.null(resp) && status_code(resp) == 200) {
    data <- tryCatch(content(resp, as = "parsed"), error = function(e) NULL)
    if (!is.null(data) && !is.null(data$events)) {
      for (ev in data$events) {
        comp <- ev$competitions[[1]]
        if (length(comp$competitors) != 2) next

        season_type <- if (!is.null(ev$season$type)) as.integer(ev$season$type) else NA_integer_
        notes_hl <- if (length(comp$notes) > 0 && !is.null(comp$notes[[1]]$headline))
          comp$notes[[1]]$headline else ""
        is_postseason <- isTRUE(season_type == 3) ||
          !is.na(parse_playoff_round(notes_hl)$roundNumber)

        if (is_postseason) {
          playoff_events[[length(playoff_events) + 1]] <- ev
        }

        completed <- isTRUE(comp$status$type$completed)
        if (!completed) next

        home <- NULL; away <- NULL
        for (ct in comp$competitors) {
          if (identical(ct$homeAway, "home")) home <- ct else away <- ct
        }
        if (is.null(home) || is.null(away)) next

        home_score <- safe_num(home$score)
        away_score <- safe_num(away$score)
        if (is.na(home_score) || is.na(away_score)) next

        home_abbrev <- home$team$abbreviation
        away_abbrev <- away$team$abbreviation
        game_date_str <- format(fetch_date, "%Y-%m-%d")
        home_box <- extract_team_box(home)
        away_box <- extract_team_box(away)

        team_rows <- list(
          list(team = home_abbrev, opponent = away_abbrev,
               runs_scored = home_score, runs_allowed = away_score,
               won = home_score > away_score,
               hits = safe_num(home_box[["hits"]]), date = game_date_str),
          list(team = away_abbrev, opponent = home_abbrev,
               runs_scored = away_score, runs_allowed = home_score,
               won = away_score > home_score,
               hits = safe_num(away_box[["hits"]]), date = game_date_str)
        )

        if (is_postseason) {
          for (r in team_rows) playoff_team_games[[length(playoff_team_games) + 1]] <- r
          next
        }

        # season_type: 1 = spring training, 2 = regular season, 4 = All-Star.
        # Only the regular season belongs in head-to-head, trends and charts;
        # the All-Star game would otherwise appear as teams named "AL" / "NL".
        if (!is.na(season_type) && season_type != 2) next

        season_game_results[[length(season_game_results) + 1]] <- list(
          date = game_date_str,
          home_team = home_abbrev,
          away_team = away_abbrev,
          home_score = home_score,
          away_score = away_score,
          winner = if (home_score > away_score) home_abbrev else away_abbrev
        )

        if (fetch_date >= (Sys.Date() - days(TREND_DAYS))) {
          for (r in team_rows) trend_games[[length(trend_games) + 1]] <- r
        }
      }
    }
  }
  fetch_date <- fetch_date + days(1)
}

cat("Fetched", length(season_game_results), "regular-season games,",
    length(playoff_events), "postseason events\n")

season_games_df <- if (length(season_game_results) > 0) bind_rows(season_game_results) else data.frame()

# ----------------------------------------------------------------------------
# Trend stats (shared shape between the one-month trend and the playoff trend)
# ----------------------------------------------------------------------------
build_trend_stats <- function(rows) {
  if (length(rows) == 0) return(NULL)
  df <- bind_rows(rows) %>%
    group_by(team) %>%
    summarise(
      games_played = n(),
      wins = sum(won, na.rm = TRUE),
      losses = sum(!won, na.rm = TRUE),
      runs_per_game = mean(runs_scored, na.rm = TRUE),
      runs_allowed_per_game = mean(runs_allowed, na.rm = TRUE),
      run_diff_per_game = mean(runs_scored - runs_allowed, na.rm = TRUE),
      hits_per_game = mean(hits, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    mutate(win_pct = ifelse(wins + losses > 0, wins / (wins + losses), NA_real_))

  for (stat in c("win_pct", "runs_per_game", "run_diff_per_game", "hits_per_game")) {
    rk <- tied_rank(-df[[stat]])
    df[[paste0(stat, "_rank")]] <- rk$rank
    df[[paste0(stat, "_rankDisplay")]] <- rk$rankDisplay
  }
  rk <- tied_rank(df$runs_allowed_per_game)
  df$runs_allowed_per_game_rank <- rk$rank
  df$runs_allowed_per_game_rankDisplay <- rk$rankDisplay
  df
}

month_trend_stats <- build_trend_stats(trend_games)
if (!is.null(month_trend_stats)) {
  cat("Computed month trend for", nrow(month_trend_stats), "teams\n")
}

# The playoff trend ranks postseason performance across the postseason field
# only, so a rank of 1 means "best of the teams still playing".
playoff_trend_stats <- build_trend_stats(playoff_team_games)
if (!is.null(playoff_trend_stats)) {
  cat("Computed playoff trend for", nrow(playoff_trend_stats), "teams\n")
}

# ============================================================================
# STEP 1c: Weekly chart series (cumulative run differential + weekly runs)
# ============================================================================
cat("\n1c. Calculating weekly chart data...\n")

get_week_num <- function(date) {
  as.integer(floor(difftime(as.Date(date), SEASON_START, units = "weeks"))) + 1
}

cum_run_diff_by_team <- data.frame(team = character(), week_num = integer(), cum_run_diff = numeric())
weekly_performance <- data.frame(team = character(), week_num = integer(),
                                 runs_scored_avg = numeric(), runs_allowed_avg = numeric())
league_cum_run_diff_stats <- list(minCumRunDiff = NA, maxCumRunDiff = NA)
league_weekly_stats <- list()
top10_by_week <- data.frame(week_num = integer(), threshold = numeric())

if (nrow(season_games_df) > 0) {
  all_team_games <- bind_rows(
    season_games_df %>% transmute(team = home_team, runs_scored = home_score,
                                  runs_allowed = away_score, date = date),
    season_games_df %>% transmute(team = away_team, runs_scored = away_score,
                                  runs_allowed = home_score, date = date)
  ) %>%
    mutate(run_diff = runs_scored - runs_allowed,
           week_num = sapply(date, get_week_num)) %>%
    filter(week_num > 0, week_num <= 30) %>%
    arrange(date)

  cum_run_diff_by_team <- all_team_games %>%
    group_by(team) %>%
    arrange(date) %>%
    mutate(cum_run_diff = cumsum(run_diff)) %>%
    group_by(team, week_num) %>%
    summarise(cum_run_diff = last(cum_run_diff), .groups = "drop")

  current_week <- get_week_num(Sys.Date())
  weekly_performance <- all_team_games %>%
    filter(week_num >= current_week - 10) %>%
    group_by(team, week_num) %>%
    summarise(
      runs_scored_avg = mean(runs_scored, na.rm = TRUE),
      runs_allowed_avg = mean(runs_allowed, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    arrange(week_num)

  if (nrow(cum_run_diff_by_team) > 0) {
    league_cum_run_diff_stats <- list(
      minCumRunDiff = round(min(cum_run_diff_by_team$cum_run_diff, na.rm = TRUE), 0),
      maxCumRunDiff = round(max(cum_run_diff_by_team$cum_run_diff, na.rm = TRUE), 0)
    )
    # The 10th-best cumulative run differential each week, drawn on the chart as
    # a "top 10" reference line.
    top10_by_week <- cum_run_diff_by_team %>%
      group_by(week_num) %>%
      filter(n() >= 20) %>%
      arrange(desc(cum_run_diff)) %>%
      slice(10) %>%
      ungroup() %>%
      select(week_num, threshold = cum_run_diff)
  }

  if (nrow(weekly_performance) > 0) {
    league_weekly_stats <- list(
      avgRunsScored = round(mean(weekly_performance$runs_scored_avg, na.rm = TRUE), 2),
      avgRunsAllowed = round(mean(weekly_performance$runs_allowed_avg, na.rm = TRUE), 2),
      minRunsScored = round(min(weekly_performance$runs_scored_avg, na.rm = TRUE), 2),
      maxRunsScored = round(max(weekly_performance$runs_scored_avg, na.rm = TRUE), 2),
      minRunsAllowed = round(min(weekly_performance$runs_allowed_avg, na.rm = TRUE), 2),
      maxRunsAllowed = round(max(weekly_performance$runs_allowed_avg, na.rm = TRUE), 2)
    )
  }
}

if (nrow(top10_by_week) > 0) {
  league_cum_run_diff_stats$top10ByWeek <- setNames(
    as.list(round(top10_by_week$threshold, 0)),
    paste0("week-", top10_by_week$week_num)
  )
}

cat("Computed chart data for", length(unique(cum_run_diff_by_team$team)), "teams\n")

# ============================================================================
# STEP 2: Standings -> seeds per league
# ============================================================================
cat("\n2. Fetching standings for playoff seeding...\n")

# ESPN nests MLB standings league -> division -> entries, so walk the tree and
# collect every entries list beneath a league node.
collect_standings_entries <- function(node) {
  out <- list()
  if (!is.null(node$standings) && !is.null(node$standings$entries)) {
    for (e in node$standings$entries) out[[length(out) + 1]] <- e
  }
  if (!is.null(node$children)) {
    for (child in node$children) {
      for (e in collect_standings_entries(child)) out[[length(out) + 1]] <- e
    }
  }
  out
}

get_standing_stat <- function(stats, name) {
  for (s in stats) if (!is.null(s$name) && s$name == name) return(safe_num(s$value))
  NA_real_
}

seeds <- list(AL = data.frame(), NL = data.frame())

standings_url <- "https://site.api.espn.com/apis/v2/sports/baseball/mlb/standings"
standings_resp <- tryCatch(GET(standings_url), error = function(e) NULL)

if (!is.null(standings_resp) && status_code(standings_resp) == 200) {
  sd <- content(standings_resp, as = "parsed")
  if (!is.null(sd$children)) {
    for (league_node in sd$children) {
      league_name <- league_node$name %||% ""
      bucket <- if (grepl("American", league_name, ignore.case = TRUE)) "AL"
                else if (grepl("National", league_name, ignore.case = TRUE)) "NL"
                else NA
      if (is.na(bucket)) next

      entries <- collect_standings_entries(league_node)
      if (length(entries) == 0) next

      rows <- lapply(entries, function(entry) {
        list(
          team_id = entry$team$id,
          team_name = entry$team$displayName,
          team_abbrev = entry$team$abbreviation,
          team_logo = if (!is.null(entry$team$logos) && length(entry$team$logos) > 0)
            entry$team$logos[[1]]$href else NA,
          wins = get_standing_stat(entry$stats, "wins"),
          losses = get_standing_stat(entry$stats, "losses"),
          win_pct = get_standing_stat(entry$stats, "winPercent"),
          # ESPN's playoffSeed already resolves MLB's tiebreakers, which for
          # baseball are head-to-head first. A naive sort by record would
          # produce the wrong Wild Card pairings whenever teams finish tied.
          playoff_seed = get_standing_stat(entry$stats, "playoffSeed")
        )
      })

      df <- bind_rows(rows) %>% distinct(team_abbrev, .keep_all = TRUE)
      if (any(!is.na(df$playoff_seed) & df$playoff_seed > 0)) {
        df <- df %>%
          arrange(ifelse(is.na(playoff_seed) | playoff_seed <= 0, 999, playoff_seed),
                  desc(wins), losses) %>%
          mutate(seed = if_else(!is.na(playoff_seed) & playoff_seed > 0,
                                as.integer(playoff_seed),
                                as.integer(row_number())))
      } else {
        df <- df %>% arrange(desc(wins), losses) %>% mutate(seed = as.integer(row_number()))
      }
      seeds[[bucket]] <- df
    }
  }
}

seeds_ok <- is.data.frame(seeds$AL) && nrow(seeds$AL) > 0 &&
            is.data.frame(seeds$NL) && nrow(seeds$NL) > 0
if (!seeds_ok) {
  cat("WARNING: Could not load standings - seeds will be missing for some leagues\n")
}

find_team_stats <- function(abbrev) {
  if (!is_valid_value(abbrev)) return(NULL)
  match_row <- team_stats %>% filter(team_abbreviation == abbrev)
  if (nrow(match_row) > 0) return(match_row[1, ])
  NULL
}

# ============================================================================
# Team / matchup builders
# ============================================================================

stat_val <- function(row, col) {
  if (is.null(row) || !col %in% names(row) || is.na(row[[col]])) {
    return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  }
  list(value = round(as.numeric(row[[col]]), 4),
       rank = as.integer(row[[paste0(col, "_rank")]]),
       rankDisplay = row[[paste0(col, "_rankDisplay")]])
}

build_trend_block <- function(trend_df, abbrev) {
  if (is.null(trend_df)) return(NULL)
  row <- trend_df %>% filter(team == abbrev)
  if (nrow(row) == 0) return(NULL)
  row <- row[1, ]
  list(
    gamesPlayed = as.integer(row$games_played),
    record = list(
      wins = as.integer(row$wins),
      losses = as.integer(row$losses),
      rank = as.integer(row$win_pct_rank),
      rankDisplay = row$win_pct_rankDisplay
    ),
    runsPerGame = list(value = round(row$runs_per_game, 2),
                       rank = as.integer(row$runs_per_game_rank),
                       rankDisplay = row$runs_per_game_rankDisplay),
    runsAllowedPerGame = list(value = round(row$runs_allowed_per_game, 2),
                              rank = as.integer(row$runs_allowed_per_game_rank),
                              rankDisplay = row$runs_allowed_per_game_rankDisplay),
    runDiffPerGame = list(value = round(row$run_diff_per_game, 2),
                          rank = as.integer(row$run_diff_per_game_rank),
                          rankDisplay = row$run_diff_per_game_rankDisplay),
    hitsPerGame = list(value = round(row$hits_per_game, 2),
                       rank = as.integer(row$hits_per_game_rank),
                       rankDisplay = row$hits_per_game_rankDisplay)
  )
}

build_team <- function(name, abbrev, logo, seed = NULL, record = NULL) {
  stats_row <- find_team_stats(abbrev)
  base <- list(
    name = name,
    abbreviation = abbrev,
    logo = logo,
    seed = if (is_valid_value(seed)) as.integer(seed) else 0L,
    wins = if (is_valid_value(record$wins)) as.integer(record$wins) else NULL,
    losses = if (is_valid_value(record$losses)) as.integer(record$losses) else NULL,
    conference = record$conference,
    seriesWins = NULL,
    score = NULL,
    isWinner = FALSE,
    teamStats = NULL
  )

  if (is.null(stats_row)) return(base)

  ts <- list(
    gamesPlayed = as.integer(stats_row$games_played),
    runsPerGame = stat_val(stats_row, "runs_per_game"),
    battingAvg = stat_val(stats_row, "batting_avg"),
    onBasePct = stat_val(stats_row, "on_base_pct"),
    sluggingPct = stat_val(stats_row, "slugging_pct"),
    ops = stat_val(stats_row, "ops"),
    hitsPerGame = stat_val(stats_row, "hits_per_game"),
    hrPerGame = stat_val(stats_row, "hr_per_game"),
    rbiPerGame = stat_val(stats_row, "rbi_per_game"),
    sbPerGame = stat_val(stats_row, "sb_per_game"),
    era = stat_val(stats_row, "era"),
    whip = stat_val(stats_row, "whip"),
    kPer9 = stat_val(stats_row, "k_per_9"),
    bbPer9 = stat_val(stats_row, "bb_per_9"),
    runsAllowedPerGame = stat_val(stats_row, "runs_allowed_per_game"),
    hitsAllowedPerGame = stat_val(stats_row, "hits_allowed_per_game"),
    hrAllowedPerGame = stat_val(stats_row, "hr_allowed_per_game"),
    fieldingPct = stat_val(stats_row, "fielding_pct"),
    errorsPerGame = stat_val(stats_row, "errors_per_game")
  )

  team_cum <- cum_run_diff_by_team %>% filter(team == abbrev)
  ts$cumRunDiffByWeek <- if (nrow(team_cum) > 0) {
    setNames(as.list(round(team_cum$cum_run_diff, 0)), paste0("week-", team_cum$week_num))
  } else setNames(list(), character(0))

  team_weekly <- weekly_performance %>% filter(team == abbrev)
  ts$performanceByWeek <- if (nrow(team_weekly) > 0) {
    setNames(lapply(seq_len(nrow(team_weekly)), function(i) {
      list(runsScored = round(team_weekly$runs_scored_avg[i], 2),
           runsAllowed = round(team_weekly$runs_allowed_avg[i], 2))
    }), paste0("week-", team_weekly$week_num))
  } else setNames(list(), character(0))

  ts$monthTrend <- build_trend_block(month_trend_stats, abbrev)
  ts$playoffTrend <- build_trend_block(playoff_trend_stats, abbrev)

  base$teamStats <- ts
  base
}

build_regular_season_history <- function(team_a_abbrev, team_b_abbrev) {
  if (!is_valid_value(team_a_abbrev) || !is_valid_value(team_b_abbrev)) return(NULL)
  if (nrow(season_games_df) == 0) return(NULL)

  h2h <- season_games_df %>%
    filter((home_team == team_a_abbrev & away_team == team_b_abbrev) |
           (home_team == team_b_abbrev & away_team == team_a_abbrev)) %>%
    arrange(date)

  if (nrow(h2h) == 0) return(NULL)

  team_a_wins <- sum(h2h$winner == team_a_abbrev, na.rm = TRUE)
  team_b_wins <- sum(h2h$winner == team_b_abbrev, na.rm = TRUE)

  games <- lapply(seq_len(nrow(h2h)), function(i) {
    row <- h2h[i, ]
    list(
      gameDate = as.character(row$date),
      homeAbbrev = row$home_team,
      awayAbbrev = row$away_team,
      homeScore = as.integer(row$home_score),
      awayScore = as.integer(row$away_score),
      winnerAbbrev = row$winner
    )
  })

  list(
    teamAAbbrev = team_a_abbrev,
    teamBAbbrev = team_b_abbrev,
    teamAWins = as.integer(team_a_wins),
    teamBWins = as.integer(team_b_wins),
    games = games
  )
}

stat_pair <- function(key, label, t1, t2) {
  list(
    label = label,
    home = list(value = t1[[key]]$value, rank = t1[[key]]$rank, rankDisplay = t1[[key]]$rankDisplay),
    away = list(value = t2[[key]]$value, rank = t2[[key]]$rank, rankDisplay = t2[[key]]$rankDisplay)
  )
}

off_vs_def <- function(off_team, def_team, off_stats, def_stats,
                       key, off_key, def_key, off_label, def_label) {
  off_v <- off_stats[[off_key]]
  def_v <- def_stats[[def_key]]
  advantage <- 0
  if (is_valid_value(off_v$rank) && is_valid_value(def_v$rank)) {
    if (off_v$rank < def_v$rank) advantage <- -1
    else if (off_v$rank > def_v$rank) advantage <- 1
  }
  list(
    statKey = key,
    offLabel = off_label,
    defLabel = def_label,
    offense = list(team = off_team, value = off_v$value,
                   rank = off_v$rank, rankDisplay = off_v$rankDisplay),
    defense = list(team = def_team, value = def_v$value,
                   rank = def_v$rank, rankDisplay = def_v$rankDisplay),
    advantage = advantage
  )
}

build_comparisons <- function(team1, team2) {
  t1 <- team1$teamStats
  t2 <- team2$teamStats
  if (is.null(t1) || is.null(t2)) return(NULL)

  batting_side <- list(
    runsPerGame = stat_pair("runsPerGame", "Runs/Game", t1, t2),
    battingAvg  = stat_pair("battingAvg",  "Batting Avg", t1, t2),
    onBasePct   = stat_pair("onBasePct",   "On-Base %", t1, t2),
    sluggingPct = stat_pair("sluggingPct", "Slugging %", t1, t2),
    ops         = stat_pair("ops",         "OPS", t1, t2),
    hitsPerGame = stat_pair("hitsPerGame", "Hits/Game", t1, t2),
    hrPerGame   = stat_pair("hrPerGame",   "HR/Game", t1, t2),
    rbiPerGame  = stat_pair("rbiPerGame",  "RBI/Game", t1, t2),
    sbPerGame   = stat_pair("sbPerGame",   "SB/Game", t1, t2)
  )
  pitching_side <- list(
    era                = stat_pair("era",                "ERA", t1, t2),
    whip               = stat_pair("whip",               "WHIP", t1, t2),
    kPer9              = stat_pair("kPer9",              "K/9", t1, t2),
    bbPer9             = stat_pair("bbPer9",             "BB/9", t1, t2),
    runsAllowedPerGame = stat_pair("runsAllowedPerGame", "Runs Allowed/Game", t1, t2),
    hitsAllowedPerGame = stat_pair("hitsAllowedPerGame", "Hits Allowed/Game", t1, t2),
    hrAllowedPerGame   = stat_pair("hrAllowedPerGame",   "HR Allowed/Game", t1, t2)
  )
  fielding_side <- list(
    fieldingPct   = stat_pair("fieldingPct",   "Fielding %", t1, t2),
    errorsPerGame = stat_pair("errorsPerGame", "Errors/Game", t1, t2)
  )

  a <- team1$abbreviation
  b <- team2$abbreviation

  home_off_vs_away_def <- list(
    runs        = off_vs_def(a, b, t1, t2, "runs",        "runsPerGame", "runsAllowedPerGame", "Runs/Game", "Runs Allowed/Game"),
    hits        = off_vs_def(a, b, t1, t2, "hits",        "hitsPerGame", "hitsAllowedPerGame", "Hits/Game", "Hits Allowed/Game"),
    hr          = off_vs_def(a, b, t1, t2, "hr",          "hrPerGame",   "hrAllowedPerGame",   "HR/Game", "HR Allowed/Game"),
    ops_vs_whip = off_vs_def(a, b, t1, t2, "ops_vs_whip", "ops",         "whip",               "OPS", "WHIP"),
    avg_vs_era  = off_vs_def(a, b, t1, t2, "avg_vs_era",  "battingAvg",  "era",                "Batting Avg", "ERA")
  )

  away_off_vs_home_def <- list(
    runs        = off_vs_def(b, a, t2, t1, "runs",        "runsPerGame", "runsAllowedPerGame", "Runs/Game", "Runs Allowed/Game"),
    hits        = off_vs_def(b, a, t2, t1, "hits",        "hitsPerGame", "hitsAllowedPerGame", "Hits/Game", "Hits Allowed/Game"),
    hr          = off_vs_def(b, a, t2, t1, "hr",          "hrPerGame",   "hrAllowedPerGame",   "HR/Game", "HR Allowed/Game"),
    ops_vs_whip = off_vs_def(b, a, t2, t1, "ops_vs_whip", "ops",         "whip",               "OPS", "WHIP"),
    avg_vs_era  = off_vs_def(b, a, t2, t1, "avg_vs_era",  "battingAvg",  "era",                "Batting Avg", "ERA")
  )

  list(
    sideBySide = list(offense = batting_side, defense = pitching_side, overall = fielding_side),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  )
}

# ============================================================================
# STEP 3: Group the postseason events into series
# ============================================================================
cat("\n3. Grouping postseason events into series...\n")

series_map <- list()

for (ev in playoff_events) {
  comp <- ev$competitions[[1]]
  if (length(comp$competitors) != 2) next

  notes_hl <- if (length(comp$notes) > 0 && !is.null(comp$notes[[1]]$headline))
    comp$notes[[1]]$headline else ""
  rinfo <- parse_playoff_round(notes_hl)
  if (is.na(rinfo$roundNumber)) next

  t1 <- comp$competitors[[1]]
  t2 <- comp$competitors[[2]]

  # Fall back to the team -> league map when ESPN's headline omits the league
  # (it does for some Wild Card notes).
  league <- rinfo$league
  if ((is.na(league) || is.null(league)) && rinfo$roundNumber < 4) {
    league <- TEAM_LEAGUES[[t1$team$abbreviation]] %||% NA
  }
  if (is.na(league)) next

  team_ids <- sort(as.character(c(t1$team$id, t2$team$id)))
  series_key <- paste(league, rinfo$roundNumber, paste(team_ids, collapse = "_"), sep = "|")

  status_name <- if (!is.null(comp$status$type$name)) comp$status$type$name else "STATUS_SCHEDULED"
  completed <- isTRUE(comp$status$type$completed)

  game_date_str <- ev$date
  if (grepl("T\\d{2}:\\d{2}Z$", game_date_str)) {
    game_date_str <- sub("Z$", ":00Z", game_date_str)
  }

  home_abbrev <- NA
  for (comp_team in comp$competitors) {
    if (!is.null(comp_team$homeAway) && comp_team$homeAway == "home") {
      home_abbrev <- comp_team$team$abbreviation
      break
    }
  }

  # Betting odds, upcoming games only (completed games would just bloat the payload).
  odds_data <- NULL
  if (!completed && !is.null(comp$odds) && length(comp$odds) > 0) {
    o <- comp$odds[[1]]
    home_spread <- if (!is.null(o$homeTeamOdds$spreadOdds)) safe_num(o$homeTeamOdds$spreadOdds)
                   else if (!is.null(o$spread)) safe_num(o$spread) else NA_real_
    home_ml <- if (!is.null(o$homeTeamOdds$moneyLine)) safe_num(o$homeTeamOdds$moneyLine) else NA_real_
    away_ml <- if (!is.null(o$awayTeamOdds$moneyLine)) safe_num(o$awayTeamOdds$moneyLine) else NA_real_
    over_under <- if (!is.null(o$overUnder)) safe_num(o$overUnder) else NA_real_
    raw_details <- if (!is.null(o$details)) as.character(o$details) else NA_character_
    provider <- if (!is.null(o$provider$name)) as.character(o$provider$name) else NA_character_

    # MLB's run line is effectively fixed at 1.5, and ESPN's `details` string
    # always leads with the favored team, so rebuild it as "ATL -1.5".
    spread_team <- NA_character_
    if (!is.na(raw_details) && nchar(raw_details) > 0) {
      tokens <- strsplit(raw_details, "\\s+")[[1]]
      if (length(tokens) > 0 &&
          tokens[1] %in% c(t1$team$abbreviation, t2$team$abbreviation)) {
        spread_team <- tokens[1]
      }
    }
    if (is.na(spread_team) && is_valid_value(home_ml) && is_valid_value(away_ml) && home_ml != away_ml) {
      spread_team <- if (home_ml < away_ml) home_abbrev
                     else if (identical(home_abbrev, t1$team$abbreviation)) t2$team$abbreviation
                     else t1$team$abbreviation
    }
    spread_magnitude <- if (is_valid_value(home_spread) && home_spread != 0) abs(home_spread) else 1.5
    details <- if (!is.na(spread_team)) sprintf("%s -%s", spread_team, format(spread_magnitude, nsmall = 1))
               else NA_character_

    if (is_valid_value(home_spread) || is_valid_value(over_under) || is_valid_value(details)) {
      odds_data <- list(
        provider = if (is_valid_value(provider)) provider else NULL,
        details = if (is_valid_value(details)) details else NULL,
        homeSpread = if (is_valid_value(home_spread)) home_spread else NULL,
        overUnder = if (is_valid_value(over_under)) over_under else NULL,
        homeMoneyLine = if (is_valid_value(home_ml)) as.integer(home_ml) else NULL,
        awayMoneyLine = if (is_valid_value(away_ml)) as.integer(away_ml) else NULL
      )
    }
  }

  game_record <- list(
    game_id = ev$id,
    game_date = game_date_str,
    status = status_name,
    completed = completed,
    homeTeamAbbrev = home_abbrev,
    team1_id = t1$team$id,
    team1_abbrev = t1$team$abbreviation,
    team1_name = t1$team$displayName,
    team1_logo = if (!is.null(t1$team$logo)) t1$team$logo else NA,
    team1_score = safe_num(t1$score),
    team1_winner = isTRUE(t1$winner),
    team2_id = t2$team$id,
    team2_abbrev = t2$team$abbreviation,
    team2_name = t2$team$displayName,
    team2_logo = if (!is.null(t2$team$logo)) t2$team$logo else NA,
    team2_score = safe_num(t2$score),
    team2_winner = isTRUE(t2$winner),
    headline = notes_hl,
    odds = odds_data
  )

  if (is.null(series_map[[series_key]])) {
    series_map[[series_key]] <- list(
      league = league,
      roundNumber = rinfo$roundNumber,
      roundName = rinfo$roundName,
      bestOf = round_best_of(rinfo$roundNumber),
      team_ids = team_ids,
      team_abbrevs = sort(c(t1$team$abbreviation, t2$team$abbreviation)),
      games = list()
    )
  }
  series_map[[series_key]]$games[[length(series_map[[series_key]]$games) + 1]] <- game_record
}

# Sort each series' games chronologically — the scoreboard is fetched day by
# day, but a postponed game can land out of order.
for (k in names(series_map)) {
  games <- series_map[[k]]$games
  if (length(games) > 1) {
    dates <- vapply(games, function(g) g$game_date %||% "", character(1))
    series_map[[k]]$games <- games[order(dates)]
  }
}

cat("Grouped into", length(series_map), "series\n")

# ============================================================================
# STEP 4: Build the bracket
# ============================================================================
cat("\n4. Building bracket structure...\n")

today <- Sys.Date()
has_playoff_games <- length(series_map) > 0
bracket_status <- if (!has_playoff_games) {
  "PROJECTED"
} else if (today > POSTSEASON_END) {
  "COMPLETED"
} else {
  "IN_PROGRESS"
}

cat("Bracket status:", bracket_status, "\n")

build_series_matchup <- function(team1, team2, league, round_number, round_name,
                                 game_id, series_entry = NULL) {
  best_of <- round_best_of(round_number)
  wins_needed <- best_of %/% 2 + 1

  # Reset state in case a team was carried over from an earlier round as winner.
  team1$isWinner <- FALSE
  team2$isWinner <- FALSE
  team1$seriesWins <- NULL
  team2$seriesWins <- NULL

  game_status <- "PROJECTED"
  series_summary <- NULL
  games_list <- list()

  # Match a competitor to a bracket team by ESPN numeric id first, falling back
  # to abbreviation so wins still count for teams built from a minimal record.
  competitor_matches_team <- function(comp_id, comp_abbrev, team) {
    if (!is.null(team$abbreviation_id) && length(team$abbreviation_id) > 0 &&
        !is.null(comp_id) && length(comp_id) > 0 &&
        as.character(comp_id) == as.character(team$abbreviation_id)) return(TRUE)
    if (!is.null(team$abbreviation) && length(team$abbreviation) > 0 &&
        !is.null(comp_abbrev) && length(comp_abbrev) > 0 &&
        as.character(comp_abbrev) == as.character(team$abbreviation)) return(TRUE)
    FALSE
  }

  if (!is.null(series_entry)) {
    count_wins <- function(team) {
      sum(vapply(series_entry$games, function(g) {
        if (!isTRUE(g$completed)) return(0L)
        if (competitor_matches_team(g$team1_id, g$team1_abbrev, team) && isTRUE(g$team1_winner)) return(1L)
        if (competitor_matches_team(g$team2_id, g$team2_abbrev, team) && isTRUE(g$team2_winner)) return(1L)
        0L
      }, integer(1)))
    }

    t1_wins <- count_wins(team1)
    t2_wins <- count_wins(team2)

    team1$seriesWins <- as.integer(t1_wins)
    team2$seriesWins <- as.integer(t2_wins)

    if (t1_wins > 0 || t2_wins > 0) {
      series_summary <- sprintf("%s %d - %d", team1$abbreviation, t1_wins, t2_wins)
    }
    if (t1_wins >= wins_needed || t2_wins >= wins_needed) {
      game_status <- "FINAL"
      if (t1_wins >= wins_needed) team1$isWinner <- TRUE else team2$isWinner <- TRUE
    } else if (t1_wins > 0 || t2_wins > 0) {
      game_status <- "IN_PROGRESS"
    } else {
      game_status <- "SCHEDULED"
    }

    # Normalize each game so team1 always refers to the matchup's team1 (the
    # left column in the app's series tab). ESPN can list home/away in either
    # order across games in the same series, which would otherwise put the
    # per-game winner indicator on the wrong side.
    games_list <- lapply(series_entry$games, function(g) {
      swap <- competitor_matches_team(g$team2_id, g$team2_abbrev, team1) ||
              competitor_matches_team(g$team1_id, g$team1_abbrev, team2)
      first <- if (swap) list(abbreviation = g$team2_abbrev, score = g$team2_score, winner = g$team2_winner)
               else      list(abbreviation = g$team1_abbrev, score = g$team1_score, winner = g$team1_winner)
      second <- if (swap) list(abbreviation = g$team1_abbrev, score = g$team1_score, winner = g$team1_winner)
                else      list(abbreviation = g$team2_abbrev, score = g$team2_score, winner = g$team2_winner)
      list(
        gameId = g$game_id,
        gameDate = g$game_date,
        status = g$status,
        completed = g$completed,
        headline = g$headline,
        homeTeamAbbrev = g$homeTeamAbbrev,
        team1 = first,
        team2 = second,
        odds = g$odds
      )
    })
  }

  list(
    gameId = game_id,
    conference = league,
    roundNumber = round_number,
    roundName = round_name,
    gameStatus = game_status,
    seriesSummary = series_summary,
    bestOf = as.integer(best_of),
    team1 = team1,
    team2 = team2,
    winner = if (isTRUE(team1$isWinner)) team1$name
             else if (isTRUE(team2$isWinner)) team2$name
             else NULL,
    games = games_list,
    comparisons = build_comparisons(team1, team2),
    regularSeasonHistory = build_regular_season_history(team1$abbreviation, team2$abbreviation)
  )
}

empty_matchup <- function(game_id, league, round_number, round_name) {
  list(
    gameId = game_id,
    conference = league,
    roundNumber = round_number,
    roundName = round_name,
    gameStatus = "TBD",
    bestOf = as.integer(round_best_of(round_number)),
    team1 = NULL, team2 = NULL, winner = NULL,
    games = list(), comparisons = NULL
  )
}

seeded_team <- function(league, seed) {
  df <- seeds[[league]]
  if (!is.data.frame(df) || nrow(df) == 0) return(NULL)
  row <- df[df$seed == seed, ]
  if (nrow(row) == 0) return(NULL)
  row <- row[1, ]
  rec <- list(
    wins = as.integer(row$wins %||% NA),
    losses = as.integer(row$losses %||% NA),
    conference = league
  )
  tm <- build_team(row$team_name, row$team_abbrev, row$team_logo, seed = seed, record = rec)
  tm$abbreviation_id <- row$team_id
  tm
}

# Build a team by abbreviation: reuse a prior-round winner (which already has
# full stats attached) when we have one, otherwise rebuild from standings.
team_by_abbrev <- function(league, abbrev, prior_winners = list()) {
  for (w in prior_winners) {
    if (!is.null(w) && identical(w$abbreviation, abbrev)) return(w)
  }
  df <- seeds[[league]]
  if (is.data.frame(df) && nrow(df) > 0) {
    row <- df[df$team_abbrev == abbrev, ]
    if (nrow(row) > 0) {
      row <- row[1, ]
      tm <- build_team(row$team_name, abbrev, row$team_logo, seed = row$seed,
                       record = list(wins = as.integer(row$wins %||% NA),
                                     losses = as.integer(row$losses %||% NA),
                                     conference = league))
      tm$abbreviation_id <- row$team_id
      return(tm)
    }
  }
  build_team(abbrev, abbrev, NA, seed = NULL, record = list(conference = league))
}

find_series_by_abbrevs <- function(league, round_number, abbrev_a, abbrev_b) {
  key_abbrevs <- sort(c(abbrev_a, abbrev_b))
  for (k in names(series_map)) {
    s <- series_map[[k]]
    if (identical(s$league, league) && s$roundNumber == round_number &&
        !is.null(s$team_abbrevs) && identical(s$team_abbrevs, key_abbrevs)) return(s)
  }
  # The World Series is league-less, so fall back to matching on round alone.
  if (round_number == 4) {
    for (k in names(series_map)) {
      s <- series_map[[k]]
      if (s$roundNumber == 4 && !is.null(s$team_abbrevs) &&
          identical(s$team_abbrevs, key_abbrevs)) return(s)
    }
  }
  NULL
}

winner_of <- function(matchup) {
  if (is.null(matchup)) return(NULL)
  if (isTRUE(matchup$team1$isWinner)) return(matchup$team1)
  if (isTRUE(matchup$team2$isWinner)) return(matchup$team2)
  NULL
}

leagues_out <- list()

for (lg in c("AL", "NL")) {
  cat("Building", lg, "bracket...\n")
  lg_lower <- tolower(lg)

  # ---- Wild Card round -----------------------------------------------------
  espn_wc_series <- Filter(function(s) identical(s$league, lg) && s$roundNumber == 1, series_map)

  wc_games <- vector("list", length(WILD_CARD_SEEDS))
  wc_winners <- vector("list", length(WILD_CARD_SEEDS))

  seed_pair_to_slot <- function(seed_a, seed_b) {
    if (is.na(seed_a) || is.na(seed_b)) return(NA_integer_)
    pair <- sort(as.integer(c(seed_a, seed_b)))
    for (idx in seq_along(WILD_CARD_SEEDS)) {
      if (identical(sort(as.integer(WILD_CARD_SEEDS[[idx]])), pair)) return(idx)
    }
    NA_integer_
  }

  seed_of <- function(abbrev) {
    df <- seeds[[lg]]
    if (!is.data.frame(df) || nrow(df) == 0) return(NA_integer_)
    row <- df[df$team_abbrev == abbrev, ]
    if (nrow(row) == 0) return(NA_integer_)
    as.integer(row$seed[1])
  }

  build_wc_matchup <- function(se, slot_idx) {
    abbrevs <- se$team_abbrevs
    # Order by seed so the higher seed is always team1.
    sa <- seed_of(abbrevs[1]); sb <- seed_of(abbrevs[2])
    if (!is.na(sa) && !is.na(sb) && sb < sa) abbrevs <- rev(abbrevs)
    t1 <- team_by_abbrev(lg, abbrevs[1])
    t2 <- team_by_abbrev(lg, abbrevs[2])
    mu <- build_series_matchup(t1, t2, lg, 1, "Wild Card Series",
                               paste0("mlb_", lg_lower, "_wc_", slot_idx), se)
    wc_games[[slot_idx]] <<- mu
    wc_winners[slot_idx] <<- list(winner_of(mu))
  }

  if (length(espn_wc_series) > 0) {
    unslotted <- list()
    for (se in espn_wc_series) {
      slot <- seed_pair_to_slot(seed_of(se$team_abbrevs[1]), seed_of(se$team_abbrevs[2]))
      if (!is.na(slot) && is.null(wc_games[[slot]])) build_wc_matchup(se, slot)
      else unslotted[[length(unslotted) + 1]] <- se
    }
    # Anything we couldn't slot by seed (missing standings, a tiebreaker game
    # ESPN labels oddly) drops into the first free slot so the bracket still renders.
    if (length(unslotted) > 0) {
      empty_slots <- which(vapply(wc_games, is.null, logical(1)))
      for (i in seq_along(unslotted)) {
        if (i > length(empty_slots)) break
        build_wc_matchup(unslotted[[i]], empty_slots[i])
      }
    }
  }

  # Fall back to projecting the remaining Wild Card slots from the standings.
  for (i in seq_along(WILD_CARD_SEEDS)) {
    if (!is.null(wc_games[[i]])) next
    pair <- WILD_CARD_SEEDS[[i]]
    t1 <- seeded_team(lg, pair[1])
    t2 <- seeded_team(lg, pair[2])
    if (is.null(t1) || is.null(t2)) {
      wc_games[[i]] <- empty_matchup(paste0("mlb_", lg_lower, "_wc_", i), lg, 1, "Wild Card Series")
      wc_winners[i] <- list(NULL)
      next
    }
    se <- find_series_by_abbrevs(lg, 1, t1$abbreviation, t2$abbreviation)
    mu <- build_series_matchup(t1, t2, lg, 1, "Wild Card Series",
                               paste0("mlb_", lg_lower, "_wc_", i), se)
    wc_games[[i]] <- mu
    wc_winners[i] <- list(winner_of(mu))
  }

  # ---- Division Series -----------------------------------------------------
  # Slot i pairs the bye seed (1 or 2) with the winner of Wild Card slot i.
  espn_ds_series <- Filter(function(s) identical(s$league, lg) && s$roundNumber == 2, series_map)

  ds_games <- vector("list", length(DIVISION_BYE_SEEDS))
  ds_winners <- vector("list", length(DIVISION_BYE_SEEDS))

  # Prefer ESPN's actual Division Series, slotted by which bye seed is in it.
  if (length(espn_ds_series) > 0) {
    unslotted <- list()
    for (se in espn_ds_series) {
      abbrevs <- se$team_abbrevs
      slot <- NA_integer_
      for (i in seq_along(DIVISION_BYE_SEEDS)) {
        bye <- seeded_team(lg, DIVISION_BYE_SEEDS[i])
        if (!is.null(bye) && bye$abbreviation %in% abbrevs) { slot <- i; break }
      }
      if (!is.na(slot) && is.null(ds_games[[slot]])) {
        bye_abbrev <- seeded_team(lg, DIVISION_BYE_SEEDS[slot])$abbreviation
        other <- abbrevs[abbrevs != bye_abbrev][1]
        t1 <- team_by_abbrev(lg, bye_abbrev, wc_winners)
        t2 <- team_by_abbrev(lg, other, wc_winners)
        mu <- build_series_matchup(t1, t2, lg, 2, "Division Series",
                                   paste0("mlb_", lg_lower, "_ds_", slot), se)
        ds_games[[slot]] <- mu
        ds_winners[slot] <- list(winner_of(mu))
      } else {
        unslotted[[length(unslotted) + 1]] <- se
      }
    }
    if (length(unslotted) > 0) {
      empty_slots <- which(vapply(ds_games, is.null, logical(1)))
      for (i in seq_along(unslotted)) {
        if (i > length(empty_slots)) break
        se <- unslotted[[i]]
        slot <- empty_slots[i]
        t1 <- team_by_abbrev(lg, se$team_abbrevs[1], wc_winners)
        t2 <- team_by_abbrev(lg, se$team_abbrevs[2], wc_winners)
        mu <- build_series_matchup(t1, t2, lg, 2, "Division Series",
                                   paste0("mlb_", lg_lower, "_ds_", slot), se)
        ds_games[[slot]] <- mu
        ds_winners[slot] <- list(winner_of(mu))
      }
    }
  }

  # Fill remaining Division Series slots from the bye seed + Wild Card winner.
  for (i in seq_along(DIVISION_BYE_SEEDS)) {
    if (!is.null(ds_games[[i]])) next
    bye <- seeded_team(lg, DIVISION_BYE_SEEDS[i])
    opponent <- wc_winners[[i]]
    if (is.null(bye) || is.null(opponent)) {
      ds_games[[i]] <- empty_matchup(paste0("mlb_", lg_lower, "_ds_", i), lg, 2, "Division Series")
      ds_winners[i] <- list(NULL)
      next
    }
    se <- find_series_by_abbrevs(lg, 2, bye$abbreviation, opponent$abbreviation)
    mu <- build_series_matchup(bye, opponent, lg, 2, "Division Series",
                               paste0("mlb_", lg_lower, "_ds_", i), se)
    ds_games[[i]] <- mu
    ds_winners[i] <- list(winner_of(mu))
  }

  # ---- League Championship Series -----------------------------------------
  espn_lcs_series <- Filter(function(s) identical(s$league, lg) && s$roundNumber == 3, series_map)

  lcs_game <- NULL
  if (length(espn_lcs_series) > 0) {
    se <- espn_lcs_series[[1]]
    t1 <- team_by_abbrev(lg, se$team_abbrevs[1], ds_winners)
    t2 <- team_by_abbrev(lg, se$team_abbrevs[2], ds_winners)
    lcs_game <- build_series_matchup(t1, t2, lg, 3, "League Championship Series",
                                     paste0("mlb_", lg_lower, "_lcs"), se)
  }
  if (is.null(lcs_game)) {
    a <- ds_winners[[1]]; b <- ds_winners[[2]]
    if (!is.null(a) && !is.null(b)) {
      se <- find_series_by_abbrevs(lg, 3, a$abbreviation, b$abbreviation)
      lcs_game <- build_series_matchup(a, b, lg, 3, "League Championship Series",
                                       paste0("mlb_", lg_lower, "_lcs"), se)
    } else {
      lcs_game <- empty_matchup(paste0("mlb_", lg_lower, "_lcs"), lg, 3, "League Championship Series")
    }
  }

  leagues_out[[length(leagues_out) + 1]] <- list(
    name = lg,
    colorHex = LEAGUE_COLORS[[lg]],
    rounds = list(
      list(roundNumber = 1, roundName = "Wild Card Series",          games = wc_games),
      list(roundNumber = 2, roundName = "Division Series",           games = ds_games),
      list(roundNumber = 3, roundName = "League Championship Series", games = list(lcs_game))
    ),
    champion = winner_of(lcs_game)
  )
}

# ---- World Series ----------------------------------------------------------
al_champ <- leagues_out[[1]]$champion
nl_champ <- leagues_out[[2]]$champion

world_series <- if (!is.null(al_champ) && !is.null(nl_champ)) {
  se <- find_series_by_abbrevs("Finals", 4, al_champ$abbreviation, nl_champ$abbreviation)
  build_series_matchup(al_champ, nl_champ, "Finals", 4, "World Series", "mlb_world_series", se)
} else {
  empty_matchup("mlb_world_series", "Finals", 4, "World Series")
}

# ============================================================================
# STEP 5: Emit JSON and upload
# ============================================================================
cat("\n5. Emitting bracket JSON...\n")

title <- paste0(MLB_SEASON, " MLB Playoff Bracket")
subtitle <- switch(bracket_status,
                   PROJECTED = "Projected bracket based on current standings",
                   IN_PROGRESS = format(today, "%B %d, %Y"),
                   COMPLETED = "Postseason complete",
                   format(today, "%B %d, %Y"))

output_data <- list(
  sport = "MLB",
  visualizationType = "MLB_PLAYOFF_BRACKET",
  title = title,
  subtitle = subtitle,
  description = paste0(
    "MLB playoff bracket with matchup statistics. Each matchup includes ",
    "regular-season team stats, league-wide rankings, head-to-head history, ",
    "and per-game results for every series game played.\n\n",
    "SERIES LENGTHS:\n\n",
    " • Wild Card Series: best-of-3, all games at the higher seed. Seeds 1 and 2 have a bye.\n\n",
    " • Division Series: best-of-5.\n\n",
    " • League Championship Series: best-of-7.\n\n",
    " • World Series: best-of-7.\n\n",
    "BATTING:\n\n",
    " • Runs/Game: Average runs scored per game. Higher is better.\n\n",
    " • Batting Avg: Hits per at-bat. Higher is better.\n\n",
    " • On-Base %: Rate of reaching base safely. Higher is better.\n\n",
    " • Slugging %: Total bases per at-bat. Higher is better.\n\n",
    " • OPS: On-base percentage plus slugging percentage. Higher is better.\n\n",
    " • Hits/Game: Average hits per game. Higher is better.\n\n",
    " • HR/Game: Average home runs per game. Higher is better.\n\n",
    " • RBI/Game: Average runs batted in per game. Higher is better.\n\n",
    " • SB/Game: Average stolen bases per game. Higher is better.\n\n",
    "PITCHING:\n\n",
    " • ERA: Earned runs allowed per 9 innings. Lower is better.\n\n",
    " • WHIP: Walks plus hits allowed per inning pitched. Lower is better.\n\n",
    " • K/9: Strikeouts per 9 innings pitched. Higher is better.\n\n",
    " • BB/9: Walks per 9 innings pitched. Lower is better.\n\n",
    " • Runs Allowed/Game: Average runs allowed per game. Lower is better.\n\n",
    " • Hits Allowed/Game: Average hits allowed per game. Lower is better.\n\n",
    " • HR Allowed/Game: Average home runs allowed per game. Lower is better.\n\n",
    "FIELDING:\n\n",
    " • Fielding %: Share of fielding chances handled cleanly. Higher is better.\n\n",
    " • Errors/Game: Average fielding errors per game. Lower is better.\n\n",
    "TRENDS:\n\n",
    " • One Month Trend ranks the last 30 days of the regular season across all 30 teams.\n\n",
    " • Playoff Trend ranks postseason games only, across the postseason field."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN",
  tags = list(
    list(label = "playoffs", layout = "left", color = "#4CAF50"),
    list(label = "bracket",  layout = "left", color = "#FF9800")
  ),
  sortOrder = -1,
  season = MLB_SEASON,
  bracketStatus = bracket_status,
  leagueCumRunDiffStats = league_cum_run_diff_stats,
  leagueWeeklyStats = league_weekly_stats,
  scatterPlotQuadrants = list(
    topRight = list(label = "High Scoring Shootouts", color = "#FF9800", lightModeColor = "#FF9800"),
    topLeft = list(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
    bottomLeft = list(label = "Pitching Duels", color = "#2196F3", lightModeColor = "#2196F3"),
    bottomRight = list(label = "Behind Both Ends", color = "#F44336", lightModeColor = "#F44336")
  ),
  conferences = leagues_out,
  finals = world_series
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

# Persist history of series for resilience across restarts
history <- load_bracket_history()
if (is.null(history)) history <- list(series = list(), lastUpdated = NULL)
for (k in names(series_map)) history$series[[k]] <- series_map[[k]]
history$lastUpdated <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
save_bracket_history(history)

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (nzchar(s3_bucket)) {
  s3_key <- paste0(S3_PREFIX, "/mlb__playoff_bracket.json")
  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path),
               "--content-type application/json")
  result <- system(cmd)
  if (result != 0) stop("Failed to upload to S3")
  cat("Uploaded to S3:", s3_path, "\n")

  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_ts <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
    s3_key, utc_ts, title
  )
  ddb_cmd <- sprintf('aws dynamodb put-item --table-name %s --item %s',
                     shQuote(dynamodb_table), shQuote(item))
  ddb_res <- system(ddb_cmd)
  if (ddb_res != 0) cat("Warning: Failed to update DynamoDB\n")
  else cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
} else {
  dev_out <- "/tmp/mlb_playoff_bracket.json"
  file.copy(tmp_file, dev_out, overwrite = TRUE)
  cat("Development mode - output written to:", dev_out, "\n")
}

cat("\n=== MLB Playoff Bracket generation complete ===\n")
