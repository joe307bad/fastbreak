#!/usr/bin/env Rscript

# NHL Playoff Bracket generator.
# Mirrors the NBA bracket script structure for the KMP app.
# Uses NHL API for team stats and standings, Natural Stat Trick for xG%.

library(httr)
library(dplyr)
library(tidyr)
library(jsonlite)
library(lubridate)
library(rvest)

# ============================================================================
# Constants
# ============================================================================
CURRENT_YEAR <- as.numeric(format(Sys.Date(), "%Y"))
CURRENT_MONTH <- as.numeric(format(Sys.Date(), "%m"))
NHL_SEASON_END <- if (CURRENT_MONTH >= 10) CURRENT_YEAR + 1 else CURRENT_YEAR
NHL_SEASON_START <- NHL_SEASON_END - 1
NHL_SEASON_ID <- paste0(NHL_SEASON_START, NHL_SEASON_END)
NHL_SEASON_STRING <- paste0(NHL_SEASON_START, "-", substr(NHL_SEASON_END, 3, 4))

PLAYOFFS_START <- as.Date(paste0(NHL_SEASON_END, "-04-12"))
PLAYOFFS_END   <- as.Date(paste0(NHL_SEASON_END, "-06-30"))

# Environment and S3 prefix
ENV <- toupper(Sys.getenv("ENV", "DEV"))
S3_PREFIX <- if (ENV == "PROD") "prod" else "dev"

HISTORY_S3_KEY <- paste0(S3_PREFIX, "/nhl__bracket_history.json")

CONFERENCE_COLORS <- list(East = "#1565C0", West = "#C62828")
FIRST_ROUND_SEEDS <- list(c(1, 8), c(4, 5), c(3, 6), c(2, 7))

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

add_api_delay <- function() Sys.sleep(0.3)
safe_num <- function(x) if (is_valid_value(x)) as.numeric(x) else NA_real_
add_nst_delay <- function() { Sys.sleep(runif(1, min = 5, max = 10)) }

TEAM_ABBREVS <- c(
  "Anaheim Ducks" = "ANA", "Arizona Coyotes" = "ARI", "Boston Bruins" = "BOS",
  "Buffalo Sabres" = "BUF", "Calgary Flames" = "CGY", "Carolina Hurricanes" = "CAR",
  "Chicago Blackhawks" = "CHI", "Colorado Avalanche" = "COL", "Columbus Blue Jackets" = "CBJ",
  "Dallas Stars" = "DAL", "Detroit Red Wings" = "DET", "Edmonton Oilers" = "EDM",
  "Florida Panthers" = "FLA", "Los Angeles Kings" = "LAK", "Minnesota Wild" = "MIN",
  "Montreal Canadiens" = "MTL", "Montréal Canadiens" = "MTL", "Nashville Predators" = "NSH",
  "New Jersey Devils" = "NJD", "New York Islanders" = "NYI", "New York Rangers" = "NYR",
  "Ottawa Senators" = "OTT", "Philadelphia Flyers" = "PHI", "Pittsburgh Penguins" = "PIT",
  "San Jose Sharks" = "SJS", "Seattle Kraken" = "SEA", "St. Louis Blues" = "STL",
  "Tampa Bay Lightning" = "TBL", "Toronto Maple Leafs" = "TOR",
  "Utah Hockey Club" = "UTA", "Utah Mammoth" = "UTA",
  "Vancouver Canucks" = "VAN", "Vegas Golden Knights" = "VGK", "Washington Capitals" = "WSH",
  "Winnipeg Jets" = "WPG"
)

TEAM_CONFERENCES <- c(
  "ANA" = "Western", "ARI" = "Western", "BOS" = "Eastern",
  "BUF" = "Eastern", "CGY" = "Western", "CAR" = "Eastern",
  "CHI" = "Western", "COL" = "Western", "CBJ" = "Eastern",
  "DAL" = "Western", "DET" = "Eastern", "EDM" = "Western",
  "FLA" = "Eastern", "LAK" = "Western", "MIN" = "Western",
  "MTL" = "Eastern", "NSH" = "Western", "NJD" = "Eastern",
  "NYI" = "Eastern", "NYR" = "Eastern", "OTT" = "Eastern",
  "PHI" = "Eastern", "PIT" = "Eastern", "SJS" = "Western",
  "SEA" = "Western", "STL" = "Western", "TBL" = "Eastern",
  "TOR" = "Eastern", "UTA" = "Western", "VAN" = "Western",
  "VGK" = "Western", "WSH" = "Eastern", "WPG" = "Western"
)

NST_TEAM_NAME_MAP <- c(
  "ANA" = "Anaheim Ducks", "ARI" = "Arizona Coyotes", "BOS" = "Boston Bruins",
  "BUF" = "Buffalo Sabres", "CGY" = "Calgary Flames", "CAR" = "Carolina Hurricanes",
  "CHI" = "Chicago Blackhawks", "COL" = "Colorado Avalanche", "CBJ" = "Columbus Blue Jackets",
  "DAL" = "Dallas Stars", "DET" = "Detroit Red Wings", "EDM" = "Edmonton Oilers",
  "FLA" = "Florida Panthers", "LAK" = "Los Angeles Kings", "MIN" = "Minnesota Wild",
  "MTL" = "Montreal Canadiens", "NSH" = "Nashville Predators", "NJD" = "New Jersey Devils",
  "NYI" = "New York Islanders", "NYR" = "New York Rangers", "OTT" = "Ottawa Senators",
  "PHI" = "Philadelphia Flyers", "PIT" = "Pittsburgh Penguins", "SJS" = "San Jose Sharks",
  "SEA" = "Seattle Kraken", "STL" = "St Louis Blues", "TBL" = "Tampa Bay Lightning",
  "TOR" = "Toronto Maple Leafs", "UTA" = "Utah Hockey Club",
  "VAN" = "Vancouver Canucks", "VGK" = "Vegas Golden Knights",
  "WSH" = "Washington Capitals", "WPG" = "Winnipeg Jets"
)

# Reverse: NST name → abbreviation
NST_NAME_TO_ABBREV <- setNames(names(NST_TEAM_NAME_MAP), NST_TEAM_NAME_MAP)
# Add alternate names (rebrands)
NST_NAME_TO_ABBREV[["Utah Mammoth"]] <- "UTA"

# ============================================================================
# History persistence
# ============================================================================
load_bracket_history <- function() {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
  if (!nzchar(s3_bucket)) {
    local_history <- "/tmp/nhl_bracket_history.json"
    if (file.exists(local_history)) return(tryCatch(fromJSON(local_history, simplifyVector = FALSE), error = function(e) NULL))
    return(NULL)
  }
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  tmp_file <- tempfile(fileext = ".json")
  result <- system(paste("aws s3 cp", shQuote(s3_path), shQuote(tmp_file), "2>/dev/null"),
                   ignore.stdout = TRUE, ignore.stderr = TRUE)
  if (result == 0 && file.exists(tmp_file)) {
    history <- tryCatch(fromJSON(tmp_file, simplifyVector = FALSE), error = function(e) NULL)
    unlink(tmp_file); return(history)
  }
  NULL
}

