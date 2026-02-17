#!/usr/bin/env Rscript

library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)

# Constants
DAYS_AHEAD <- 7

# Helper function to create tied ranks
tied_rank <- function(x) {
  numeric_ranks <- rank(x, ties.method = "min", na.last = "keep")
  rank_counts <- table(numeric_ranks[!is.na(numeric_ranks)])

  display_ranks <- sapply(numeric_ranks, function(r) {
    if (is.na(r)) {
      return(NA_character_)
    }
    if (rank_counts[as.character(r)] > 1) {
      paste0("T", r)
    } else {
      as.character(r)
    }
  })

  return(list(rank = numeric_ranks, rankDisplay = display_ranks))
}

# Helper function for API rate limiting
add_api_delay <- function() {
  Sys.sleep(0.3)
}

cat("=== Loading CBB data ===\n")

# ============================================================================
# STEP 1: Load top teams from sports-reference CSV
# ============================================================================
cat("\n1. Loading top teams from sports-reference CSV...\n")

# Get the script directory to find the CSV
script_dir <- getwd()
csv_path <- file.path(script_dir, "data/cbb/2026/sports-ref-top-teams.csv")

# Read CSV, skipping the first row which contains SRS headers
top_teams_raw <- read.csv(csv_path, skip = 1, stringsAsFactors = FALSE)

# Clean column names
colnames(top_teams_raw) <- c("Rk", "School", "Conf", "X", "AP_Rank", "W", "L", "Pts", "Opp",
                              "MOV", "X1", "SOS", "X2", "OSRS", "DSRS", "SRS",
                              "ORtg", "DRtg", "NRtg")

# Process the data
top_teams <- top_teams_raw %>%
  filter(!is.na(Rk) & Rk != "") %>%
  mutate(
    Rk = as.numeric(Rk),
    AP_Rank = as.numeric(AP_Rank),
    W = as.numeric(W),
    L = as.numeric(L),
    Pts = as.numeric(Pts),
    Opp = as.numeric(Opp),
    MOV = as.numeric(MOV),
    SOS = as.numeric(SOS),
    OSRS = as.numeric(OSRS),
    DSRS = as.numeric(DSRS),
    SRS = as.numeric(SRS),
    ORtg = as.numeric(ORtg),
    DRtg = as.numeric(DRtg),
    NRtg = as.numeric(NRtg)
  ) %>%
  select(Rk, School, Conf, AP_Rank, W, L, Pts, Opp, MOV, SOS, OSRS, DSRS, SRS, ORtg, DRtg, NRtg)

cat("Loaded", nrow(top_teams), "top teams\n")

# Create a list of team names for matching
top_team_names <- tolower(top_teams$School)

# ============================================================================
# STEP 2: Calculate ranks for each stat
# ============================================================================
cat("\n2. Calculating stat ranks...\n")

# Higher is better for these stats
pts_ranks <- tied_rank(-top_teams$Pts)
mov_ranks <- tied_rank(-top_teams$MOV)
sos_ranks <- tied_rank(-top_teams$SOS)
osrs_ranks <- tied_rank(-top_teams$OSRS)
srs_ranks <- tied_rank(-top_teams$SRS)
ortg_ranks <- tied_rank(-top_teams$ORtg)
nrtg_ranks <- tied_rank(-top_teams$NRtg)
wins_ranks <- tied_rank(-top_teams$W)

# Lower is better for these stats
opp_ranks <- tied_rank(top_teams$Opp)
dsrs_ranks <- tied_rank(-top_teams$DSRS)  # Higher DSRS = better defense (negate for ranking)
drtg_ranks <- tied_rank(top_teams$DRtg)  # Lower DRtg = better defense
losses_ranks <- tied_rank(top_teams$L)

