library(dplyr)
library(jsonlite)
library(httr)

# Load NBA utilities
args <- commandArgs(trailingOnly = FALSE)
file_arg <- grep("^--file=", args, value = TRUE)
if (length(file_arg) > 0) {
  script_dir <- dirname(sub("^--file=", "", file_arg))
} else {
  script_dir <- getwd()
}
source(file.path(script_dir, "..", "utils", "nba_utils.R"))

# API rate limiting
add_api_delay <- function() Sys.sleep(0.3)

# Detect if NBA playoffs are active using ESPN API
detect_nba_postseason <- function() {
  tryCatch({
    url <- "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard"
    add_api_delay()
    resp <- GET(url)

    if (status_code(resp) == 200) {
      data <- content(resp, as = "parsed")
      season_type <- data$season$type
      season_year <- data$season$year
      is_postseason <- !is.null(season_type) && season_type == 3
      cat(sprintf("ESPN NBA season: %d, type: %d (%s)\n",
                  season_year,
                  season_type,
                  switch(as.character(season_type),
                         "1" = "Preseason",
                         "2" = "Regular Season",
                         "3" = "Playoffs",
                         "4" = "Offseason",
                         "Unknown")))
      return(list(is_postseason = is_postseason, season_year = season_year))
    }
    return(list(is_postseason = FALSE, season_year = NULL))
  }, error = function(e) {
    cat("Error detecting NBA season type:", e$message, "\n")
    return(list(is_postseason = FALSE, season_year = NULL))
  })
}

# Fetch playoff player stats from ESPN API
fetch_espn_playoff_stats <- function(season_year) {
  url <- "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/statistics/byathlete"
  params <- list(
    season = season_year,
    seasontype = 3,  # playoffs
    limit = 100
  )

  add_api_delay()
  resp <- GET(url, query = params)

  if (status_code(resp) != 200) {
    cat("ESPN API returned status:", status_code(resp), "\n")
    return(NULL)
  }

  data <- content(resp, as = "text", encoding = "UTF-8")
  parsed <- fromJSON(data, flatten = TRUE)

  if (is.null(parsed$athletes) || nrow(parsed$athletes) == 0) {
    return(NULL)
  }

  # Extract player stats from the nested structure
  athletes <- parsed$athletes

  # Parse the categories to extract stats
  # Categories: general (GP, MIN, etc), offensive (PTS, etc), defensive (REB, BLK, STL)
  player_stats <- data.frame(
    player_id = athletes$athlete.id,
    player_name = athletes$athlete.displayName,
    team_abbrev = sapply(athletes$athlete.teams, function(t) {
      if (!is.null(t) && nrow(t) > 0) t$abbreviation[1] else NA
    }),
    stringsAsFactors = FALSE
  )

  # Extract stats from categories
  # Offensive order: PTS, FGM, FGA, FG%, 3PM, 3PA, 3P%, FTM, FTA, FT%, AST, TO
  for (i in seq_len(nrow(athletes))) {
    cats <- athletes$categories[[i]]
    if (!is.null(cats)) {
      for (j in seq_len(nrow(cats))) {
        cat_name <- cats$name[j]
        values <- cats$values[[j]]

        if (cat_name == "general") {
          # GP, MIN, PF, ...
          player_stats$GP[i] <- values[1]
          player_stats$MIN[i] <- values[2]
        } else if (cat_name == "offensive") {
          # Order: PTS, FGM, FGA, FG%, 3PM, 3PA, 3P%, FTM, FTA, FT%, AST, TO
          player_stats$PTS[i] <- values[1]
          player_stats$FGM[i] <- values[2]
          player_stats$FGA[i] <- values[3]
          player_stats$FG_PCT[i] <- values[4]
          player_stats$THREE_PM[i] <- values[5]
          player_stats$THREE_PA[i] <- values[6]
          player_stats$THREE_PCT[i] <- values[7]
          player_stats$FTM[i] <- values[8]
          player_stats$FTA[i] <- values[9]
          player_stats$FT_PCT[i] <- values[10]
          player_stats$AST[i] <- values[11]
          player_stats$TO[i] <- values[12]
        } else if (cat_name == "defensive") {
          # STL, BLK, REB
          player_stats$STL[i] <- values[1]
          player_stats$BLK[i] <- values[2]
          player_stats$REB[i] <- values[3]
        }
      }
    }
  }

  # Convert to numeric
  player_stats <- player_stats %>%
    mutate(
      GP = as.numeric(GP),
      MIN = as.numeric(MIN),
      PTS = as.numeric(PTS),
      FGA = as.numeric(FGA),
      FTA = as.numeric(FTA),
      AST = as.numeric(AST),
      REB = as.numeric(REB),
      STL = as.numeric(STL),
      BLK = as.numeric(BLK),
      FG_PCT = as.numeric(FG_PCT),
      THREE_PCT = as.numeric(THREE_PCT),
      FT_PCT = as.numeric(FT_PCT),
      TO = as.numeric(TO)
    )

  # Calculate advanced metrics
  player_stats <- player_stats %>%
    mutate(
      TOTAL_MIN = round(GP * MIN, 0),
      # True Shooting % = PTS / (2 * (FGA + 0.44 * FTA))
      TS_PCT = round(PTS / (2 * (FGA + 0.44 * FTA)) * 100, 1),
      # Assist to Turnover ratio
      AST_TO = round(AST / pmax(TO, 0.1), 2),
      # Per-36 production
      PER_36 = round((PTS + REB + AST) / MIN * 36, 1)
    )

  return(player_stats)
}