save_bracket_history <- function(history) {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
  tmp_file <- tempfile(fileext = ".json")
  write_json(history, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")
  if (!nzchar(s3_bucket)) {
    file.copy(tmp_file, "/tmp/nhl_bracket_history.json", overwrite = TRUE)
    unlink(tmp_file); return(TRUE)
  }
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  result <- system(paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json"))
  unlink(tmp_file); result == 0
}

# ============================================================================
# NST xG% scraper (simplified from nhl__matchup_stats.R)
# ============================================================================
scrape_nst_team_xg <- function(from_date, to_date, season_id) {
  url <- sprintf(
    "https://www.naturalstattrick.com/teamtable.php?fromseason=%s&thruseason=%s&stype=2&sit=5v5&score=all&rate=n&team=all&loc=B&gpf=410&fd=%s&td=%s",
    season_id, season_id,
    format(from_date, "%Y-%m-%d"), format(to_date, "%Y-%m-%d")
  )
  tryCatch({
    add_nst_delay()
    page <- read_html(url)
    tables <- page %>% html_elements("table")
    if (length(tables) == 0) return(NULL)
    df <- tables[[1]] %>% html_table(fill = TRUE)
    # Fix empty column names (NST has a blank first column)
    col_names <- names(df)
    empty_cols <- which(col_names == "" | is.na(col_names))
    if (length(empty_cols) > 0) {
      col_names[empty_cols] <- paste0("X", empty_cols)
      names(df) <- col_names
    }
    if (nrow(df) == 0 || !"Team" %in% names(df)) return(NULL)
    df <- df %>%
      mutate(team_abbreviation = NST_NAME_TO_ABBREV[Team]) %>%
      filter(!is.na(team_abbreviation))
    xgf_col <- if ("xGF%" %in% names(df)) "xGF%" else if ("xGF" %in% names(df)) "xGF" else NULL
    xga_col <- if ("xGA" %in% names(df)) "xGA" else NULL
    if (!is.null(xgf_col)) {
      df$xgf_pct <- as.numeric(gsub("[^0-9.]", "", df[[xgf_col]])) / 100
    } else if (!is.null(xga_col) && "xGF" %in% names(df)) {
      xgf <- as.numeric(df[["xGF"]]); xga <- as.numeric(df[[xga_col]])
      df$xgf_pct <- xgf / (xgf + xga)
    } else { return(NULL) }
    df %>% select(team_abbreviation, xgf_pct) %>% filter(!is.na(xgf_pct))
  }, error = function(e) { cat("NST scrape error:", e$message, "\n"); NULL })
}

cat("=== NHL Playoff Bracket Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")
cat("Season:", NHL_SEASON_STRING, "\n")

# ============================================================================
# STEP 1: Team stats from NHL API
# ============================================================================
cat("\n1. Loading NHL team stats...\n")

team_stats <- tryCatch({
  url <- sprintf("https://api.nhle.com/stats/rest/en/team/summary?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2", NHL_SEASON_ID)
  resp <- GET(url)
  if (status_code(resp) != 200) stop("NHL API error")
  fromJSON(content(resp, "text", encoding = "UTF-8"))$data
}, error = function(e) { stop("Failed to load team stats: ", e$message) })

realtime_stats <- tryCatch({
  url <- sprintf("https://api.nhle.com/stats/rest/en/team/realtime?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2", NHL_SEASON_ID)
  resp <- GET(url)
  if (status_code(resp) == 200) fromJSON(content(resp, "text", encoding = "UTF-8"))$data else NULL
}, error = function(e) NULL)

team_stats <- team_stats %>%
  mutate(
    team_id = teamId,
    team_name = teamFullName,
    team_abbreviation = TEAM_ABBREVS[teamFullName],
    games_played = as.numeric(gamesPlayed),
    wins = as.numeric(wins), losses = as.numeric(losses),
    ot_losses = as.numeric(otLosses), points = as.numeric(points),
    points_pct = as.numeric(pointPct),
    goals_per_game = as.numeric(goalsForPerGame),
    goals_against_per_game = as.numeric(goalsAgainstPerGame),
    goal_diff_per_game = goals_per_game - goals_against_per_game,
    shots_for_per_game = as.numeric(shotsForPerGame),
    shots_against_per_game = as.numeric(shotsAgainstPerGame),
    faceoff_win_pct = as.numeric(faceoffWinPct),
    power_play_pct = as.numeric(powerPlayPct),
    penalty_kill_pct = as.numeric(penaltyKillPct),
    conference = TEAM_CONFERENCES[team_abbreviation]
  ) %>% filter(!is.na(team_abbreviation))

if (!is.null(realtime_stats)) {
  rt <- realtime_stats %>%
    mutate(team_abbreviation = TEAM_ABBREVS[teamFullName]) %>%
    filter(!is.na(team_abbreviation)) %>%
    mutate(
      hits_per_game = as.numeric(hits) / as.numeric(gamesPlayed),
      blocks_per_game = as.numeric(blockedShots) / as.numeric(gamesPlayed),
      takeaways_per_game = as.numeric(takeaways) / as.numeric(gamesPlayed),
      giveaways_per_game = as.numeric(giveaways) / as.numeric(gamesPlayed)
    ) %>% select(team_abbreviation, hits_per_game, blocks_per_game, takeaways_per_game, giveaways_per_game)
  team_stats <- team_stats %>% left_join(rt, by = "team_abbreviation")
}

# Season xG% from Natural Stat Trick
today <- Sys.Date()
nhl_season_start <- as.Date(paste0(NHL_SEASON_START, "-10-01"))
cat("Fetching season xG% from Natural Stat Trick...\n")
nst_season_xg <- scrape_nst_team_xg(nhl_season_start, today, NHL_SEASON_ID)

if (!is.null(nst_season_xg) && nrow(nst_season_xg) > 0) {
  team_stats <- team_stats %>%
    left_join(nst_season_xg %>% select(team_abbreviation, xgf_pct), by = "team_abbreviation")
} else {
  team_stats$xgf_pct <- NA_real_
}

# Compute ranks
r <- function(x, lower_better = FALSE) if (lower_better) tied_rank(x) else tied_rank(-x)

rk_list <- list(
  points_pct = r(team_stats$points_pct),
  goals_per_game = r(team_stats$goals_per_game),
  goal_diff_per_game = r(team_stats$goal_diff_per_game),
  shots_for_per_game = r(team_stats$shots_for_per_game),
  faceoff_win_pct = r(team_stats$faceoff_win_pct),
  power_play_pct = r(team_stats$power_play_pct),
  goals_against_per_game = r(team_stats$goals_against_per_game, lower_better = TRUE),
  shots_against_per_game = r(team_stats$shots_against_per_game, lower_better = TRUE),
  penalty_kill_pct = r(team_stats$penalty_kill_pct)
)

for (stat_name in names(rk_list)) {
  team_stats[[paste0(stat_name, "_rank")]] <- rk_list[[stat_name]]$rank
  team_stats[[paste0(stat_name, "_rankDisplay")]] <- rk_list[[stat_name]]$rankDisplay
}

# Optional ranks for realtime + xG
for (stat_name in c("hits_per_game", "blocks_per_game", "takeaways_per_game", "xgf_pct")) {
  if (stat_name %in% names(team_stats) && any(!is.na(team_stats[[stat_name]]))) {
    rk <- r(team_stats[[stat_name]])
    team_stats[[paste0(stat_name, "_rank")]] <- rk$rank
    team_stats[[paste0(stat_name, "_rankDisplay")]] <- rk$rankDisplay
  }
}
for (stat_name in c("giveaways_per_game")) {
  if (stat_name %in% names(team_stats) && any(!is.na(team_stats[[stat_name]]))) {
    rk <- r(team_stats[[stat_name]], lower_better = TRUE)
    team_stats[[paste0(stat_name, "_rank")]] <- rk$rank
    team_stats[[paste0(stat_name, "_rankDisplay")]] <- rk$rankDisplay
  }
}

cat("Computed stats + ranks for", nrow(team_stats), "teams\n")

# ============================================================================
# Weekly chart data (cumulative xG% + weekly xG%)
# ============================================================================
cat("\nComputing weekly chart data...\n")
NUM_TREND_WEEKS <- 10
cum_xgf_pct_by_team <- list()
weekly_xgf_pct <- list()
trend_start <- today - weeks(NUM_TREND_WEEKS)

for (week_num in 1:NUM_TREND_WEEKS) {
  week_end <- min(trend_start + weeks(week_num), today)
  week_key <- paste0("week-", week_num)

  cum_data <- scrape_nst_team_xg(nhl_season_start, week_end, NHL_SEASON_ID)
  if (!is.null(cum_data)) {
    for (i in seq_len(nrow(cum_data))) {
      team <- cum_data$team_abbreviation[i]
      if (!is.null(team) && !is.na(team)) {
        if (is.null(cum_xgf_pct_by_team[[team]])) cum_xgf_pct_by_team[[team]] <- list()
        cum_xgf_pct_by_team[[team]][[week_key]] <- round(cum_data$xgf_pct[i] * 100, 1)
      }
    }
  }

  snap_data <- scrape_nst_team_xg(week_end - days(6), week_end, NHL_SEASON_ID)
  if (!is.null(snap_data)) {
    for (i in seq_len(nrow(snap_data))) {
      team <- snap_data$team_abbreviation[i]
      if (!is.null(team) && !is.na(team)) {
        if (is.null(weekly_xgf_pct[[team]])) weekly_xgf_pct[[team]] <- list()
        weekly_xgf_pct[[team]][[week_key]] <- round(snap_data$xgf_pct[i] * 100, 1)
      }
    }
  }
}

cat("Computed weekly xG% for", length(cum_xgf_pct_by_team), "teams\n")

# Weekly Points% from ESPN regular season results (same 10-week window)
# Computed later after recent_games are fetched — placeholder
weekly_points_pct <- list()

# 10th-best reference line
tenth_xgf_pct_by_week <- list()
if (length(cum_xgf_pct_by_team) > 0) {
  all_week_keys <- sort(unique(unlist(lapply(cum_xgf_pct_by_team, names))))
  for (wk in all_week_keys) {
    vals <- c()
    for (team in names(cum_xgf_pct_by_team)) {
      v <- cum_xgf_pct_by_team[[team]][[wk]]
      if (!is.null(v) && !is.na(v)) vals <- c(vals, v)
    }
    if (length(vals) >= 25) {
      sorted <- sort(vals, decreasing = TRUE)
      if (length(sorted) >= 10) tenth_xgf_pct_by_week[[wk]] <- sorted[10]
    }
  }
}

# League-wide bounds
league_cum_xg_stats <- NULL
if (length(cum_xgf_pct_by_team) > 0) {
  all_vals <- unlist(cum_xgf_pct_by_team)
  if (length(all_vals) > 0) {
    league_cum_xg_stats <- list(
      minCumXgPct = round(min(all_vals, na.rm = TRUE), 1),
      maxCumXgPct = round(max(all_vals, na.rm = TRUE), 1)
    )
  }
}

league_xg_vs_pts <- NULL
if (length(weekly_xgf_pct) > 0) {
  all_xg <- c(); all_pts <- c()
  for (team in names(weekly_xgf_pct)) {
    ts_row <- team_stats %>% filter(team_abbreviation == team)
    pts_pct <- if (nrow(ts_row) > 0) ts_row$points_pct[1] * 100 else NA
    for (wk in names(weekly_xgf_pct[[team]])) {
      xg <- weekly_xgf_pct[[team]][[wk]]
      if (!is.null(xg) && !is.na(xg) && !is.na(pts_pct)) {
        all_xg <- c(all_xg, xg); all_pts <- c(all_pts, pts_pct)
      }
    }
  }
  if (length(all_xg) > 0) {
    league_xg_vs_pts <- list(
      avgXgPct = round(mean(all_xg), 1), avgPointsPct = round(mean(all_pts), 1),
      minXgPct = round(min(all_xg), 1), maxXgPct = round(max(all_xg), 1),
      minPointsPct = round(min(all_pts), 1), maxPointsPct = round(max(all_pts), 1)
    )
  }
}

# ============================================================================
# STEP 2: Standings
# ============================================================================
cat("\n2. Fetching NHL standings...\n")

standings_resp <- tryCatch(GET("https://api-web.nhle.com/v1/standings/now"), error = function(e) NULL)

seeds <- list(East = list(), West = list())
if (!is.null(standings_resp) && status_code(standings_resp) == 200) {
  sd <- fromJSON(content(standings_resp, "text", encoding = "UTF-8"))
  if (!is.null(sd$standings) && nrow(sd$standings) > 0) {
    raw <- sd$standings
    standings_df <- data.frame(
      team_abbrev = raw$teamAbbrev$default,
      team_name = raw$teamName$default,
      team_logo = raw$teamLogo,
      wins = as.numeric(raw$wins),
      losses = as.numeric(raw$losses),
      ot_losses = as.numeric(raw$otLosses),
      points = as.numeric(raw$points),
      conference = raw$conferenceName,
      stringsAsFactors = FALSE
    )

    for (conf_name in c("Eastern", "Western")) {
      bucket <- if (conf_name == "Eastern") "East" else "West"
      conf_teams <- standings_df %>%
        filter(conference == conf_name) %>%
        arrange(desc(points), desc(wins)) %>%
        mutate(seed = row_number())
      seeds[[bucket]] <- conf_teams
    }
  }
}

east_n <- if (is.data.frame(seeds$East)) nrow(seeds$East) else 0
west_n <- if (is.data.frame(seeds$West)) nrow(seeds$West) else 0
cat("East seeds:", east_n, " West seeds:", west_n, "\n")

# ============================================================================
# STEP 2b: Month trend + head-to-head from ESPN regular season
# ============================================================================
cat("\n2b. Fetching regular season games for trend + h2h...\n")

# Fetch the full regular season so head-to-head comparisons reflect the
# regular-season series rather than the in-progress playoff matchup. ESPN's
# scoreboard returns postseason games on playoff dates unless seasontype=2
# is explicitly requested.
regular_season_start <- as.Date(paste0(NHL_SEASON_START, "-10-01"))
rs_fetch_end <- min(today, PLAYOFFS_START - 1)
recent_games <- list()

d <- regular_season_start
while (d <= rs_fetch_end) {
  date_str <- format(d, "%Y%m%d")
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard?seasontype=2&dates=", date_str)
  Sys.sleep(0.5)
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
        recent_games[[length(recent_games) + 1]] <- list(
          game_date = as.character(as.Date(ev$date)),
          home_abbrev = home$team$abbreviation,
          away_abbrev = away$team$abbreviation,
          home_score = safe_num(home$score),
          away_score = safe_num(away$score),
          home_winner = isTRUE(home$winner),
          away_winner = isTRUE(away$winner)
        )
      }
    }
  }
  d <- d + 1
}

