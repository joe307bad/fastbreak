library(baseballr)
library(dplyr)
library(jsonlite)

# ============================================================================
# MLB Power Rankings — scatter of team wRC+ vs team FIP-
#
# Why these two stats:
#  - wRC+   = Weighted Runs Created Plus, at the team level. Park- and
#             league-adjusted overall offensive production. 100 = league
#             average. Captures every batting event weighted by run value
#             and adjusts for the run environment a team plays in.
#  - FIP-   = Fielding Independent Pitching, normalized so 100 = league
#             average and lower is better. Strips defense and luck on
#             balls in play out of the pitching staff's results, leaving
#             a stable measure of true team-pitching skill.
#
# Together these isolate the two halves of run differential after stripping
# out park, league, and BABIP/HR-luck noise — i.e. how powerful a team
# actually is, not how lucky.
# ============================================================================

current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
mlb_season <- if (current_month >= 3) current_year else current_year - 1

cat("Processing MLB Power Rankings for", mlb_season, "season\n")

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
    wRC_plus = as.numeric(wRC_plus)
  )

pit <- team_pitching %>%
  transmute(
    team = team_name_abb,
    FIP_minus = as.numeric(`FIP-`)
  )

teams <- inner_join(bat, pit, by = "team") %>%
  filter(!is.na(wRC_plus), !is.na(FIP_minus)) %>%
  arrange(desc(wRC_plus - FIP_minus))

cat("Teams ranked:", nrow(teams), "\n")
cat("\nTop 10 by (wRC+ minus FIP-):\n")
print(head(teams, 10))

data_points <- teams %>%
  rowwise() %>%
  mutate(
    division = team_divisions[team],
    league = team_leagues[team],
    data_point = list(list(
      label = team,
      x = round(wRC_plus, 0),
      y = round(FIP_minus, 0),
      sum = round(wRC_plus - FIP_minus, 0),
      teamCode = team,
      division = if (!is.na(division) && !is.null(division)) as.character(division) else NULL,
      conference = if (!is.na(league) && !is.null(league)) as.character(league) else NULL
    ))
  ) %>%
  pull(data_point)

# invertYAxis = TRUE so lower FIP- (better pitching) renders at the top.
# Up-and-to-the-right = elite offense + elite pitching.
output_data <- list(
  sport = "MLB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("MLB Power Rankings - ", mlb_season, "-", substr(mlb_season + 1, 3, 4)),
  subtitle = "Team wRC+ vs Team FIP-",
  description = paste0(
    "All 30 MLB teams plotted by offense against pitching. Up and to the ",
    "right is elite: a team strong on both sides of the ball after stripping ",
    "out park, league, and luck-on-balls-in-play noise.\n\n",
    "STATS:\n\n",
    " • wRC+: Weighted Runs Created Plus — park- and league-adjusted team ",
    "offensive value, where 100 is league average. Higher is better.\n\n",
    " • FIP-: Fielding Independent Pitching, normalized to league average ",
    "where 100 is average. Lower is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
  xAxisLabel = "wRC+",
  yAxisLabel = "FIP-",
  xColumnLabel = "wRC+",
  yColumnLabel = "FIP-",
  invertYAxis = TRUE,
  quadrantTopRight    = list(color = "#4CAF50", label = "Elite Both Ways"),
  quadrantTopLeft     = list(color = "#2196F3", label = "Pitching-Driven"),
  quadrantBottomLeft  = list(color = "#9E9E9E", label = "Rebuilding"),
  quadrantBottomRight = list(color = "#FF9800", label = "Slug, No Stop"),
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

env <- toupper(Sys.getenv("ENV", "DEV"))
s3_key <- if (env == "PROD") "prod/mlb__power_rankings.json" else "dev/mlb__power_rankings.json"

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

cat("\n=== MLB Power Rankings generation complete ===\n")
cat("Teams shown:", length(data_points), "\n")
cat("\nTop 5 by FIP- (best pitching):\n")
print(head(teams %>% arrange(FIP_minus), 5))
cat("\nTop 5 by wRC+ (best offense):\n")
print(head(teams %>% arrange(desc(wRC_plus)), 5))
