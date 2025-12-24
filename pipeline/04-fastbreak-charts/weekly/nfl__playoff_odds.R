library(rvest)
library(dplyr)
library(jsonlite)
library(nflreadr)

# Load team info for division data and create name mapping
teams_info <- nflreadr::load_teams() %>%
  select(team_abbr, team_nick, team_division)

# Create mapping from team nickname to abbreviation
# The playoff status website uses team nicknames (e.g., "Seahawks", "Patriots")
team_name_to_abbr <- setNames(teams_info$team_abbr, teams_info$team_nick)

# URL for playoff probabilities
url <- "https://www.playoffstatus.com/nfl/nflpostseasonprob.html"

cat("Scraping NFL Playoff Odds from:", url, "\n")

# Fetch and parse the page
page <- tryCatch({
  read_html(url)
}, error = function(e) {
  cat("Error fetching page:", e$message, "\n")
  stop(e)
})

# Find all tables on the page
tables <- page %>% html_nodes("table")
cat("Found", length(tables), "tables on page\n")

# The main data table is typically the largest one with team data
# Look for table with team names and percentages
playoff_data <- NULL

for (i in seq_along(tables)) {
  tbl <- tryCatch({
    tables[[i]] %>% html_table(fill = TRUE)
  }, error = function(e) {
    NULL
  })

  if (!is.null(tbl) && nrow(tbl) > 5) {
    # Check if this looks like the playoff odds table
    col_names <- tolower(names(tbl))
    if (any(grepl("team|sb|super|playoff|wild|div", col_names))) {
      cat("Found playoff table at index", i, "with", nrow(tbl), "rows\n")
      playoff_data <- tbl
      break
    }
  }
}

# If we couldn't find by column names, try to find the largest table
if (is.null(playoff_data)) {
  cat("Searching for largest table with numeric data...\n")
  for (i in seq_along(tables)) {
    tbl <- tryCatch({
      tables[[i]] %>% html_table(fill = TRUE)
    }, error = function(e) {
      NULL
    })

    if (!is.null(tbl) && nrow(tbl) > 10) {
      # Check if it has percentage-like values
      if (any(grepl("%|[0-9]+", as.character(tbl[[1]])))) {
        cat("Using table at index", i, "with", nrow(tbl), "rows\n")
        playoff_data <- tbl
        break
      }
    }
  }
}

if (is.null(playoff_data)) {
  stop("Could not find playoff probability table on page")
}

cat("\nTable columns:", paste(names(playoff_data), collapse = ", "), "\n")
cat("Table preview:\n")
print(head(playoff_data, 3))

# Convert to data frame and give columns simple names
playoff_data <- as.data.frame(playoff_data)
names(playoff_data) <- paste0("col", 1:ncol(playoff_data))

cat("\nRenamed columns:", paste(names(playoff_data), collapse = ", "), "\n")

# Check if first row contains header text (like "Team", "Conference", etc.)
first_row <- as.character(playoff_data[1, ])
if (any(grepl("^Team$|^Conference|^W$|^L$|^T$", first_row, ignore.case = TRUE))) {
  cat("First row appears to be headers, skipping it\n")
  playoff_data <- playoff_data[-1, ]
}

# Based on the observed structure:
# col1 = Team name
# col2 = Conference (AFC/NFC)
# col3,4,5 = W, L, T (record parts)
# col6 = Super Bowl Winner %
# col7 = Super Bowl %
# col8 = Conference Championship %
# col9 = Round 2 (Divisional) %
# col10 = Round 1 (Wild Card/Playoff) %

cat("\nData after header removal:\n")
print(head(playoff_data, 3))

# Clean percentage values - remove % and convert to numeric
clean_pct <- function(x) {
  x <- gsub("%", "", as.character(x))
  x <- gsub("X|x|-|^$", "0", x)  # X or - or empty means eliminated
  x <- trimws(x)
  suppressWarnings(as.numeric(x))
}

# Build cleaned dataframe based on column positions
cleaned_data <- data.frame(
  team = as.character(playoff_data$col1),
  conference = as.character(playoff_data$col2),
  stringsAsFactors = FALSE
)

# Build record from W-L-T columns if they exist
if (ncol(playoff_data) >= 5) {
  cleaned_data$record <- paste0(
    playoff_data$col3, "-",
    playoff_data$col4, "-",
    playoff_data$col5
  )
}