cat("Fetched", length(recent_games), "regular season games\n")

# Compute month trend from the last 28 days of regular season games.
month_trend_stats <- NULL
if (length(recent_games) > 0) {
  month_cutoff <- rs_fetch_end - days(27)
  games_df <- bind_rows(recent_games) %>%
    filter(as.Date(game_date) >= month_cutoff)
  home_stats <- games_df %>%
    group_by(team_abbreviation = home_abbrev) %>%
    summarise(games = n(), wins = sum(home_winner, na.rm = TRUE),
              losses = sum(!home_winner, na.rm = TRUE),
              gf = sum(home_score, na.rm = TRUE), ga = sum(away_score, na.rm = TRUE), .groups = "drop")
  away_stats_trend <- games_df %>%
    group_by(team_abbreviation = away_abbrev) %>%
    summarise(games = n(), wins = sum(away_winner, na.rm = TRUE),
              losses = sum(!away_winner, na.rm = TRUE),
              gf = sum(away_score, na.rm = TRUE), ga = sum(home_score, na.rm = TRUE), .groups = "drop")

  month_trend_stats <- bind_rows(home_stats, away_stats_trend) %>%
    group_by(team_abbreviation) %>%
    summarise(games_played = sum(games), wins = sum(wins), losses = sum(losses),
              gf = sum(gf), ga = sum(ga), .groups = "drop") %>%
    mutate(gpg = gf / games_played, gapg = ga / games_played,
           goal_diff = gpg - gapg, win_pct = wins / (wins + losses))

  gpg_r <- tied_rank(-month_trend_stats$gpg)
  gapg_r <- tied_rank(month_trend_stats$gapg)
  diff_r <- tied_rank(-month_trend_stats$goal_diff)
  rec_r <- tied_rank(-month_trend_stats$win_pct)
  month_trend_stats <- month_trend_stats %>%
    mutate(gpg_rank = gpg_r$rank, gpg_rankDisplay = gpg_r$rankDisplay,
           gapg_rank = gapg_r$rank, gapg_rankDisplay = gapg_r$rankDisplay,
           goal_diff_rank = diff_r$rank, goal_diff_rankDisplay = diff_r$rankDisplay,
           record_rank = rec_r$rank, record_rankDisplay = rec_r$rankDisplay)
  cat("Computed month trend for", nrow(month_trend_stats), "teams\n")
}

# Playoff trend is computed after playoff games are fetched (Step 3). Declared
# here so build_team can safely reference it before the data exists.
playoff_trend_stats <- NULL

# Build head-to-head lookup from recent games
h2h_games <- recent_games

# Compute weekly Points% from recent games (for xG vs Points scatter chart)
if (length(recent_games) > 0) {
  games_df_pts <- bind_rows(recent_games)
  trend_start_pts <- today - weeks(NUM_TREND_WEEKS)

  for (week_num in 1:NUM_TREND_WEEKS) {
    week_end <- min(trend_start_pts + weeks(week_num), today)
    week_start <- week_end - days(6)
    week_key <- paste0("week-", week_num)

    wg <- games_df_pts %>%
      filter(as.Date(game_date) >= week_start & as.Date(game_date) <= week_end)
    if (nrow(wg) == 0) next

    all_teams <- unique(c(wg$home_abbrev, wg$away_abbrev))
    for (team in all_teams) {
      th <- wg %>% filter(home_abbrev == team)
      ta <- wg %>% filter(away_abbrev == team)
      wins <- sum(th$home_winner, na.rm = TRUE) + sum(ta$away_winner, na.rm = TRUE)
      gp <- nrow(th) + nrow(ta)
      if (gp > 0) {
        pts_pct <- round((wins * 2) / (gp * 2) * 100, 1)  # Simplified: no OT loss tracking
        if (is.null(weekly_points_pct[[team]])) weekly_points_pct[[team]] <- list()
        weekly_points_pct[[team]][[week_key]] <- pts_pct
      }
    }
  }
  cat("Computed weekly Points% for", length(weekly_points_pct), "teams\n")

  # Update league_xg_vs_pts with actual weekly data
  if (length(weekly_xgf_pct) > 0 && length(weekly_points_pct) > 0) {
    all_xg <- c(); all_pts <- c()
    for (team in names(weekly_xgf_pct)) {
      for (wk in names(weekly_xgf_pct[[team]])) {
        xg <- weekly_xgf_pct[[team]][[wk]]
        pts <- weekly_points_pct[[team]][[wk]]
        if (!is.null(xg) && !is.null(pts) && !is.na(xg) && !is.na(pts)) {
          all_xg <- c(all_xg, xg); all_pts <- c(all_pts, pts)
        }
      }
    }
    if (length(all_xg) > 0) {
      league_xg_vs_pts <- list(
        avgXgPct = round(mean(all_xg), 1), avgPointsPct = round(mean(all_pts), 1),
        minXgPct = round(min(all_xg), 1), maxXgPct = round(max(all_xg), 1),
        minPointsPct = round(min(all_pts), 1), maxPointsPct = round(max(all_pts), 1)
      )
    }
  }
}