top_teams <- top_teams %>%
  mutate(
    # Points per game ranks
    Pts_rank = pts_ranks$rank,
    Pts_rankDisplay = pts_ranks$rankDisplay,

    # Opponent points ranks
    Opp_rank = opp_ranks$rank,
    Opp_rankDisplay = opp_ranks$rankDisplay,

    # Margin of victory ranks
    MOV_rank = mov_ranks$rank,
    MOV_rankDisplay = mov_ranks$rankDisplay,

    # Strength of schedule ranks
    SOS_rank = sos_ranks$rank,
    SOS_rankDisplay = sos_ranks$rankDisplay,

    # Offensive SRS ranks
    OSRS_rank = osrs_ranks$rank,
    OSRS_rankDisplay = osrs_ranks$rankDisplay,

    # Defensive SRS ranks (lower is better)
    DSRS_rank = dsrs_ranks$rank,
    DSRS_rankDisplay = dsrs_ranks$rankDisplay,

    # Overall SRS ranks
    SRS_rank = srs_ranks$rank,
    SRS_rankDisplay = srs_ranks$rankDisplay,

    # Offensive rating ranks
    ORtg_rank = ortg_ranks$rank,
    ORtg_rankDisplay = ortg_ranks$rankDisplay,

    # Defensive rating ranks (lower is better)
    DRtg_rank = drtg_ranks$rank,
    DRtg_rankDisplay = drtg_ranks$rankDisplay,

    # Net rating ranks
    NRtg_rank = nrtg_ranks$rank,
    NRtg_rankDisplay = nrtg_ranks$rankDisplay,

    # Wins ranks
    W_rank = wins_ranks$rank,
    W_rankDisplay = wins_ranks$rankDisplay,

    # Losses ranks (lower is better)
    L_rank = losses_ranks$rank,
    L_rankDisplay = losses_ranks$rankDisplay
  )

# Create lookup for team stats
team_stats_lookup <- split(top_teams, seq(nrow(top_teams)))
names(team_stats_lookup) <- tolower(top_teams$School)

cat("Calculated ranks for all stats\n")

# ============================================================================
# STEP 3: Fetch competitions from ESPN API
# ============================================================================
cat("\n3. Fetching CBB games from ESPN API...\n")

# Build date range for the API
today <- Sys.Date()
end_date <- today + DAYS_AHEAD

# Format dates as YYYYMMDD
start_date_str <- format(today, "%Y%m%d")
end_date_str <- format(end_date, "%Y%m%d")

# ESPN CBB scoreboard API - groups=50 for Division I
scoreboard_url <- paste0(
  "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/scoreboard",
  "?dates=", start_date_str, "-", end_date_str,
  "&groups=50&limit=365"
)

cat("Fetching from:", scoreboard_url, "\n")

scoreboard_resp <- tryCatch({
  GET(scoreboard_url)
}, error = function(e) {
  cat("Error fetching scoreboard:", e$message, "\n")
  NULL
})

if (is.null(scoreboard_resp) || status_code(scoreboard_resp) != 200) {
  cat("Failed to fetch scoreboard data. Status:",
      if (!is.null(scoreboard_resp)) status_code(scoreboard_resp) else "NULL", "\n")
  quit(status = 1)
}

scoreboard_data <- content(scoreboard_resp, as = "parsed")
events <- scoreboard_data$events

if (is.null(events) || length(events) == 0) {
  cat("No games found in the date range\n")
  quit(status = 0)
}

cat("Found", length(events), "total events\n")

# ============================================================================
# STEP 4: Filter competitions involving top teams
# ============================================================================
cat("\n4. Filtering competitions involving top teams...\n")

# Check if ESPN team.location matches any top team School exactly (case-insensitive)
# ESPN's team.location field gives clean school names like "Iowa State", "Duke", "Purdue"
# This avoids issues like "Purdue Fort Wayne" matching "Purdue"
find_matching_top_team <- function(espn_location) {
  if (is.null(espn_location)) return(NULL)

  espn_lower <- tolower(espn_location)

  # Exact match against top team names (from School column)
  if (espn_lower %in% top_team_names) {
    return(espn_lower)
  }

  # Handle a few known variations between ESPN location and sports-reference School
  location_mappings <- list(
    "usc" = "southern california",
    "lsu" = "louisiana state",
    "smu" = "southern methodist",
    "vcu" = "virginia commonwealth",
    "byu" = "brigham young",
    "ucf" = "ucf",
    "unc" = "north carolina",
    "ole miss" = "mississippi"
  )

  if (espn_lower %in% names(location_mappings)) {
    mapped <- location_mappings[[espn_lower]]
    if (mapped %in% top_team_names) {
      return(mapped)
    }
  }

  return(NULL)
}

