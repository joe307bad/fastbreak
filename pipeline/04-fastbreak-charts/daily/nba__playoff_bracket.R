#!/usr/bin/env Rscript

# NBA Playoff Bracket generator.
# Builds a bracket for the current NBA postseason with matchup stats and writes
# JSON for the KMP app (visualizationType = "NBA_PLAYOFF_BRACKET").
#
# Pre-playoffs   -> projected bracket from current standings (top 8 per conference).
# During/after   -> live bracket from ESPN postseason scoreboard, with series scores.
#
# Mirrors the pattern in cbb__bracket_stats for structure, and reuses the NBA
# team-stats / matchup-comparison logic from nba__matchup_stats.

library(hoopR)
library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)
library(lubridate)

# ============================================================================
# Constants
# ============================================================================
CURRENT_YEAR <- as.numeric(format(Sys.Date(), "%Y"))
CURRENT_MONTH <- as.numeric(format(Sys.Date(), "%m"))
NBA_SEASON <- if (CURRENT_MONTH >= 10) CURRENT_YEAR + 1 else CURRENT_YEAR
NBA_SEASON_STRING <- paste0(NBA_SEASON - 1, "-", substr(NBA_SEASON, 3, 4))

# Window used when scanning ESPN scoreboard for postseason games.
# Playoffs roughly run mid-April through mid-June.
PLAYOFFS_START <- as.Date(paste0(NBA_SEASON, "-04-15"))
PLAYOFFS_END   <- as.Date(paste0(NBA_SEASON, "-06-30"))

# Environment and S3 prefix
ENV <- toupper(Sys.getenv("ENV", "DEV"))
S3_PREFIX <- if (ENV == "PROD") "prod" else "dev"

# S3 key for persisting bracket history (so completed series/games survive restarts)
HISTORY_S3_KEY <- paste0(S3_PREFIX, "/nba__bracket_history.json")

CONFERENCE_COLORS <- list(
  East = "#1565C0",
  West = "#C62828"
)

# Round definitions per conference
CONFERENCE_ROUNDS <- list(
  list(roundNumber = 1, roundName = "First Round", games = 4),
  list(roundNumber = 2, roundName = "Conference Semifinals", games = 2),
  list(roundNumber = 3, roundName = "Conference Finals", games = 1)
)

# First-round seed matchup order: 1v8, 4v5, 3v6, 2v7
FIRST_ROUND_SEEDS <- list(c(1, 8), c(4, 5), c(3, 6), c(2, 7))

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

add_api_delay <- function() Sys.sleep(0.3)

safe_num <- function(x) if (is_valid_value(x)) as.numeric(x) else NA_real_

# Parse ESPN playoff-note headlines into a round identifier.
# Examples: "East First Round - Game 3", "East Conf. Semifinals - Game 1",
# "NBA Finals - Game 7"
parse_playoff_round <- function(headline) {
  if (is.null(headline) || headline == "") {
    return(list(conference = NA, roundNumber = NA, roundName = NA))
  }
  hl <- tolower(headline)

  conference <- NA
  if (grepl("east", hl)) conference <- "East"
  else if (grepl("west", hl)) conference <- "West"

  round_num <- NA
  round_name <- NA
  if (grepl("finals", hl) && !grepl("conf", hl) && is.na(conference)) {
    round_num <- 4; round_name <- "NBA Finals"; conference <- "Finals"
  } else if (grepl("conf(\\.|erence)? finals", hl)) {
    round_num <- 3; round_name <- "Conference Finals"
  } else if (grepl("conf(\\.|erence)? semifinals|semifinals", hl)) {
    round_num <- 2; round_name <- "Conference Semifinals"
  } else if (grepl("first round|1st round|round 1", hl)) {
    round_num <- 1; round_name <- "First Round"
  } else if (grepl("play-in|play in", hl)) {
    round_num <- 0; round_name <- "Play-In"
  }

  list(conference = conference, roundNumber = round_num, roundName = round_name)
}

# ============================================================================
# History persistence (same pattern as cbb__bracket_stats)
# ============================================================================
load_bracket_history <- function() {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
  if (!nzchar(s3_bucket)) {
    local_history <- "/tmp/nba_bracket_history.json"
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
    file.copy(tmp_file, "/tmp/nba_bracket_history.json", overwrite = TRUE)
    unlink(tmp_file)
    return(TRUE)
  }
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  unlink(tmp_file)
  result == 0
}

cat("=== NBA Playoff Bracket Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")
cat("Season:", NBA_SEASON_STRING, "\n")

# ============================================================================
# STEP 1: Season team stats (mirrors nba__matchup_stats)
# ============================================================================
cat("\n1. Loading NBA team box scores...\n")

team_box <- hoopR::load_nba_team_box(seasons = NBA_SEASON) %>%
  filter(!grepl("All-Star|All Star|Rising Stars|^World$|^Stripes$|^Team ",
                team_display_name, ignore.case = TRUE))

cat("Loaded", nrow(team_box), "team box score records\n")

# Regular-season games only, used for head-to-head matchup history.
# season_type: 1=preseason, 2=regular season, 3=postseason, 4=all-star.
regular_season_box <- if ("season_type" %in% names(team_box)) {
  team_box %>% filter(season_type == 2)
} else {
  # Fallback: assume regular season when season_type is unavailable
  team_box
}
cat("Regular-season box rows:", nrow(regular_season_box), "\n")

