# College Basketball Team Efficiency
# Reads ORtg and DRtg from sports-ref-ratings.csv
# Plots only top 64 teams by net rating
# Usage: Rscript cbb__team_efficiency.R [year]

library(dplyr)
library(jsonlite)
library(readr)

# Get year from command line args, default to current year
args <- commandArgs(trailingOnly = TRUE)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
default_year <- if (current_month >= 10) current_year + 1 else current_year

year <- if (length(args) >= 1) as.integer(args[1]) else default_year

# Data directory - use /app/data in container, or relative path for local dev
data_base <- if (dir.exists("/app/data")) {
  "/app/data"
} else {
  # Local development: look for data folder relative to script
  cmd_args <- commandArgs(trailingOnly = FALSE)
  script_path <- sub("--file=", "", cmd_args[grep("--file=", cmd_args)])
  script_dir <- dirname(script_path)
  file.path(dirname(script_dir), "data")
}

# Build path
data_dir <- file.path(data_base, "cbb", year)
csv_path <- file.path(data_dir, "sports-ref-ratings.csv")

cat("Processing College Basketball Team Efficiency for", year, "\n")
cat("CSV path:", csv_path, "\n\n")

# Read from sports-ref-ratings.csv
# Row 1: group headers, Row 2: column names, Row 3+: data
# Columns: Rk, School, Conf, (blank), AP Rank, W, L, Pts, Opp, MOV, (blank), SOS, (blank), OSRS, DSRS, SRS, ORtg, DRtg, NRtg
ratings_data <- read_csv(csv_path, skip = 2, col_names = FALSE, show_col_types = FALSE)

# Assign column names based on the structure
colnames(ratings_data) <- c("Rk", "School", "Conf", "X4", "AP_Rank", "W", "L", "Pts", "Opp",
                             "MOV", "X11", "SOS", "X13", "OSRS", "DSRS", "SRS",
                             "ORtg", "DRtg", "NRtg")

# Also read school stats for PPG data
school_stats_path <- file.path(data_dir, "sports-ref-school-stats.csv")
school_stats_raw <- read_csv(school_stats_path, skip = 2, col_names = FALSE, show_col_types = FALSE)
colnames(school_stats_raw) <- c("Rk", "School", "G", "W", "L", "WL_Pct", "SRS", "SOS", "X9",
                                 "Conf_W", "Conf_L", "X12", "Home_W", "Home_L", "X15",
                                 "Away_W", "Away_L", "X18", "Pts_Tm", "Pts_Opp", "X21",
                                 "MP", "FG", "FGA", "FG_Pct", "ThreeP", "ThreePA", "ThreeP_Pct",
                                 "FT", "FTA", "FT_Pct", "ORB", "TRB", "AST", "STL", "BLK", "TOV", "PF")

school_stats_data <- school_stats_raw %>%
  filter(!is.na(Rk) & Rk != "" & Rk != "Rk") %>%
  mutate(
    G = as.numeric(G),
    Pts_Tm = as.numeric(Pts_Tm),
    Pts_Opp = as.numeric(Pts_Opp),
    PPG = round(Pts_Tm / G, 1),
    OPP_PPG = round(Pts_Opp / G, 1)
  ) %>%
  select(School, PPG, OPP_PPG)

team_ratings <- ratings_data %>%
  select(Rk, School, Conf, ORtg, DRtg, NRtg) %>%
  filter(!is.na(School) & School != "School") %>%
  mutate(
    Rk = as.numeric(Rk),
    ORtg = as.numeric(ORtg),
    DRtg = as.numeric(DRtg),
    NRtg = as.numeric(NRtg)
  ) %>%
  filter(!is.na(ORtg) & !is.na(DRtg) & !is.na(NRtg)) %>%
  # Join with school stats for PPG
  left_join(school_stats_data, by = "School") %>%
  # Filter to top 64 by net rating
  arrange(desc(NRtg)) %>%
  head(64)

cat("Loaded top", nrow(team_ratings), "teams by net rating\n")

cat("\nTop 10 by Net Rating:\n")
print(head(team_ratings %>%
  select(School, ORtg, DRtg, NRtg), 10))

# Convert to list format for JSON matching ScatterPlotVisualization model
data_points <- team_ratings %>%
  rowwise() %>%
  mutate(data_point = list(list(
    label = School,
    x = round(ORtg, 2),
    y = round(DRtg, 2),
    sum = round(NRtg, 2),
    conference = Conf,
    ppg = PPG,
    oppPpg = OPP_PPG
  ))) %>%
  pull(data_point)

# Create output object
output_data <- list(
  sport = "CBB",
  visualizationType = "SCATTER_PLOT",
  title = paste0("College Basketball Team Efficiency - ", year - 1, "-", substr(year, 3, 4)),
  subtitle = "Offensive vs Defensive Rating (Top 64 Teams)",
  description = "Offensive Rating measures points scored per 100 possessions, while Defensive Rating measures points allowed per 100 possessions (lower is better). Teams in the top-right quadrant have elite offenses and defenses. Only showing top 64 teams by net rating.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "Sports Reference",
  xAxisLabel = "Offensive Rating",
  yAxisLabel = "Defensive Rating",
  xColumnLabel = "ORtg",
  yColumnLabel = "DRtg",
  invertYAxis = TRUE,
  quadrantTopRight = list(color = "#4CAF50", label = "Elite"),
  quadrantTopLeft = list(color = "#2196F3", label = "Defense First"),
  quadrantBottomLeft = list(color = "#F44336", label = "Struggling"),
  quadrantBottomRight = list(color = "#FFEB3B", label = "Offense First"),
  subject = "TEAM",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "top 64", layout = "left", color = "#FF9800"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  # Development mode - write to local file
  dev_output <- "/tmp/cbb_team_efficiency.json"
  write_json(
    output_data, dev_output,
    pretty = TRUE, auto_unbox = TRUE, null = "null"
  )
  cat("\nDevelopment mode - output written to:", dev_output, "\n")
  cat("To upload to S3, set AWS_S3_BUCKET environment variable\n")
} else {
  is_prod <- tolower(Sys.getenv("PROD")) == "true"

  s3_key <- if (is_prod) {
    "cbb__team_efficiency.json"
  } else {
    "dev/cbb__team_efficiency.json"
  }

  # Write JSON to temp file and upload via AWS CLI
  tmp_file <- tempfile(fileext = ".json")
  write_json(
    output_data, tmp_file,
    pretty = TRUE, auto_unbox = TRUE, null = "null"
  )

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
  chart_interval <- "never"

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
    cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
  }
}

cat("\nTotal teams in output:", length(data_points), "\n")
cat("Done.\n")