# ============================================================================
# Team / matchup builders
# ============================================================================

# ESPN uses shorter abbreviations for some teams
ESPN_TO_NHL_ABBREV <- c(
  "TB" = "TBL", "LA" = "LAK", "SJ" = "SJS", "NJ" = "NJD",
  "NY" = "NYR"  # NYR is the default; NYI handled separately if needed
)

normalize_abbrev <- function(abbrev) {
  if (abbrev %in% names(ESPN_TO_NHL_ABBREV)) ESPN_TO_NHL_ABBREV[[abbrev]] else abbrev
}

find_team_stats <- function(abbrev) {
  if (is_valid_value(abbrev)) {
    nhl_abbrev <- normalize_abbrev(abbrev)
    match_row <- team_stats %>% filter(team_abbreviation == nhl_abbrev)
    if (nrow(match_row) > 0) return(match_row[1, ])
    # Fallback: try original
    match_row <- team_stats %>% filter(team_abbreviation == abbrev)
    if (nrow(match_row) > 0) return(match_row[1, ])
  }
  NULL
}

stat_entry <- function(value, rank, rankDisplay) {
  list(value = if (is_valid_value(value)) round(as.numeric(value), 4) else NULL,
       rank = if (is_valid_value(rank)) as.integer(rank) else NULL,
       rankDisplay = if (is_valid_value(rankDisplay)) rankDisplay else NULL)
}

build_team <- function(name, abbrev, logo, seed = NULL, record = NULL) {
  base <- list(
    name = name, abbreviation = abbrev, logo = logo,
    seed = if (is_valid_value(seed)) as.integer(seed) else NULL,
    wins = if (is_valid_value(record$wins)) as.integer(record$wins) else NULL,
    losses = if (is_valid_value(record$losses)) as.integer(record$losses) else NULL,
    conference = record$conference,
    seriesWins = NULL, score = NULL, isWinner = FALSE, teamStats = NULL
  )

  s <- find_team_stats(abbrev)
  if (is.null(s)) return(base)

  ts <- list(
    gamesPlayed = as.integer(s$games_played),
    pointsPct = stat_entry(s$points_pct, s$points_pct_rank, s$points_pct_rankDisplay),
    goalsPerGame = stat_entry(s$goals_per_game, s$goals_per_game_rank, s$goals_per_game_rankDisplay),
    goalDiffPerGame = stat_entry(s$goal_diff_per_game, s$goal_diff_per_game_rank, s$goal_diff_per_game_rankDisplay),
    shotsForPerGame = stat_entry(s$shots_for_per_game, s$shots_for_per_game_rank, s$shots_for_per_game_rankDisplay),
    powerPlayPct = stat_entry(s$power_play_pct, s$power_play_pct_rank, s$power_play_pct_rankDisplay),
    faceoffWinPct = stat_entry(s$faceoff_win_pct, s$faceoff_win_pct_rank, s$faceoff_win_pct_rankDisplay),
    goalsAgainstPerGame = stat_entry(s$goals_against_per_game, s$goals_against_per_game_rank, s$goals_against_per_game_rankDisplay),
    shotsAgainstPerGame = stat_entry(s$shots_against_per_game, s$shots_against_per_game_rank, s$shots_against_per_game_rankDisplay),
    penaltyKillPct = stat_entry(s$penalty_kill_pct, s$penalty_kill_pct_rank, s$penalty_kill_pct_rankDisplay)
  )

  # Optional stats
  for (stat in c("hits_per_game", "blocks_per_game", "takeaways_per_game", "giveaways_per_game", "xgf_pct")) {
    if (stat %in% names(s) && !is.na(s[[stat]])) {
      camel <- gsub("_(.)", "\\U\\1", stat, perl = TRUE)
      ts[[camel]] <- stat_entry(s[[stat]], s[[paste0(stat, "_rank")]], s[[paste0(stat, "_rankDisplay")]])
    }
  }

  # Chart data — try both ESPN and NHL abbreviations
  nhl_ab <- normalize_abbrev(abbrev)
  chart_ab <- if (!is.null(cum_xgf_pct_by_team[[nhl_ab]])) nhl_ab else abbrev
  if (!is.null(cum_xgf_pct_by_team[[chart_ab]])) {
    ts$cumXgfPctByWeek <- cum_xgf_pct_by_team[[chart_ab]]
  }
  if (!is.null(weekly_xgf_pct[[chart_ab]])) {
    ts$weeklyXgfPct <- weekly_xgf_pct[[chart_ab]]
  }
  # Points% uses ESPN abbreviations from recent_games
  pts_ab <- if (!is.null(weekly_points_pct[[abbrev]])) abbrev else nhl_ab
  if (!is.null(weekly_points_pct[[pts_ab]])) {
    ts$weeklyPointsPct <- weekly_points_pct[[pts_ab]]
  }

  # Month trend — ESPN abbreviations from recent_games
  if (!is.null(month_trend_stats)) {
    mt <- month_trend_stats %>% filter(team_abbreviation == abbrev | team_abbreviation == nhl_ab)
    if (nrow(mt) > 0) {
      ts$monthTrend <- list(
        record = list(wins = as.integer(mt$wins), losses = as.integer(mt$losses),
                      rank = as.integer(mt$record_rank), rankDisplay = mt$record_rankDisplay),
        goalsPerGame = list(value = round(mt$gpg, 2), rank = as.integer(mt$gpg_rank), rankDisplay = mt$gpg_rankDisplay),
        goalsAgainstPerGame = list(value = round(mt$gapg, 2), rank = as.integer(mt$gapg_rank), rankDisplay = mt$gapg_rankDisplay),
        goalDiffPerGame = list(value = round(mt$goal_diff, 2), rank = as.integer(mt$goal_diff_rank), rankDisplay = mt$goal_diff_rankDisplay)
      )
    }
  }

  # Playoff trend — computed from playoff games only, ranked across playoff teams
  if (!is.null(playoff_trend_stats)) {
    pt <- playoff_trend_stats %>% filter(team_abbreviation == abbrev | team_abbreviation == nhl_ab)
    if (nrow(pt) > 0) {
      ts$playoffTrend <- list(
        gamesPlayed = as.integer(pt$games_played),
        record = list(wins = as.integer(pt$wins), losses = as.integer(pt$losses),
                      rank = as.integer(pt$record_rank), rankDisplay = pt$record_rankDisplay),
        goalsPerGame = list(value = round(pt$gpg, 2), rank = as.integer(pt$gpg_rank), rankDisplay = pt$gpg_rankDisplay),
        goalsAgainstPerGame = list(value = round(pt$gapg, 2), rank = as.integer(pt$gapg_rank), rankDisplay = pt$gapg_rankDisplay),
        goalDiffPerGame = list(value = round(pt$goal_diff, 2), rank = as.integer(pt$goal_diff_rank), rankDisplay = pt$goal_diff_rankDisplay)
      )
    }
  }

  base$teamStats <- ts
  if (is.null(base$wins) && !is.null(s$wins)) base$wins <- as.integer(s$wins)
  if (is.null(base$losses) && !is.null(s$losses)) base$losses <- as.integer(s$losses)
  base
}

# Build head-to-head regular season history between two teams
build_h2h_history <- function(abbrev_a, abbrev_b) {
  if (length(h2h_games) == 0) return(NULL)
  h2h <- Filter(function(g) {
    (g$home_abbrev == abbrev_a && g$away_abbrev == abbrev_b) ||
    (g$home_abbrev == abbrev_b && g$away_abbrev == abbrev_a)
  }, h2h_games)
  if (length(h2h) == 0) return(NULL)

  a_wins <- sum(vapply(h2h, function(g) {
    (g$home_abbrev == abbrev_a && isTRUE(g$home_winner)) ||
    (g$away_abbrev == abbrev_a && isTRUE(g$away_winner))
  }, logical(1)))
  b_wins <- sum(vapply(h2h, function(g) {
    (g$home_abbrev == abbrev_b && isTRUE(g$home_winner)) ||
    (g$away_abbrev == abbrev_b && isTRUE(g$away_winner))
  }, logical(1)))

  games_out <- lapply(h2h, function(g) {
    list(
      gameDate = g$game_date,
      homeAbbrev = g$home_abbrev,
      awayAbbrev = g$away_abbrev,
      homeScore = as.integer(g$home_score),
      awayScore = as.integer(g$away_score),
      winnerAbbrev = if (isTRUE(g$home_winner)) g$home_abbrev
                     else if (isTRUE(g$away_winner)) g$away_abbrev else NULL
    )
  })

  list(
    teamAAbbrev = abbrev_a, teamBAbbrev = abbrev_b,
    teamAWins = as.integer(a_wins), teamBWins = as.integer(b_wins),
    games = games_out
  )
}

