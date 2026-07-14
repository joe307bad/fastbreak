library(baseballr)
library(dplyr)
library(jsonlite)

# ============================================================================
# MLB Pitching Leaders — scatter of K-BB% vs xFIP for qualified starters
#
# Why these two stats:
#  - K-BB%  = strikeout-rate minus walk-rate. Among the simplest rate stats and
#             also the most predictive of future run prevention. Captures
#             "stuff + command" in a single number.
#  - xFIP   = Expected Fielding Independent Pitching. Strips out luck on
#             balls in play AND normalizes HR/FB to league average, so it
#             isolates a pitcher's true talent better than ERA or even FIP.
#
# Output is a SCATTER_PLOT visualization; the existing KMP renderer reads it
# without any client changes.
# ============================================================================

# Determine current MLB season
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
# MLB season runs March/April through October. Before March, fall back to
# the prior season so we never produce an empty chart.
mlb_season <- if (current_month >= 3) current_year else current_year - 1

primary_mlb_team <- function(team_abb) {
  if (is.na(team_abb) || !nzchar(team_abb) || grepl("Tms", team_abb, fixed = TRUE)) {
    return(NA_character_)
  }
  trimws(strsplit(as.character(team_abb), ",")[[1]][1])
}

scatter_plot_point <- function(label, x, y, sum, team_code, division = NULL, conference = NULL) {
  pt <- list(label = label, x = x, y = y, sum = sum, teamCode = team_code)
  if (!is.null(division) && !is.na(division) && nzchar(division)) pt$division <- as.character(division)
  if (!is.null(conference) && !is.na(conference) && nzchar(conference)) pt$conference <- as.character(conference)
  pt
}

cat("Processing MLB Pitching Leaders for", mlb_season, "season\n")

# Pull pitcher leaderboard from FanGraphs (qual="0" so we filter ourselves)
pitcher_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_pitcher_leaders(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading pitcher stats:", e$message, "\n"); stop(e)
})

cat("Loaded", nrow(pitcher_stats), "pitcher rows from FanGraphs\n")

# MLB league/division mapping (FanGraphs abbreviations).
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

# Filter to starters with enough work to be meaningfully ranked. Thresholds
# are deliberately permissive early in the season so the chart populates
# in April.
min_ip <- 20
min_gs <- 4

qualified_pitchers <- pitcher_stats %>%
  mutate(
    player = PlayerName,
    team = team_name_abb,
    IP = as.numeric(IP),
    GS = as.integer(GS),
    K_BB_pct = as.numeric(`K-BB_pct`),
    xFIP = as.numeric(xFIP)
  ) %>%
  filter(
    !is.na(IP), !is.na(GS), !is.na(K_BB_pct), !is.na(xFIP),
    IP >= min_ip,
    GS >= min_gs,
    !grepl("Tms", team, fixed = TRUE)
  )

cat("Qualified starters (IP >=", min_ip, ", GS >=", min_gs, "):",
    nrow(qualified_pitchers), "\n")

# Top 50 by K-BB% keeps the chart readable while still surfacing the leaderboard
top_pitchers <- qualified_pitchers %>%
  arrange(desc(K_BB_pct)) %>%
  head(50) %>%
  select(player, team, IP, GS, K_BB_pct, xFIP)

cat("\nTop 10 by K-BB%:\n")
print(head(top_pitchers, 10))

# Convert K-BB% to a percent (0.278 -> 27.8) so the axis renders naturally
data_points <- top_pitchers %>%
  rowwise() %>%
  mutate(
    primary_team = primary_mlb_team(team),
    data_point = list(scatter_plot_point(
      label = player,
      x = round(K_BB_pct * 100, 1),
      y = round(xFIP, 2),
      sum = round(K_BB_pct * 100, 1),
      team_code = team,
      division = if (is.na(primary_team)) NULL else team_divisions[[primary_team]],
      conference = if (is.na(primary_team)) NULL else team_leagues[[primary_team]]
    ))
  ) %>%
  pull(data_point)

# Build the visualization payload. invertYAxis = TRUE so lower xFIP renders at
# the top, i.e. "up + right = better".
output_data <- list(
  sport = "MLB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("MLB Pitching Leaders - ", mlb_season, "-", substr(mlb_season + 1, 3, 4)),
  subtitle = "K-BB% vs xFIP",
  description = paste0(
    "Top 50 starting pitchers (min ", min_ip, " IP, ", min_gs,
    " GS) plotted by swing-and-miss command against luck-independent run ",
    "prevention. Up and to the right is elite.\n\n",
    "STATS:\n\n",
    " • K-BB%: Strikeout rate minus walk rate — the simplest, most predictive ",
    "measure of missing bats with command. Higher is better.\n\n",
    " • xFIP: Expected Fielding Independent Pitching — run prevention from a ",
    "pitcher's true skill profile, normalizing home-run rate to league ",
    "average. Lower is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
  xAxisLabel = "K-BB%",
  yAxisLabel = "xFIP",
  xColumnLabel = "K-BB%",
  yColumnLabel = "xFIP",
  invertYAxis = TRUE,
  quadrantTopRight    = list(color = "#4CAF50", label = "Elite Stuff + Command"),
  quadrantTopLeft     = list(color = "#2196F3", label = "Contact Managers"),
  quadrantBottomLeft  = list(color = "#9E9E9E", label = "Modest Stuff + Command"),
  quadrantBottomRight = list(color = "#FF9800", label = "K Artists, HR-Prone"),
  subject = "PLAYER",
  tags = list(
    list(label = "player", layout = "left", color = "#2196F3"),
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
s3_key <- if (env == "PROD") "prod/mlb__pitcher_leaders.json" else "dev/mlb__pitcher_leaders.json"

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE)

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path),
             "--content-type application/json")
if (system(cmd) != 0) stop("Failed to upload to S3")
cat("\nUploaded to S3:", s3_path, "\n")

# DynamoDB timestamp update
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

cat("\n=== MLB Pitching Leaders generation complete ===\n")
cat("Pitchers shown:", length(data_points), "\n")
cat("\nTop 5 by xFIP (best):\n")
print(head(top_pitchers %>% arrange(xFIP), 5))
cat("\nTop 5 by K-BB%:\n")
print(head(top_pitchers %>% arrange(desc(K_BB_pct)), 5))
