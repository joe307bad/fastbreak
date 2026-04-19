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
          details <- if (!is.null(o$details)) as.character(o$details) else NA
          home_ml <- if (!is.null(o$homeTeamOdds$moneyLine)) safe_num(o$homeTeamOdds$moneyLine) else NA
          away_ml <- if (!is.null(o$awayTeamOdds$moneyLine)) safe_num(o$awayTeamOdds$moneyLine) else NA

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

  comparisons <- build_comparisons(home_s, away_s, game$home_team_abbrev, game$away_team_abbrev)

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
    comparisons = comparisons
  )

  # Add results for completed games
  if (isTRUE(game$game_completed)) {
    matchup$results <- list(
      homeScore = game$home_score,
      awayScore = game$away_score,
      winner = if (!is.null(game$home_score) && !is.null(game$away_score)) {
        if (game$home_score > game$away_score) game$home_team_abbrev else game$away_team_abbrev
      } else NULL
    )
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
  dataPoints = matchups_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/mlb__matchup_stats.json"
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