# ============================================================================
# Comparisons (mirrors nhl__matchup_stats.R build_nhl_comparisons)
# ============================================================================

build_comparisons <- function(team1, team2) {
  t1 <- team1$teamStats; t2 <- team2$teamStats
  if (is.null(t1) || is.null(t2)) return(NULL)

  make_side <- function(key, label) {
    list(label = label,
         home = list(value = t1[[key]]$value, rank = t1[[key]]$rank, rankDisplay = t1[[key]]$rankDisplay),
         away = list(value = t2[[key]]$value, rank = t2[[key]]$rank, rankDisplay = t2[[key]]$rankDisplay))
  }

  offense <- list(
    pointsPct = make_side("pointsPct", "Points %"),
    goalsPerGame = make_side("goalsPerGame", "Goals/Game"),
    goalDiffPerGame = make_side("goalDiffPerGame", "Goal Diff/Game"),
    shotsForPerGame = make_side("shotsForPerGame", "Shots/Game"),
    powerPlayPct = make_side("powerPlayPct", "Power Play %"),
    faceoffWinPct = make_side("faceoffWinPct", "Faceoff Win %")
  )
  if (!is.null(t1$hitsPerGame)) offense$hitsPerGame <- make_side("hitsPerGame", "Hits/Game")
  if (!is.null(t1$takeawaysPerGame)) offense$takeawaysPerGame <- make_side("takeawaysPerGame", "Takeaways/Game")
  if (!is.null(t1$xgfPct)) offense$xgfPct <- make_side("xgfPct", "xG% (5v5)")

  defense <- list(
    goalsAgainstPerGame = make_side("goalsAgainstPerGame", "Goals Against/Game"),
    shotsAgainstPerGame = make_side("shotsAgainstPerGame", "Shots Against/Game"),
    penaltyKillPct = make_side("penaltyKillPct", "Penalty Kill %")
  )
  if (!is.null(t1$blocksPerGame)) defense$blocksPerGame <- make_side("blocksPerGame", "Blocks/Game")
  if (!is.null(t1$giveawaysPerGame)) defense$giveawaysPerGame <- make_side("giveawaysPerGame", "Giveaways/Game")

  # Off vs def matchups
  calc_adv <- function(off_rank, def_rank) {
    if (is.null(off_rank) || is.null(def_rank)) return(0)
    if (off_rank < def_rank) return(-1)
    if (off_rank > def_rank) return(1)
    0
  }

  ovd <- function(off_team, def_team, off_stats, def_stats, key, off_key, def_key, off_label, def_label) {
    list(statKey = key, offLabel = off_label, defLabel = def_label,
         offense = list(team = off_team, value = off_stats[[off_key]]$value,
                        rank = off_stats[[off_key]]$rank, rankDisplay = off_stats[[off_key]]$rankDisplay),
         defense = list(team = def_team, value = def_stats[[def_key]]$value,
                        rank = def_stats[[def_key]]$rank, rankDisplay = def_stats[[def_key]]$rankDisplay),
         advantage = calc_adv(off_stats[[off_key]]$rank, def_stats[[def_key]]$rank))
  }

  a <- team1$abbreviation; b <- team2$abbreviation
  home_off_vs_away_def <- list(
    goals = ovd(a, b, t1, t2, "goals", "goalsPerGame", "goalsAgainstPerGame", "Goals/Game", "Goals Against/Game"),
    shots = ovd(a, b, t1, t2, "shots", "shotsForPerGame", "shotsAgainstPerGame", "Shots/Game", "Shots Against/Game"),
    powerplay = ovd(a, b, t1, t2, "powerplay", "powerPlayPct", "penaltyKillPct", "Power Play %", "Penalty Kill %")
  )
  away_off_vs_home_def <- list(
    goals = ovd(b, a, t2, t1, "goals", "goalsPerGame", "goalsAgainstPerGame", "Goals/Game", "Goals Against/Game"),
    shots = ovd(b, a, t2, t1, "shots", "shotsForPerGame", "shotsAgainstPerGame", "Shots/Game", "Shots Against/Game"),
    powerplay = ovd(b, a, t2, t1, "powerplay", "powerPlayPct", "penaltyKillPct", "Power Play %", "Penalty Kill %")
  )

  list(
    sideBySide = list(offense = offense, defense = defense),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  )
}

# ============================================================================
# STEP 3: Fetch playoff games from ESPN (same approach as NBA bracket)
# ============================================================================
cat("\n3. Fetching NHL playoff games from ESPN...\n")

parse_nhl_playoff_round <- function(headline) {
  if (is.null(headline) || headline == "") return(list(conference = NA, roundNumber = NA, roundName = NA))
  hl <- tolower(headline)
  conference <- NA
  if (grepl("east", hl)) conference <- "Eastern"
  else if (grepl("west", hl)) conference <- "Western"

  round_num <- NA; round_name <- NA
  if (grepl("stanley cup final", hl)) {
    round_num <- 4; round_name <- "Stanley Cup Final"; conference <- "Finals"
  } else if (grepl("1st round|first round", hl)) {
    round_num <- 1; round_name <- "First Round"
  } else if (grepl("2nd round|second round|semifinal", hl)) {
    # Check semifinals BEFORE the "final" branch below — otherwise a loose
    # "conf.*final" match consumes "conference semifinals" (the "final" inside
    # "semifinals") and classifies R2 games as R3, leaving the Conference
    # Finals filter contaminated with stale semifinal series.
    round_num <- 2; round_name <- "Conference Semifinals"
  } else if (grepl("final", hl)) {
    round_num <- 3; round_name <- "Conference Finals"
  }
  list(conference = conference, roundNumber = round_num, roundName = round_name)
}

fetch_start <- max(PLAYOFFS_START, today - 90)
fetch_end <- min(PLAYOFFS_END, today + 14)
if (today < PLAYOFFS_START) { fetch_start <- PLAYOFFS_START; fetch_end <- min(PLAYOFFS_END, PLAYOFFS_START + 14) }

series_map <- list()
current_date <- fetch_start

