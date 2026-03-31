#!/usr/bin/env Rscript

# Source common CBB functions (relative to this script's location)
args <- commandArgs(trailingOnly = FALSE)
script_path <- sub("--file=", "", args[grep("--file=", args)])
script_dir <- normalizePath(dirname(script_path))
source(file.path(script_dir, "cbb__common.R"))

# Constants
DAYS_AHEAD <- 7

cat("=== Loading CBB data ===\n")

# ============================================================================
# STEP 1: Load data using common functions
# ============================================================================
cat("\n1. Loading CBB data...\n")

data_dir <- file.path(dirname(script_dir), "data/cbb/2026")
cbb_data <- load_cbb_data(data_dir)
combined_data <- cbb_data$combined_data
team_stats_lookup <- cbb_data$team_stats_lookup
ratings_data <- cbb_data$ratings_data

# ============================================================================
# STEP 2: Get top 25 AP ranked teams
# ============================================================================
cat("\n2. Getting top 25 AP ranked teams...\n")

top_25_teams <- ratings_data %>%
  filter(!is.na(AP_Rank)) %>%
  arrange(AP_Rank) %>%
  head(25)

cat("Found", nrow(top_25_teams), "AP ranked teams\n")
print(top_25_teams %>% select(AP_Rank, School, Conf))

# Create a list of team names for matching
top_team_names <- tolower(top_25_teams$School)

# ============================================================================
# STEP 3: Fetch competitions from ESPN API
# ============================================================================
cat("\n3. Fetching CBB games from ESPN API...\n")

today <- Sys.Date()
end_date <- today + DAYS_AHEAD
start_date_str <- format(today, "%Y%m%d")
end_date_str <- format(end_date, "%Y%m%d")

scoreboard_url <- paste0(
  "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/scoreboard",
  "?dates=", start_date_str, "-", end_date_str,
  "&groups=50&limit=365"
)

cat("Fetching from:", scoreboard_url, "\n")

scoreboard_resp <- tryCatch({
  GET(scoreboard_url)
}, error = function(e) {
  cat("Error fetching scoreboard:", e$message, "\n")
  NULL
})

if (is.null(scoreboard_resp) || status_code(scoreboard_resp) != 200) {
  cat("Failed to fetch scoreboard data.\n")
  quit(status = 1)
}

scoreboard_data <- content(scoreboard_resp, as = "parsed")
events <- scoreboard_data$events

if (is.null(events) || length(events) == 0) {
  cat("No games found in the date range\n")
  quit(status = 0)
}

cat("Found", length(events), "total events\n")

# ============================================================================
# STEP 4: Filter competitions involving top 25 teams
# ============================================================================
cat("\n4. Filtering competitions involving top 25 teams...\n")

find_matching_top_team <- function(espn_location) {
  if (is.null(espn_location)) return(NULL)
  espn_lower <- tolower(espn_location)
  if (espn_lower %in% top_team_names) return(espn_lower)

  location_mappings <- list(
    "usc" = "southern california",
    "lsu" = "louisiana state",
    "smu" = "southern methodist",
    "vcu" = "virginia commonwealth",
    "byu" = "brigham young",
    "ucf" = "ucf",
    "unc" = "north carolina",
    "ole miss" = "mississippi"
  )

  if (espn_lower %in% names(location_mappings)) {
    mapped <- location_mappings[[espn_lower]]
    if (mapped %in% top_team_names) return(mapped)
  }
  return(NULL)
}

matchups <- list()

