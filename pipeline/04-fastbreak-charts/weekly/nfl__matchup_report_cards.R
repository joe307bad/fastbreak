library(nflreadr)
library(dplyr)
library(tidyr)
library(jsonlite)

# Get current season
current_season <- as.numeric(format(Sys.Date(), "%Y"))

cat("Loading NFL data for season:", current_season, "\n")

# Load play-by-play data for stats calculations
pbp <- nflreadr::load_pbp(current_season)

# Get the most recent completed week
most_recent_week <- max(pbp$week, na.rm = TRUE)
upcoming_week <- most_recent_week + 1

cat("Most recent completed week:", most_recent_week, "\n")
cat("Generating matchup report cards for week:", upcoming_week, "\n")

# Load schedule to get upcoming games
schedule <- nflreadr::load_schedules(current_season)

# Filter for upcoming week's games
upcoming_games <- schedule %>%
  filter(week == upcoming_week) %>%
  select(game_id, week, gameday, gametime, home_team, away_team) %>%
  arrange(gameday, gametime)

if (nrow(upcoming_games) == 0) {
  cat("No upcoming games found for week", upcoming_week, "\n")
  cat("Checking if season is over or in offseason...\n")
  # Try to show what weeks are available
  available_weeks <- unique(schedule$week)
  cat("Available weeks in schedule:", paste(available_weeks, collapse = ", "), "\n")
  stop("No games to process")
}

cat("Found", nrow(upcoming_games), "games for week", upcoming_week, "\n")

# Calculate team stats from play-by-play data
# 1. Offensive EPA per play
offense_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(posteam)) %>%
  group_by(posteam) %>%
  summarise(off_epa = round(mean(epa, na.rm = TRUE), 3), .groups = "drop") %>%
  rename(team = posteam)

# 2. Defensive EPA per play (lower is better)
defense_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(defteam)) %>%
  group_by(defteam) %>%
  summarise(def_epa = round(mean(epa, na.rm = TRUE), 3), .groups = "drop") %>%
  rename(team = defteam)

# 3. Pass EPA per play (offensive)
pass_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(posteam), pass == 1) %>%
  group_by(posteam) %>%
  summarise(pass_epa = round(mean(epa, na.rm = TRUE), 3), .groups = "drop") %>%
  rename(team = posteam)

# 4. Rush EPA per play (offensive)
rush_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(posteam), rush == 1) %>%
  group_by(posteam) %>%
  summarise(rush_epa = round(mean(epa, na.rm = TRUE), 3), .groups = "drop") %>%
  rename(team = posteam)

# 5. Pass defense EPA (lower is better)
pass_def_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(defteam), pass == 1) %>%
  group_by(defteam) %>%
  summarise(pass_def_epa = round(mean(epa, na.rm = TRUE), 3), .groups = "drop") %>%
  rename(team = defteam)

# 6. Rush defense EPA (lower is better)
rush_def_epa <- pbp %>%
  filter(week <= most_recent_week, !is.na(epa), !is.na(defteam), rush == 1) %>%
  group_by(defteam) %>%
  summarise(rush_def_epa = round(mean(epa, na.rm = TRUE), 3), .groups = "drop") %>%
  rename(team = defteam)

# 7. Turnovers forced
turnovers_forced <- pbp %>%
  filter(week <= most_recent_week) %>%
  filter(interception == 1 | fumble_lost == 1) %>%
  group_by(defteam) %>%
  summarise(to_forced = n(), .groups = "drop") %>%
  rename(team = defteam)

# 8. Turnovers committed
turnovers_committed <- pbp %>%
  filter(week <= most_recent_week) %>%
  filter(interception == 1 | fumble_lost == 1) %>%
  group_by(posteam) %>%
  summarise(to_committed = n(), .groups = "drop") %>%
  rename(team = posteam)

# 9. Points scored per game and points allowed per game from schedule
team_records <- schedule %>%
  filter(week <= most_recent_week, !is.na(home_score)) %>%
  select(game_id, week, home_team, away_team, home_score, away_score) %>%
  pivot_longer(
    cols = c(home_team, away_team),
    names_to = "team_location",
    values_to = "team"
  ) %>%
  mutate(
    points_for = ifelse(team_location == "home_team", home_score, away_score),
    points_against = ifelse(team_location == "home_team", away_score, home_score),
    win = points_for > points_against,
    loss = points_for < points_against,
    tie = points_for == points_against
  ) %>%
  group_by(team) %>%
  summarise(
    wins = sum(win),
    losses = sum(loss),
    ties = sum(tie),
    games_played = n(),
    ppg = round(mean(points_for), 1),
    papg = round(mean(points_against), 1),
    .groups = "drop"
  ) %>%
  mutate(
    record = ifelse(ties > 0,
                    paste0(wins, "-", losses, "-", ties),
                    paste0(wins, "-", losses))
  )