while (current_date <= fetch_end) {
  date_str <- format(current_date, "%Y%m%d")
  url <- paste0("https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard",
                "?seasontype=3&dates=", date_str)
  Sys.sleep(0.5)  # Rate limiting for ESPN
  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (!is.null(resp) && status_code(resp) == 200) {
    data <- content(resp, as = "parsed")
    if (!is.null(data$events)) {
      for (ev in data$events) {
        comp <- ev$competitions[[1]]
        if (length(comp$competitors) != 2) next

        notes_hl <- if (length(comp$notes) > 0 && !is.null(comp$notes[[1]]$headline))
          comp$notes[[1]]$headline else ""
        rinfo <- parse_nhl_playoff_round(notes_hl)
        if (is.na(rinfo$roundNumber)) next

        t1 <- comp$competitors[[1]]
        t2 <- comp$competitors[[2]]
        team_ids <- sort(as.character(c(t1$team$id, t2$team$id)))
        series_key <- paste(rinfo$conference, rinfo$roundNumber,
                            paste(team_ids, collapse = "_"), sep = "|")

        status_name <- if (!is.null(comp$status$type$name)) comp$status$type$name else "STATUS_SCHEDULED"
        completed <- isTRUE(comp$status$type$completed)

        game_date_str <- ev$date
        if (grepl("T\\d{2}:\\d{2}Z$", game_date_str)) game_date_str <- sub("Z$", ":00Z", game_date_str)

        home_abbrev <- NA
        for (comp_team in comp$competitors) {
          if (!is.null(comp_team$homeAway) && comp_team$homeAway == "home") {
            home_abbrev <- comp_team$team$abbreviation; break
          }
        }

        # Betting odds for upcoming games (ESPN surfaces them on competitions[0].odds).
        odds_data <- NULL
        if (!completed && !is.null(comp$odds) && length(comp$odds) > 0) {
          o <- comp$odds[[1]]
          home_spread <- NA_real_
          if (!is.null(o$homeTeamOdds) && !is.null(o$homeTeamOdds$spreadOdds)) {
            home_spread <- safe_num(o$homeTeamOdds$spreadOdds)
          } else if (!is.null(o$spread)) {
            home_spread <- safe_num(o$spread)
          }
          home_ml <- if (!is.null(o$homeTeamOdds$moneyLine)) safe_num(o$homeTeamOdds$moneyLine) else NA_real_
          away_ml <- if (!is.null(o$awayTeamOdds$moneyLine)) safe_num(o$awayTeamOdds$moneyLine) else NA_real_
          over_under <- if (!is.null(o$overUnder)) safe_num(o$overUnder) else NA_real_
          provider <- if (!is.null(o$provider$name)) as.character(o$provider$name) else NA_character_

          # Build details as goal spread (e.g. "CAR -1.5") instead of money line
          details <- NA_character_
          if (is_valid_value(home_spread)) {
            spread_team <- home_abbrev
            spread_val <- home_spread
            if (home_spread > 0) {
              # Away team is favored
              spread_team <- t2$team$abbreviation
              spread_val <- -home_spread
            }
            details <- sprintf("%s %s", spread_team, ifelse(spread_val >= 0, paste0("+", spread_val), as.character(spread_val)))
          }

          has_any <- is_valid_value(home_spread) || is_valid_value(over_under) || is_valid_value(details)
          if (has_any) {
            odds_data <- list(
              provider = if (is_valid_value(provider)) provider else NULL,
              details = if (is_valid_value(details)) details else NULL,
              homeSpread = if (is_valid_value(home_spread)) home_spread else NULL,
              overUnder = if (is_valid_value(over_under)) over_under else NULL
            )
          }
        }

        game_record <- list(
          game_id = ev$id, game_date = game_date_str,
          status = status_name, completed = completed,
          home_abbrev = home_abbrev,
          team1_abbrev = t1$team$abbreviation,
          team1_score = safe_num(t1$score),
          team1_winner = isTRUE(t1$winner),
          team2_abbrev = t2$team$abbreviation,
          team2_score = safe_num(t2$score),
          team2_winner = isTRUE(t2$winner),
          conference = rinfo$conference, roundNumber = rinfo$roundNumber, roundName = rinfo$roundName,
          odds = odds_data
        )

        team_abbrevs <- sort(c(t1$team$abbreviation, t2$team$abbreviation))
        if (is.null(series_map[[series_key]])) {
          series_map[[series_key]] <- list(
            conference = rinfo$conference, roundNumber = rinfo$roundNumber, roundName = rinfo$roundName,
            team_ids = team_ids, team_abbrevs = team_abbrevs, games = list()
          )
        }
        series_map[[series_key]]$games[[length(series_map[[series_key]]$games) + 1]] <- game_record
      }
    }
  }
  current_date <- current_date + 1
}

# Alias for rest of script
playoff_events <- series_map
cat("Fetched", length(playoff_events), "playoff series\n")

# ============================================================================
# Playoff trend (postseason games only, ranked across playoff teams)
# Same metric set as monthTrend, but the games used and the ranking pool are
# limited to the playoffs so we can see how teams stack up across the games
# that have actually been played in the postseason.
# ============================================================================
cat("\nComputing playoff trend (postseason games only)...\n")

po_games <- list()
for (k in names(playoff_events)) {
  s <- playoff_events[[k]]
  for (g in s$games) {
    if (!isTRUE(g$completed)) next
    po_games[[length(po_games) + 1]] <- list(
      team1_abbrev = g$team1_abbrev, team1_score = g$team1_score, team1_winner = g$team1_winner,
      team2_abbrev = g$team2_abbrev, team2_score = g$team2_score, team2_winner = g$team2_winner
    )
  }
}

if (length(po_games) > 0) {
  po_df <- bind_rows(po_games)
  t1_stats <- po_df %>%
    group_by(team_abbreviation = team1_abbrev) %>%
    summarise(games = n(), wins = sum(team1_winner, na.rm = TRUE),
              losses = sum(!team1_winner, na.rm = TRUE),
              gf = sum(team1_score, na.rm = TRUE), ga = sum(team2_score, na.rm = TRUE), .groups = "drop")
  t2_stats <- po_df %>%
    group_by(team_abbreviation = team2_abbrev) %>%
    summarise(games = n(), wins = sum(team2_winner, na.rm = TRUE),
              losses = sum(!team2_winner, na.rm = TRUE),
              gf = sum(team2_score, na.rm = TRUE), ga = sum(team1_score, na.rm = TRUE), .groups = "drop")

  playoff_trend_stats <- bind_rows(t1_stats, t2_stats) %>%
    group_by(team_abbreviation) %>%
    summarise(games_played = sum(games), wins = sum(wins), losses = sum(losses),
              gf = sum(gf), ga = sum(ga), .groups = "drop") %>%
    mutate(gpg = gf / games_played, gapg = ga / games_played,
           goal_diff = gpg - gapg,
           win_pct = ifelse(wins + losses > 0, wins / (wins + losses), NA_real_))

  gpg_r  <- tied_rank(-playoff_trend_stats$gpg)
  gapg_r <- tied_rank( playoff_trend_stats$gapg)
  diff_r <- tied_rank(-playoff_trend_stats$goal_diff)
  rec_r  <- tied_rank(-playoff_trend_stats$win_pct)
  playoff_trend_stats <- playoff_trend_stats %>%
    mutate(gpg_rank = gpg_r$rank, gpg_rankDisplay = gpg_r$rankDisplay,
           gapg_rank = gapg_r$rank, gapg_rankDisplay = gapg_r$rankDisplay,
           goal_diff_rank = diff_r$rank, goal_diff_rankDisplay = diff_r$rankDisplay,
           record_rank = rec_r$rank, record_rankDisplay = rec_r$rankDisplay)
  cat("Computed playoff trend for", nrow(playoff_trend_stats), "teams (", length(po_games), "games)\n")
} else {
  cat("No completed playoff games found yet\n")
}

# ============================================================================
# STEP 4: Build bracket
# ============================================================================
cat("\n4. Building bracket structure...\n")

has_playoff_games <- length(playoff_events) > 0
bracket_status <- if (!has_playoff_games) "PROJECTED" else if (today > PLAYOFFS_END) "COMPLETED" else "IN_PROGRESS"

build_series_matchup <- function(team1, team2, conference, round_number, round_name, game_id, series_entry = NULL) {
  # Reset state in case teams were carried over from an earlier round as winners.
  team1$isWinner <- FALSE
  team2$isWinner <- FALSE
  team1$seriesWins <- NULL
  team2$seriesWins <- NULL

  game_status <- "PROJECTED"
  series_summary <- NULL
  games_list <- list()

  if (!is.null(series_entry)) {
    t1_wins <- sum(vapply(series_entry$games, function(g) {
      if (!isTRUE(g$completed)) return(0L)
      if (g$team1_abbrev == team1$abbreviation && isTRUE(g$team1_winner)) return(1L)
      if (g$team2_abbrev == team1$abbreviation && isTRUE(g$team2_winner)) return(1L)
      0L
    }, integer(1)))
    t2_wins <- sum(vapply(series_entry$games, function(g) {
      if (!isTRUE(g$completed)) return(0L)
      if (g$team1_abbrev == team2$abbreviation && isTRUE(g$team1_winner)) return(1L)
      if (g$team2_abbrev == team2$abbreviation && isTRUE(g$team2_winner)) return(1L)
      0L
    }, integer(1)))

    team1$seriesWins <- as.integer(t1_wins)
    team2$seriesWins <- as.integer(t2_wins)

    if (t1_wins > 0 || t2_wins > 0) {
      series_summary <- sprintf("%s %d - %d", team1$abbreviation, t1_wins, t2_wins)
    }
    if (t1_wins == 4 || t2_wins == 4) {
      game_status <- "FINAL"
      if (t1_wins == 4) team1$isWinner <- TRUE else team2$isWinner <- TRUE
    } else if (t1_wins > 0 || t2_wins > 0) { game_status <- "IN_PROGRESS"
    } else { game_status <- "SCHEDULED" }

    # Normalize each game so team1 always refers to the matchup's team1 (the
    # "left" column in the KMP series tab). ESPN can list home/away in either
    # order across games in the same series, so without this the per-game
    # winner indicator can end up on the wrong side.
    games_list <- lapply(series_entry$games, function(g) {
      swap <- !is.null(team1$abbreviation) && !is.null(g$team2_abbrev) &&
              g$team2_abbrev == team1$abbreviation
      if (swap) {
        list(gameId = g$game_id, gameDate = g$game_date, status = g$status, completed = g$completed,
             homeTeamAbbrev = g$home_abbrev,
             team1 = list(abbreviation = g$team2_abbrev, score = g$team2_score, winner = g$team2_winner),
             team2 = list(abbreviation = g$team1_abbrev, score = g$team1_score, winner = g$team1_winner),
             odds = g$odds)
      } else {
        list(gameId = g$game_id, gameDate = g$game_date, status = g$status, completed = g$completed,
             homeTeamAbbrev = g$home_abbrev,
             team1 = list(abbreviation = g$team1_abbrev, score = g$team1_score, winner = g$team1_winner),
             team2 = list(abbreviation = g$team2_abbrev, score = g$team2_score, winner = g$team2_winner),
             odds = g$odds)
      }
    })
  }

  list(
    gameId = game_id, conference = conference, roundNumber = round_number, roundName = round_name,
    gameStatus = game_status, seriesSummary = series_summary, bestOf = 7,
    team1 = team1, team2 = team2,
    winner = if (isTRUE(team1$isWinner)) team1$name else if (isTRUE(team2$isWinner)) team2$name else NULL,
    games = games_list,
    comparisons = build_comparisons(team1, team2),
    regularSeasonHistory = build_h2h_history(team1$abbreviation, team2$abbreviation)
  )
}

