#!/usr/bin/env Rscript

# MLB Matchup Stats generator.
# Fetches games from ESPN (past DAYS_BEHIND + future DAYS_AHEAD),
# builds team stats from ESPN team statistics endpoint,
# and outputs matchup comparisons in the same format as NBA/NHL matchup scripts.

library(httr)
library(dplyr)
library(tidyr)
library(jsonlite)
library(lubridate)

# ============================================================================
# Constants
# ============================================================================
DAYS_AHEAD <- 7
DAYS_BEHIND <- 4
MAX_RESULTS_GAMES <- 75  # Max completed games to fetch box scores for

# ============================================================================
# Helpers
# ============================================================================
is_valid_value <- function(x) !is.null(x) && length(x) > 0 && !is.na(x[1])
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

add_api_delay <- function() Sys.sleep(0.5)
safe_num <- function(x) if (is_valid_value(x)) as.numeric(x) else NA_real_

# Division / league mapping
TEAM_DIVISIONS <- c(
  "ARI" = "NL West", "ATL" = "NL East", "BAL" = "AL East", "BOS" = "AL East",
  "CHC" = "NL Central", "CWS" = "AL Central", "CIN" = "NL Central", "CLE" = "AL Central",
  "COL" = "NL West", "DET" = "AL Central", "HOU" = "AL West", "KC" = "AL Central",
  "LAA" = "AL West", "LAD" = "NL West", "MIA" = "NL East", "MIL" = "NL Central",
  "MIN" = "AL Central", "NYM" = "NL East", "NYY" = "AL East", "ATH" = "AL West",
  "PHI" = "NL East", "PIT" = "NL Central", "SD" = "NL West", "SF" = "NL West",
  "SEA" = "AL West", "STL" = "NL Central", "TB" = "AL East", "TEX" = "AL West",
  "TOR" = "AL East", "WSH" = "NL East"
)

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

cat("=== MLB Matchup Stats Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")

# ============================================================================
# STEP 1: Load team stats from ESPN team statistics endpoint
# ============================================================================
cat("\n1. Loading team stats from ESPN...\n")

# Get all team abbreviations
all_teams_resp <- tryCatch(GET("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/teams"), error = function(e) NULL)
team_abbrevs <- c()
if (!is.null(all_teams_resp) && status_code(all_teams_resp) == 200) {
  teams_data <- content(all_teams_resp, as = "parsed")
  team_list <- teams_data$sports[[1]]$leagues[[1]]$teams
  for (t in team_list) {
    team_abbrevs <- c(team_abbrevs, t$team$abbreviation)
  }
}
cat("Found", length(team_abbrevs), "teams\n")

