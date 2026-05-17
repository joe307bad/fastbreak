library(baseballr)
library(dplyr)
library(jsonlite)

# ============================================================================
# MLB Team Scoring Leaders — scatter of Runs Scored vs Runs Allowed
#
# The simplest possible team-quality view: how many runs you put on the
# board vs how many you give up. Run differential is the foundation of
# Pythagorean win expectancy, so this chart is essentially "where does
# every team sit on the run-differential plane?"
# ============================================================================

current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
mlb_season <- if (current_month >= 3) current_year else current_year - 1

cat("Processing MLB Team Scoring Leaders for", mlb_season, "season\n")

team_batting <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_team_batter(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      lg = "all",
      qual = "0"
    )
  ))
}, error = function(e) { cat("Error loading team batting:", e$message, "\n"); stop(e) })

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

cat("Loaded", nrow(team_batting), "team batting rows and",
    nrow(team_pitching), "team pitching rows\n")

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

bat <- team_batting %>%
  transmute(
    team = team_name_abb,
    runs_scored = as.integer(R)
  )

pit <- team_pitching %>%
  transmute(
    team = team_name_abb,
    runs_allowed = as.integer(R)
  )

teams <- inner_join(bat, pit, by = "team") %>%
  filter(!is.na(runs_scored), !is.na(runs_allowed)) %>%
  arrange(desc(runs_scored - runs_allowed))

cat("Teams ranked:", nrow(teams), "\n")
cat("\nTop 10 by run differential:\n")
print(head(teams %>% mutate(diff = runs_scored - runs_allowed), 10))

data_points <- teams %>%
  rowwise() %>%
  mutate(
    division = team_divisions[team],
    league = team_leagues[team],
    data_point = list(list(
      label = team,
      x = runs_scored,
      y = runs_allowed,
      sum = runs_scored - runs_allowed,
      teamCode = team,
      division = if (!is.na(division) && !is.null(division)) as.character(division) else NULL,
      conference = if (!is.na(league) && !is.null(league)) as.character(league) else NULL
    ))
  ) %>%
  pull(data_point)

# invertYAxis = TRUE so fewer runs allowed renders at the top.
# Up-and-to-the-right = scoring a lot, allowing few = winning baseball.
output_data <- list(
  sport = "MLB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("MLB Team Scoring Leaders - ", mlb_season, "-", substr(mlb_season + 1, 3, 4)),
  subtitle = "Runs Scored vs Runs Allowed",
  description = paste0(
    "All 30 MLB teams plotted by runs scored against runs allowed. Up and ",
    "to the right is dominant; teams above the diagonal score more than ",
    "they allow.\n\n",
    "STATS:\n\n",
    " • Runs Scored: Total runs scored on offense this season. Higher is better.\n\n",
    " • Runs Allowed: Total runs allowed on defense this season. Lower is better.\n\n",
    " • Run Differential: Runs scored minus runs allowed. Higher is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
  xAxisLabel = "Runs Scored",
  yAxisLabel = "Runs Allowed",
  xColumnLabel = "RS",
  yColumnLabel = "RA",
  invertYAxis = TRUE,
  quadrantTopRight    = list(color = "#4CAF50", label = "Dominant"),
  quadrantTopLeft     = list(color = "#2196F3", label = "Pitching-Led"),
  quadrantBottomLeft  = list(color = "#9E9E9E", label = "Outscored"),
  quadrantBottomRight = list(color = "#FF9800", label = "Slugfest"),
  subject = "TEAM",
  tags = list(
    list(label = "team", layout = "left", color = "#2196F3"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  dataPoints = data_points
)

# ============================================================================
# Upload
# ============================================================================
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (!nzchar(s3_bucket)) stop("AWS_S3_BUCKET environment variable is not set")

is_prod <- tolower(Sys.getenv("PROD")) == "true"
s3_key <- if (is_prod) "mlb__team_scoring_leaders.json" else "dev/mlb__team_scoring_leaders.json"

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE)

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

cat("\n=== MLB Team Scoring Leaders generation complete ===\n")
cat("Teams shown:", length(data_points), "\n")
cat("\nTop 5 by Runs Scored:\n")
print(head(teams %>% arrange(desc(runs_scored)), 5))
cat("\nTop 5 by Runs Allowed (fewest):\n")
print(head(teams %>% arrange(runs_allowed), 5))