team_stats <- team_box %>%
  group_by(team_id, team_display_name, team_short_display_name) %>%
  summarise(
    team_abbreviation = first(team_abbreviation),
    team_logo = first(team_logo),
    games_played = n(),
    points = sum(team_score, na.rm = TRUE),
    field_goals_made = sum(field_goals_made, na.rm = TRUE),
    field_goals_attempted = sum(field_goals_attempted, na.rm = TRUE),
    three_point_field_goals_made = sum(three_point_field_goals_made, na.rm = TRUE),
    three_point_field_goals_attempted = sum(three_point_field_goals_attempted, na.rm = TRUE),
    offensive_rebounds_total = sum(offensive_rebounds, na.rm = TRUE),
    defensive_rebounds_total = sum(defensive_rebounds, na.rm = TRUE),
    assists_total = sum(assists, na.rm = TRUE),
    steals_total = sum(steals, na.rm = TRUE),
    blocks_total = sum(blocks, na.rm = TRUE),
    turnovers_total = sum(turnovers, na.rm = TRUE),
    opp_points = sum(opponent_team_score, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    points_per_game = points / games_played,
    fg_pct = field_goals_made / field_goals_attempted * 100,
    three_pt_pct = three_point_field_goals_made / three_point_field_goals_attempted * 100,
    rebounds_per_game = (offensive_rebounds_total + defensive_rebounds_total) / games_played,
    assists_per_game = assists_total / games_played,
    steals_per_game = steals_total / games_played,
    blocks_per_game = blocks_total / games_played,
    turnovers_per_game = turnovers_total / games_played,
    opp_points_per_game = opp_points / games_played
  )

opponent_stats <- team_box %>%
  select(game_id, team_id,
         opp_id = opponent_team_id,
         opp_field_goals_made = field_goals_made,
         opp_field_goals_attempted = field_goals_attempted,
         opp_three_point_made = three_point_field_goals_made,
         opp_three_point_attempted = three_point_field_goals_attempted,
         opp_assists = assists,
         opp_turnovers_forced = turnovers) %>%
  group_by(team_id = opp_id) %>%
  summarise(
    games = n(),
    opp_fg_made = sum(opp_field_goals_made, na.rm = TRUE),
    opp_fg_attempted = sum(opp_field_goals_attempted, na.rm = TRUE),
    opp_three_pt_made = sum(opp_three_point_made, na.rm = TRUE),
    opp_three_pt_attempted = sum(opp_three_point_attempted, na.rm = TRUE),
    opp_assists_total = sum(opp_assists, na.rm = TRUE),
    opp_turnovers_total = sum(opp_turnovers_forced, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  mutate(
    opp_fg_pct = opp_fg_made / opp_fg_attempted * 100,
    opp_three_pt_pct = opp_three_pt_made / opp_three_pt_attempted * 100,
    opp_assists_per_game = opp_assists_total / games,
    opp_turnovers_per_game = opp_turnovers_total / games
  )

team_stats <- team_stats %>% left_join(opponent_stats, by = "team_id")

# Net rating from box scores (used when NBA API is unavailable)
game_ratings <- team_box %>%
  select(game_id, game_date, team_id, team_abbreviation, team_display_name,
         team_score, opponent_team_score,
         field_goals_attempted, free_throws_attempted,
         offensive_rebounds, turnovers) %>%
  inner_join(
    team_box %>% select(game_id,
                        opp_team_id = team_id,
                        opp_fga = field_goals_attempted,
                        opp_fta = free_throws_attempted,
                        opp_oreb = offensive_rebounds,
                        opp_tov = turnovers),
    by = "game_id",
    relationship = "many-to-many"
  ) %>%
  filter(team_id != opp_team_id) %>%
  mutate(
    team_poss = field_goals_attempted + 0.44 * free_throws_attempted - offensive_rebounds + turnovers,
    opp_poss  = opp_fga + 0.44 * opp_fta - opp_oreb + opp_tov,
    avg_poss  = (team_poss + opp_poss) / 2,
    off_rating = ifelse(avg_poss > 0, (team_score / avg_poss) * 100, NA_real_),
    def_rating = ifelse(avg_poss > 0, (opponent_team_score / avg_poss) * 100, NA_real_),
    net_rating_g = off_rating - def_rating
  )

ratings_by_team <- game_ratings %>%
  group_by(team_display_name) %>%
  summarise(
    offensive_rating = mean(off_rating, na.rm = TRUE),
    defensive_rating = mean(def_rating, na.rm = TRUE),
    net_rating       = mean(net_rating_g, na.rm = TRUE),
    .groups = "drop"
  )

team_stats <- team_stats %>% left_join(ratings_by_team, by = "team_display_name")

# Weekly chart data (cumulative net rating + weekly efficiency)
season_start_date <- as.Date(paste0(NBA_SEASON - 1, "-10-01"))
chart_game_ratings <- game_ratings %>%
  mutate(
    game_date_parsed = game_date,
    week_num = as.integer(floor(difftime(game_date_parsed, season_start_date, units = "weeks")) + 1)
  ) %>%
  filter(week_num > 0, !is.na(net_rating_g))

cum_net_rating_by_team <- chart_game_ratings %>%
  group_by(team_abbreviation) %>%
  arrange(game_date_parsed) %>%
  mutate(cum_avg_net_rating = cummean(net_rating_g)) %>%
  group_by(team_abbreviation, week_num) %>%
  slice_tail(n = 1) %>%
  ungroup() %>%
  select(team_abbreviation, week_num, cum_net_rating = cum_avg_net_rating)

tenth_net_rating_by_week <- cum_net_rating_by_team %>%
  group_by(week_num) %>%
  filter(n() >= 25) %>%
  arrange(desc(cum_net_rating)) %>%
  slice(10) %>%
  ungroup() %>%
  select(week_num, tenth_net_rating = cum_net_rating)

league_cum_net_rating_stats <- list(
  minCumNetRating = round(min(cum_net_rating_by_team$cum_net_rating, na.rm = TRUE), 1),
  maxCumNetRating = round(max(cum_net_rating_by_team$cum_net_rating, na.rm = TRUE), 1)
)

weekly_efficiency <- chart_game_ratings %>%
  group_by(team_abbreviation, week_num) %>%
  summarise(
    off_rating = mean(off_rating, na.rm = TRUE),
    def_rating = mean(def_rating, na.rm = TRUE),
    .groups = "drop"
  ) %>%
  group_by(team_abbreviation) %>%
  arrange(desc(week_num)) %>%
  slice_head(n = 10) %>%
  arrange(week_num) %>%
  ungroup()

league_efficiency_stats <- weekly_efficiency %>%
  summarise(
    avg_off_rating = mean(off_rating, na.rm = TRUE),
    avg_def_rating = mean(def_rating, na.rm = TRUE),
    min_off_rating = min(off_rating, na.rm = TRUE),
    max_off_rating = max(off_rating, na.rm = TRUE),
    min_def_rating = min(def_rating, na.rm = TRUE),
    max_def_rating = max(def_rating, na.rm = TRUE)
  )

cat("Computed chart data for", length(unique(cum_net_rating_by_team$team_abbreviation)), "teams\n")

# ============================================================================
# 1-month trend (last 4 weeks of the regular season)
# Mirrors the "monthTrend" block in nba__matchup_stats.R so each matchup can
# show how each team was trending heading into / during the playoffs.
# ============================================================================
cat("\nComputing 1-month trend (last 4 weeks, regular season)...\n")

rs_game_ids <- if ("season_type" %in% names(team_box)) {
  unique(team_box$game_id[team_box$season_type == 2])
} else {
  unique(team_box$game_id)
}

rs_game_ratings <- game_ratings %>%
  filter(game_id %in% rs_game_ids) %>%
  left_join(
    team_box %>% select(game_id, team_id,
                        assists_game = assists,
                        opp_abbrev = opponent_team_abbreviation),
    by = c("game_id", "team_id")
  ) %>%
  mutate(
    game_date_parsed = game_date,
    week_num = as.integer(floor(difftime(game_date_parsed, season_start_date, units = "weeks")) + 1)
  ) %>%
  filter(week_num > 0, !is.na(net_rating_g))

month_trend_stats <- tryCatch({
  if (nrow(rs_game_ratings) == 0) {
    NULL
  } else {
    current_week <- max(rs_game_ratings$week_num, na.rm = TRUE)
    month_start_week <- max(current_week - 3, 1)

    month_games <- rs_game_ratings %>% filter(week_num >= month_start_week)
    cat("Month trend: weeks", month_start_week, "to", current_week,
        "(", nrow(month_games), "team-games)\n")

    base_stats <- month_games %>%
      group_by(team_abbreviation) %>%
      summarise(
        games_played = n(),
        wins = sum(team_score > opponent_team_score, na.rm = TRUE),
        losses = sum(team_score < opponent_team_score, na.rm = TRUE),
        avg_net_rating = mean(net_rating_g, na.rm = TRUE),
        avg_off_rating = mean(off_rating, na.rm = TRUE),
        avg_def_rating = mean(def_rating, na.rm = TRUE),
        points_per_game = mean(team_score, na.rm = TRUE),
        assists_per_game = mean(assists_game, na.rm = TRUE),
        turnovers_per_game = mean(turnovers, na.rm = TRUE),
        .groups = "drop"
      )

    turnovers_forced <- month_games %>%
      group_by(defending_team = opp_abbrev) %>%
      summarise(turnovers_forced_pg = mean(turnovers, na.rm = TRUE), .groups = "drop")

    df <- base_stats %>%
      left_join(turnovers_forced, by = c("team_abbreviation" = "defending_team")) %>%
      mutate(
        turnover_diff = coalesce(turnovers_forced_pg, 0) - turnovers_per_game,
        win_pct = ifelse(wins + losses > 0, wins / (wins + losses), NA_real_)
      )

    nr  <- tied_rank(-df$avg_net_rating);    df$net_rating_rank <- nr$rank;  df$net_rating_rankDisplay <- nr$rankDisplay
    orr <- tied_rank(-df$avg_off_rating);    df$off_rating_rank <- orr$rank; df$off_rating_rankDisplay <- orr$rankDisplay
    dr  <- tied_rank( df$avg_def_rating);    df$def_rating_rank <- dr$rank;  df$def_rating_rankDisplay <- dr$rankDisplay
    pp  <- tied_rank(-df$points_per_game);   df$ppg_rank <- pp$rank;         df$ppg_rankDisplay <- pp$rankDisplay
    ap  <- tied_rank(-df$assists_per_game);  df$apg_rank <- ap$rank;         df$apg_rankDisplay <- ap$rankDisplay
    tp  <- tied_rank( df$turnovers_per_game);df$tpg_rank <- tp$rank;         df$tpg_rankDisplay <- tp$rankDisplay
    td  <- tied_rank(-df$turnover_diff);     df$tov_diff_rank <- td$rank;    df$tov_diff_rankDisplay <- td$rankDisplay
    rr  <- tied_rank(-df$win_pct);           df$record_rank <- rr$rank;      df$record_rankDisplay <- rr$rankDisplay
    df
  }
}, error = function(e) {
  cat("Month trend calc error:", e$message, "\n")
  NULL
})

if (!is.null(month_trend_stats)) {
  cat("Computed month trend for", nrow(month_trend_stats), "teams\n")
}

# ============================================================================
# Playoff trend (postseason games only, ranked across playoff teams)
# Same metric set as monthTrend, but the games used and the ranking pool are
# limited to the postseason so we can see how teams stack up across the games
# that have actually been played in the playoffs.
# ============================================================================
cat("\nComputing playoff trend (postseason games only)...\n")

playoff_game_ids <- if ("season_type" %in% names(team_box)) {
  unique(team_box$game_id[team_box$season_type == 3])
} else character(0)

playoff_trend_stats <- tryCatch({
  if (length(playoff_game_ids) == 0) {
    cat("No postseason games found yet\n")
    NULL
  } else {
    po_game_ratings <- game_ratings %>%
      filter(game_id %in% playoff_game_ids) %>%
      left_join(
        team_box %>% select(game_id, team_id,
                            assists_game = assists,
                            opp_abbrev = opponent_team_abbreviation),
        by = c("game_id", "team_id")
      ) %>%
      filter(!is.na(net_rating_g))

    cat("Playoff trend:", nrow(po_game_ratings), "team-games\n")

    base_stats <- po_game_ratings %>%
      group_by(team_abbreviation) %>%
      summarise(
        games_played = n(),
        wins = sum(team_score > opponent_team_score, na.rm = TRUE),
        losses = sum(team_score < opponent_team_score, na.rm = TRUE),
        avg_net_rating = mean(net_rating_g, na.rm = TRUE),
        avg_off_rating = mean(off_rating, na.rm = TRUE),
        avg_def_rating = mean(def_rating, na.rm = TRUE),
        points_per_game = mean(team_score, na.rm = TRUE),
        assists_per_game = mean(assists_game, na.rm = TRUE),
        turnovers_per_game = mean(turnovers, na.rm = TRUE),
        .groups = "drop"
      )

    turnovers_forced <- po_game_ratings %>%
      group_by(defending_team = opp_abbrev) %>%
      summarise(turnovers_forced_pg = mean(turnovers, na.rm = TRUE), .groups = "drop")

    df <- base_stats %>%
      left_join(turnovers_forced, by = c("team_abbreviation" = "defending_team")) %>%
      mutate(
        turnover_diff = coalesce(turnovers_forced_pg, 0) - turnovers_per_game,
        win_pct = ifelse(wins + losses > 0, wins / (wins + losses), NA_real_)
      )

    nr  <- tied_rank(-df$avg_net_rating);    df$net_rating_rank <- nr$rank;  df$net_rating_rankDisplay <- nr$rankDisplay
    orr <- tied_rank(-df$avg_off_rating);    df$off_rating_rank <- orr$rank; df$off_rating_rankDisplay <- orr$rankDisplay
    dr  <- tied_rank( df$avg_def_rating);    df$def_rating_rank <- dr$rank;  df$def_rating_rankDisplay <- dr$rankDisplay
    pp  <- tied_rank(-df$points_per_game);   df$ppg_rank <- pp$rank;         df$ppg_rankDisplay <- pp$rankDisplay
    ap  <- tied_rank(-df$assists_per_game);  df$apg_rank <- ap$rank;         df$apg_rankDisplay <- ap$rankDisplay
    tp  <- tied_rank( df$turnovers_per_game);df$tpg_rank <- tp$rank;         df$tpg_rankDisplay <- tp$rankDisplay
    td  <- tied_rank(-df$turnover_diff);     df$tov_diff_rank <- td$rank;    df$tov_diff_rankDisplay <- td$rankDisplay
    rr  <- tied_rank(-df$win_pct);           df$record_rank <- rr$rank;      df$record_rankDisplay <- rr$rankDisplay
    df
  }
}, error = function(e) {
  cat("Playoff trend calc error:", e$message, "\n")
  NULL
})

if (!is.null(playoff_trend_stats)) {
  cat("Computed playoff trend for", nrow(playoff_trend_stats), "teams\n")
}

# Ranks
team_stats <- team_stats %>%
  mutate(
    across(c(points_per_game, fg_pct, three_pt_pct, rebounds_per_game,
             assists_per_game, steals_per_game, blocks_per_game,
             offensive_rating, net_rating), as.numeric)
  )

ppg_r <- tied_rank(-team_stats$points_per_game)
fg_r  <- tied_rank(-team_stats$fg_pct)
tp_r  <- tied_rank(-team_stats$three_pt_pct)
rpg_r <- tied_rank(-team_stats$rebounds_per_game)
apg_r <- tied_rank(-team_stats$assists_per_game)
spg_r <- tied_rank(-team_stats$steals_per_game)
bpg_r <- tied_rank(-team_stats$blocks_per_game)
tpg_r <- tied_rank( team_stats$turnovers_per_game)
oppg_r <- tied_rank(team_stats$opp_points_per_game)
ofg_r  <- tied_rank(team_stats$opp_fg_pct)
otp_r  <- tied_rank(team_stats$opp_three_pt_pct)
oap_r  <- tied_rank(team_stats$opp_assists_per_game)
otv_r  <- tied_rank(-team_stats$opp_turnovers_per_game)
ort_r  <- tied_rank(-team_stats$offensive_rating)
drt_r  <- tied_rank( team_stats$defensive_rating)
nrt_r  <- tied_rank(-team_stats$net_rating)

team_stats <- team_stats %>%
  mutate(
    points_per_game_rank = ppg_r$rank, points_per_game_rankDisplay = ppg_r$rankDisplay,
    fg_pct_rank = fg_r$rank, fg_pct_rankDisplay = fg_r$rankDisplay,
    three_pt_pct_rank = tp_r$rank, three_pt_pct_rankDisplay = tp_r$rankDisplay,
    rebounds_per_game_rank = rpg_r$rank, rebounds_per_game_rankDisplay = rpg_r$rankDisplay,
    assists_per_game_rank = apg_r$rank, assists_per_game_rankDisplay = apg_r$rankDisplay,
    steals_per_game_rank = spg_r$rank, steals_per_game_rankDisplay = spg_r$rankDisplay,
    blocks_per_game_rank = bpg_r$rank, blocks_per_game_rankDisplay = bpg_r$rankDisplay,
    turnovers_per_game_rank = tpg_r$rank, turnovers_per_game_rankDisplay = tpg_r$rankDisplay,
    opp_points_per_game_rank = oppg_r$rank, opp_points_per_game_rankDisplay = oppg_r$rankDisplay,
    opp_fg_pct_rank = ofg_r$rank, opp_fg_pct_rankDisplay = ofg_r$rankDisplay,
    opp_three_pt_pct_rank = otp_r$rank, opp_three_pt_pct_rankDisplay = otp_r$rankDisplay,
    opp_assists_per_game_rank = oap_r$rank, opp_assists_per_game_rankDisplay = oap_r$rankDisplay,
    opp_turnovers_per_game_rank = otv_r$rank, opp_turnovers_per_game_rankDisplay = otv_r$rankDisplay,
    offensive_rating_rank = ort_r$rank, offensive_rating_rankDisplay = ort_r$rankDisplay,
    defensive_rating_rank = drt_r$rank, defensive_rating_rankDisplay = drt_r$rankDisplay,
    net_rating_rank = nrt_r$rank, net_rating_rankDisplay = nrt_r$rankDisplay
  )

cat("Computed stats + ranks for", nrow(team_stats), "teams\n")

# ============================================================================
# STEP 2: Standings -> seeds per conference
# ============================================================================
cat("\n2. Fetching standings for conference seeding...\n")

standings_url <- "https://site.api.espn.com/apis/v2/sports/basketball/nba/standings"
standings_resp <- tryCatch(GET(standings_url), error = function(e) NULL)

seeds <- list(East = list(), West = list())

if (!is.null(standings_resp) && status_code(standings_resp) == 200) {
  sd <- content(standings_resp, as = "parsed")
  if (!is.null(sd$children)) {
    for (conf in sd$children) {
      conf_name <- conf$name  # "Eastern Conference" / "Western Conference"
      bucket <- if (grepl("East", conf_name)) "East" else if (grepl("West", conf_name)) "West" else NA
      if (is.na(bucket) || is.null(conf$standings$entries)) next

      get_stat <- function(stats, name) {
        for (s in stats) if (!is.null(s$name) && s$name == name) return(as.numeric(s$value))
        NA_real_
      }

      rows <- lapply(conf$standings$entries, function(entry) {
        list(
          team_id = entry$team$id,
          team_name = entry$team$displayName,
          team_abbrev = entry$team$abbreviation,
          team_logo = if (!is.null(entry$team$logos) && length(entry$team$logos) > 0)
            entry$team$logos[[1]]$href else NA,
          wins = get_stat(entry$stats, "wins"),
          losses = get_stat(entry$stats, "losses"),
          win_pct = get_stat(entry$stats, "winPercent"),
          # ESPN's playoffSeed already accounts for tiebreakers (head-to-head,
          # division titles, play-in results). Prefer it over a naive sort by
          # wins/losses, which can produce the wrong first-round matchups when
          # teams are tied.
          playoff_seed = get_stat(entry$stats, "playoffSeed")
        )
      })
      df <- bind_rows(rows)
      if (any(!is.na(df$playoff_seed) & df$playoff_seed > 0)) {
        df <- df %>%
          arrange(ifelse(is.na(playoff_seed) | playoff_seed <= 0, 999, playoff_seed),
                  desc(wins), losses) %>%
          mutate(seed = if_else(!is.na(playoff_seed) & playoff_seed > 0,
                                as.integer(playoff_seed),
                                row_number()))
      } else {
        df <- df %>%
          arrange(desc(wins), losses) %>%
          mutate(seed = row_number())
      }
      seeds[[bucket]] <- df
    }
  }
}

seeds_ok <- is.data.frame(seeds$East) && nrow(seeds$East) > 0 &&
            is.data.frame(seeds$West) && nrow(seeds$West) > 0
if (!seeds_ok) {
  cat("WARNING: Could not load standings - seeds will be missing for some conferences\n")
}

# Helper to find team_stats row by ESPN abbreviation (falls back to displayName)
find_team_stats <- function(abbrev, team_name) {
  if (is_valid_value(abbrev)) {
    match_row <- team_stats %>% filter(team_abbreviation == abbrev)
    if (nrow(match_row) > 0) return(match_row[1, ])
  }
  if (is_valid_value(team_name)) {
    match_row <- team_stats %>% filter(team_display_name == team_name)
    if (nrow(match_row) > 0) return(match_row[1, ])
  }
  NULL
}

# ============================================================================
# Team / matchup builders
# ============================================================================

build_team <- function(name, abbrev, logo, seed = NULL, record = NULL) {
  stats_row <- find_team_stats(abbrev, name)
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
    pointsPerGame = list(value = round(stats_row$points_per_game, 1),
                         rank = as.integer(stats_row$points_per_game_rank),
                         rankDisplay = stats_row$points_per_game_rankDisplay),
    fieldGoalPct = list(value = round(stats_row$fg_pct / 100, 4),
                        rank = as.integer(stats_row$fg_pct_rank),
                        rankDisplay = stats_row$fg_pct_rankDisplay),
    threePointPct = list(value = round(stats_row$three_pt_pct / 100, 4),
                         rank = as.integer(stats_row$three_pt_pct_rank),
                         rankDisplay = stats_row$three_pt_pct_rankDisplay),
    reboundsPerGame = list(value = round(stats_row$rebounds_per_game, 1),
                           rank = as.integer(stats_row$rebounds_per_game_rank),
                           rankDisplay = stats_row$rebounds_per_game_rankDisplay),
    assistsPerGame = list(value = round(stats_row$assists_per_game, 1),
                          rank = as.integer(stats_row$assists_per_game_rank),
                          rankDisplay = stats_row$assists_per_game_rankDisplay),
    stealsPerGame = list(value = round(stats_row$steals_per_game, 1),
                         rank = as.integer(stats_row$steals_per_game_rank),
                         rankDisplay = stats_row$steals_per_game_rankDisplay),
    blocksPerGame = list(value = round(stats_row$blocks_per_game, 1),
                         rank = as.integer(stats_row$blocks_per_game_rank),
                         rankDisplay = stats_row$blocks_per_game_rankDisplay),
    turnoversPerGame = list(value = round(stats_row$turnovers_per_game, 1),
                            rank = as.integer(stats_row$turnovers_per_game_rank),
                            rankDisplay = stats_row$turnovers_per_game_rankDisplay),
    offensiveRating = list(value = round(stats_row$offensive_rating, 1),
                           rank = as.integer(stats_row$offensive_rating_rank),
                           rankDisplay = stats_row$offensive_rating_rankDisplay),
    defensiveRating = list(value = round(stats_row$defensive_rating, 1),
                           rank = as.integer(stats_row$defensive_rating_rank),
                           rankDisplay = stats_row$defensive_rating_rankDisplay),
    netRating = list(value = round(stats_row$net_rating, 1),
                     rank = as.integer(stats_row$net_rating_rank),
                     rankDisplay = stats_row$net_rating_rankDisplay),
    oppPointsPerGame = list(value = round(stats_row$opp_points_per_game, 1),
                            rank = as.integer(stats_row$opp_points_per_game_rank),
                            rankDisplay = stats_row$opp_points_per_game_rankDisplay),
    oppFieldGoalPct = list(value = round(stats_row$opp_fg_pct / 100, 4),
                           rank = as.integer(stats_row$opp_fg_pct_rank),
                           rankDisplay = stats_row$opp_fg_pct_rankDisplay),
    oppThreePointPct = list(value = round(stats_row$opp_three_pt_pct / 100, 4),
                            rank = as.integer(stats_row$opp_three_pt_pct_rank),
                            rankDisplay = stats_row$opp_three_pt_pct_rankDisplay),
    oppAssistsPerGame = list(value = round(stats_row$opp_assists_per_game, 1),
                             rank = as.integer(stats_row$opp_assists_per_game_rank),
                             rankDisplay = stats_row$opp_assists_per_game_rankDisplay),
    forcedTurnoversPerGame = list(value = round(stats_row$opp_turnovers_per_game, 1),
                                  rank = as.integer(stats_row$opp_turnovers_per_game_rank),
                                  rankDisplay = stats_row$opp_turnovers_per_game_rankDisplay)
  )

  # Add cumulative net rating by week
  team_abbr <- stats_row$team_abbreviation
  team_cum <- cum_net_rating_by_team %>% filter(team_abbreviation == team_abbr)
  ts$cumNetRatingByWeek <- if (nrow(team_cum) > 0) {
    setNames(as.list(round(team_cum$cum_net_rating, 1)),
             paste0("week-", team_cum$week_num))
  } else setNames(list(), character(0))

  # Add weekly efficiency (off/def rating per week)
  team_eff <- weekly_efficiency %>% filter(team_abbreviation == team_abbr)
  ts$efficiencyByWeek <- if (nrow(team_eff) > 0) {
    setNames(lapply(seq_len(nrow(team_eff)), function(i) {
      list(offRating = round(team_eff$off_rating[i], 1),
           defRating = round(team_eff$def_rating[i], 1))
    }), paste0("week-", team_eff$week_num))
  } else setNames(list(), character(0))

  # 1-month trend rankings (last 4 weeks of regular season)
  team_month <- if (!is.null(month_trend_stats)) {
    month_trend_stats %>% filter(team_abbreviation == team_abbr)
  } else data.frame()
  ts$monthTrend <- if (nrow(team_month) > 0) {
    list(
      gamesPlayed = as.integer(team_month$games_played),
      record = list(
        wins = as.integer(team_month$wins),
        losses = as.integer(team_month$losses),
        rank = as.integer(team_month$record_rank),
        rankDisplay = team_month$record_rankDisplay
      ),
      netRating = list(value = round(team_month$avg_net_rating, 1),
                       rank = as.integer(team_month$net_rating_rank),
                       rankDisplay = team_month$net_rating_rankDisplay),
      offensiveRating = list(value = round(team_month$avg_off_rating, 1),
                             rank = as.integer(team_month$off_rating_rank),
                             rankDisplay = team_month$off_rating_rankDisplay),
      defensiveRating = list(value = round(team_month$avg_def_rating, 1),
                             rank = as.integer(team_month$def_rating_rank),
                             rankDisplay = team_month$def_rating_rankDisplay),
      pointsPerGame = list(value = round(team_month$points_per_game, 1),
                           rank = as.integer(team_month$ppg_rank),
                           rankDisplay = team_month$ppg_rankDisplay),
      assistsPerGame = list(value = round(team_month$assists_per_game, 1),
                            rank = as.integer(team_month$apg_rank),
                            rankDisplay = team_month$apg_rankDisplay),
      turnoversPerGame = list(value = round(team_month$turnovers_per_game, 1),
                              rank = as.integer(team_month$tpg_rank),
                              rankDisplay = team_month$tpg_rankDisplay),
      turnoverDiff = list(value = round(team_month$turnover_diff, 1),
                          rank = as.integer(team_month$tov_diff_rank),
                          rankDisplay = team_month$tov_diff_rankDisplay)
    )
  } else NULL

  # Playoff trend rankings (postseason games only, ranked vs other playoff teams)
  team_playoff <- if (!is.null(playoff_trend_stats)) {
    playoff_trend_stats %>% filter(team_abbreviation == team_abbr)
  } else data.frame()
  ts$playoffTrend <- if (nrow(team_playoff) > 0) {
    list(
      gamesPlayed = as.integer(team_playoff$games_played),
      record = list(
        wins = as.integer(team_playoff$wins),
        losses = as.integer(team_playoff$losses),
        rank = as.integer(team_playoff$record_rank),
        rankDisplay = team_playoff$record_rankDisplay
      ),
      netRating = list(value = round(team_playoff$avg_net_rating, 1),
                       rank = as.integer(team_playoff$net_rating_rank),
                       rankDisplay = team_playoff$net_rating_rankDisplay),
      offensiveRating = list(value = round(team_playoff$avg_off_rating, 1),
                             rank = as.integer(team_playoff$off_rating_rank),
                             rankDisplay = team_playoff$off_rating_rankDisplay),
      defensiveRating = list(value = round(team_playoff$avg_def_rating, 1),
                             rank = as.integer(team_playoff$def_rating_rank),
                             rankDisplay = team_playoff$def_rating_rankDisplay),
      pointsPerGame = list(value = round(team_playoff$points_per_game, 1),
                           rank = as.integer(team_playoff$ppg_rank),
                           rankDisplay = team_playoff$ppg_rankDisplay),
      assistsPerGame = list(value = round(team_playoff$assists_per_game, 1),
                            rank = as.integer(team_playoff$apg_rank),
                            rankDisplay = team_playoff$apg_rankDisplay),
      turnoversPerGame = list(value = round(team_playoff$turnovers_per_game, 1),
                              rank = as.integer(team_playoff$tpg_rank),
                              rankDisplay = team_playoff$tpg_rankDisplay),
      turnoverDiff = list(value = round(team_playoff$turnover_diff, 1),
                          rank = as.integer(team_playoff$tov_diff_rank),
                          rankDisplay = team_playoff$tov_diff_rankDisplay)
    )
  } else NULL

  base$teamStats <- ts
  if (is.null(base$wins)) base$wins <- as.integer(stats_row$wins %||% NA)
  if (is.null(base$losses)) base$losses <- as.integer(stats_row$losses %||% NA)
  base
}

build_regular_season_history <- function(team_a_id, team_b_id,
                                         team_a_abbrev, team_b_abbrev) {
  if (!is_valid_value(team_a_id) || !is_valid_value(team_b_id)) return(NULL)

  h2h <- regular_season_box %>%
    filter(as.character(team_id) == as.character(team_a_id),
           as.character(opponent_team_id) == as.character(team_b_id)) %>%
    arrange(game_date)

  if (nrow(h2h) == 0) return(NULL)

  team_a_wins <- sum(h2h$team_score > h2h$opponent_team_score, na.rm = TRUE)
  team_b_wins <- sum(h2h$team_score < h2h$opponent_team_score, na.rm = TRUE)

  games <- lapply(seq_len(nrow(h2h)), function(i) {
    row <- h2h[i, ]
    a_score <- safe_num(row$team_score)
    b_score <- safe_num(row$opponent_team_score)
    a_home <- !is.null(row$team_home_away) &&
              tolower(as.character(row$team_home_away)) == "home"
    winner <- if (is.na(a_score) || is.na(b_score)) NA_character_
              else if (a_score > b_score) team_a_abbrev
              else if (b_score > a_score) team_b_abbrev
              else NA_character_
    list(
      gameDate = as.character(row$game_date),
      homeAbbrev = if (a_home) team_a_abbrev else team_b_abbrev,
      awayAbbrev = if (a_home) team_b_abbrev else team_a_abbrev,
      homeScore = if (a_home) as.integer(a_score) else as.integer(b_score),
      awayScore = if (a_home) as.integer(b_score) else as.integer(a_score),
      winnerAbbrev = winner
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
    home = list(
      value = t1[[key]]$value,
      rank  = t1[[key]]$rank,
      rankDisplay = t1[[key]]$rankDisplay
    ),
    away = list(
      value = t2[[key]]$value,
      rank  = t2[[key]]$rank,
      rankDisplay = t2[[key]]$rankDisplay
    )
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

  offense_side <- list(
    pointsPerGame    = stat_pair("pointsPerGame",    "Points/Game",    t1, t2),
    offensiveRating  = stat_pair("offensiveRating",  "ORtg", t1, t2),
    fieldGoalPct     = stat_pair("fieldGoalPct",     "FG%",            t1, t2),
    threePointPct    = stat_pair("threePointPct",    "3P%",            t1, t2),
    assistsPerGame   = stat_pair("assistsPerGame",   "Assists/Game",   t1, t2),
    reboundsPerGame  = stat_pair("reboundsPerGame",  "Rebounds/Game",  t1, t2),
    turnoversPerGame = stat_pair("turnoversPerGame", "Turnovers/Game", t1, t2)
  )
  defense_side <- list(
    oppPointsPerGame      = stat_pair("oppPointsPerGame",      "Opp Points/Game", t1, t2),
    defensiveRating       = stat_pair("defensiveRating",       "DRtg", t1, t2),
    oppFieldGoalPct       = stat_pair("oppFieldGoalPct",       "Opp FG%", t1, t2),
    oppThreePointPct      = stat_pair("oppThreePointPct",      "Opp 3P%", t1, t2),
    stealsPerGame         = stat_pair("stealsPerGame",         "Steals/Game", t1, t2),
    blocksPerGame         = stat_pair("blocksPerGame",         "Blocks/Game", t1, t2),
    forcedTurnoversPerGame = stat_pair("forcedTurnoversPerGame","Forced TO/Game", t1, t2)
  )
  overall_side <- list(
    netRating = stat_pair("netRating", "Net Rating", t1, t2)
  )

  a <- team1$abbreviation
  b <- team2$abbreviation

  home_off_vs_away_def <- list(
    pointsPerGame   = off_vs_def(a, b, t1, t2, "pointsPerGame",   "pointsPerGame",   "oppPointsPerGame",      "Points/Game", "Opp Points Allowed"),
    rating          = off_vs_def(a, b, t1, t2, "rating",          "offensiveRating", "defensiveRating",       "ORtg", "DRtg"),
    fieldGoalPct    = off_vs_def(a, b, t1, t2, "fieldGoalPct",    "fieldGoalPct",    "oppFieldGoalPct",       "FG%", "Opp FG% Allowed"),
    threePointPct   = off_vs_def(a, b, t1, t2, "threePointPct",   "threePointPct",   "oppThreePointPct",      "3P%", "Opp 3P% Allowed"),
    assists         = off_vs_def(a, b, t1, t2, "assists",         "assistsPerGame",  "oppAssistsPerGame",     "Assists/Game", "Assists Allowed"),
    turnovers       = off_vs_def(a, b, t1, t2, "turnovers",       "turnoversPerGame","forcedTurnoversPerGame","Turnovers/Game", "Forced TO/Game")
  )

  away_off_vs_home_def <- list(
    pointsPerGame   = off_vs_def(b, a, t2, t1, "pointsPerGame",   "pointsPerGame",   "oppPointsPerGame",      "Points/Game", "Opp Points Allowed"),
    rating          = off_vs_def(b, a, t2, t1, "rating",          "offensiveRating", "defensiveRating",       "ORtg", "DRtg"),
    fieldGoalPct    = off_vs_def(b, a, t2, t1, "fieldGoalPct",    "fieldGoalPct",    "oppFieldGoalPct",       "FG%", "Opp FG% Allowed"),
    threePointPct   = off_vs_def(b, a, t2, t1, "threePointPct",   "threePointPct",   "oppThreePointPct",      "3P%", "Opp 3P% Allowed"),
    assists         = off_vs_def(b, a, t2, t1, "assists",         "assistsPerGame",  "oppAssistsPerGame",     "Assists/Game", "Assists Allowed"),
    turnovers       = off_vs_def(b, a, t2, t1, "turnovers",       "turnoversPerGame","forcedTurnoversPerGame","Turnovers/Game", "Forced TO/Game")
  )

  list(
    sideBySide = list(offense = offense_side, defense = defense_side, overall = overall_side),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  )
}

# ============================================================================
# STEP 3: Fetch postseason games from ESPN (scoreboard with seasontype=3)
# ============================================================================
cat("\n3. Fetching NBA postseason games from ESPN...\n")

today <- Sys.Date()
fetch_start <- max(PLAYOFFS_START, today - 90)
fetch_end   <- min(PLAYOFFS_END, today + 7)

if (today < PLAYOFFS_START) {
  fetch_start <- PLAYOFFS_START
  fetch_end   <- min(PLAYOFFS_END, PLAYOFFS_START + 7)
}

playoff_events <- list()
current_date <- fetch_start

while (current_date <= fetch_end) {
  date_str <- format(current_date, "%Y%m%d")
  url <- paste0(
    "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard",
    "?seasontype=3&dates=", date_str
  )
  add_api_delay()
  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (!is.null(resp) && status_code(resp) == 200) {
    data <- content(resp, as = "parsed")
    if (!is.null(data$events)) {
      for (ev in data$events) playoff_events[[length(playoff_events) + 1]] <- ev
    }
  }
  current_date <- current_date + 1
}

cat("Fetched", length(playoff_events), "postseason events\n")

# Group events into series: (conference + round + sorted team pair) -> list of games
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
  team_ids <- sort(as.character(c(t1$team$id, t2$team$id)))
  series_key <- paste(rinfo$conference, rinfo$roundNumber,
                      paste(team_ids, collapse = "_"), sep = "|")

  status_name <- if (!is.null(comp$status$type$name)) comp$status$type$name else "STATUS_SCHEDULED"
  completed <- isTRUE(comp$status$type$completed)

  # Normalize date to have seconds for Instant parsing
  game_date_str <- ev$date
  if (grepl("T\\d{2}:\\d{2}Z$", game_date_str)) {
    game_date_str <- sub("Z$", ":00Z", game_date_str)
  }

  # Determine home team from ESPN's homeAway field
  home_abbrev <- NA
  for (comp_team in comp$competitors) {
    if (!is.null(comp_team$homeAway) && comp_team$homeAway == "home") {
      home_abbrev <- comp_team$team$abbreviation
      break
    }
  }

  # Extract betting odds for upcoming games (skip completed ones to save payload).
  # ESPN surfaces odds in competitions[0].odds[[1]] — we pull spread, total,
  # and moneylines when present.
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
    details <- if (!is.null(o$details)) as.character(o$details) else NA_character_
    provider <- if (!is.null(o$provider$name)) as.character(o$provider$name) else NA_character_

    has_any <- is_valid_value(home_spread) || is_valid_value(home_ml) ||
               is_valid_value(away_ml) || is_valid_value(over_under) ||
               is_valid_value(details)
    if (has_any) {
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
    team2_score = if (completed) safe_num(t2$score) else NA,
    team2_winner = isTRUE(t2$winner),
    headline = notes_hl,
    odds = odds_data
  )

  team_abbrevs_sorted <- sort(c(t1$team$abbreviation, t2$team$abbreviation))
  if (is.null(series_map[[series_key]])) {
    series_map[[series_key]] <- list(
      conference = rinfo$conference,
      roundNumber = rinfo$roundNumber,
      roundName = rinfo$roundName,
      team_ids = team_ids,
      team_abbrevs = team_abbrevs_sorted,
      games = list()
    )
  }
  series_map[[series_key]]$games[[length(series_map[[series_key]]$games) + 1]] <- game_record
}

cat("Grouped into", length(series_map), "series\n")

# ============================================================================
# STEP 4: Build bracket structure
# ============================================================================
cat("\n4. Building bracket structure...\n")

# Determine playoff status
has_playoff_games <- length(series_map) > 0
bracket_status <- if (!has_playoff_games) {
  "PROJECTED"
} else if (today > PLAYOFFS_END) {
  "COMPLETED"
} else {
  "IN_PROGRESS"
}

cat("Bracket status:", bracket_status, "\n")

# Build a matchup (series wrapper) game object
build_series_matchup <- function(team1, team2, conference, round_number, round_name,
                                 game_id, series_entry = NULL) {
  # Reset state in case teams were carried over from an earlier round as winners.
  team1$isWinner <- FALSE
  team2$isWinner <- FALSE
  team1$seriesWins <- NULL
  team2$seriesWins <- NULL

  game_status <- "PROJECTED"
  series_summary <- NULL
  games_list <- list()

  # Match a game competitor against a bracket team by ESPN numeric id first,
  # falling back to abbreviation so we still count wins when abbreviation_id is
  # missing (e.g. teams rehydrated from a minimal fallback record).
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
    t1_wins <- sum(vapply(series_entry$games, function(g) {
      if (!isTRUE(g$completed)) return(0L)
      if (competitor_matches_team(g$team1_id, g$team1_abbrev, team1) && isTRUE(g$team1_winner)) return(1L)
      if (competitor_matches_team(g$team2_id, g$team2_abbrev, team1) && isTRUE(g$team2_winner)) return(1L)
      0L
    }, integer(1)))
    t2_wins <- sum(vapply(series_entry$games, function(g) {
      if (!isTRUE(g$completed)) return(0L)
      if (competitor_matches_team(g$team1_id, g$team1_abbrev, team2) && isTRUE(g$team1_winner)) return(1L)
      if (competitor_matches_team(g$team2_id, g$team2_abbrev, team2) && isTRUE(g$team2_winner)) return(1L)
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
    } else if (t1_wins > 0 || t2_wins > 0) {
      game_status <- "IN_PROGRESS"
    } else {
      game_status <- "SCHEDULED"
    }

    # Normalize each game so team1 always refers to the matchup's team1 (the
    # "left" column in the KMP series tab). ESPN can list home/away in either
    # order across games in the same series, so without this the per-game
    # winner indicator can end up on the wrong side.
    games_list <- lapply(series_entry$games, function(g) {
      swap <- competitor_matches_team(g$team2_id, g$team2_abbrev, team1) ||
              competitor_matches_team(g$team1_id, g$team1_abbrev, team2)
      if (swap) {
        list(
          gameId = g$game_id,
          gameDate = g$game_date,
          status = g$status,
          completed = g$completed,
          headline = g$headline,
          homeTeamAbbrev = g$homeTeamAbbrev,
          team1 = list(
            abbreviation = g$team2_abbrev, score = g$team2_score,
            winner = g$team2_winner
          ),
          team2 = list(
            abbreviation = g$team1_abbrev, score = g$team1_score,
            winner = g$team1_winner
          ),
          odds = g$odds
        )
      } else {
        list(
          gameId = g$game_id,
          gameDate = g$game_date,
          status = g$status,
          completed = g$completed,
          headline = g$headline,
          homeTeamAbbrev = g$homeTeamAbbrev,
          team1 = list(
            abbreviation = g$team1_abbrev, score = g$team1_score,
            winner = g$team1_winner
          ),
          team2 = list(
            abbreviation = g$team2_abbrev, score = g$team2_score,
            winner = g$team2_winner
          ),
          odds = g$odds
        )
      }
    })
  }

  comparisons <- build_comparisons(team1, team2)

  regular_season_history <- build_regular_season_history(
    team1$abbreviation_id, team2$abbreviation_id,
    team1$abbreviation, team2$abbreviation
  )

  list(
    gameId = game_id,
    conference = conference,
    roundNumber = round_number,
    roundName = round_name,
    gameStatus = game_status,
    seriesSummary = series_summary,
    bestOf = 7,
    team1 = team1,
    team2 = team2,
    winner = if (isTRUE(team1$isWinner)) team1$name
             else if (isTRUE(team2$isWinner)) team2$name
             else NULL,
    games = games_list,
    comparisons = comparisons,
    regularSeasonHistory = regular_season_history
  )
}

# Helper: pull a seeded team into a build_team record
seeded_team <- function(conference, seed) {
  df <- seeds[[conference]]
  if (is.null(df) || nrow(df) == 0 || seed > nrow(df)) return(NULL)
  row <- df[df$seed == seed, ][1, ]
  rec <- list(
    wins = as.integer(row$wins %||% NA),
    losses = as.integer(row$losses %||% NA),
    conference = conference
  )
  tm <- build_team(row$team_name, row$team_abbrev, row$team_logo, seed = seed, record = rec)
  tm$abbreviation_id <- row$team_id  # ESPN numeric id for series matching
  tm
}

# Find matching series by abbreviation or team ID
find_series_by_abbrevs <- function(conference, round_number, abbrev_a, abbrev_b) {
  key_abbrevs <- sort(c(abbrev_a, abbrev_b))
  for (k in names(series_map)) {
    s <- series_map[[k]]
    if (identical(s$conference, conference) && s$roundNumber == round_number &&
        !is.null(s$team_abbrevs) && identical(s$team_abbrevs, key_abbrevs)) return(s)
  }
  if (round_number == 4) {
    for (k in names(series_map)) {
      s <- series_map[[k]]
      if (s$roundNumber == 4 && !is.null(s$team_abbrevs) && identical(s$team_abbrevs, key_abbrevs)) return(s)
    }
  }
  NULL
}

# Build bracket per conference — ESPN series as source of truth
conferences_out <- list()

for (conf in c("East", "West")) {
  cat("Building", conf, "conference bracket...\n")

  # Check if ESPN has actual R1 series for this conference
  espn_r1_series <- Filter(function(s) identical(s$conference, conf) && s$roundNumber == 1, series_map)

  r1_games <- vector("list", 4)
  r1_winners <- vector("list", 4)

  if (length(espn_r1_series) >= 4) {
    # Use actual ESPN matchups, slotted into bracket position by seed pair so
    # the semifinal pairing logic below (1v8 winner vs 4v5 winner, 3v6 winner
    # vs 2v7 winner) lines up. ESPN returns R1 series in arbitrary order based
    # on game scheduling, so without this slotting the semis pair the wrong
    # winners.
    conf_seeds <- seeds[[conf]]
    seed_pair_to_slot <- function(seed_a, seed_b) {
      if (is.na(seed_a) || is.na(seed_b)) return(NA_integer_)
      pair <- sort(as.integer(c(seed_a, seed_b)))
      for (idx in seq_along(FIRST_ROUND_SEEDS)) {
        if (identical(sort(as.integer(FIRST_ROUND_SEEDS[[idx]])), pair)) return(idx)
      }
      NA_integer_
    }

    build_r1_matchup <- function(se, slot_idx) {
      abbrevs <- se$team_abbrevs
      t1 <- NULL; t2 <- NULL
      if (is.data.frame(conf_seeds) && nrow(conf_seeds) > 0) {
        s1 <- conf_seeds %>% filter(team_abbrev == abbrevs[1])
        s2 <- conf_seeds %>% filter(team_abbrev == abbrevs[2])
        if (nrow(s1) > 0) t1 <- seeded_team(conf, s1$seed[1])
        if (nrow(s2) > 0) t2 <- seeded_team(conf, s2$seed[1])
      }
      # Fallback: build minimal team from abbreviation
      if (is.null(t1)) t1 <- build_team(abbrevs[1], abbrevs[1], NA, seed = NULL, record = list(conference = conf))
      if (is.null(t2)) t2 <- build_team(abbrevs[2], abbrevs[2], NA, seed = NULL, record = list(conference = conf))

      mu <- build_series_matchup(t1, t2, conf, 1, "First Round",
                                 paste0("nba_", tolower(conf), "_r1_", slot_idx), se)
      r1_games[[slot_idx]] <<- mu
      if (isTRUE(mu$team1$isWinner)) r1_winners[[slot_idx]] <<- mu$team1
      else if (isTRUE(mu$team2$isWinner)) r1_winners[[slot_idx]] <<- mu$team2
      else r1_winners[slot_idx] <<- list(NULL)
    }

    unslotted <- list()
    for (se in espn_r1_series) {
      abbrevs <- se$team_abbrevs
      seed_a <- NA_integer_; seed_b <- NA_integer_
      if (is.data.frame(conf_seeds) && nrow(conf_seeds) > 0) {
        s1 <- conf_seeds %>% filter(team_abbrev == abbrevs[1])
        s2 <- conf_seeds %>% filter(team_abbrev == abbrevs[2])
        if (nrow(s1) > 0) seed_a <- as.integer(s1$seed[1])
        if (nrow(s2) > 0) seed_b <- as.integer(s2$seed[1])
      }
      slot <- seed_pair_to_slot(seed_a, seed_b)
      if (!is.na(slot) && is.null(r1_games[[slot]])) {
        build_r1_matchup(se, slot)
      } else {
        unslotted[[length(unslotted) + 1]] <- se
      }
    }

    # Anything we couldn't slot by seed (missing standings, play-in surprise,
    # etc.) falls into the first available slot so the bracket still renders.
    if (length(unslotted) > 0) {
      empty_slots <- which(vapply(r1_games, is.null, logical(1)))
      for (i in seq_along(unslotted)) {
        if (i > length(empty_slots)) break
        build_r1_matchup(unslotted[[i]], empty_slots[i])
      }
    }
  } else {
    # Fall back to standings projection
    for (i in seq_along(FIRST_ROUND_SEEDS)) {
      pair <- FIRST_ROUND_SEEDS[[i]]
      t1 <- seeded_team(conf, pair[1])
      t2 <- seeded_team(conf, pair[2])
      if (is.null(t1) || is.null(t2)) next
      se <- find_series_by_abbrevs(conf, 1, t1$abbreviation, t2$abbreviation)
      mu <- build_series_matchup(t1, t2, conf, 1, "First Round",
                                 paste0("nba_", tolower(conf), "_r1_", i), se)
      r1_games[[i]] <- mu
      if (isTRUE(mu$team1$isWinner)) r1_winners[[i]] <- mu$team1
      else if (isTRUE(mu$team2$isWinner)) r1_winners[[i]] <- mu$team2
      else r1_winners[i] <- list(NULL)
    }
  }

  # Conference Semifinals: prefer ESPN's actual R2 series when available
  espn_r2_series <- Filter(function(s) identical(s$conference, conf) && s$roundNumber == 2, series_map)

  semi_pairs <- list(c(1, 2), c(3, 4))
  r2_games <- vector("list", length(semi_pairs))
  r2_winners <- vector("list", length(semi_pairs))

  # Helper to build a team from standings or R1 winners
  build_r2_team <- function(abbrev) {
    # Try to reuse R1 winner with full stats
    for (w in r1_winners) {
      if (!is.null(w) && !is.null(w$abbreviation) && w$abbreviation == abbrev) return(w)
    }
    # Fall back to building from standings/stats
    conf_seeds <- seeds[[conf]]
    if (is.data.frame(conf_seeds) && nrow(conf_seeds) > 0) {
      sx <- conf_seeds %>% filter(team_abbrev == abbrev)
      if (nrow(sx) > 0) {
        rec <- list(wins = as.integer(sx$wins[1]), losses = as.integer(sx$losses[1]), conference = conf)
        return(build_team(sx$team_name[1], abbrev, sx$team_logo[1], seed = sx$seed[1], record = rec))
      }
    }
    build_team(abbrev, abbrev, NA, seed = NULL, record = list(conference = conf))
  }

  # First, use ESPN R2 series directly when available
  if (length(espn_r2_series) > 0) {
    for (i in seq_along(espn_r2_series)) {
      if (i > 2) break
      se <- espn_r2_series[[i]]
      abbrevs <- se$team_abbrevs

      t1 <- build_r2_team(abbrevs[1])
      t2 <- build_r2_team(abbrevs[2])

      mu <- build_series_matchup(t1, t2, conf, 2, "Conference Semifinals",
                                 paste0("nba_", tolower(conf), "_r2_", i), se)
      r2_games[[i]] <- mu
      if (isTRUE(mu$team1$isWinner)) r2_winners[[i]] <- mu$team1
      else if (isTRUE(mu$team2$isWinner)) r2_winners[[i]] <- mu$team2
      else r2_winners[i] <- list(NULL)
    }
  }

  # Fill in any remaining R2 slots from R1 winners (fallback)
  for (i in seq_along(semi_pairs)) {
    if (!is.null(r2_games[[i]])) next
    idx <- semi_pairs[[i]]
    a <- r1_winners[[idx[1]]]
    b <- r1_winners[[idx[2]]]
    if (is.null(a) || is.null(b)) {
      r2_games[[i]] <- list(
        gameId = paste0("nba_", tolower(conf), "_r2_", i),
        conference = conf, roundNumber = 2, roundName = "Conference Semifinals",
        gameStatus = "TBD", bestOf = 7,
        team1 = NULL, team2 = NULL, winner = NULL, games = list(), comparisons = NULL
      )
      r2_winners[i] <- list(NULL)
      next
    }
    series_entry <- find_series_by_abbrevs(conf, 2, a$abbreviation, b$abbreviation)
    mu <- build_series_matchup(a, b, conf, 2, "Conference Semifinals",
                               paste0("nba_", tolower(conf), "_r2_", i), series_entry)
    r2_games[[i]] <- mu
    if (isTRUE(mu$team1$isWinner)) r2_winners[[i]] <- mu$team1
    else if (isTRUE(mu$team2$isWinner)) r2_winners[[i]] <- mu$team2
    else r2_winners[i] <- list(NULL)
  }

  # Conference Finals: prefer ESPN's actual R3 series when available
  espn_r3_series <- Filter(function(s) identical(s$conference, conf) && s$roundNumber == 3, series_map)

  cf_game <- NULL
  if (length(espn_r3_series) > 0) {
    # Use ESPN's actual Conference Finals matchup
    se <- espn_r3_series[[1]]
    abbrevs <- se$team_abbrevs
    conf_seeds <- seeds[[conf]]

    build_cf_team <- function(abbrev) {
      # Try to reuse R2 winner with full stats
      for (w in r2_winners) {
        if (!is.null(w) && !is.null(w$abbreviation) && w$abbreviation == abbrev) return(w)
      }
      # Fall back to building from standings/stats
      if (is.data.frame(conf_seeds) && nrow(conf_seeds) > 0) {
        sx <- conf_seeds %>% filter(team_abbrev == abbrev)
        if (nrow(sx) > 0) {
          rec <- list(wins = as.integer(sx$wins[1]), losses = as.integer(sx$losses[1]), conference = conf)
          return(build_team(sx$team_name[1], abbrev, sx$team_logo[1], seed = sx$seed[1], record = rec))
        }
      }
      build_team(abbrev, abbrev, NA, seed = NULL, record = list(conference = conf))
    }

    t1 <- build_cf_team(abbrevs[1])
    t2 <- build_cf_team(abbrevs[2])
    cf_game <- build_series_matchup(t1, t2, conf, 3, "Conference Finals",
                                    paste0("nba_", tolower(conf), "_r3"), se)
  }

  # Fall back to R2 winners if no ESPN R3 data
  if (is.null(cf_game)) {
    a <- r2_winners[[1]]; b <- r2_winners[[2]]
    if (!is.null(a) && !is.null(b)) {
      series_entry <- find_series_by_abbrevs(conf, 3, a$abbreviation, b$abbreviation)
      cf_game <- build_series_matchup(a, b, conf, 3, "Conference Finals",
                                      paste0("nba_", tolower(conf), "_r3"), series_entry)
    } else {
      cf_game <- list(
        gameId = paste0("nba_", tolower(conf), "_r3"),
        conference = conf, roundNumber = 3, roundName = "Conference Finals",
        gameStatus = "TBD", bestOf = 7,
        team1 = NULL, team2 = NULL, winner = NULL, games = list(), comparisons = NULL
      )
    }
  }

  conferences_out[[length(conferences_out) + 1]] <- list(
    name = conf,
    colorHex = CONFERENCE_COLORS[[conf]],
    rounds = list(
      list(roundNumber = 1, roundName = "First Round",            games = Filter(Negate(is.null), r1_games)),
      list(roundNumber = 2, roundName = "Conference Semifinals",  games = Filter(Negate(is.null), r2_games)),
      list(roundNumber = 3, roundName = "Conference Finals",      games = list(cf_game))
    ),
    champion = if (!is.null(cf_game$winner))
      (if (isTRUE(cf_game$team1$isWinner)) cf_game$team1 else cf_game$team2)
      else NULL
  )
}

# NBA Finals
east_champ <- conferences_out[[1]]$champion
west_champ <- conferences_out[[2]]$champion

finals_game <- if (!is.null(east_champ) && !is.null(west_champ)) {
  series_entry <- find_series_by_abbrevs("Finals", 4, east_champ$abbreviation, west_champ$abbreviation)
  build_series_matchup(east_champ, west_champ, "Finals", 4, "NBA Finals",
                       "nba_finals", series_entry)
} else {
  list(
    gameId = "nba_finals",
    conference = "Finals", roundNumber = 4, roundName = "NBA Finals",
    gameStatus = "TBD", bestOf = 7,
    team1 = NULL, team2 = NULL, winner = NULL, games = list(), comparisons = NULL
  )
}

# ============================================================================
# STEP 5: Emit JSON and upload
# ============================================================================
cat("\n5. Emitting bracket JSON...\n")

title <- paste0(NBA_SEASON, " NBA Playoff Bracket")
subtitle <- switch(bracket_status,
                   PROJECTED = "Projected bracket based on current standings",
                   IN_PROGRESS = format(today, "%B %d, %Y"),
                   COMPLETED = "Playoffs complete",
                   format(today, "%B %d, %Y"))

output_data <- list(
  sport = "NBA",
  visualizationType = "NBA_PLAYOFF_BRACKET",
  title = title,
  subtitle = subtitle,
  description = paste0(
    "NBA playoff bracket with matchup statistics.\n\n",
    "Each matchup includes regular-season offensive/defensive team stats, ",
    "rankings across the league, and head-to-head stat comparisons. ",
    "Series summaries reflect completed games in the current postseason."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "hoopR / ESPN",
  tags = list(
    list(label = "playoffs", layout = "left", color = "#4CAF50"),
    list(label = "bracket",  layout = "left", color = "#FF9800")
  ),
  sortOrder = -1,
  season = NBA_SEASON,
  bracketStatus = bracket_status,
  tenthNetRatingByWeek = if (nrow(tenth_net_rating_by_week) > 0) {
    setNames(as.list(round(tenth_net_rating_by_week$tenth_net_rating, 1)),
             paste0("week-", tenth_net_rating_by_week$week_num))
  } else setNames(list(), character(0)),
  leagueCumNetRatingStats = league_cum_net_rating_stats,
  leagueEfficiencyStats = list(
    avgOffRating = round(league_efficiency_stats$avg_off_rating, 1),
    avgDefRating = round(league_efficiency_stats$avg_def_rating, 1),
    minOffRating = round(league_efficiency_stats$min_off_rating, 1),
    maxOffRating = round(league_efficiency_stats$max_off_rating, 1),
    minDefRating = round(league_efficiency_stats$min_def_rating, 1),
    maxDefRating = round(league_efficiency_stats$max_def_rating, 1)
  ),
  scatterPlotQuadrants = list(
    topRight = list(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
    topLeft = list(label = "Defensive", color = "#2196F3", lightModeColor = "#2196F3"),
    bottomLeft = list(label = "Struggling", color = "#F44336", lightModeColor = "#F44336"),
    bottomRight = list(label = "Offensive", color = "#FF9800", lightModeColor = "#FF9800")
  ),
  conferences = conferences_out,
  finals = finals_game
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

# Persist history of completed series for resilience
history <- load_bracket_history()
if (is.null(history)) history <- list(series = list(), lastUpdated = NULL)
for (k in names(series_map)) history$series[[k]] <- series_map[[k]]
history$lastUpdated <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
save_bracket_history(history)

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (nzchar(s3_bucket)) {
  s3_key <- paste0(S3_PREFIX, "/nba__playoff_bracket.json")
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
  dev_out <- "/tmp/nba_playoff_bracket.json"
  file.copy(tmp_file, dev_out, overwrite = TRUE)
  cat("Development mode - output written to:", dev_out, "\n")
}

cat("\n=== NBA Playoff Bracket generation complete ===\n")