# Fetch stats per team
team_stats_list <- list()
for (abbrev in team_abbrevs) {
  add_api_delay()
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/teams/", abbrev, "/statistics")
  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (is.null(resp) || status_code(resp) != 200) next

  data <- content(resp, as = "parsed")
  cats <- data$results$stats$categories
  if (is.null(cats)) next

  get_stat <- function(category, stat_name) {
    for (cat in cats) {
      if (cat$name == category) {
        for (s in cat$stats) {
          if (s$name == stat_name) return(safe_num(s$value))
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
    # Batting
    runs = get_stat("batting", "runs"),
    hits = get_stat("batting", "hits"),
    home_runs = get_stat("batting", "homeRuns"),
    rbis = get_stat("batting", "RBIs"),
    stolen_bases = get_stat("batting", "stolenBases"),
    batting_avg = get_stat("batting", "avg"),
    on_base_pct = get_stat("batting", "onBasePct"),
    slugging_pct = get_stat("batting", "slugAvg"),
    ops = get_stat("batting", "OPS"),
    at_bats = get_stat("batting", "atBats"),
    strikeouts_batting = get_stat("batting", "strikeouts"),
    walks_batting = get_stat("batting", "walks"),
    # Pitching
    era = get_stat("pitching", "ERA"),
    whip = get_stat("pitching", "WHIP"),
    quality_starts = get_stat("pitching", "qualityStarts"),
    saves = get_stat("pitching", "saves"),
    strikeouts_pitching = get_stat("pitching", "strikeouts"),
    walks_pitching = get_stat("pitching", "walks"),
    innings_pitched = get_stat("pitching", "innings"),
    hits_allowed = get_stat("pitching", "hits"),
    runs_allowed = get_stat("pitching", "runs"),
    home_runs_allowed = get_stat("pitching", "homeRuns"),
    # Fielding
    errors = get_stat("fielding", "errors"),
    fielding_pct = get_stat("fielding", "fieldingPct")
  )
  cat("  Loaded stats for", abbrev, "\n")
}

cat("Loaded stats for", length(team_stats_list), "teams\n")

# Convert to data frame and compute per-game stats + ranks
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
    division = TEAM_DIVISIONS[team_abbreviation],
    league = TEAM_LEAGUES[team_abbreviation]
  )

# Compute ranks
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
# STEP 1b: Fetch season game results (for trends + H2H)
# ============================================================================
cat("\n1b. Fetching season game results...\n")

TREND_DAYS <- 30
SEASON_START <- as.Date(paste0(format(Sys.Date(), "%Y"), "-03-15"))  # approx Opening Day window
season_fetch_start <- max(SEASON_START, Sys.Date() - days(180))  # cap at 180 days
season_fetch_end <- Sys.Date() - days(1)  # yesterday

# Fetch completed game scores from ESPN scoreboard
# season_game_results: one entry per game (not per team) for H2H grouping
season_game_results <- list()
# trend_games: one entry per team-game for trend stats
trend_games <- list()
fetch_date <- season_fetch_start

extract_team_stats <- function(competitor) {
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

while (fetch_date <= season_fetch_end) {
  date_str <- format(fetch_date, "%Y%m%d")
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard?dates=", date_str)
  add_api_delay()

  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (!is.null(resp) && status_code(resp) == 200) {
    data <- content(resp, as = "parsed")
    if (!is.null(data$events)) {
      for (ev in data$events) {
        comp <- ev$competitions[[1]]
        if (length(comp$competitors) != 2) next
        if (!isTRUE(comp$status$type$completed)) next

        home <- NULL; away <- NULL
        for (ct in comp$competitors) {
          if (ct$homeAway == "home") home <- ct else away <- ct
        }
        if (is.null(home) || is.null(away)) next

        home_score <- safe_num(home$score)
        away_score <- safe_num(away$score)
        if (is.na(home_score) || is.na(away_score)) next

        home_abbrev <- home$team$abbreviation
        away_abbrev <- away$team$abbreviation
        game_date_str <- format(fetch_date, "%Y-%m-%d")
        winner <- if (home_score > away_score) home_abbrev else away_abbrev

        # Store one entry per game for H2H
        season_game_results[[length(season_game_results) + 1]] <- list(
          date = game_date_str,
          home_team = home_abbrev,
          away_team = away_abbrev,
          home_score = home_score,
          away_score = away_score,
          winner = winner
        )

        # Store per-team entries for trend (only last 30 days)
        if (fetch_date >= (Sys.Date() - days(TREND_DAYS))) {
          home_box <- extract_team_stats(home)
          away_box <- extract_team_stats(away)

          trend_games[[length(trend_games) + 1]] <- list(
            team = home_abbrev, opponent = away_abbrev,
            runs_scored = home_score, runs_allowed = away_score,
            won = home_score > away_score,
            hits = safe_num(home_box[["hits"]]), hrs = safe_num(home_box[["homeRuns"]]),
            date = game_date_str
          )
          trend_games[[length(trend_games) + 1]] <- list(
            team = away_abbrev, opponent = home_abbrev,
            runs_scored = away_score, runs_allowed = home_score,
            won = away_score > home_score,
            hits = safe_num(away_box[["hits"]]), hrs = safe_num(away_box[["homeRuns"]]),
            date = game_date_str
          )
        }
      }
    }
  }
  fetch_date <- fetch_date + days(1)
}

cat("Fetched", length(season_game_results), "season games,", length(trend_games), "trend entries\n")

# Build season games data frame for H2H lookups
season_games_df <- if (length(season_game_results) > 0) bind_rows(season_game_results) else data.frame()

# Compute 1-month trend stats per team
month_trend_stats <- NULL
if (length(trend_games) > 0) {
  trend_df <- bind_rows(trend_games)

  month_trend_stats <- trend_df %>%
    group_by(team) %>%
    summarise(
      games_played = n(),
      wins = sum(won, na.rm = TRUE),
      losses = sum(!won, na.rm = TRUE),
      runs_per_game = mean(runs_scored, na.rm = TRUE),
      runs_allowed_per_game = mean(runs_allowed, na.rm = TRUE),
      run_diff_per_game = mean(runs_scored - runs_allowed, na.rm = TRUE),
      hits_per_game = mean(hits, na.rm = TRUE),
      hrs_per_game = mean(hrs, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    mutate(win_pct = wins / (wins + losses))

  for (stat in c("win_pct", "runs_per_game", "run_diff_per_game", "hits_per_game", "hrs_per_game")) {
    rk <- tied_rank(-month_trend_stats[[stat]])
    month_trend_stats[[paste0(stat, "_rank")]] <- rk$rank
    month_trend_stats[[paste0(stat, "_rankDisplay")]] <- rk$rankDisplay
  }
  for (stat in c("runs_allowed_per_game")) {
    rk <- tied_rank(month_trend_stats[[stat]])
    month_trend_stats[[paste0(stat, "_rank")]] <- rk$rank
    month_trend_stats[[paste0(stat, "_rankDisplay")]] <- rk$rankDisplay
  }

  cat("Calculated month trend rankings for", nrow(month_trend_stats), "teams\n")
}

# ============================================================================
# STEP 1c: Calculate weekly performance data for charts
# ============================================================================
cat("\n1c. Calculating weekly performance data for charts...\n")

# Calculate week number from season start
get_week_num <- function(date) {
  as.integer(floor(difftime(as.Date(date), SEASON_START, units = "weeks"))) + 1
}

# Initialize chart data structures
cum_run_diff_by_team <- data.frame(team = character(), week_num = integer(), cum_run_diff = numeric())
weekly_performance <- data.frame(team = character(), week_num = integer(), runs_scored_avg = numeric(), runs_allowed_avg = numeric())
league_cum_run_diff_stats <- list(minCumRunDiff = NA, maxCumRunDiff = NA)
league_weekly_stats <- list()

if (nrow(season_games_df) > 0) {
  # Full season data for cumulative chart
  all_team_games <- bind_rows(
    season_games_df %>% transmute(
      team = home_team,
      runs_scored = home_score,
      runs_allowed = away_score,
      date = date
    ),
    season_games_df %>% transmute(
      team = away_team,
      runs_scored = away_score,
      runs_allowed = home_score,
      date = date
    )
  ) %>%
    mutate(
      run_diff = runs_scored - runs_allowed,
      week_num = sapply(date, get_week_num)
    ) %>%
    filter(week_num > 0, week_num <= 30) %>%
    arrange(date)

  # Cumulative run differential by week (for line chart)
  cum_run_diff_by_team <- all_team_games %>%
    group_by(team) %>%
    arrange(date) %>%
    mutate(cum_run_diff = cumsum(run_diff)) %>%
    group_by(team, week_num) %>%
    summarise(cum_run_diff = last(cum_run_diff), .groups = "drop")

  # Weekly performance (for scatter plot) - last 10 weeks
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

  # League-wide stats for consistent chart scaling
  if (nrow(cum_run_diff_by_team) > 0) {
    # Calculate top 10 threshold for each week (historical)
    # Sort descending within each week and get the 10th best value
    top10_by_week <- cum_run_diff_by_team %>%
      group_by(week_num) %>%
      summarise(
        top10_threshold = if (n() >= 10) {
          sort(cum_run_diff, decreasing = TRUE)[10]
        } else NA_real_,
        .groups = "drop"
      ) %>%
      filter(!is.na(top10_threshold))

    # Convert to named list for JSON output
    top10_by_week_list <- if (nrow(top10_by_week) > 0) {
      setNames(
        as.list(round(top10_by_week$top10_threshold, 0)),
        paste0("week-", top10_by_week$week_num)
      )
    } else {
      setNames(list(), character(0))
    }

    league_cum_run_diff_stats <- list(
      minCumRunDiff = min(cum_run_diff_by_team$cum_run_diff, na.rm = TRUE),
      maxCumRunDiff = max(cum_run_diff_by_team$cum_run_diff, na.rm = TRUE),
      top10ByWeek = top10_by_week_list
    )
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

  cat("Calculated weekly data for", length(unique(cum_run_diff_by_team$team)), "teams\n")
}

# ============================================================================
# H2H builder: find all season games between two teams, group into series
# ============================================================================
build_h2h <- function(team_a, team_b) {
  if (nrow(season_games_df) == 0) return(NULL)

  # Find all games between these two teams (in either home/away direction)
  h2h_games <- season_games_df %>%
    filter(
      (home_team == team_a & away_team == team_b) |
      (home_team == team_b & away_team == team_a)
    ) %>%
    arrange(date)

  if (nrow(h2h_games) == 0) return(NULL)

  # Overall record from team_a's perspective
  a_wins <- sum(h2h_games$winner == team_a)
  b_wins <- sum(h2h_games$winner == team_b)

  # Group consecutive games into series
  # A new series starts when there's a gap of more than 1 day between games
  h2h_games$date_parsed <- as.Date(h2h_games$date)
  h2h_games$series_id <- 1
  if (nrow(h2h_games) > 1) {
    for (i in 2:nrow(h2h_games)) {
      gap <- as.integer(h2h_games$date_parsed[i] - h2h_games$date_parsed[i - 1])
      if (gap > 2) {
        h2h_games$series_id[i] <- h2h_games$series_id[i - 1] + 1
      } else {
        h2h_games$series_id[i] <- h2h_games$series_id[i - 1]
      }
    }
  }

  series_list <- list()
  for (sid in unique(h2h_games$series_id)) {
    sg <- h2h_games %>% filter(series_id == sid)
    start_date <- min(sg$date)
    end_date <- max(sg$date)

    # Format dates for display
    start_d <- as.Date(start_date)
    end_d <- as.Date(end_date)
    if (start_date == end_date) {
      date_range <- format(start_d, "%b %d")
    } else {
      if (format(start_d, "%b") == format(end_d, "%b")) {
        date_range <- paste0(format(start_d, "%b %d"), "-", format(end_d, "%d"))
      } else {
        date_range <- paste0(format(start_d, "%b %d"), " - ", format(end_d, "%b %d"))
      }
    }

    a_series_wins <- sum(sg$winner == team_a)
    b_series_wins <- sum(sg$winner == team_b)

    games_detail <- list()
    for (r in 1:nrow(sg)) {
      row <- sg[r, ]
      games_detail[[r]] <- list(
        date = row$date,
        homeTeam = row$home_team,
        awayTeam = row$away_team,
        homeScore = as.integer(row$home_score),
        awayScore = as.integer(row$away_score),
        winner = row$winner
      )
    }

    series_list[[length(series_list) + 1]] <- list(
      dateRange = date_range,
      startDate = start_date,
      endDate = end_date,
      teamAWins = as.integer(a_series_wins),
      teamBWins = as.integer(b_series_wins),
      games = games_detail
    )
  }

  list(
    teamA = team_a,
    teamB = team_b,
    teamAWins = as.integer(a_wins),
    teamBWins = as.integer(b_wins),
    totalGames = as.integer(nrow(h2h_games)),
    series = series_list
  )
}

# ============================================================================
# STEP 2: Fetch games from ESPN scoreboard
# ============================================================================
cat("\n2. Fetching games...\n")

today <- Sys.Date()
start_date <- today - days(DAYS_BEHIND)
end_date <- today + days(DAYS_AHEAD)

all_games <- list()
current_date <- start_date

while (current_date <= end_date) {
  date_str <- format(current_date, "%Y%m%d")
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard?dates=", date_str)
  add_api_delay()

  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (!is.null(resp) && status_code(resp) == 200) {
    data <- content(resp, as = "parsed")
    if (!is.null(data$events)) {
      for (ev in data$events) {
        comp <- ev$competitions[[1]]
        if (length(comp$competitors) != 2) next

        game_date <- ev$date
        if (grepl("T\\d{2}:\\d{2}Z$", game_date)) game_date <- sub("Z$", ":00Z", game_date)

        status_name <- if (!is.null(comp$status$type$name)) comp$status$type$name else "STATUS_SCHEDULED"
        completed <- isTRUE(comp$status$type$completed)

        home <- NULL; away <- NULL
        for (ct in comp$competitors) {
          if (ct$homeAway == "home") home <- ct else away <- ct
        }
        if (is.null(home) || is.null(away)) next

        home_records <- home$records
        away_records <- away$records
        home_record <- if (length(home_records) > 0) home_records[[1]]$summary else NA
        away_record <- if (length(away_records) > 0) away_records[[1]]$summary else NA

        # Odds
        odds_data <- NULL
        if (!is.null(comp$odds) && length(comp$odds) > 0) {
          o <- comp$odds[[1]]
          home_spread <- safe_num(o$spread)
          over_under <- safe_num(o$overUnder)
          home_ml <- if (!is.null(o$homeTeamOdds$moneyLine)) safe_num(o$homeTeamOdds$moneyLine) else NA
          away_ml <- if (!is.null(o$awayTeamOdds$moneyLine)) safe_num(o$awayTeamOdds$moneyLine) else NA
          raw_details <- if (!is.null(o$details)) as.character(o$details) else NA_character_

          # Build details as the run-line spread (e.g. "ATL -1.5") rather than
          # ESPN's moneyline string. We need two pieces:
          #   1. Which team is favored.
          #   2. The run-line magnitude (almost always 1.5 for MLB).
          #
          # ESPN's `o$spread` is sometimes reported home-relative and sometimes
          # favorite-relative (the latter especially when homeMoneyline /
          # awayMoneyline are null), so it can't be trusted to identify the
          # favored side on its own. ESPN's pre-formatted `o$details` string
          # ("ATL -149") always leads with the favored team abbreviation, so
          # use that as the source of truth and fall back to other signals.
          spread_team <- NA_character_

          # 1) Prefer the leading abbreviation in ESPN's details string, but only
          #    if it matches one of the two competitors (guards against parsing
          #    over/under or unrelated strings).
          if (!is.na(raw_details) && nchar(raw_details) > 0) {
            tokens <- strsplit(raw_details, "\\s+")[[1]]
            if (length(tokens) > 0) {
              candidate <- tokens[1]
              if (!is.na(candidate)
                  && candidate %in% c(home$team$abbreviation, away$team$abbreviation)) {
                spread_team <- candidate
              }
            }
          }

          # 2) Otherwise, use moneylines: the more negative ML is the favorite.
          if (is.na(spread_team)
              && is_valid_value(home_ml) && is_valid_value(away_ml)
              && home_ml != away_ml) {
            spread_team <- if (home_ml < away_ml) home$team$abbreviation
                           else away$team$abbreviation
          }

          # 3) Last resort: treat o$spread as home-relative.
          if (is.na(spread_team) && is_valid_value(home_spread) && home_spread != 0) {
            spread_team <- if (home_spread < 0) home$team$abbreviation
                           else away$team$abbreviation
          }

          # MLB run line is effectively fixed at 1.5 — use abs(spread) when
          # ESPN gives us a non-zero magnitude, otherwise default to 1.5.
          spread_magnitude <-
            if (is_valid_value(home_spread) && home_spread != 0) abs(home_spread)
            else 1.5

          details <- NA_character_
          if (!is.na(spread_team)) {
            details <- sprintf("%s -%s", spread_team, format(spread_magnitude, nsmall = 1))
          }

          if (is_valid_value(home_spread) || is_valid_value(over_under) || is_valid_value(details)) {
            odds_data <- list(
              provider = if (!is.null(o$provider$name)) o$provider$name else NULL,
              spread = if (is_valid_value(home_spread)) home_spread else NULL,
              overUnder = if (is_valid_value(over_under)) over_under else NULL,
              homeMoneyline = if (is_valid_value(home_ml)) as.integer(home_ml) else NULL,
              awayMoneyline = if (is_valid_value(away_ml)) as.integer(away_ml) else NULL,
              details = if (is_valid_value(details)) details else NULL
            )
          }
        }

        # Location
        location_data <- NULL
        if (!is.null(comp$venue)) {
          v <- comp$venue
          location_data <- list(
            stadium = if (!is.null(v$fullName)) v$fullName else NULL,
            city = if (!is.null(v$address) && !is.null(v$address$city)) v$address$city else NULL,
            state = if (!is.null(v$address) && !is.null(v$address$state)) v$address$state else NULL
          )
        }

        all_games[[length(all_games) + 1]] <- list(
          game_id = ev$id,
          game_date = game_date,
          game_name = ev$name,
          game_status = status_name,
          game_completed = completed,
          home_team_id = home$team$id,
          home_team_name = home$team$displayName,
          home_team_abbrev = home$team$abbreviation,
          home_team_logo = if (!is.null(home$team$logo)) home$team$logo else NA,
          home_score = if (completed) safe_num(home$score) else NULL,
          home_record = home_record,
          away_team_id = away$team$id,
          away_team_name = away$team$displayName,
          away_team_abbrev = away$team$abbreviation,
          away_team_logo = if (!is.null(away$team$logo)) away$team$logo else NA,
          away_score = if (completed) safe_num(away$score) else NULL,
          away_record = away_record,
          location = location_data,
          odds = odds_data
        )
      }
    }
  }
  current_date <- current_date + days(1)
}

cat("Found", length(all_games), "games\n")

if (length(all_games) == 0) {
  cat("No games found. Exiting.\n")
  quit(status = 0)
}

# ============================================================================
# STEP 3: Build matchup data
# ============================================================================
cat("\n3. Building matchup data...\n")

stat_val <- function(row, col) {
  if (is.null(row) || !col %in% names(row) || is.na(row[[col]])) return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  list(value = round(as.numeric(row[[col]]), 4),
       rank = as.integer(row[[paste0(col, "_rank")]]),
       rankDisplay = row[[paste0(col, "_rankDisplay")]])
}

build_comparisons <- function(home_stats, away_stats, home_abbrev, away_abbrev) {
  if (is.null(home_stats) || is.null(away_stats)) return(NULL)

  make_side <- function(label, col) {
    list(label = label,
         home = stat_val(home_stats, col),
         away = stat_val(away_stats, col))
  }

  offense <- list(
    runsPerGame = make_side("Runs/Game", "runs_per_game"),
    battingAvg = make_side("Batting Avg", "batting_avg"),
    onBasePct = make_side("On-Base %", "on_base_pct"),
    sluggingPct = make_side("Slugging %", "slugging_pct"),
    ops = make_side("OPS", "ops"),
    hitsPerGame = make_side("Hits/Game", "hits_per_game"),
    hrPerGame = make_side("HR/Game", "hr_per_game"),
    rbiPerGame = make_side("RBI/Game", "rbi_per_game"),
    sbPerGame = make_side("SB/Game", "sb_per_game")
  )

  pitching <- list(
    era = make_side("ERA", "era"),
    whip = make_side("WHIP", "whip"),
    kPer9 = make_side("K/9", "k_per_9"),
    bbPer9 = make_side("BB/9", "bb_per_9"),
    runsAllowedPerGame = make_side("Runs Allowed/Game", "runs_allowed_per_game"),
    hitsAllowedPerGame = make_side("Hits Allowed/Game", "hits_allowed_per_game"),
    hrAllowedPerGame = make_side("HR Allowed/Game", "hr_allowed_per_game")
  )

  defense <- list(
    fieldingPct = make_side("Fielding %", "fielding_pct"),
    errorsPerGame = make_side("Errors/Game", "errors_per_game")
  )

  # Off vs Def (batting vs pitching)
  calc_adv <- function(off_rank, def_rank) {
    if (is.null(off_rank) || is.null(def_rank) || is.na(off_rank) || is.na(def_rank)) return(0)
    if (off_rank < def_rank) return(-1)
    if (off_rank > def_rank) return(1)
    0
  }

  ovd <- function(off_team, def_team, off_stats, def_stats, key, off_col, def_col, off_label, def_label) {
    off_v <- stat_val(off_stats, off_col)
    def_v <- stat_val(def_stats, def_col)
    list(statKey = key, offLabel = off_label, defLabel = def_label,
         offense = list(team = off_team, value = off_v$value, rank = off_v$rank, rankDisplay = off_v$rankDisplay),
         defense = list(team = def_team, value = def_v$value, rank = def_v$rank, rankDisplay = def_v$rankDisplay),
         advantage = calc_adv(off_v$rank, def_v$rank))
  }

  home_off_vs_away_def <- list(
    runs = ovd(home_abbrev, away_abbrev, home_stats, away_stats, "runs", "runs_per_game", "runs_allowed_per_game", "Runs/Game", "Runs Allowed/Game"),
    hits = ovd(home_abbrev, away_abbrev, home_stats, away_stats, "hits", "hits_per_game", "hits_allowed_per_game", "Hits/Game", "Hits Allowed/Game"),
    hr = ovd(home_abbrev, away_abbrev, home_stats, away_stats, "hr", "hr_per_game", "hr_allowed_per_game", "HR/Game", "HR Allowed/Game"),
    ops_vs_whip = ovd(home_abbrev, away_abbrev, home_stats, away_stats, "ops_vs_whip", "ops", "whip", "OPS", "WHIP"),
    avg_vs_era = ovd(home_abbrev, away_abbrev, home_stats, away_stats, "avg_vs_era", "batting_avg", "era", "Batting Avg", "ERA")
  )

  away_off_vs_home_def <- list(
    runs = ovd(away_abbrev, home_abbrev, away_stats, home_stats, "runs", "runs_per_game", "runs_allowed_per_game", "Runs/Game", "Runs Allowed/Game"),
    hits = ovd(away_abbrev, home_abbrev, away_stats, home_stats, "hits", "hits_per_game", "hits_allowed_per_game", "Hits/Game", "Hits Allowed/Game"),
    hr = ovd(away_abbrev, home_abbrev, away_stats, home_stats, "hr", "hr_per_game", "hr_allowed_per_game", "HR/Game", "HR Allowed/Game"),
    ops_vs_whip = ovd(away_abbrev, home_abbrev, away_stats, home_stats, "ops_vs_whip", "ops", "whip", "OPS", "WHIP"),
    avg_vs_era = ovd(away_abbrev, home_abbrev, away_stats, home_stats, "avg_vs_era", "batting_avg", "era", "Batting Avg", "ERA")
  )

  list(
    sideBySide = list(offense = offense, defense = pitching, overall = defense),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  )
}

# ============================================================================
# Helper: fetch MLB box score from ESPN summary endpoint
# ============================================================================
build_mlb_game_results <- function(game, home_season_stats, away_season_stats) {
  game_id <- game$game_id
  cat("  Fetching box score for game", game_id, "...\n")

  home_won <- game$home_score > game$away_score
  winner <- if (home_won) game$home_team_abbrev else game$away_team_abbrev

  result <- list(
    homeScore = as.integer(game$home_score),
    awayScore = as.integer(game$away_score),
    winner = winner,
    margin = as.integer(abs(game$home_score - game$away_score)),
    homeWon = home_won
  )

  add_api_delay()
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/summary?event=", game_id)
  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (is.null(resp) || status_code(resp) != 200) return(result)

  data <- tryCatch(content(resp, as = "parsed"), error = function(e) NULL)
  if (is.null(data) || is.null(data$boxscore)) return(result)

  bs <- data$boxscore
  teams <- bs$teams
  if (is.null(teams) || length(teams) < 2) return(result)

  # Find home and away team data
  home_bs <- NULL; away_bs <- NULL
  for (t in teams) {
    abbrev <- t$team$abbreviation
    if (abbrev == game$home_team_abbrev) home_bs <- t
    else if (abbrev == game$away_team_abbrev) away_bs <- t
  }
  if (is.null(home_bs) || is.null(away_bs)) return(result)

  # Extract stats from a team's boxscore
  extract_box <- function(team_data) {
    batting <- list(); pitching <- list()
    for (sg in team_data$statistics) {
      stats <- list()
      for (s in sg$stats) stats[[s$name]] <- s$displayValue
      if (sg$name == "batting") batting <- stats
      else if (sg$name == "pitching") pitching <- stats
    }
    list(
      # Batting
      runs = safe_num(batting[["runs"]]),
      hits = safe_num(batting[["hits"]]),
      doubles = safe_num(batting[["doubles"]]),
      triples = safe_num(batting[["triples"]]),
      homeRuns = safe_num(batting[["homeRuns"]]),
      rbis = safe_num(batting[["RBIs"]]),
      walks = safe_num(batting[["walks"]]),
      strikeouts = safe_num(batting[["strikeouts"]]),
      stolenBases = safe_num(batting[["stolenBases"]]),
      atBats = safe_num(batting[["atBats"]]),
      avg = batting[["avg"]],
      obp = batting[["onBasePct"]],
      slg = batting[["slugAvg"]],
      ops = batting[["OPS"]],
      runnersLOB = safe_num(batting[["runnersLeftOnBase"]]),
      # Pitching
      era = pitching[["ERA"]],
      pitchingHits = safe_num(pitching[["hits"]]),
      earnedRuns = safe_num(pitching[["earnedRuns"]]),
      pitchingWalks = safe_num(pitching[["walks"]]),
      pitchingStrikeouts = safe_num(pitching[["strikeouts"]]),
      pitchingHomeRuns = safe_num(pitching[["homeRuns"]]),
      pitches = safe_num(pitching[["pitches"]]),
      innings = pitching[["thirdInnings"]]
    )
  }

  home_box <- extract_box(home_bs)
  away_box <- extract_box(away_bs)

  result$teamBoxScore <- list(
    home = home_box,
    away = away_box
  )

  # Compare key stats to season averages
  compare_stat <- function(game_val, season_val) {
    if (is.null(game_val) || is.null(season_val) || is.na(game_val) || is.na(season_val)) return(NULL)
    list(gameValue = round(game_val, 3), seasonAvg = round(season_val, 3), difference = round(game_val - season_val, 3))
  }

  if (!is.null(home_season_stats) && !is.null(away_season_stats)) {
    result$vsSeasonAvg <- list(
      home = list(
        runs = compare_stat(home_box$runs, home_season_stats$runs_per_game),
        hits = compare_stat(home_box$hits, home_season_stats$hits_per_game * home_season_stats$games_played / home_season_stats$games_played),
        homeRuns = compare_stat(home_box$homeRuns, home_season_stats$hr_per_game * home_season_stats$games_played / home_season_stats$games_played),
        strikeoutsBatting = compare_stat(home_box$strikeouts, home_season_stats$strikeouts_batting / home_season_stats$games_played),
        walksBatting = compare_stat(home_box$walks, home_season_stats$walks_batting / home_season_stats$games_played)
      ),
      away = list(
        runs = compare_stat(away_box$runs, away_season_stats$runs_per_game),
        hits = compare_stat(away_box$hits, away_season_stats$hits_per_game * away_season_stats$games_played / away_season_stats$games_played),
        homeRuns = compare_stat(away_box$homeRuns, away_season_stats$hr_per_game * away_season_stats$games_played / away_season_stats$games_played),
        strikeoutsBatting = compare_stat(away_box$strikeouts, away_season_stats$strikeouts_batting / away_season_stats$games_played),
        walksBatting = compare_stat(away_box$walks, away_season_stats$walks_batting / away_season_stats$games_played)
      )
    )
  }

  result
}

# Sort games: most recent completed first, then future games
# This ensures box score fetching prioritizes recent games
all_games <- all_games[order(
  -sapply(all_games, function(g) if (isTRUE(g$game_completed)) 1 else 0),  # completed first
  -sapply(all_games, function(g) as.numeric(as.POSIXct(g$game_date, format="%Y-%m-%dT%H:%M", tz="UTC"))),  # most recent first
  na.last = TRUE
)]

# Compute per-team season high runs from season_game_results
mlb_season_highs <- list()
if (nrow(season_games_df) > 0) {
  # For each team, compute max runs scored across all season games
  home_max <- season_games_df %>%
    group_by(team = home_team) %>%
    summarise(max_runs = max(home_score, na.rm = TRUE), .groups = "drop")
  away_max <- season_games_df %>%
    group_by(team = away_team) %>%
    summarise(max_runs = max(away_score, na.rm = TRUE), .groups = "drop")
  all_max <- bind_rows(home_max, away_max) %>%
    group_by(team) %>%
    summarise(max_runs = max(max_runs, na.rm = TRUE), .groups = "drop")

  # Also compute prior max (max of all games BEFORE each game, per team)
  # Sort all team-game entries by date
  team_runs_by_date <- bind_rows(
    season_games_df %>% transmute(team = home_team, runs = home_score, date = date),
    season_games_df %>% transmute(team = away_team, runs = away_score, date = date)
  ) %>% arrange(date)

  for (t in unique(team_runs_by_date$team)) {
    tg <- team_runs_by_date %>% filter(team == t)
    vals <- tg$runs
    prior_max <- rep(NA_real_, length(vals))
    if (length(vals) > 1) {
      running <- vals[1]
      for (j in 2:length(vals)) {
        prior_max[j] <- running
        running <- max(running, vals[j], na.rm = TRUE)
      }
    }
    mlb_season_highs[[t]] <- list(dates = tg$date, runs = vals, prior_max_runs = prior_max)
  }
}

# Helper: check if a team's box score stats are season highs
check_mlb_season_highs <- function(team_abbrev, game_date, box_score) {
  highs <- list()
  team_data <- mlb_season_highs[[team_abbrev]]
  if (!is.null(team_data)) {
    # Find the matching game by date (approximate - use last entry for that date)
    date_str <- substr(game_date, 1, 10)
    idx <- which(team_data$dates == date_str)
    if (length(idx) > 0) {
      i <- tail(idx, 1)  # Use last match for that date
      runs <- team_data$runs[i]
      prior <- team_data$prior_max_runs[i]
      if (!is.na(runs) && !is.na(prior) && runs > prior) {
        highs[["runs"]] <- list(previousHigh = prior, differential = round(runs - prior, 0))
      }
    }
  }
  # Check box score stats (hits, HR, RBI, etc.) - these are per-game values
  if (!is.null(box_score)) {
    box_stats <- list(
      hits = safe_num(box_score$hits),
      homeRuns = safe_num(box_score$homeRuns),
      rbis = safe_num(box_score$rbis),
      stolenBases = safe_num(box_score$stolenBases)
    )
    # For box score stats, we don't have full season data, so skip season high check
    # (would need to cache all historical box scores)
  }
  if (length(highs) == 0) return(NULL)
  highs
}

results_fetched <- 0
matchups_json <- list()

for (game in all_games) {
  home_row <- team_stats %>% filter(team_abbreviation == game$home_team_abbrev)
  away_row <- team_stats %>% filter(team_abbreviation == game$away_team_abbrev)

  home_s <- if (nrow(home_row) > 0) home_row[1, ] else NULL
  away_s <- if (nrow(away_row) > 0) away_row[1, ] else NULL

  build_team_data <- function(game_prefix, stats_row) {
    abbrev <- game[[paste0(game_prefix, "_team_abbrev")]]
    list(
      id = game[[paste0(game_prefix, "_team_id")]],
      name = game[[paste0(game_prefix, "_team_name")]],
      abbreviation = abbrev,
      logo = game[[paste0(game_prefix, "_team_logo")]],
      record = game[[paste0(game_prefix, "_record")]],
      division = TEAM_DIVISIONS[abbrev] %||% NA,
      league = TEAM_LEAGUES[abbrev] %||% NA,
      stats = if (!is.null(stats_row)) list(
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
      ) else NULL
    )
  }

  home_team_data <- build_team_data("home", home_s)
  away_team_data <- build_team_data("away", away_s)

  # Add 1-month trend to each team's stats
  build_month_trend <- function(abbrev) {
    if (is.null(month_trend_stats)) return(setNames(list(), character(0)))
    row <- month_trend_stats %>% filter(team == abbrev)
    if (nrow(row) == 0) return(setNames(list(), character(0)))
    row <- row[1, ]
    list(
      gamesPlayed = as.integer(row$games_played),
      record = list(
        wins = as.integer(row$wins),
        losses = as.integer(row$losses),
        rank = as.integer(row$win_pct_rank),
        rankDisplay = row$win_pct_rankDisplay
      ),
      runsPerGame = list(
        value = round(row$runs_per_game, 2),
        rank = as.integer(row$runs_per_game_rank),
        rankDisplay = row$runs_per_game_rankDisplay
      ),
      runsAllowedPerGame = list(
        value = round(row$runs_allowed_per_game, 2),
        rank = as.integer(row$runs_allowed_per_game_rank),
        rankDisplay = row$runs_allowed_per_game_rankDisplay
      ),
      runDiffPerGame = list(
        value = round(row$run_diff_per_game, 2),
        rank = as.integer(row$run_diff_per_game_rank),
        rankDisplay = row$run_diff_per_game_rankDisplay
      ),
      hitsPerGame = list(
        value = round(row$hits_per_game, 2),
        rank = as.integer(row$hits_per_game_rank),
        rankDisplay = row$hits_per_game_rankDisplay
      ),
      hrsPerGame = list(
        value = round(row$hrs_per_game, 2),
        rank = as.integer(row$hrs_per_game_rank),
        rankDisplay = row$hrs_per_game_rankDisplay
      )
    )
  }

  if (!is.null(home_team_data$stats)) {
    home_team_data$stats$monthTrend <- build_month_trend(game$home_team_abbrev)

    # Add cumulative run differential by week (for line chart)
    team_cum_run_diff <- cum_run_diff_by_team %>%
      filter(team == game$home_team_abbrev) %>%
      select(week_num, cum_run_diff)

    home_team_data$stats$cumRunDiffByWeek <- if (nrow(team_cum_run_diff) > 0) {
      setNames(
        as.list(round(team_cum_run_diff$cum_run_diff, 0)),
        paste0("week-", team_cum_run_diff$week_num)
      )
    } else {
      setNames(list(), character(0))
    }

    # Add weekly performance (for scatter plot)
    team_weekly_perf <- weekly_performance %>%
      filter(team == game$home_team_abbrev) %>%
      select(week_num, runs_scored_avg, runs_allowed_avg)

    home_team_data$stats$performanceByWeek <- if (nrow(team_weekly_perf) > 0) {
      setNames(
        lapply(1:nrow(team_weekly_perf), function(i) {
          list(
            runsScored = round(team_weekly_perf$runs_scored_avg[i], 2),
            runsAllowed = round(team_weekly_perf$runs_allowed_avg[i], 2)
          )
        }),
        paste0("week-", team_weekly_perf$week_num)
      )
    } else {
      setNames(list(), character(0))
    }
  }
  if (!is.null(away_team_data$stats)) {
    away_team_data$stats$monthTrend <- build_month_trend(game$away_team_abbrev)

    # Add cumulative run differential by week (for line chart)
    team_cum_run_diff <- cum_run_diff_by_team %>%
      filter(team == game$away_team_abbrev) %>%
      select(week_num, cum_run_diff)

    away_team_data$stats$cumRunDiffByWeek <- if (nrow(team_cum_run_diff) > 0) {
      setNames(
        as.list(round(team_cum_run_diff$cum_run_diff, 0)),
        paste0("week-", team_cum_run_diff$week_num)
      )
    } else {
      setNames(list(), character(0))
    }

    # Add weekly performance (for scatter plot)
    team_weekly_perf <- weekly_performance %>%
      filter(team == game$away_team_abbrev) %>%
      select(week_num, runs_scored_avg, runs_allowed_avg)

    away_team_data$stats$performanceByWeek <- if (nrow(team_weekly_perf) > 0) {
      setNames(
        lapply(1:nrow(team_weekly_perf), function(i) {
          list(
            runsScored = round(team_weekly_perf$runs_scored_avg[i], 2),
            runsAllowed = round(team_weekly_perf$runs_allowed_avg[i], 2)
          )
        }),
        paste0("week-", team_weekly_perf$week_num)
      )
    } else {
      setNames(list(), character(0))
    }
  }

  comparisons <- build_comparisons(home_s, away_s, game$home_team_abbrev, game$away_team_abbrev)

  # Build H2H data (away team = teamA, home team = teamB to match matchup layout)
  h2h_data <- build_h2h(game$away_team_abbrev, game$home_team_abbrev)

  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = game$game_name,
    gameStatus = game$game_status,
    gameCompleted = game$game_completed,
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    location = game$location,
    odds = game$odds,
    comparisons = comparisons,
    h2h = h2h_data
  )

  # Add results for completed games (with box score for recent games)
  if (isTRUE(game$game_completed) && !is.null(game$home_score) && !is.null(game$away_score)) {
    if (results_fetched < MAX_RESULTS_GAMES) {
      matchup$results <- build_mlb_game_results(game, home_s, away_s)
      results_fetched <- results_fetched + 1
    } else {
      matchup$results <- list(
        homeScore = as.integer(game$home_score),
        awayScore = as.integer(game$away_score),
        winner = if (game$home_score > game$away_score) game$home_team_abbrev else game$away_team_abbrev,
        margin = as.integer(abs(game$home_score - game$away_score)),
        homeWon = game$home_score > game$away_score
      )
    }
    # Add season highs
    home_sh <- check_mlb_season_highs(game$home_team_abbrev, game$game_date, matchup$results$teamBoxScore$home)
    away_sh <- check_mlb_season_highs(game$away_team_abbrev, game$game_date, matchup$results$teamBoxScore$away)
    if (!is.null(home_sh) || !is.null(away_sh)) {
      matchup$results$seasonHighs <- list(home = home_sh, away = away_sh)
    }
  }

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

cat("Built", length(matchups_json), "matchups\n")

# ============================================================================
# STEP 4: Output JSON
# ============================================================================
cat("\n4. Generating output...\n")

output_data <- list(
  sport = "MLB",
  visualizationType = "MLB_MATCHUP",
  title = paste0("MLB Matchups - ", format(start_date, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("Games from the past", DAYS_BEHIND, "days and next", DAYS_AHEAD, "days"),
  description = paste0(
    "MLB matchup statistics with team batting, pitching, and fielding comparisons.\n\n",
    "BATTING:\n • Runs/Game, Batting Avg, On-Base %, Slugging %, OPS\n",
    " • Hits/Game, HR/Game, RBI/Game, SB/Game\n\n",
    "PITCHING:\n • ERA, WHIP, K/9, BB/9\n",
    " • Runs Allowed/Game, Hits Allowed/Game, HR Allowed/Game\n\n",
    "FIELDING:\n • Fielding %, Errors/Game\n\n",
    "All stats are season totals through the current date."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  leagueCumRunDiffStats = league_cum_run_diff_stats,
  leagueWeeklyStats = league_weekly_stats,
  dataPoints = matchups_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
env <- toupper(Sys.getenv("ENV", "DEV"))

if (nzchar(s3_bucket)) {
  s3_key <- if (env == "PROD") "prod/mlb__matchup_stats.json" else "dev/mlb__matchup_stats.json"
  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  if (result != 0) stop("Failed to upload to S3")
  cat("Uploaded to S3:", s3_path, "\n")

  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_ts <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  chart_title <- paste0("MLB Matchups - ", format(start_date, "%b %d"), " - ", format(end_date, "%b %d"))
  item <- sprintf('{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
                  s3_key, utc_ts, chart_title)
  ddb_cmd <- sprintf('aws dynamodb put-item --table-name %s --item %s', shQuote(dynamodb_table), shQuote(item))
  ddb_result <- system(ddb_cmd)
  if (ddb_result != 0) cat("Warning: Failed to update DynamoDB\n")
  else cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
} else {
  dev_output <- "/tmp/mlb_matchup_stats.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
}

cat("\n=== MLB Matchup Stats generation complete ===\n")
