library(baseballr)
library(dplyr)
library(jsonlite)

# ============================================================================
# MLB Team Wins — bar chart of total wins by team for the season.
# Pulled from team pitching W (every game has exactly one win credited,
# so the team-level W column equals total team wins).
# ============================================================================

current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
mlb_season <- if (current_month >= 3) current_year else current_year - 1

cat("Processing MLB Team Wins for", mlb_season, "season\n")

team_pitching <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_team_pitcher(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      lg = "all",
      qual = "0"
    )
  ))
}, error = function(e) { cat("Error loading team pitching:", e$message, "\n"); stop(e) })

cat("Loaded", nrow(team_pitching), "team pitching rows\n")

team_divisions <- c(
  "BAL" = "AL East", "BOS" = "AL East", "NYY" = "AL East", "TBR" = "AL East", "TOR" = "AL East",
  "CHW" = "AL Central", "CLE" = "AL Central", "DET" = "AL Central", "KCR" = "AL Central", "MIN" = "AL Central",
  "HOU" = "AL West", "LAA" = "AL West", "OAK" = "AL West", "ATH" = "AL West", "SEA" = "AL West", "TEX" = "AL West",
  "ATL" = "NL East", "MIA" = "NL East", "NYM" = "NL East", "PHI" = "NL East", "WSN" = "NL East",
  "CHC" = "NL Central", "CIN" = "NL Central", "MIL" = "NL Central", "PIT" = "NL Central", "STL" = "NL Central",
  "ARI" = "NL West", "COL" = "NL West", "LAD" = "NL West", "SDP" = "NL West", "SFG" = "NL West"
)

team_leagues <- c(
  "BAL" = "American", "BOS" = "American", "NYY" = "American", "TBR" = "American", "TOR" = "American",
  "CHW" = "American", "CLE" = "American", "DET" = "American", "KCR" = "American", "MIN" = "American",
  "HOU" = "American", "LAA" = "American", "OAK" = "American", "ATH" = "American", "SEA" = "American", "TEX" = "American",
  "ATL" = "National", "MIA" = "National", "NYM" = "National", "PHI" = "National", "WSN" = "National",
  "CHC" = "National", "CIN" = "National", "MIL" = "National", "PIT" = "National", "STL" = "National",
  "ARI" = "National", "COL" = "National", "LAD" = "National", "SDP" = "National", "SFG" = "National"
)

teams <- team_pitching %>%
  transmute(
    team = team_name_abb,
    W = as.integer(W)
  ) %>%
  filter(!is.na(W)) %>%
  arrange(desc(W))

cat("Teams ranked:", nrow(teams), "\n")
cat("\nTop 10 by W:\n")
print(head(teams, 10))

data_points <- teams %>%
  rowwise() %>%
  mutate(
    division = team_divisions[team],
    conference = team_leagues[team],
    data_point = list(list(
      label = team,
      value = W,
      division = if (!is.na(division) && !is.null(division)) as.character(division) else NULL,
      conference = if (!is.na(conference) && !is.null(conference)) as.character(conference) else NULL
    ))
  ) %>%
  pull(data_point)

output_data <- list(
  sport = "MLB",
  visualizationType = "BAR_GRAPH",
  title = paste0("MLB Team Wins - ", mlb_season, "-", substr(mlb_season + 1, 3, 4)),
  subtitle = "Total Wins by Team",
  yAxisLabel = "Wins",
  description = paste0(
    "Total games won by each team in the ", mlb_season,
    " MLB season, sorted from most to fewest. The bottom-line scoreboard ",
    "view of the standings."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 2,
  dataPoints = data_points
)

# ============================================================================
# Upload
# ============================================================================
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (!nzchar(s3_bucket)) stop("AWS_S3_BUCKET environment variable is not set")

env <- toupper(Sys.getenv("ENV", "DEV"))
s3_key <- if (env == "PROD") "prod/mlb__team_wins.json" else "dev/mlb__team_wins.json"

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null")

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path),
             "--content-type application/json")
if (system(cmd) != 0) stop("Failed to upload to S3")
cat("\nUploaded to S3:", s3_path, "\n")

dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
chart_title <- output_data$title

dynamodb_item <- sprintf(
  '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
  s3_key, utc_timestamp, chart_title
)
ddb_cmd <- sprintf('aws dynamodb put-item --table-name %s --item %s',
                   shQuote(dynamodb_table), shQuote(dynamodb_item))
if (system(ddb_cmd) != 0) {
  warning("Failed to update DynamoDB timestamp (non-fatal)")
} else {
  cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
}

cat("\n=== MLB Team Wins generation complete ===\n")
cat("Teams shown:", length(data_points), "\n")