# Store matchups involving top teams
matchups <- list()

for (event in events) {
  if (is.null(event$competitions) || length(event$competitions) == 0) {
    next
  }

  competition <- event$competitions[[1]]
  teams <- competition$competitors

  if (length(teams) != 2) {
    next
  }

  home_team <- NULL
  away_team <- NULL

  for (team in teams) {
    if (team$homeAway == "home") {
      home_team <- team
    } else {
      away_team <- team
    }
  }

  if (is.null(home_team) || is.null(away_team)) {
    next
  }

  # Check if either team is a top team using team.location for exact matching
  # team.location gives clean school names like "Iowa State", "Purdue" (not "Purdue Fort Wayne")
  home_location <- home_team$team$location
  away_location <- away_team$team$location

  home_top_match <- find_matching_top_team(home_location)
  away_top_match <- find_matching_top_team(away_location)

  # Only include if at least one team is a top team
  if (is.null(home_top_match) && is.null(away_top_match)) {
    next
  }

  # Extract venue information
  location_data <- NULL
  if (!is.null(competition$venue)) {
    venue <- competition$venue
    stadium_name <- if (!is.null(venue$fullName)) venue$fullName else NA
    city <- if (!is.null(venue$address) && !is.null(venue$address$city)) venue$address$city else NA
    state <- if (!is.null(venue$address) && !is.null(venue$address$state)) venue$address$state else NA

    location_parts <- c()
    if (!is.na(stadium_name)) location_parts <- c(location_parts, stadium_name)
    if (!is.na(city)) location_parts <- c(location_parts, city)
    if (!is.na(state)) location_parts <- c(location_parts, state)

    location_data <- list(
      stadium = stadium_name,
      city = city,
      state = state,
      fullLocation = if (length(location_parts) > 0) paste(location_parts, collapse = ", ") else NA
    )
  }

  # Extract odds if available
  odds_data <- NULL
  if (!is.null(competition$odds) && length(competition$odds) > 0) {
    odds <- competition$odds[[1]]

    home_spread <- NA
    if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$spreadOdds)) {
      home_spread <- as.numeric(odds$homeTeamOdds$spreadOdds)
    } else if (!is.null(odds$spread)) {
      home_spread <- as.numeric(odds$spread)
    }

    odds_data <- list(
      provider = if (!is.null(odds$provider)) odds$provider$name else NA,
      spread = home_spread,
      overUnder = if (!is.null(odds$overUnder)) as.numeric(odds$overUnder) else NA,
      homeMoneyline = if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$moneyLine))
        as.numeric(odds$homeTeamOdds$moneyLine) else NA,
      awayMoneyline = if (!is.null(odds$awayTeamOdds) && !is.null(odds$awayTeamOdds$moneyLine))
        as.numeric(odds$awayTeamOdds$moneyLine) else NA
    )
  }

  # Get game date
  game_date <- event$date
  if (grepl("T\\d{2}:\\d{2}Z$", game_date)) {
    game_date <- sub("Z$", ":00Z", game_date)
  }

  matchup_info <- list(
    game_id = event$id,
    game_date = game_date,
    game_name = event$name,
    home_team_id = home_team$team$id,
    home_team_name = home_team$team$displayName,
    home_team_abbrev = if (!is.null(home_team$team$abbreviation)) home_team$team$abbreviation else home_location,
    home_team_logo = if (!is.null(home_team$team$logo)) home_team$team$logo else NA,
    home_top_team_key = home_top_match,
    away_team_id = away_team$team$id,
    away_team_name = away_team$team$displayName,
    away_team_abbrev = if (!is.null(away_team$team$abbreviation)) away_team$team$abbreviation else away_location,
    away_team_logo = if (!is.null(away_team$team$logo)) away_team$team$logo else NA,
    away_top_team_key = away_top_match,
    location = location_data,
    odds = odds_data
  )

  matchups[[length(matchups) + 1]] <- matchup_info
}

cat("Found", length(matchups), "games involving top teams\n")

if (length(matchups) == 0) {
  cat("No games involving top teams found. Exiting.\n")
  quit(status = 0)
}