# Get season info
season_info <- detect_nba_postseason()

cat("Processing NBA Playoff Player Impact\n")

if (!season_info$is_postseason) {
  cat("\nNBA Playoffs are not currently active. Exiting.\n")
  cat("This visualization is only generated during the NBA playoffs.\n")
  quit(status = 0)
}

season_year <- season_info$season_year
cat("\nPlayoffs are active - fetching playoff player stats for", season_year, "...\n")

# Fetch playoff stats from ESPN
player_stats <- fetch_espn_playoff_stats(season_year)

if (is.null(player_stats) || nrow(player_stats) == 0) {
  cat("No playoff data available yet.\n")
  quit(status = 0)
}

cat("Loaded playoff stats for", nrow(player_stats), "players\n")

# Filter to players with meaningful minutes (at least 2 games and 15 MPG)
min_games <- 2
min_mpg <- 15

qualified_players <- player_stats %>%
  filter(
    GP >= min_games,
    MIN >= min_mpg,
    !is.na(TS_PCT),
    !is.infinite(TS_PCT),
    TS_PCT > 0,
    !is.na(team_abbrev)
  )

cat("Qualified players (>=", min_games, "games, >=", min_mpg, "MPG):",
    nrow(qualified_players), "\n")

if (nrow(qualified_players) < 10) {
  cat("\nNot enough qualified players yet. Playoffs may have just started.\n")
  quit(status = 0)
}

# Select top 50 players by total minutes for the visualization
top_players <- qualified_players %>%
  arrange(desc(TOTAL_MIN)) %>%
  head(50)

cat("\nTop Players by Playoff Minutes:\n")
print(head(top_players %>%
  select(player_name, team_abbrev, TOTAL_MIN, GP, PTS, TS_PCT), 10))

# Calculate averages for quadrant positioning
avg_minutes <- mean(top_players$TOTAL_MIN)
avg_ts <- mean(top_players$TS_PCT, na.rm = TRUE)

cat("\nQuadrant center: Total Min =", round(avg_minutes, 0),
    ", TS% =", round(avg_ts, 1), "\n")

# Convert to list format for JSON
# x = Total Playoff Minutes, y = True Shooting %
data_points <- top_players %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = player_name,
    x = TOTAL_MIN,
    y = TS_PCT,
    sum = round(PTS, 1),  # PPG as secondary stat
    teamCode = team_abbrev,
    division = nba_team_divisions[team_abbrev],
    conference = nba_team_conferences[team_abbrev],
    gamesPlayed = GP,
    minutesPerGame = round(MIN, 1),
    pointsPerGame = round(PTS, 1),
    reboundsPerGame = round(REB, 1),
    assistsPerGame = round(AST, 1),
    stealsPerGame = round(STL, 1),
    blocksPerGame = round(BLK, 1),
    assistToTurnover = AST_TO
  ))) %>%
  pull(data_point)

# Calculate stats for description
max_minutes_player <- top_players %>% slice_max(TOTAL_MIN, n = 1)
max_ts_player <- top_players %>% slice_max(TS_PCT, n = 1)
max_ppg_player <- top_players %>% slice_max(PTS, n = 1)

# Get season label
nba_season <- season_year
season_label <- paste0(nba_season - 1, "-", substr(nba_season, 3, 4))