# Combine all stats into one dataframe
team_stats <- offense_epa %>%
  left_join(defense_epa, by = "team") %>%
  left_join(pass_epa, by = "team") %>%
  left_join(rush_epa, by = "team") %>%
  left_join(pass_def_epa, by = "team") %>%
  left_join(rush_def_epa, by = "team") %>%
  left_join(turnovers_forced, by = "team") %>%
  left_join(turnovers_committed, by = "team") %>%
  left_join(team_records, by = "team") %>%
  mutate(
    to_forced = replace_na(to_forced, 0),
    to_committed = replace_na(to_committed, 0),
    to_diff = to_forced - to_committed
  )

cat("Team stats calculated for", nrow(team_stats), "teams\n")

# Helper function to get team stat safely
get_stat <- function(stats_df, team_abbr, stat_name) {
  val <- stats_df %>% filter(team == team_abbr) %>% pull(!!sym(stat_name))
  if (length(val) == 0 || is.na(val)) return(NA)
  return(val)
}

# Build matchup data points
matchups <- lapply(1:nrow(upcoming_games), function(i) {
  game <- upcoming_games[i, ]
  home <- game$home_team
  away <- game$away_team

  # Build game time string
  game_time <- tryCatch({
    if (!is.na(game$gameday) && !is.na(game$gametime)) {
      paste0(game$gameday, "T", game$gametime, ":00")
    } else {
      NA
    }
  }, error = function(e) NA)

  # Build comparisons list
  comparisons <- list()

  # Record comparison
  home_record <- get_stat(team_stats, home, "record")
  away_record <- get_stat(team_stats, away, "record")
  if (!is.na(home_record) && !is.na(away_record)) {
    comparisons <- c(comparisons, list(list(
      title = "Record",
      homeTeamValue = home_record,
      awayTeamValue = away_record
    )))
  }

  # Points per game
  home_ppg <- get_stat(team_stats, home, "ppg")
  away_ppg <- get_stat(team_stats, away, "ppg")
  if (!is.na(home_ppg) && !is.na(away_ppg)) {
    comparisons <- c(comparisons, list(list(
      title = "Points/Game",
      homeTeamValue = home_ppg,
      awayTeamValue = away_ppg
    )))
  }

  # Points allowed per game (lower is better)
  home_papg <- get_stat(team_stats, home, "papg")
  away_papg <- get_stat(team_stats, away, "papg")
  if (!is.na(home_papg) && !is.na(away_papg)) {
    comparisons <- c(comparisons, list(list(
      title = "Points Allowed/Game",
      homeTeamValue = home_papg,
      awayTeamValue = away_papg,
      inverted = TRUE
    )))
  }

  # Offensive EPA
  home_off_epa <- get_stat(team_stats, home, "off_epa")
  away_off_epa <- get_stat(team_stats, away, "off_epa")
  if (!is.na(home_off_epa) && !is.na(away_off_epa)) {
    comparisons <- c(comparisons, list(list(
      title = "Offensive EPA/Play",
      homeTeamValue = home_off_epa,
      awayTeamValue = away_off_epa
    )))
  }

  # Defensive EPA (lower is better)
  home_def_epa <- get_stat(team_stats, home, "def_epa")
  away_def_epa <- get_stat(team_stats, away, "def_epa")
  if (!is.na(home_def_epa) && !is.na(away_def_epa)) {
    comparisons <- c(comparisons, list(list(
      title = "Defensive EPA/Play",
      homeTeamValue = home_def_epa,
      awayTeamValue = away_def_epa,
      inverted = TRUE
    )))
  }

  # Pass offense EPA
  home_pass_epa <- get_stat(team_stats, home, "pass_epa")
  away_pass_epa <- get_stat(team_stats, away, "pass_epa")
  if (!is.na(home_pass_epa) && !is.na(away_pass_epa)) {
    comparisons <- c(comparisons, list(list(
      title = "Pass EPA/Play",
      homeTeamValue = home_pass_epa,
      awayTeamValue = away_pass_epa
    )))
  }

  # Rush offense EPA
  home_rush_epa <- get_stat(team_stats, home, "rush_epa")
  away_rush_epa <- get_stat(team_stats, away, "rush_epa")
  if (!is.na(home_rush_epa) && !is.na(away_rush_epa)) {
    comparisons <- c(comparisons, list(list(
      title = "Rush EPA/Play",
      homeTeamValue = home_rush_epa,
      awayTeamValue = away_rush_epa
    )))
  }

  # Pass defense EPA (lower is better)
  home_pass_def <- get_stat(team_stats, home, "pass_def_epa")
  away_pass_def <- get_stat(team_stats, away, "pass_def_epa")
  if (!is.na(home_pass_def) && !is.na(away_pass_def)) {
    comparisons <- c(comparisons, list(list(
      title = "Pass Defense EPA/Play",
      homeTeamValue = home_pass_def,
      awayTeamValue = away_pass_def,
      inverted = TRUE
    )))
  }

  # Rush defense EPA (lower is better)
  home_rush_def <- get_stat(team_stats, home, "rush_def_epa")
  away_rush_def <- get_stat(team_stats, away, "rush_def_epa")
  if (!is.na(home_rush_def) && !is.na(away_rush_def)) {
    comparisons <- c(comparisons, list(list(
      title = "Rush Defense EPA/Play",
      homeTeamValue = home_rush_def,
      awayTeamValue = away_rush_def,
      inverted = TRUE
    )))
  }

  # Turnover differential
  home_to_diff <- get_stat(team_stats, home, "to_diff")
  away_to_diff <- get_stat(team_stats, away, "to_diff")
  if (!is.na(home_to_diff) && !is.na(away_to_diff)) {
    comparisons <- c(comparisons, list(list(
      title = "Turnover Diff",
      homeTeamValue = as.integer(home_to_diff),
      awayTeamValue = as.integer(away_to_diff)
    )))
  }

  # Build matchup object
  matchup <- list(
    homeTeam = home,
    awayTeam = away,
    week = as.integer(game$week),
    comparisons = comparisons
  )

  # Add game time if available
  if (!is.na(game_time)) {
    matchup$gameTime <- game_time
  }

  matchup
})

