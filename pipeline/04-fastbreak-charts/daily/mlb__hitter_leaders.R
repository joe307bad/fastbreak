library(baseballr)
library(dplyr)
library(jsonlite)

# ============================================================================
# MLB Hitting Leaders — scatter of wRC+ vs Barrel% for qualified hitters
#
# Why these two stats:
#  - wRC+     = Weighted Runs Created Plus. The single best "all in one"
#               offensive stat: it combines every batting event (BB, HBP,
#               1B, 2B, 3B, HR) weighted by run value, then adjusts for
#               park and league. 100 = league average, 150 = elite.
#  - Barrel%  = Percent of batted balls with Statcast "barrel" exit
#               velocity + launch angle. Isolates elite contact quality
#               without being another run-value metric (unlike xwOBA,
#               which overlaps heavily with wRC+).
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

# FanGraphs uses "2 Tms" when a player played for multiple teams. Use the
# first comma-separated team when present; otherwise skip division lookup.
primary_mlb_team <- function(team_abb) {
  if (is.na(team_abb) || !nzchar(team_abb) || grepl("Tms", team_abb, fixed = TRUE)) {
    return(NA_character_)
  }
  trimws(strsplit(as.character(team_abb), ",")[[1]][1])
}

# jsonlite auto_unbox turns explicit NULL list fields into {} — omit optional keys.
scatter_plot_point <- function(label, x, y, sum, team_code, division = NULL, conference = NULL) {
  pt <- list(label = label, x = x, y = y, sum = sum, teamCode = team_code)
  if (!is.null(division) && !is.na(division) && nzchar(division)) pt$division <- as.character(division)
  if (!is.null(conference) && !is.na(conference) && nzchar(conference)) pt$conference <- as.character(conference)
  pt
}

cat("Processing MLB Hitting Leaders for", mlb_season, "season\n")

# Pull batter leaderboard from FanGraphs (qual="0" so we filter ourselves)
batter_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_batter_leaders(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading batter stats:", e$message, "\n"); stop(e)
})

cat("Loaded", nrow(batter_stats), "batter rows from FanGraphs\n")

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

# Filter to hitters with enough work to be meaningfully ranked. Thresholds
# are deliberately permissive early in the season so the chart populates
# in April. ~100 PA roughly equals a few weeks of regular play.
min_pa <- 100
min_g <- 25

qualified_hitters <- batter_stats %>%
  mutate(
    player = PlayerName,
    team = team_name_abb,
    PA = as.integer(PA),
    G = as.integer(G),
    wRC_plus = as.numeric(wRC_plus),
    # FanGraphs returns Barrel% as a proportion (e.g. 0.12); show as percent.
    Barrel_pct = round(as.numeric(Barrel_pct) * 100, 1)
  ) %>%
  filter(
    !is.na(PA), !is.na(G), !is.na(wRC_plus), !is.na(Barrel_pct),
    PA >= min_pa,
    G >= min_g,
    !grepl("Tms", team, fixed = TRUE)
  )

cat("Qualified hitters (PA >=", min_pa, ", G >=", min_g, "):",
    nrow(qualified_hitters), "\n")

# Top 50 by wRC+ keeps the chart readable while still surfacing the leaderboard
top_hitters <- qualified_hitters %>%
  arrange(desc(wRC_plus)) %>%
  head(50) %>%
  select(player, team, PA, G, wRC_plus, Barrel_pct)

cat("\nTop 10 by wRC+:\n")
print(head(top_hitters, 10))

# wRC+ is an integer-style index (100 = avg). Barrel% is a percent around
# ~5–15 for qualified hitters; one decimal keeps the axis readable.
data_points <- top_hitters %>%
  rowwise() %>%
  mutate(
    primary_team = primary_mlb_team(team),
    data_point = list(scatter_plot_point(
      label = player,
      x = round(wRC_plus, 0),
      y = Barrel_pct,
      sum = round(wRC_plus, 0),
      team_code = team,
      division = if (is.na(primary_team)) NULL else team_divisions[[primary_team]],
      conference = if (is.na(primary_team)) NULL else team_leagues[[primary_team]]
    ))
  ) %>%
  pull(data_point)

# Build the visualization payload. Both axes "higher is better" so no
# inversion needed — up and to the right = elite.
output_data <- list(
  sport = "MLB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("MLB Hitting Leaders - ", mlb_season, "-", substr(mlb_season + 1, 3, 4)),
  subtitle = paste0("At least ", min_pa, " plate appearances"),
  description = paste0(
    "Top 50 hitters (min ", min_pa, " PA, ", min_g,
    " G) plotted by run production (wRC+) against elite contact rate (Barrel%). ",
    "Up and to the right is elite: real production backed by barrels.\n\n",
    "STATS:\n\n",
    " • wRC+: Weighted Runs Created Plus — park- and league-adjusted total ",
    "offensive value, where 100 is league average. Higher is better.\n\n",
    " • Barrel%: Percent of batted balls hit with optimal exit velocity and ",
    "launch angle (Statcast). Isolates contact quality without overlapping ",
    "wRC+ as another run-value metric. Higher is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
  xAxisLabel = "wRC+",
  yAxisLabel = "Barrel%",
  xColumnLabel = "wRC+",
  yColumnLabel = "Barrel%",
  invertYAxis = FALSE,
  quadrantTopRight    = list(color = "#4CAF50", label = "Elite Production + Contact"),
  quadrantTopLeft     = list(color = "#2196F3", label = "Hard Contact, Waiting on Results"),
  quadrantBottomLeft  = list(color = "#9E9E9E", label = "Struggling"),
  quadrantBottomRight = list(color = "#FF9800", label = "Producing Without Barrels"),
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
s3_key <- if (env == "PROD") "prod/mlb__hitter_leaders.json" else "dev/mlb__hitter_leaders.json"

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

cat("\n=== MLB Hitting Leaders generation complete ===\n")
cat("Hitters shown:", length(data_points), "\n")
cat("\nTop 5 by Barrel% (best):\n")
print(head(top_hitters %>% arrange(desc(Barrel_pct)), 5))
cat("\nTop 5 by wRC+:\n")
print(head(top_hitters %>% arrange(desc(wRC_plus)), 5))