# Create output object
output_data <- list(
  sport = "NBA",
  visualizationType = "SCATTER_PLOT",
  title = paste0("NBA Playoff Efficiency - ", season_label),
  subtitle = "Total Minutes vs True Shooting %",
  description = paste0(
    "Playoff performers plotted by workload against scoring efficiency. The ",
    "top-right quadrant holds high-volume, efficient playoff scorers.\n\n",
    "STATS:\n\n",
    " • Total Playoff Minutes: Minutes played across the postseason — a proxy ",
    "for workload and trust. Higher means a bigger role.\n\n",
    " • True Shooting % (TS%): Scoring efficiency accounting for 2-pointers, ",
    "3-pointers, and free throws. Higher is better.\n\n",
    " • Points Per Game (PPG): Average points scored per playoff game. ",
    "Higher is better.\n\n",
    "Minutes leader: ", max_minutes_player$player_name,
    " (", max_minutes_player$TOTAL_MIN, " min). ",
    "TS% leader: ", max_ts_player$player_name,
    " (", round(max_ts_player$TS_PCT, 1), "%). ",
    "PPG leader: ", max_ppg_player$player_name,
    " (", round(max_ppg_player$PTS, 1), " ppg)."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN",
  xAxisLabel = "Total Playoff Minutes",
  yAxisLabel = "True Shooting %",
  xColumnLabel = "MIN",
  yColumnLabel = "TS%",
  invertYAxis = FALSE,
  quadrantTopRight = list(color = "#4CAF50", label = "Elite Efficiency"),
  quadrantTopLeft = list(color = "#2196F3", label = "Efficient Role"),
  quadrantBottomLeft = list(color = "#9E9E9E", label = "Limited Role"),
  quadrantBottomRight = list(color = "#FF9800", label = "High Volume"),
  subject = "PLAYER",
  tags = list(
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = "playoffs", layout = "right", color = "#E91E63")
  ),
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  # Local development - write to tmp file
  tmp_file <- tempfile(pattern = "nba_playoff_player_impact_", fileext = ".json")
  write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE)
  cat("\nLocal development mode - JSON written to:", tmp_file, "\n")
} else {
  env <- toupper(Sys.getenv("ENV", "DEV"))

  s3_key <- if (env == "PROD") {
    "prod/nba__playoff_player_impact.json"
  } else {
    "dev/nba__playoff_player_impact.json"
  }

  # Write JSON to temp file and upload via AWS CLI
  tmp_file <- tempfile(fileext = ".json")
  write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE)

  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)

  if (result != 0) {
    stop("Failed to upload to S3")
  }

  cat("\nUploaded to S3:", s3_path, "\n")

  # Update DynamoDB
  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  chart_title <- output_data$title
  chart_interval <- "daily"

  dynamodb_item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "%s"}}',
    s3_key, utc_timestamp, chart_title, chart_interval
  )
  dynamodb_cmd <- sprintf(
    'aws dynamodb put-item --table-name %s --item %s',
    shQuote(dynamodb_table),
    shQuote(dynamodb_item)
  )

  dynamodb_result <- system(dynamodb_cmd)

  if (dynamodb_result != 0) {
    warning("Failed to update DynamoDB timestamp (non-fatal)")
  } else {
    cat("Updated DynamoDB:", dynamodb_table, "\n")
  }
}

# Print summary
cat("\n========================================\n")
cat("NBA Playoff Efficiency Summary\n")
cat("========================================\n")
cat("Total players shown:", length(data_points), "\n")
cat("Total playoff games played so far:", sum(top_players$GP), "player-games\n")

cat("\nTop 5 by Total Playoff Minutes:\n")
print(
  top_players %>%
    arrange(desc(TOTAL_MIN)) %>%
    head(5) %>%
    select(player_name, team_abbrev, TOTAL_MIN, GP, PTS, TS_PCT)
)

cat("\nTop 5 by True Shooting %:\n")
print(
  top_players %>%
    arrange(desc(TS_PCT)) %>%
    head(5) %>%
    select(player_name, team_abbrev, TOTAL_MIN, PTS, TS_PCT)
)

cat("\nTop 5 by PPG:\n")
print(
  top_players %>%
    arrange(desc(PTS)) %>%
    head(5) %>%
    select(player_name, team_abbrev, TOTAL_MIN, PTS, TS_PCT)
)

cat("\nElite Efficiency (High Minutes + High TS%):\n")
playoff_stars <- top_players %>%
  filter(TOTAL_MIN >= avg_minutes, TS_PCT >= avg_ts) %>%
  arrange(desc(TS_PCT))
print(
  playoff_stars %>%
    head(10) %>%
    select(player_name, team_abbrev, TOTAL_MIN, PTS, TS_PCT)
)

cat("\nDone!\n")
