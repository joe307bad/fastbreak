library(baseballr)
library(dplyr)
library(jsonlite)

# ============================================================================
# MLB Hitting Leaders — scatter of wRC+ vs xwOBA for qualified hitters
#
# Why these two stats:
#  - wRC+   = Weighted Runs Created Plus. The single best "all in one"
#             offensive stat: it combines every batting event (BB, HBP,
#             1B, 2B, 3B, HR) weighted by run value, then adjusts for
#             park and league. 100 = league average, 150 = elite.
#  - xwOBA  = Expected Weighted On-Base Average. Statcast-derived
#             expected wOBA based on quality of contact (exit velocity
#             and launch angle), so it strips out luck on balls in play
#             and isolates a hitter's true contact-quality skill — the
#             hitting analogue of xFIP for pitchers.
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
    xwOBA = as.numeric(xwOBA)
  ) %>%
  filter(
    !is.na(PA), !is.na(G), !is.na(wRC_plus), !is.na(xwOBA),
    PA >= min_pa,
    G >= min_g
  )

cat("Qualified hitters (PA >=", min_pa, ", G >=", min_g, "):",
    nrow(qualified_hitters), "\n")

# Top 50 by wRC+ keeps the chart readable while still surfacing the leaderboard
top_hitters <- qualified_hitters %>%
  arrange(desc(wRC_plus)) %>%
  head(50) %>%
  select(player, team, PA, G, wRC_plus, xwOBA)

cat("\nTop 10 by wRC+:\n")
print(head(top_hitters, 10))

# wRC+ is already an integer-style index (100 = avg). xwOBA is a rate around
# .300-.400; round to 3 decimals so the axis renders cleanly.
data_points <- top_hitters %>%
  rowwise() %>%
  mutate(
    primary_team = strsplit(team, ",")[[1]][1],
    division = team_divisions[primary_team],
    league = team_leagues[primary_team],
    data_point = list(list(
      label = player,
      x = round(wRC_plus, 0),
      y = round(xwOBA, 3),
      sum = round(wRC_plus, 0),
      teamCode = team,
      division = if (!is.na(division) && !is.null(division)) as.character(division) else NULL,
      conference = if (!is.na(league) && !is.null(league)) as.character(league) else NULL
    ))
  ) %>%
  pull(data_point)

# Build the visualization payload. Both axes "higher is better" so no
# inversion needed — up and to the right = elite.
output_data <- list(
  sport = "MLB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("MLB Hitting Leaders - ", mlb_season, "-", substr(mlb_season + 1, 3, 4)),
  subtitle = "wRC+ vs xwOBA",
  description = paste0(
    "Top 50 hitters (min ", min_pa, " PA, ", min_g,
    " G) plotted by run production against contact quality. Up and to ",
    "the right is elite: real production backed by sustainable contact.\n\n",
    "STATS:\n\n",
    " • wRC+: Weighted Runs Created Plus — park- and league-adjusted total ",
    "offensive value, where 100 is league average. Higher is better.\n\n",
    " • xwOBA: Expected Weighted On-Base Average — expected offensive value ",
    "from Statcast quality of contact, stripping out luck on balls in play. ",
    "Higher is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
  xAxisLabel = "wRC+",
  yAxisLabel = "xwOBA",
  xColumnLabel = "wRC+",
  yColumnLabel = "xwOBA",
  invertYAxis = FALSE,
  quadrantTopRight    = list(color = "#4CAF50", label = "Elite, Sustainable"),
  quadrantTopLeft     = list(color = "#2196F3", label = "Unlucky, Due to Improve"),
  quadrantBottomLeft  = list(color = "#9E9E9E", label = "Struggling"),
  quadrantBottomRight = list(color = "#FF9800", label = "Outperforming Contact"),
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

is_prod <- tolower(Sys.getenv("PROD")) == "true"
s3_key <- if (is_prod) "mlb__hitter_leaders.json" else "dev/mlb__hitter_leaders.json"

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
cat("\nTop 5 by xwOBA (best):\n")
print(head(top_hitters %>% arrange(desc(xwOBA)), 5))
cat("\nTop 5 by wRC+:\n")
print(head(top_hitters %>% arrange(desc(wRC_plus)), 5))