# Get percentage columns (adjust indices based on actual structure)
if (ncol(playoff_data) >= 10) {
  cleaned_data$super_bowl <- clean_pct(playoff_data$col7)
  cleaned_data$conf_champ <- clean_pct(playoff_data$col8)
  cleaned_data$divisional <- clean_pct(playoff_data$col9)
  cleaned_data$make_playoffs <- clean_pct(playoff_data$col10)
} else if (ncol(playoff_data) >= 7) {
  # Fallback for different column layouts
  cleaned_data$super_bowl <- clean_pct(playoff_data$col6)
  cleaned_data$conf_champ <- clean_pct(playoff_data$col7)
  cleaned_data$divisional <- clean_pct(playoff_data[[ncol(playoff_data) - 1]])
  cleaned_data$make_playoffs <- clean_pct(playoff_data[[ncol(playoff_data)]])
}

# Filter out empty rows, convert team names to abbreviations, join with division data, and sort by playoff odds
cleaned_data <- cleaned_data %>%
  filter(!is.na(team) & team != "" & !grepl("^\\s*$", team)) %>%
  filter(!grepl("^Team$|^Conference$|^Division$", team, ignore.case = TRUE)) %>%
  mutate(team_abbr = team_name_to_abbr[team]) %>%
  mutate(team_abbr = ifelse(is.na(team_abbr), team, team_abbr)) %>%  # Fallback to original if not found
  left_join(teams_info, by = "team_abbr") %>%
  arrange(desc(make_playoffs))

cat("\nCleaned data preview:\n")
print(head(cleaned_data, 10))

# Get current week from the page title or default
page_title <- page %>% html_node("title") %>% html_text()
week_match <- regmatches(page_title, regexpr("Week\\s*\\d+", page_title, ignore.case = TRUE))
current_week <- if (length(week_match) > 0) week_match else "Current"
cat("\nWeek:", current_week, "\n")

# Build table data points for JSON output
data_points <- lapply(1:nrow(cleaned_data), function(i) {
  row <- cleaned_data[i, ]

  columns <- list()

  if (!is.null(row$conference) && nzchar(row$conference)) {
    columns <- c(columns, list(list(label = "Conf", value = row$conference)))
  }

  if (!is.null(row$team_division) && !is.na(row$team_division) && nzchar(row$team_division)) {
    columns <- c(columns, list(list(label = "Division", value = row$team_division)))
  }

  if (!is.null(row$record) && nzchar(row$record)) {
    columns <- c(columns, list(list(label = "Record", value = row$record)))
  }

  if (!is.null(row$make_playoffs) && !is.na(row$make_playoffs)) {
    columns <- c(columns, list(list(label = "Playoff %", value = paste0(row$make_playoffs, "%"))))
  }

  if (!is.null(row$divisional) && !is.na(row$divisional)) {
    columns <- c(columns, list(list(label = "Div Round %", value = paste0(row$divisional, "%"))))
  }

  if (!is.null(row$conf_champ) && !is.na(row$conf_champ)) {
    columns <- c(columns, list(list(label = "Conf Champ %", value = paste0(row$conf_champ, "%"))))
  }

  if (!is.null(row$super_bowl) && !is.na(row$super_bowl)) {
    columns <- c(columns, list(list(label = "Super Bowl %", value = paste0(row$super_bowl, "%"))))
  }

  list(
    label = row$team_abbr,
    columns = columns
  )
})

# Create output object matching TableVisualization model
output_data <- list(
  sport = "NFL",
  visualizationType = "TABLE",
  title = paste("Playoff Odds -", current_week),
  subtitle = "Probability of reaching each playoff round",
  description = "This table shows each team's probability of making the playoffs and advancing through each round. Data is sourced from PlayoffStatus.com which calculates odds based on current standings, remaining schedule, and historical performance patterns.",
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "PlayoffStatus.com",
  dataPoints = data_points
)

# Upload to S3
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (!nzchar(s3_bucket)) {
  stop("AWS_S3_BUCKET environment variable is not set")
}

is_prod <- tolower(Sys.getenv("PROD")) == "true"

s3_key <- if (is_prod) {
  "nfl__playoff_odds.json"
} else {
  "dev/nfl__playoff_odds.json"
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
cat("\nTable Summary:\n")
cat(sprintf("  Total teams: %d\n", nrow(cleaned_data)))
cat(sprintf("  Teams with >50%% playoff odds: %d\n", sum(cleaned_data$make_playoffs > 50, na.rm = TRUE)))
if (nrow(cleaned_data) > 0) {
  cat(sprintf("  Highest playoff odds: %s (%.0f%%)\n", cleaned_data$team_abbr[1], cleaned_data$make_playoffs[1]))
}