# ============================================================================
# STEP 5: Build matchup comparison data
# ============================================================================
cat("\n5. Building matchup comparisons...\n")

# Helper function to build team stats object
build_team_stats <- function(team_key, team_name, team_abbrev, team_logo) {
  # If this team is in our top teams, get their stats
  if (!is.null(team_key)) {
    team_data <- team_stats_lookup[[team_key]]
    if (!is.null(team_data) && nrow(team_data) > 0) {
      return(list(
        name = team_data$School,
        abbreviation = team_abbrev,
        logo = team_logo,
        conference = team_data$Conf,
        apRank = if (!is.na(team_data$AP_Rank)) as.integer(team_data$AP_Rank) else NULL,
        srsRank = as.integer(team_data$Rk),
        wins = as.integer(team_data$W),
        losses = as.integer(team_data$L),
        stats = list(
          pointsPerGame = list(
            value = round(team_data$Pts, 1),
            rank = as.integer(team_data$Pts_rank),
            rankDisplay = team_data$Pts_rankDisplay
          ),
          oppPointsPerGame = list(
            value = round(team_data$Opp, 1),
            rank = as.integer(team_data$Opp_rank),
            rankDisplay = team_data$Opp_rankDisplay
          ),
          marginOfVictory = list(
            value = round(team_data$MOV, 2),
            rank = as.integer(team_data$MOV_rank),
            rankDisplay = team_data$MOV_rankDisplay
          ),
          strengthOfSchedule = list(
            value = round(team_data$SOS, 2),
            rank = as.integer(team_data$SOS_rank),
            rankDisplay = team_data$SOS_rankDisplay
          ),
          offensiveSRS = list(
            value = round(team_data$OSRS, 2),
            rank = as.integer(team_data$OSRS_rank),
            rankDisplay = team_data$OSRS_rankDisplay
          ),
          defensiveSRS = list(
            value = round(team_data$DSRS, 2),
            rank = as.integer(team_data$DSRS_rank),
            rankDisplay = team_data$DSRS_rankDisplay
          ),
          srs = list(
            value = round(team_data$SRS, 2),
            rank = as.integer(team_data$SRS_rank),
            rankDisplay = team_data$SRS_rankDisplay
          ),
          offensiveRating = list(
            value = round(team_data$ORtg, 2),
            rank = as.integer(team_data$ORtg_rank),
            rankDisplay = team_data$ORtg_rankDisplay
          ),
          defensiveRating = list(
            value = round(team_data$DRtg, 2),
            rank = as.integer(team_data$DRtg_rank),
            rankDisplay = team_data$DRtg_rankDisplay
          ),
          netRating = list(
            value = round(team_data$NRtg, 2),
            rank = as.integer(team_data$NRtg_rank),
            rankDisplay = team_data$NRtg_rankDisplay
          )
        )
      ))
    }
  }

  # Return basic info for non-top teams
  return(list(
    name = team_name,
    abbreviation = team_abbrev,
    logo = team_logo,
    conference = NULL,
    apRank = NULL,
    srsRank = NULL,
    wins = NULL,
    losses = NULL,
    stats = NULL
  ))
}

# Helper to get stat or null placeholder
get_stat_or_null <- function(stats, stat_name) {
  if (is.null(stats) || is.null(stats[[stat_name]])) {
    return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  }
  return(stats[[stat_name]])
}