cat("Built", length(matchups), "matchup report cards\n")

# Create output object
output_data <- list(
  sport = "NFL",
  visualizationType = "MATCHUP",
  title = paste("Week", upcoming_week, "Matchup Report Cards"),
  subtitle = paste("Statistical comparison for all Week", upcoming_week, "games"),
  description = "Each matchup shows a head-to-head comparison of key team statistics. EPA (Expected Points Added) measures the value each play adds to a team's expected score. Positive offensive EPA is good, while negative defensive EPA indicates a strong defense. Turnover differential shows how well teams protect the ball vs forcing turnovers.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "nflfastR / nflreadr",
  week = as.integer(upcoming_week),
  dataPoints = matchups
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nfl__matchup_report_cards.json"
} else {
  "dev/nfl__matchup_report_cards.json"
}

# Write JSON to temp file and upload via AWS CLI
tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE)

# Also write a local copy for debugging
cat("\nJSON preview (first 100 lines):\n")
json_lines <- readLines(tmp_file, n = 100)
cat(paste(json_lines, collapse = "\n"), "\n")

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
result <- system(cmd)

if (result != 0) {
  stop("Failed to upload to S3")
}

cat("\nUploaded to S3:", s3_path, "\n")

# Update DynamoDB with updatedAt, title, and interval
dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
chart_title <- output_data$title
chart_interval <- "weekly"

dynamodb_item <- sprintf('{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "%s"}}', s3_key, utc_timestamp, chart_title, chart_interval)
dynamodb_cmd <- sprintf(
  'aws dynamodb put-item --table-name %s --item %s',
  shQuote(dynamodb_table),
  shQuote(dynamodb_item)
)

dynamodb_result <- system(dynamodb_cmd)

if (dynamodb_result != 0) {
  warning("Failed to update DynamoDB timestamp (non-fatal)")
} else {
  cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "updatedAt:", utc_timestamp, "title:", chart_title, "interval:", chart_interval, "\n")
}

# Print summary
cat("\n=== Matchup Report Cards Summary ===\n")
cat("Week:", upcoming_week, "\n")
cat("Total matchups:", length(matchups), "\n")
cat("\nGames:\n")
for (m in matchups) {
  cat(sprintf("  %s @ %s\n", m$awayTeam, m$homeTeam))
}
cat("\nStats included per matchup:", length(matchups[[1]]$comparisons), "\n")