for (event in events) {
  if (is.null(event$competitions) || length(event$competitions) == 0) next

  competition <- event$competitions[[1]]
  teams <- competition$competitors
  if (length(teams) != 2) next

  home_team <- NULL
  away_team <- NULL
  for (team in teams) {
    if (team$homeAway == "home") home_team <- team
    else away_team <- team
  }
  if (is.null(home_team) || is.null(away_team)) next

  home_location <- home_team$team$location
  away_location <- away_team$team$location
  home_top_match <- find_matching_top_team(home_location)
  away_top_match <- find_matching_top_team(away_location)

  # Only include if BOTH teams are AP top 25 teams
  if (is.null(home_top_match) || is.null(away_top_match)) next

  location_data <- NULL
  if (!is.null(competition$venue)) {
    venue <- competition$venue
    stadium_name <- if (!is.null(venue$fullName)) venue$fullName else NA
    city <- if (!is.null(venue$address) && !is.null(venue$address$city)) venue$address$city else NA
    state <- if (!is.null(venue$address) && !is.null(venue$address$state)) venue$address$state else NA
    location_parts <- c()
    if (!is.na(stadium_name)) location_parts <- c(location_parts, stadium_name)
    if (!is.na(city)) location_parts <- c(location_parts, city)
    if (!is.na(state)) location_parts <- c(location_parts, state)
    location_data <- list(
      stadium = stadium_name, city = city, state = state,
      fullLocation = if (length(location_parts) > 0) paste(location_parts, collapse = ", ") else NA
    )
  }

  odds_data <- NULL
  if (!is.null(competition$odds) && length(competition$odds) > 0) {
    odds <- competition$odds[[1]]
    home_spread <- NA
    if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$spreadOdds)) {
      home_spread <- as.numeric(odds$homeTeamOdds$spreadOdds)
    } else if (!is.null(odds$spread)) {
      home_spread <- as.numeric(odds$spread)
    }
    odds_data <- list(
      provider = if (!is.null(odds$provider)) odds$provider$name else NA,
      spread = home_spread,
      overUnder = if (!is.null(odds$overUnder)) as.numeric(odds$overUnder) else NA,
      homeMoneyline = if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$moneyLine))
        as.numeric(odds$homeTeamOdds$moneyLine) else NA,
      awayMoneyline = if (!is.null(odds$awayTeamOdds) && !is.null(odds$awayTeamOdds$moneyLine))
        as.numeric(odds$awayTeamOdds$moneyLine) else NA
    )
  }

  game_date <- event$date
  if (grepl("T\\d{2}:\\d{2}Z$", game_date)) game_date <- sub("Z$", ":00Z", game_date)

  matchups[[length(matchups) + 1]] <- list(
    game_id = event$id,
    game_date = game_date,
    game_name = event$name,
    home_team_id = home_team$team$id,
    home_team_name = home_team$team$displayName,
    home_team_abbrev = if (!is.null(home_team$team$abbreviation)) home_team$team$abbreviation else home_location,
    home_team_logo = if (!is.null(home_team$team$logo)) home_team$team$logo else NA,
    home_top_team_key = home_top_match,
    away_team_id = away_team$team$id,
    away_team_name = away_team$team$displayName,
    away_team_abbrev = if (!is.null(away_team$team$abbreviation)) away_team$team$abbreviation else away_location,
    away_team_logo = if (!is.null(away_team$team$logo)) away_team$team$logo else NA,
    away_top_team_key = away_top_match,
    location = location_data,
    odds = odds_data
  )
}

cat("Found", length(matchups), "games with both teams in AP Top 25\n")

if (length(matchups) == 0) {
  cat("No games with both teams in AP Top 25 found. Exiting.\n")
  quit(status = 0)
}

# ============================================================================
# STEP 5: Build matchup comparison data
# ============================================================================
cat("\n5. Building matchup comparisons...\n")

matchups_json <- list()
for (game in matchups) {
  cat("Processing:", game$away_team_abbrev, "@", game$home_team_abbrev, "\n")

  home_team_data <- build_team_stats(game$home_top_team_key, game$home_team_name, game$home_team_abbrev, game$home_team_logo, NULL, team_stats_lookup)
  away_team_data <- build_team_stats(game$away_top_team_key, game$away_team_name, game$away_team_abbrev, game$away_team_logo, NULL, team_stats_lookup)
  comparisons <- build_comparisons(home_team_data, away_team_data)

  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = game$game_name,
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    comparisons = comparisons
  )

  if (!is.null(game$location)) matchup$location <- game$location
  if (!is.null(game$odds)) matchup$odds <- game$odds

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

# ============================================================================
# STEP 6: Generate output JSON
# ============================================================================
cat("\n6. Generating output JSON...\n")

output_data <- list(
  sport = "CBB",
  visualizationType = "CBB_MATCHUP",
  title = paste0("College Basketball Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("AP Top 25 vs Top 25 matchups for the next", DAYS_AHEAD, "days"),
  description = paste0(
    "Matchup statistics for games where BOTH teams are AP Top 25.\n\n",
    "OFFENSIVE STATS:\n",
    " - PPG, FG%, 3P%, FT%, Assists, Off Rebounds, Turnovers\n",
    " - Offensive Rating, Offensive SRS\n\n",
    "DEFENSIVE STATS (what they allow):\n",
    " - Opp PPG, Opp FG%, Opp 3P%, Opp eFG%, Opp TS%\n",
    " - Defensive Rating, Defensive SRS\n",
    " - Steals, Blocks, Forced Turnover %\n\n",
    "OVERALL:\n",
    " - SRS, Net Rating, Strength of Schedule, Margin of Victory\n\n",
    "All stats from Sports Reference. Rankings are among all D1 teams."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN / Sports Reference",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "top 25 vs top 25", layout = "left", color = "#FF9800"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  dataPoints = matchups_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

cat("Generated stats for", length(matchups_json), "matchup(s)\n")

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/cbb__matchup_stats.json"
  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  if (result != 0) stop("Failed to upload to S3")
  cat("Uploaded to S3:", s3_path, "\n")

  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  chart_title <- paste0("CBB Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d"))
  dynamodb_item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
    s3_key, utc_timestamp, chart_title
  )
  dynamodb_cmd <- sprintf('aws dynamodb put-item --table-name %s --item %s', shQuote(dynamodb_table), shQuote(dynamodb_item))
  dynamodb_result <- system(dynamodb_cmd)
  if (dynamodb_result != 0) cat("Warning: Failed to update DynamoDB\n")
  else cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
} else {
  dev_output <- "/tmp/cbb_matchup_test.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
}

cat("\n=== CBB Matchup Stats generation complete ===\n")