# Helper function to build comparison data
build_cbb_comparisons <- function(home_team_data, away_team_data) {
  home_stats <- home_team_data$stats
  away_stats <- away_team_data$stats

  # Side-by-side offensive comparison
  off_comparison <- list(
    pointsPerGame = list(
      label = "Points/Game",
      home = get_stat_or_null(home_stats, "pointsPerGame"),
      away = get_stat_or_null(away_stats, "pointsPerGame")
    ),
    offensiveRating = list(
      label = "Offensive Rating",
      home = get_stat_or_null(home_stats, "offensiveRating"),
      away = get_stat_or_null(away_stats, "offensiveRating")
    ),
    offensiveSRS = list(
      label = "Offensive SRS",
      home = get_stat_or_null(home_stats, "offensiveSRS"),
      away = get_stat_or_null(away_stats, "offensiveSRS")
    ),
    marginOfVictory = list(
      label = "Margin of Victory",
      home = get_stat_or_null(home_stats, "marginOfVictory"),
      away = get_stat_or_null(away_stats, "marginOfVictory")
    ),
    strengthOfSchedule = list(
      label = "Strength of Schedule",
      home = get_stat_or_null(home_stats, "strengthOfSchedule"),
      away = get_stat_or_null(away_stats, "strengthOfSchedule")
    )
  )

  # Side-by-side defensive comparison
  def_comparison <- list(
    oppPointsPerGame = list(
      label = "Opp Points/Game",
      home = get_stat_or_null(home_stats, "oppPointsPerGame"),
      away = get_stat_or_null(away_stats, "oppPointsPerGame")
    ),
    defensiveRating = list(
      label = "Defensive Rating",
      home = get_stat_or_null(home_stats, "defensiveRating"),
      away = get_stat_or_null(away_stats, "defensiveRating")
    ),
    defensiveSRS = list(
      label = "Defensive SRS",
      home = get_stat_or_null(home_stats, "defensiveSRS"),
      away = get_stat_or_null(away_stats, "defensiveSRS")
    )
  )

  # Overall comparison
  overall_comparison <- list(
    srs = list(
      label = "Simple Rating System",
      home = get_stat_or_null(home_stats, "srs"),
      away = get_stat_or_null(away_stats, "srs")
    ),
    netRating = list(
      label = "Net Rating",
      home = get_stat_or_null(home_stats, "netRating"),
      away = get_stat_or_null(away_stats, "netRating")
    ),
    strengthOfSchedule = list(
      label = "Strength of Schedule",
      home = get_stat_or_null(home_stats, "strengthOfSchedule"),
      away = get_stat_or_null(away_stats, "strengthOfSchedule")
    )
  )

  # Helper to calculate advantage (returns 0 if either rank is null)
  calc_advantage <- function(off_rank, def_rank) {
    if (is.null(off_rank) || is.null(def_rank)) return(0)
    if (off_rank < def_rank) return(-1)
    if (off_rank > def_rank) return(1)
    return(0)
  }

  # Get stats with null handling
  home_ppg <- get_stat_or_null(home_stats, "pointsPerGame")
  home_ortg <- get_stat_or_null(home_stats, "offensiveRating")
  home_opp_ppg <- get_stat_or_null(home_stats, "oppPointsPerGame")
  home_drtg <- get_stat_or_null(home_stats, "defensiveRating")

  away_ppg <- get_stat_or_null(away_stats, "pointsPerGame")
  away_ortg <- get_stat_or_null(away_stats, "offensiveRating")
  away_opp_ppg <- get_stat_or_null(away_stats, "oppPointsPerGame")
  away_drtg <- get_stat_or_null(away_stats, "defensiveRating")

  # Home offense vs Away defense
  home_off_vs_away_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame",
      offLabel = "Points/Game",
      defLabel = "Opp Points Allowed",
      offense = list(
        team = home_team_data$abbreviation,
        value = home_ppg$value,
        rank = home_ppg$rank,
        rankDisplay = home_ppg$rankDisplay
      ),
      defense = list(
        team = away_team_data$abbreviation,
        value = away_opp_ppg$value,
        rank = away_opp_ppg$rank,
        rankDisplay = away_opp_ppg$rankDisplay
      ),
      advantage = calc_advantage(home_ppg$rank, away_opp_ppg$rank)
    ),
    rating = list(
      statKey = "rating",
      offLabel = "Offensive Rating",
      defLabel = "Defensive Rating",
      offense = list(
        team = home_team_data$abbreviation,
        value = home_ortg$value,
        rank = home_ortg$rank,
        rankDisplay = home_ortg$rankDisplay
      ),
      defense = list(
        team = away_team_data$abbreviation,
        value = away_drtg$value,
        rank = away_drtg$rank,
        rankDisplay = away_drtg$rankDisplay
      ),
      advantage = calc_advantage(home_ortg$rank, away_drtg$rank)
    )
  )

  # Away offense vs Home defense
  away_off_vs_home_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame",
      offLabel = "Points/Game",
      defLabel = "Opp Points Allowed",
      offense = list(
        team = away_team_data$abbreviation,
        value = away_ppg$value,
        rank = away_ppg$rank,
        rankDisplay = away_ppg$rankDisplay
      ),
      defense = list(
        team = home_team_data$abbreviation,
        value = home_opp_ppg$value,
        rank = home_opp_ppg$rank,
        rankDisplay = home_opp_ppg$rankDisplay
      ),
      advantage = calc_advantage(away_ppg$rank, home_opp_ppg$rank)
    ),
    rating = list(
      statKey = "rating",
      offLabel = "Offensive Rating",
      defLabel = "Defensive Rating",
      offense = list(
        team = away_team_data$abbreviation,
        value = away_ortg$value,
        rank = away_ortg$rank,
        rankDisplay = away_ortg$rankDisplay
      ),
      defense = list(
        team = home_team_data$abbreviation,
        value = home_drtg$value,
        rank = home_drtg$rank,
        rankDisplay = home_drtg$rankDisplay
      ),
      advantage = calc_advantage(away_ortg$rank, home_drtg$rank)
    )
  )

  return(list(
    sideBySide = list(
      offense = off_comparison,
      defense = def_comparison,
      overall = overall_comparison
    ),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  ))
}

