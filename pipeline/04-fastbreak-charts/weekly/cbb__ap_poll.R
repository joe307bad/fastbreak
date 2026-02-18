#!/usr/bin/env Rscript

library(jsonlite)
library(dplyr)
library(purrr)

# Get current season (CBB season spans two years, use the ending year)
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
# CBB season runs Nov-April, so if we're in Jan-June, we're in the "current_year" season
# If we're in July-Dec, we're approaching/in the "current_year + 1" season
current_season <- if (current_month <= 6) current_year else current_year + 1

cat("Fetching AP Poll data for season:", current_season, "\n")

# Function to fetch AP poll history from ESPN API
get_ap_poll_history <- function(season = 2026, weeks = 1:18) {
  base_url <- paste0(
    "http://site.api.espn.com/apis/site/v2/sports/",
    "basketball/mens-college-basketball/rankings"
  )

  map_dfr(weeks, function(week) {
    url <- paste0(base_url, "?seasons=", season, "&weeks=", week)
    tryCatch({
      data <- fromJSON(url, flatten = FALSE)

      # Check if rankings exist and have data
      if (is.null(data$rankings) || nrow(data$rankings) == 0) {
        cat("  Week", week, ": No rankings data\n")
        return(NULL)
      }

      # rankings is a data frame, ranks is a list column
      # Get first ranking's ranks (AP Poll is first)
      if (is.null(data$rankings$ranks) || length(data$rankings$ranks) == 0) {
        cat("  Week", week, ": No AP ranks\n")
        return(NULL)
      }

      ranks_df <- data$rankings$ranks[[1]]

      if (is.null(ranks_df) || nrow(ranks_df) == 0) {
        cat("  Week", week, ": Empty ranks\n")
        return(NULL)
      }

      # team is a nested data frame, extract what we need
      result <- tibble(
        current = ranks_df$current,
        previous = ranks_df$previous,
        points = ranks_df$points,
        trend = ranks_df$trend,
        team_id = ranks_df$team$id,
        team_location = ranks_df$team$location,
        team_nickname = ranks_df$team$nickname,
        team_abbr = ranks_df$team$abbreviation,
        week = week,
        season = season
      )

      cat("  Week", week, ": Found", nrow(result), "teams\n")
      result
    }, error = function(e) {
      cat("  Week", week, ": Error -", e$message, "\n")
      NULL
    })
  })
}

# Fetch the past ~15 weeks of AP poll data
poll_history <- get_ap_poll_history(season = current_season, weeks = 1:18)

if (is.null(poll_history) || nrow(poll_history) == 0) {
  stop("No AP poll data found for season ", current_season)
}

cat("\nTotal records fetched:", nrow(poll_history), "\n")

# Get unique weeks for labeling
weeks_available <- sort(unique(poll_history$week))
most_recent_week <- max(weeks_available)

# Determine which weeks to show (last 10 weeks with data)
weeks_to_show <- tail(weeks_available, 10)

cat("Weeks available:", paste(weeks_available, collapse = ", "), "\n")
cat("Showing weeks:", paste(weeks_to_show, collapse = ", "), "\n")

# Filter to weeks we want to display
poll_filtered <- poll_history %>%
  filter(week %in% weeks_to_show)

# Find teams that appear in the most recent week's top 25 (these are our focus teams)
most_recent_teams <- poll_filtered %>%
  filter(week == most_recent_week) %>%
  arrange(current) %>%
  head(25) %>%
  pull(team_location)

cat("\nTop 25 teams in week", most_recent_week, ":\n")
print(most_recent_teams)

# For the chart, we'll show the top 15 teams for readability
top_teams_to_show <- head(most_recent_teams, 15)

# Define distinct colors for each series line
series_colors <- c(
  "#E91E63",  # Pink
  "#2196F3",  # Blue
  "#4CAF50",  # Green
  "#FF9800",  # Orange
  "#9C27B0",  # Purple
  "#00BCD4",  # Cyan
  "#FF5722",  # Deep Orange
  "#795548",  # Brown
  "#607D8B",  # Blue Grey
  "#FFC107",  # Amber
  "#3F51B5",  # Indigo
  "#009688",  # Teal
  "#8BC34A",  # Light Green
  "#CDDC39",  # Lime
  "#F44336"   # Red
)

# Build series data for each team
series_data <- lapply(seq_along(top_teams_to_show), function(i) {
  team_loc <- top_teams_to_show[i]

  team_data <- poll_filtered %>%
    filter(team_location == team_loc) %>%
    arrange(week)

  # Create data points list (negate y so rank 1 appears at top)
  data_points <- lapply(seq_len(nrow(team_data)), function(j) {
    list(
      x = as.numeric(team_data$week[j]),
      y = -as.numeric(team_data$current[j])
    )
  })

  # Get abbreviation for label if available
  team_abbr <- team_data$team_abbr[1]
  label <- if (!is.na(team_abbr) && nchar(team_abbr) > 0) team_abbr else team_loc

  list(
    label = label,
    color = series_colors[i],
    fullName = team_loc,
    dataPoints = data_points
  )
})

# Create output object matching LineChartVisualization model
output_data <- list(
  sport = "CBB",
  visualizationType = "LINE_CHART",
  title = paste("AP Poll Rankings - Week", most_recent_week),
  subtitle = "Top 15 Teams Ranking Trend",
  description = "This chart tracks AP Poll rankings for the top 15 college basketball teams over the past 10 weeks. Higher positions indicate better rankings (1 = best). Rising lines show teams climbing in the rankings, while falling lines show teams dropping. Teams that appear and disappear may have moved in or out of the Top 25.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN API",
  yAxisAbsoluteLabels = TRUE,
  tags = list(
    list(label = "rankings", layout = "left", color = "#4CAF50"),
    list(label = "ap poll", layout = "right", color = "#9C27B0")
  ),
  series = series_data
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "cbb__ap_poll.json"
} else {
  "dev/cbb__ap_poll.json"
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
cat("\nSeries Summary:\n")
for (s in series_data) {
  if (length(s$dataPoints) > 0) {
    latest_rank <- s$dataPoints[[length(s$dataPoints)]]$y
    cat(sprintf("  %s (%s): %d weeks of data, current rank: %d\n",
                s$label,
                s$fullName,
                length(s$dataPoints),
                latest_rank))
  }
}