seeded_team <- function(conference, seed) {
  df <- seeds[[conference]]
  if (is.null(df) || nrow(df) == 0 || seed > nrow(df)) return(NULL)
  row <- df[df$seed == seed, ][1, ]
  rec <- list(wins = as.integer(row$wins), losses = as.integer(row$losses), conference = conference)
  build_team(row$team_name, row$team_abbrev, row$team_logo, seed = seed, record = rec)
}

find_series <- function(conference, round_number, abbrev_a, abbrev_b) {
  key_abbrevs <- sort(c(abbrev_a, abbrev_b))
  for (k in names(playoff_events)) {
    s <- playoff_events[[k]]
    if (identical(s$conference, conference) && identical(s$roundNumber, round_number) &&
        !is.null(s$team_abbrevs) && identical(s$team_abbrevs, key_abbrevs)) return(s)
  }
  if (round_number == 4) {
    for (k in names(playoff_events)) {
      s <- playoff_events[[k]]
      if (identical(s$roundNumber, 4) && !is.null(s$team_abbrevs) && identical(s$team_abbrevs, key_abbrevs)) return(s)
    }
  }
  NULL
}

conferences_out <- list()
for (conf in c("East", "West")) {
  espn_conf <- if (conf == "East") "Eastern" else "Western"

  # Check if ESPN has actual R1 series for this conference
  espn_r1_series <- Filter(function(s) identical(s$conference, espn_conf) && s$roundNumber == 1, playoff_events)

  r1_games <- vector("list", 4); r1_winners <- vector("list", 4)

  # ESPN abbreviation mapping (ESPN uses shorter forms for some teams)
  espn_to_standings <- c("TB" = "TBL", "LA" = "LAK", "SJ" = "SJS", "NJ" = "NJD", "NY" = "NYR")
  map_abbrev <- function(a) if (a %in% names(espn_to_standings)) espn_to_standings[[a]] else a

  build_nhl_team <- function(abbrev) {
    conf_seeds <- seeds[[conf]]
    if (is.data.frame(conf_seeds) && nrow(conf_seeds) > 0) {
      sx <- conf_seeds %>% filter(team_abbrev == abbrev | team_abbrev == map_abbrev(abbrev))
      if (nrow(sx) > 0) {
        return(build_team(
          sx$team_name[1], abbrev, sx$team_logo[1], seed = sx$seed[1],
          record = list(wins = as.integer(sx$wins[1]), losses = as.integer(sx$losses[1]),
                        conference = conf)
        ))
      }
    }
    build_team(abbrev, abbrev, NA, seed = NULL, record = list(conference = conf))
  }

  # Reorder ESPN R1 series to align with the bracket's R2 mapping when possible.
  # NHL pairs by division (not by seed), so without this the semifinal pairing
  # below (`semi_pairs`) can pair the wrong R1 winners — e.g. an Atlantic
  # winner with a Metropolitan winner.
  espn_r2_series <- Filter(function(s) identical(s$conference, espn_conf) && s$roundNumber == 2, playoff_events)
  if (length(espn_r1_series) >= 4 && length(espn_r2_series) >= 1) {
    ordered <- vector("list", 4)
    used <- c()
    for (r2_idx in seq_along(espn_r2_series)) {
      if (r2_idx > 2) break
      r2_abbrevs <- espn_r2_series[[r2_idx]]$team_abbrevs
      parents <- c()
      for (i in seq_along(espn_r1_series)) {
        if (i %in% used) next
        r1_abbrevs <- espn_r1_series[[i]]$team_abbrevs
        if (any(r1_abbrevs %in% r2_abbrevs)) {
          parents <- c(parents, i)
          used <- c(used, i)
          if (length(parents) == 2) break
        }
      }
      if (length(parents) >= 1) {
        target_slots <- if (r2_idx == 1) c(1, 2) else c(3, 4)
        for (j in seq_along(parents)) {
          if (j > length(target_slots)) break
          ordered[[target_slots[j]]] <- espn_r1_series[[parents[j]]]
        }
      }
    }
    remaining <- setdiff(seq_along(espn_r1_series), used)
    empty <- which(vapply(ordered, is.null, logical(1)))
    for (k in seq_along(remaining)) {
      if (k > length(empty)) break
      ordered[[empty[k]]] <- espn_r1_series[[remaining[k]]]
    }
    espn_r1_series <- Filter(Negate(is.null), ordered)
  }

  if (length(espn_r1_series) >= 4) {
    # Use actual ESPN matchups (not standings projection)
    for (i in seq_along(espn_r1_series)) {
      if (i > 4) break
      se <- espn_r1_series[[i]]
      abbrevs <- se$team_abbrevs
      t1 <- build_nhl_team(abbrevs[1])
      t2 <- build_nhl_team(abbrevs[2])
      mu <- build_series_matchup(t1, t2, conf, 1, "First Round", paste0("nhl_", tolower(conf), "_r1_", i), se)
      r1_games[[i]] <- mu
      if (isTRUE(mu$team1$isWinner)) r1_winners[[i]] <- mu$team1
      else if (isTRUE(mu$team2$isWinner)) r1_winners[[i]] <- mu$team2
      else r1_winners[i] <- list(NULL)
    }
  } else {
    # Fall back to standings projection
    for (i in seq_along(FIRST_ROUND_SEEDS)) {
      pair <- FIRST_ROUND_SEEDS[[i]]
      t1 <- seeded_team(conf, pair[1]); t2 <- seeded_team(conf, pair[2])
      if (is.null(t1) || is.null(t2)) next
      se <- find_series(espn_conf, 1, t1$abbreviation, t2$abbreviation)
      mu <- build_series_matchup(t1, t2, conf, 1, "First Round", paste0("nhl_", tolower(conf), "_r1_", i), se)
      r1_games[[i]] <- mu
      if (isTRUE(mu$team1$isWinner)) r1_winners[[i]] <- mu$team1
      else if (isTRUE(mu$team2$isWinner)) r1_winners[[i]] <- mu$team2
      else r1_winners[i] <- list(NULL)
    }
  }

  # Conference Semifinals: prefer ESPN's actual R2 matchups when available.
  # When R2 has started but R1 isn't fully resolved (or our R1 slotting is
  # off), pairing R1 winners by `semi_pairs` can produce incorrect series.
  semi_pairs <- list(c(1, 2), c(3, 4))
  r2_games <- vector("list", 2); r2_winners <- vector("list", 2)

  if (length(espn_r2_series) > 0) {
    for (i in seq_along(espn_r2_series)) {
      if (i > 2) break
      se <- espn_r2_series[[i]]
      abbrevs <- se$team_abbrevs
      # Reuse R1 winner records (with seriesWins, etc.) when available
      reuse_winner <- function(abbrev) {
        for (w in r1_winners) {
          if (!is.null(w) && !is.null(w$abbreviation) && w$abbreviation == abbrev) return(w)
        }
        NULL
      }
      # ESPN labels the unresolved competitor with a combined name like
      # "Lightning/Canadiens" before R1 finishes. Treat that side as TBD
      # rather than fabricating a team with a null seed (which downstream
      # consumers reject).
      is_placeholder <- function(abbrev) is.character(abbrev) && grepl("/", abbrev, fixed = TRUE)
      t1 <- if (is_placeholder(abbrevs[1])) NULL else (reuse_winner(abbrevs[1]) %||% build_nhl_team(abbrevs[1]))
      t2 <- if (is_placeholder(abbrevs[2])) NULL else (reuse_winner(abbrevs[2]) %||% build_nhl_team(abbrevs[2]))
      if (is.null(t1) && is.null(t2)) next
      if (is.null(t1) || is.null(t2)) {
        r2_games[[i]] <- list(
          gameId = paste0("nhl_", tolower(conf), "_r2_", i),
          conference = conf, roundNumber = 2, roundName = "Conference Semifinals",
          gameStatus = "TBD", bestOf = 7,
          team1 = t1, team2 = t2,
          winner = NULL, games = list(), comparisons = NULL
        )
        r2_winners[i] <- list(NULL)
        next
      }
      mu <- build_series_matchup(t1, t2, conf, 2, "Conference Semifinals",
                                 paste0("nhl_", tolower(conf), "_r2_", i), se)
      r2_games[[i]] <- mu
      if (isTRUE(mu$team1$isWinner)) r2_winners[[i]] <- mu$team1
      else if (isTRUE(mu$team2$isWinner)) r2_winners[[i]] <- mu$team2
      else r2_winners[i] <- list(NULL)
    }
  }

  for (i in seq_along(semi_pairs)) {
    if (!is.null(r2_games[[i]])) next
    idx <- semi_pairs[[i]]
    a <- r1_winners[[idx[1]]]; b <- r1_winners[[idx[2]]]
    if (is.null(a) || is.null(b)) { r2_winners[i] <- list(NULL); next }
    se <- find_series(espn_conf, 2, a$abbreviation, b$abbreviation)
    mu <- build_series_matchup(a, b, conf, 2, "Conference Semifinals", paste0("nhl_", tolower(conf), "_r2_", i), se)
    r2_games[[i]] <- mu
    if (isTRUE(mu$team1$isWinner)) r2_winners[[i]] <- mu$team1
    else if (isTRUE(mu$team2$isWinner)) r2_winners[[i]] <- mu$team2
    else r2_winners[i] <- list(NULL)
  }

  # Conference Finals: prefer ESPN's actual R3 series when available
  espn_r3_series <- Filter(function(s) identical(s$conference, espn_conf) && s$roundNumber == 3, playoff_events)

  cf_game <- NULL
  if (length(espn_r3_series) > 0) {
    # Use ESPN's actual Conference Finals matchup
    se <- espn_r3_series[[1]]
    abbrevs <- se$team_abbrevs

    build_cf_team <- function(abbrev) {
      # Try to reuse R2 winner with full stats
      for (w in r2_winners) {
        if (!is.null(w) && !is.null(w$abbreviation) && w$abbreviation == abbrev) return(w)
      }
      # Fall back to building from standings/stats
      build_nhl_team(abbrev)
    }

    t1 <- build_cf_team(abbrevs[1])
    t2 <- build_cf_team(abbrevs[2])
    cf_game <- build_series_matchup(t1, t2, conf, 3, "Conference Finals",
                                    paste0("nhl_", tolower(conf), "_r3"), se)
  }

  # Fall back to R2 winners if no ESPN R3 data
  if (is.null(cf_game)) {
    a <- r2_winners[[1]]; b <- r2_winners[[2]]
    if (!is.null(a) && !is.null(b)) {
      se <- find_series(espn_conf, 3, a$abbreviation, b$abbreviation)
      cf_game <- build_series_matchup(a, b, conf, 3, "Conference Finals", paste0("nhl_", tolower(conf), "_r3"), se)
    } else {
      cf_game <- list(gameId = paste0("nhl_", tolower(conf), "_r3"), conference = conf, roundNumber = 3,
                      roundName = "Conference Finals", gameStatus = "TBD", bestOf = 7,
                      team1 = NULL, team2 = NULL, winner = NULL, games = list(), comparisons = NULL)
    }
  }

  conferences_out[[length(conferences_out) + 1]] <- list(
    name = conf, colorHex = CONFERENCE_COLORS[[conf]],
    rounds = list(
      list(roundNumber = 1, roundName = "First Round", games = Filter(Negate(is.null), r1_games)),
      list(roundNumber = 2, roundName = "Conference Semifinals", games = Filter(Negate(is.null), r2_games)),
      list(roundNumber = 3, roundName = "Conference Finals", games = list(cf_game))
    ),
    champion = if (!is.null(cf_game$winner))
      (if (isTRUE(cf_game$team1$isWinner)) cf_game$team1 else cf_game$team2) else NULL
  )
}