# Build matchup JSON
matchups_json <- list()

for (game in matchups) {
  cat("Processing:", game$away_team_abbrev, "@", game$home_team_abbrev, "\n")

  # Build team data
  home_team_data <- build_team_stats(
    game$home_top_team_key,
    game$home_team_name,
    game$home_team_abbrev,
    game$home_team_logo
  )

  away_team_data <- build_team_stats(
    game$away_top_team_key,
    game$away_team_name,
    game$away_team_abbrev,
    game$away_team_logo
  )

  # Build comparisons
  comparisons <- build_cbb_comparisons(home_team_data, away_team_data)

  # Build matchup object
  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = game$game_name,
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    comparisons = comparisons
  )

  # Add location if available
  if (!is.null(game$location)) {
    matchup$location <- game$location
  }

  # Add odds if available
  if (!is.null(game$odds)) {
    matchup$odds <- game$odds
  }

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

# ============================================================================
# STEP 6: Generate output JSON
# ============================================================================
cat("\n6. Generating output JSON...\n")

output_data <- list(
  sport = "CBB",
  visualizationType = "CBB_MATCHUP",
  title = paste0("College Basketball Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("Top team matchups and statistical analysis for the next", DAYS_AHEAD, "days"),
  description = paste0(
    "Matchup statistics for games involving Sports Reference Top 64 teams.\n\n",
    "TEAM STATS:\n\n",
    " - Points Per Game: Average points scored per game\n\n",
    " - Opp Points Per Game: Average points allowed per game\n\n",
    " - Margin of Victory: Average scoring margin\n\n",
    " - Strength of Schedule (SOS): Quality of opponents faced\n\n",
    " - Offensive SRS: Offensive component of Simple Rating System\n\n",
    " - Defensive SRS: Defensive component of Simple Rating System\n\n",
    " - SRS: Simple Rating System (combines MOV and SOS)\n\n",
    " - Offensive Rating: Points scored per 100 possessions\n\n",
    " - Defensive Rating: Points allowed per 100 possessions (lower is better)\n\n",
    " - Net Rating: Offensive Rating - Defensive Rating\n\n",
    "All stats from Sports Reference. Rankings are among Top 64 teams."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN / Sports Reference",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "top 64", layout = "left", color = "#FF9800"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  dataPoints = matchups_json
)

# Write to temp file first
tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

cat("Generated stats for", length(matchups_json), "matchup(s)\n")

# Upload to S3 if in production
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/cbb__matchup_stats.json"

  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)

  if (result != 0) {
    stop("Failed to upload to S3")
  }

  cat("Uploaded to S3:", s3_path, "\n")

  # Update DynamoDB with metadata
  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  chart_title <- paste0("CBB Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d"))
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
    cat("Warning: Failed to update DynamoDB\n")
  } else {
    cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
  }
} else {
  # Development mode - just print the output location and copy to persistent location
  dev_output <- "/tmp/cbb_matchup_test.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
  cat("To upload to S3, set AWS_S3_BUCKET environment variable\n")
}

cat("\n=== CBB Matchup Stats generation complete ===\n")