# Stanley Cup Final
east_champ <- conferences_out[[1]]$champion
west_champ <- conferences_out[[2]]$champion
finals_game <- if (!is.null(east_champ) && !is.null(west_champ)) {
  se <- find_series("Finals", 4, east_champ$abbreviation, west_champ$abbreviation)
  build_series_matchup(east_champ, west_champ, "Finals", 4, "Stanley Cup Final", "nhl_finals", se)
} else {
  list(gameId = "nhl_finals", conference = "Finals", roundNumber = 4, roundName = "Stanley Cup Final",
       gameStatus = "TBD", bestOf = 7, team1 = NULL, team2 = NULL, winner = NULL, games = list(), comparisons = NULL)
}

# ============================================================================
# STEP 5: Emit JSON
# ============================================================================
cat("\n5. Emitting bracket JSON...\n")

title <- paste0(NHL_SEASON_END, " NHL Playoff Bracket")
subtitle <- switch(bracket_status, PROJECTED = "Projected", IN_PROGRESS = format(today, "%B %d, %Y"), COMPLETED = "Playoffs complete")

output_data <- list(
  sport = "NHL",
  visualizationType = "NHL_PLAYOFF_BRACKET",
  title = title, subtitle = subtitle,
  description = paste0(
    "NHL playoff bracket with matchup statistics. Each matchup compares the ",
    "two teams' regular-season offense and defense with league-wide rankings.\n\n",
    "OFFENSE:\n\n",
    " • Points %: Points earned as a share of points available, (W×2 + OTL) / (GP×2). Higher is better.\n\n",
    " • Goals/Game: Average goals scored per game. Higher is better.\n\n",
    " • Goal Diff/Game: Goals scored minus goals allowed per game. Higher is better.\n\n",
    " • Shots/Game: Average shots on goal per game. Higher is better.\n\n",
    " • Power Play %: Share of power plays that produce a goal. Higher is better.\n\n",
    " • Faceoff Win %: Share of faceoffs won. Higher is better.\n\n",
    " • Hits/Game: Average hits (legal body checks) delivered per game. Higher is better.\n\n",
    " • Takeaways/Game: Average turnovers forced per game. Higher is better.\n\n",
    " • xG% (5v5): Share of expected goals generated at even strength, based on shot quality. Higher is better.\n\n",
    "DEFENSE:\n\n",
    " • Goals Against/Game: Average goals allowed per game. Lower is better.\n\n",
    " • Shots Against/Game: Average shots allowed per game. Lower is better.\n\n",
    " • Penalty Kill %: Share of opponent power plays killed without a goal. Higher is better.\n\n",
    " • Blocks/Game: Average shots blocked per game. Higher is better.\n\n",
    " • Giveaways/Game: Average turnovers committed per game. Lower is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "NHL API / Natural Stat Trick",
  tags = list(list(label = "playoffs", layout = "left", color = "#4CAF50"),
              list(label = "bracket", layout = "left", color = "#FF9800")),
  sortOrder = -1, season = NHL_SEASON_END,
  bracketStatus = bracket_status,
  tenthXgfPctByWeek = if (length(tenth_xgf_pct_by_week) > 0) tenth_xgf_pct_by_week else setNames(list(), character(0)),
  leagueCumXgStats = league_cum_xg_stats,
  leagueXgVsPointsStats = league_xg_vs_pts,
  scatterPlotQuadrants = list(
    topRight = list(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
    topLeft = list(label = "Lucky", color = "#2196F3", lightModeColor = "#2196F3"),
    bottomLeft = list(label = "Struggling", color = "#F44336", lightModeColor = "#F44336"),
    bottomRight = list(label = "Unlucky", color = "#FF9800", lightModeColor = "#FF9800")
  ),
  conferences = conferences_out,
  finals = finals_game
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

history <- load_bracket_history()
if (is.null(history)) history <- list(series = list(), lastUpdated = NULL)
for (k in names(playoff_events)) history$series[[k]] <- playoff_events[[k]]
history$lastUpdated <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
save_bracket_history(history)

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (nzchar(s3_bucket)) {
  s3_key <- paste0(S3_PREFIX, "/nhl__playoff_bracket.json")
  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  result <- system(paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json"))
  if (result != 0) stop("Failed to upload to S3")
  cat("Uploaded to S3:", s3_path, "\n")
  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_ts <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  item <- sprintf('{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
                  s3_key, utc_ts, title)
  system(sprintf('aws dynamodb put-item --table-name %s --item %s', shQuote(dynamodb_table), shQuote(item)))
} else {
  dev_out <- "/tmp/nhl_playoff_bracket.json"
  file.copy(tmp_file, dev_out, overwrite = TRUE)
  cat("Development mode - output written to:", dev_out, "\n")
}

cat("\n=== NHL Playoff Bracket generation complete ===\n")
