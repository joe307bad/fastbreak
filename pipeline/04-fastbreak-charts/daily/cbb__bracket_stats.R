#!/usr/bin/env Rscript

# Source common CBB functions (relative to this script's location)
args <- commandArgs(trailingOnly = FALSE)
script_path <- sub("--file=", "", args[grep("--file=", args)])
script_dir <- normalizePath(dirname(script_path))
source(file.path(script_dir, "cbb__common.R"))

# ============================================================================
# Constants
# ============================================================================
SELECTION_SUNDAY <- as.Date("2026-03-15")  # When bracket is announced
TOURNAMENT_START <- as.Date("2026-03-19")  # First tournament games (Round of 64)
TOURNAMENT_END <- as.Date("2026-04-07")    # Championship game

# S3 key for persisting bracket history across daily runs
HISTORY_S3_KEY <- "dev/cbb__bracket_history.json"

REGION_COLORS <- list(
  East = "#1565C0",
  South = "#2E7D32",
  Midwest = "#F57F17",
  West = "#C62828"
)

ROUND_NAMES <- c("Round of 64", "Round of 32", "Sweet 16", "Elite 8")

# Standard bracket seeding order (1v16, 8v9, 5v12, 4v13, 6v11, 3v14, 7v10, 2v15)
BRACKET_SEED_ORDER <- list(
  c(1, 16), c(8, 9), c(5, 12), c(4, 13),
  c(6, 11), c(3, 14), c(7, 10), c(2, 15)
)

# ============================================================================
# History Persistence Functions
# ============================================================================

#' Load bracket history from S3
#' Returns NULL if no history exists or in development mode
load_bracket_history <- function() {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

  if (!nzchar(s3_bucket)) {
    # Development mode - try to load from local temp file
    local_history <- "/tmp/cbb_bracket_history.json"
    if (file.exists(local_history)) {
      cat("Loading history from local file:", local_history, "\n")
      tryCatch({
        return(fromJSON(local_history, simplifyVector = FALSE))
      }, error = function(e) {
        cat("Warning: Could not parse local history:", e$message, "\n")
        return(NULL)
      })
    }
    return(NULL)
  }

  # Production mode - download from S3
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  tmp_file <- tempfile(fileext = ".json")

  cmd <- paste("aws s3 cp", shQuote(s3_path), shQuote(tmp_file), "2>/dev/null")
  result <- system(cmd, ignore.stdout = TRUE, ignore.stderr = TRUE)

  if (result == 0 && file.exists(tmp_file)) {
    cat("Loaded bracket history from S3\n")
    tryCatch({
      history <- fromJSON(tmp_file, simplifyVector = FALSE)
      unlink(tmp_file)
      return(history)
    }, error = function(e) {
      cat("Warning: Could not parse S3 history:", e$message, "\n")
      unlink(tmp_file)
      return(NULL)
    })
  }

  cat("No existing bracket history found\n")
  return(NULL)
}

#' Save bracket history to S3
#' @param history List containing completed games indexed by gameId
save_bracket_history <- function(history) {
  s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

  tmp_file <- tempfile(fileext = ".json")
  write_json(history, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

  if (!nzchar(s3_bucket)) {
    # Development mode - save locally
    local_history <- "/tmp/cbb_bracket_history.json"
    file.copy(tmp_file, local_history, overwrite = TRUE)
    cat("Saved history to local file:", local_history, "\n")
    unlink(tmp_file)
    return(TRUE)
  }

  # Production mode - upload to S3
  s3_path <- paste0("s3://", s3_bucket, "/", HISTORY_S3_KEY)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  unlink(tmp_file)

  if (result == 0) {
    cat("Saved bracket history to S3:", s3_path, "\n")
    return(TRUE)
  } else {
    cat("Warning: Failed to save bracket history to S3\n")
    return(FALSE)
  }
}

#' Parse region and round from ESPN notes headline
#' @param headline String like "Men's Basketball Championship - East Region - 1st Round"
#' @return List with region and round fields
parse_bracket_info <- function(headline) {
  if (is.null(headline) || headline == "") {
    return(list(region = "Unknown", round = "Unknown", roundNumber = 0))
  }

  # Extract region
  region <- "Finals"  # Default for Final Four / Championship
  if (grepl("East Region", headline, ignore.case = TRUE)) region <- "East"
  else if (grepl("South Region", headline, ignore.case = TRUE)) region <- "South"
  else if (grepl("Midwest Region", headline, ignore.case = TRUE)) region <- "Midwest"
  else if (grepl("West Region", headline, ignore.case = TRUE)) region <- "West"

  # Extract round
  round <- "Unknown"
  round_number <- 0
  if (grepl("1st Round", headline, ignore.case = TRUE)) {
    round <- "Round of 64"
    round_number <- 1
  } else if (grepl("2nd Round", headline, ignore.case = TRUE)) {
    round <- "Round of 32"
    round_number <- 2
  } else if (grepl("Sweet 16", headline, ignore.case = TRUE)) {
    round <- "Sweet 16"
    round_number <- 3
  } else if (grepl("Elite 8|Elite Eight", headline, ignore.case = TRUE)) {
    round <- "Elite 8"
    round_number <- 4
  } else if (grepl("Final Four", headline, ignore.case = TRUE)) {
    round <- "Final Four"
    round_number <- 5
  } else if (grepl("National Championship|Championship", headline, ignore.case = TRUE)) {
    round <- "Championship"
    round_number <- 6
  }

  return(list(region = region, round = round, roundNumber = round_number))
}

cat("=== NCAA Bracket Stats Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")

# ============================================================================
# STEP 1: Load CBB data using common functions
# ============================================================================
cat("\n1. Loading CBB data...\n")

data_dir <- file.path(dirname(script_dir), "data/cbb/2026")
cbb_data <- load_cbb_data(data_dir)
combined_data <- cbb_data$combined_data
team_stats_lookup <- cbb_data$team_stats_lookup

# ============================================================================
# STEP 2: Determine tournament status
# ============================================================================
cat("\n2. Determining tournament status...\n")

today <- Sys.Date()
is_pre_selection <- today < SELECTION_SUNDAY  # Before bracket is announced
is_bracket_announced <- today >= SELECTION_SUNDAY  # Bracket is out, use ESPN data
is_tournament_active <- today >= TOURNAMENT_START && today <= TOURNAMENT_END
is_tournament_complete <- today > TOURNAMENT_END

if (is_pre_selection) {
  tournament_status <- "PROJECTED"
  cat("Selection Sunday has not happened. Building projected bracket from top 64 teams.\n")
} else if (is_tournament_active) {
  tournament_status <- "IN_PROGRESS"
  cat("Tournament is active. Fetching live bracket data from ESPN.\n")
} else if (is_bracket_announced && !is_tournament_complete) {
  tournament_status <- "IN_PROGRESS"  # Bracket announced but games not started yet
  cat("Bracket announced. Fetching actual tournament bracket from ESPN.\n")
} else {
  tournament_status <- "COMPLETED"
  cat("Tournament is complete.\n")
}

# ============================================================================
# Tournament Game Processing Functions
# ============================================================================

#' Process a single tournament game from ESPN event data
#' @param event ESPN event object
#' @param team_stats_lookup Named list of team statistics
#' @return Processed game object or NULL
process_tournament_game <- function(event, team_stats_lookup) {
  if (length(event$competitions) == 0) return(NULL)

  competition <- event$competitions[[1]]
  teams <- competition$competitors
  if (length(teams) != 2) return(NULL)

  # Parse region and round from notes
  notes <- competition$notes
  headline <- if (length(notes) > 0 && !is.null(notes[[1]]$headline)) notes[[1]]$headline else ""
  bracket_info <- parse_bracket_info(headline)

  # Determine game status
  game_status <- "SCHEDULED"
  if (!is.null(competition$status$type$name)) {
    status_name <- competition$status$type$name
    if (status_name == "STATUS_FINAL") {
      game_status <- "FINAL"
    } else if (status_name == "STATUS_IN_PROGRESS") {
      game_status <- "IN_PROGRESS"
    }
  }

  team1 <- teams[[1]]
  team2 <- teams[[2]]

  # Get team info
  team1_name <- team1$team$displayName
  team2_name <- team2$team$displayName
  team1_abbrev <- if (!is.null(team1$team$abbreviation)) team1$team$abbreviation else substr(team1_name, 1, 4)
  team2_abbrev <- if (!is.null(team2$team$abbreviation)) team2$team$abbreviation else substr(team2_name, 1, 4)
  team1_logo <- if (!is.null(team1$team$logo)) team1$team$logo else NULL
  team2_logo <- if (!is.null(team2$team$logo)) team2$team$logo else NULL
  team1_seed <- if (!is.null(team1$curatedRank$current)) as.integer(team1$curatedRank$current) else 0
  team2_seed <- if (!is.null(team2$curatedRank$current)) as.integer(team2$curatedRank$current) else 0

  team1_key <- find_team_key(team1_name, team_stats_lookup)
  team2_key <- find_team_key(team2_name, team_stats_lookup)

  team1_data <- build_team_stats(team1_key, team1_name, team1_abbrev, team1_logo, team1_seed, team_stats_lookup)
  team2_data <- build_team_stats(team2_key, team2_name, team2_abbrev, team2_logo, team2_seed, team_stats_lookup)

  # Add scores for completed/in-progress games
  if (game_status %in% c("FINAL", "IN_PROGRESS")) {
    team1_data$score <- as.integer(team1$score)
    team2_data$score <- as.integer(team2$score)
    if (game_status == "FINAL") {
      team1_data$isWinner <- isTRUE(team1$winner)
      team2_data$isWinner <- isTRUE(team2$winner)
    }
  }

  comparisons <- build_comparisons(team1_data, team2_data)

  # Build box score for completed games
  box_score <- NULL
  if (game_status == "FINAL") {
    box_score <- fetch_game_box_score(event$id)
  }

  # Build game object
  game_date <- event$date
  if (grepl("T\\d{2}:\\d{2}Z$", game_date)) game_date <- sub("Z$", ":00Z", game_date)

  game <- list(
    gameId = event$id,
    gameNumber = as.integer(gsub("[^0-9]", "", event$id)) %% 100,
    gameDate = game_date,
    gameStatus = game_status,
    region = bracket_info$region,
    round = bracket_info$round,
    roundNumber = bracket_info$roundNumber,
    team1 = team1_data,
    team2 = team2_data,
    winner = if (game_status == "FINAL") {
      if (isTRUE(team1_data$isWinner)) team1_data$name else team2_data$name
    } else NULL,
    winnerSeed = if (game_status == "FINAL") {
      if (isTRUE(team1_data$isWinner)) team1_data$seed else team2_data$seed
    } else NULL,
    location = NULL,
    odds = NULL,
    boxScore = box_score,
    comparisons = comparisons
  )

  # Add location if available
  if (!is.null(competition$venue)) {
    venue <- competition$venue
    game$location <- list(
      stadium = if (!is.null(venue$fullName)) venue$fullName else NULL,
      city = if (!is.null(venue$address) && !is.null(venue$address$city)) venue$address$city else NULL,
      state = if (!is.null(venue$address) && !is.null(venue$address$state)) venue$address$state else NULL
    )
  }

  return(game)
}

#' Fetch box score for a completed game
#' @param game_id ESPN game ID
#' @return Box score object or NULL
fetch_game_box_score <- function(game_id) {
  summary_url <- paste0(
    "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/summary?event=",
    game_id
  )

  Sys.sleep(0.5)  # Rate limiting

  summary_resp <- tryCatch({
    GET(summary_url)
  }, error = function(e) NULL)

  if (is.null(summary_resp) || status_code(summary_resp) != 200) {
    return(NULL)
  }

  summary_data <- content(summary_resp, as = "parsed")

  if (is.null(summary_data$boxscore) || is.null(summary_data$boxscore$teams)) {
    return(NULL)
  }

  bs_teams <- summary_data$boxscore$teams
  if (length(bs_teams) < 2) return(NULL)

  parse_team_box <- function(team_bs) {
    stats <- team_bs$statistics
    get_stat <- function(name) {
      for (s in stats) {
        if (s$name == name) return(as.numeric(s$displayValue))
      }
      return(0)
    }
    list(
      points = get_stat("points"),
      fgm = get_stat("fieldGoalsMade"),
      fga = get_stat("fieldGoalsAttempted"),
      fgPct = get_stat("fieldGoalPct"),
      fg3m = get_stat("threePointFieldGoalsMade"),
      fg3a = get_stat("threePointFieldGoalsAttempted"),
      fg3Pct = get_stat("threePointFieldGoalPct"),
      ftm = get_stat("freeThrowsMade"),
      fta = get_stat("freeThrowsAttempted"),
      ftPct = get_stat("freeThrowPct"),
      rebounds = get_stat("totalRebounds"),
      assists = get_stat("assists"),
      steals = get_stat("steals"),
      blocks = get_stat("blocks"),
      turnovers = get_stat("turnovers")
    )
  }

  list(
    team1 = parse_team_box(bs_teams[[1]]),
    team2 = parse_team_box(bs_teams[[2]])
  )
}

#' Organize all games into bracket structure by region and round
#' @param all_games Named list of games indexed by gameId
#' @return List of regions with rounds containing games
organize_bracket_data <- function(all_games) {
  # Initialize regions structure
  regions_data <- list()
  for (region_name in c("East", "South", "Midwest", "West")) {
    regions_data[[region_name]] <- list(
      name = region_name,
      colorHex = REGION_COLORS[[region_name]],
      rounds = list(
        list(roundNumber = 1, roundName = "Round of 64", games = list()),
        list(roundNumber = 2, roundName = "Round of 32", games = list()),
        list(roundNumber = 3, roundName = "Sweet 16", games = list()),
        list(roundNumber = 4, roundName = "Elite 8", games = list())
      )
    )
  }

  # Distribute games to regions and rounds
  for (game_id in names(all_games)) {
    game <- all_games[[game_id]]

    region <- game$region
    round_num <- game$roundNumber

    # Skip Final Four and Championship (handled separately)
    if (region == "Finals" || round_num > 4) next

    # Skip unknown region/round
    if (is.null(region) || region == "Unknown" || round_num == 0) next

    # Add to appropriate region/round
    if (!is.null(regions_data[[region]])) {
      current_games <- regions_data[[region]]$rounds[[round_num]]$games
      regions_data[[region]]$rounds[[round_num]]$games[[length(current_games) + 1]] <- game
    }
  }

  return(regions_data)
}

#' Build Final Four section from completed Elite 8 games
#' @param all_games All tournament games
#' @return Final Four data structure
build_final_four <- function(all_games) {
  final_four_games <- list()
  championship_game <- NULL

  for (game_id in names(all_games)) {
    game <- all_games[[game_id]]

    if (!is.null(game$round) && game$round == "Final Four") {
      final_four_games[[length(final_four_games) + 1]] <- game
    } else if (!is.null(game$round) && game$round == "Championship") {
      championship_game <- game
    }
  }

  # Determine semifinal matchups
  semifinal1 <- if (length(final_four_games) >= 1) final_four_games[[1]] else NULL
  semifinal2 <- if (length(final_four_games) >= 2) final_four_games[[2]] else NULL

  # Project Final Four if Elite 8 is complete but Final Four games not yet scheduled
  if (is.null(semifinal1) || is.null(semifinal2)) {
    elite_8_winners <- list()

    for (game_id in names(all_games)) {
      game <- all_games[[game_id]]
      if (!is.null(game$round) && game$round == "Elite 8" && game$gameStatus == "FINAL") {
        winner_data <- if (isTRUE(game$team1$isWinner)) game$team1 else game$team2
        elite_8_winners[[game$region]] <- winner_data
      }
    }

    # Traditional Final Four pairings: East vs West, South vs Midwest
    if (length(elite_8_winners) >= 2) {
      if (!is.null(elite_8_winners$East) && !is.null(elite_8_winners$West) && is.null(semifinal1)) {
        semifinal1 <- list(
          gameId = "projected_ff_1",
          gameStatus = "PROJECTED",
          round = "Final Four",
          roundNumber = 5,
          team1 = elite_8_winners$East,
          team2 = elite_8_winners$West,
          comparisons = build_comparisons(elite_8_winners$East, elite_8_winners$West)
        )
      }
      if (!is.null(elite_8_winners$South) && !is.null(elite_8_winners$Midwest) && is.null(semifinal2)) {
        semifinal2 <- list(
          gameId = "projected_ff_2",
          gameStatus = "PROJECTED",
          round = "Final Four",
          roundNumber = 5,
          team1 = elite_8_winners$South,
          team2 = elite_8_winners$Midwest,
          comparisons = build_comparisons(elite_8_winners$South, elite_8_winners$Midwest)
        )
      }
    }
  }

  list(
    semifinal1 = semifinal1,
    semifinal2 = semifinal2,
    championship = championship_game
  )
}

# ============================================================================
# STEP 3: Build projected bracket (pre-selection) or fetch actual bracket from ESPN
# ============================================================================

if (is_pre_selection) {
  cat("\n3. Building projected bracket from top 64 teams by SRS...\n")

  # Get top 64 teams by SRS (Simple Rating System)
  top_64 <- combined_data %>%
    arrange(SRS_rank) %>%
    head(64)

  cat("Top 64 teams selected\n")

  # Distribute teams into 4 regions with seeds 1-16
  # Top 4 = #1 seeds, next 4 = #2 seeds, etc.
  region_names <- c("East", "South", "Midwest", "West")

  regions_data <- list()

  for (region_idx in 1:4) {
    region_name <- region_names[region_idx]
    cat("Building", region_name, "region...\n")

    # Get the 16 teams for this region
    # Teams are distributed: ranks 1,5,9,13... go to East, 2,6,10,14... to South, etc.
    region_team_indices <- seq(region_idx, 64, by = 4)
    region_teams <- top_64[region_team_indices, ]

    # Assign seeds 1-16 to these teams based on their order
    region_teams$seed <- 1:16

    # Build Round of 64 games using standard bracket seeding
    round_1_games <- list()
    game_number <- (region_idx - 1) * 8 + 1

    for (matchup in BRACKET_SEED_ORDER) {
      seed1 <- matchup[1]
      seed2 <- matchup[2]

      team1_row <- region_teams[region_teams$seed == seed1, ]
      team2_row <- region_teams[region_teams$seed == seed2, ]

      team1_key <- tolower(team1_row$School)
      team2_key <- tolower(team2_row$School)

      # Build abbreviated names (first 4 chars uppercase)
      team1_abbrev <- toupper(substr(gsub("[^A-Za-z]", "", team1_row$School), 1, 4))
      team2_abbrev <- toupper(substr(gsub("[^A-Za-z]", "", team2_row$School), 1, 4))

      team1_data <- build_team_stats(team1_key, team1_row$School, team1_abbrev, NULL, seed1, team_stats_lookup)
      team2_data <- build_team_stats(team2_key, team2_row$School, team2_abbrev, NULL, seed2, team_stats_lookup)
      comparisons <- build_comparisons(team1_data, team2_data)

      game <- list(
        gameId = paste0("projected_", region_idx, "_", game_number),
        gameNumber = game_number,
        gameDate = NULL,
        gameStatus = "PROJECTED",
        team1 = team1_data,
        team2 = team2_data,
        location = NULL,
        odds = NULL,
        boxScore = NULL,
        comparisons = comparisons
      )

      round_1_games[[length(round_1_games) + 1]] <- game
      game_number <- game_number + 1
    }

    # Build empty placeholder rounds for Round of 32, Sweet 16, Elite 8
    round_2_games <- list()
    round_3_games <- list()
    round_4_games <- list()

    for (i in 1:4) {
      round_2_games[[i]] <- list(
        gameId = paste0("projected_r2_", region_idx, "_", i),
        gameNumber = 32 + (region_idx - 1) * 4 + i,
        gameDate = NULL,
        gameStatus = "TBD",
        team1 = NULL,
        team2 = NULL,
        location = NULL,
        odds = NULL,
        boxScore = NULL,
        comparisons = NULL
      )
    }

    for (i in 1:2) {
      round_3_games[[i]] <- list(
        gameId = paste0("projected_r3_", region_idx, "_", i),
        gameNumber = 48 + (region_idx - 1) * 2 + i,
        gameDate = NULL,
        gameStatus = "TBD",
        team1 = NULL,
        team2 = NULL,
        location = NULL,
        odds = NULL,
        boxScore = NULL,
        comparisons = NULL
      )
    }

    round_4_games[[1]] <- list(
      gameId = paste0("projected_r4_", region_idx),
      gameNumber = 56 + region_idx,
      gameDate = NULL,
      gameStatus = "TBD",
      team1 = NULL,
      team2 = NULL,
      location = NULL,
      odds = NULL,
      boxScore = NULL,
      comparisons = NULL
    )

    regions_data[[region_name]] <- list(
      name = region_name,
      colorHex = REGION_COLORS[[region_name]],
      rounds = list(
        list(roundNumber = 1, roundName = "Round of 64", games = round_1_games),
        list(roundNumber = 2, roundName = "Round of 32", games = round_2_games),
        list(roundNumber = 3, roundName = "Sweet 16", games = round_3_games),
        list(roundNumber = 4, roundName = "Elite 8", games = round_4_games)
      )
    )
  }

  # Final Four placeholder
  final_four <- list(
    semifinal1 = list(
      gameId = "projected_ff_1",
      gameNumber = 61,
      gameDate = NULL,
      gameStatus = "TBD",
      team1 = NULL,
      team2 = NULL,
      location = NULL,
      odds = NULL,
      boxScore = NULL,
      comparisons = NULL
    ),
    semifinal2 = list(
      gameId = "projected_ff_2",
      gameNumber = 62,
      gameDate = NULL,
      gameStatus = "TBD",
      team1 = NULL,
      team2 = NULL,
      location = NULL,
      odds = NULL,
      boxScore = NULL,
      comparisons = NULL
    ),
    championship = list(
      gameId = "projected_champ",
      gameNumber = 63,
      gameDate = NULL,
      gameStatus = "TBD",
      team1 = NULL,
      team2 = NULL,
      location = NULL,
      odds = NULL,
      boxScore = NULL,
      comparisons = NULL
    )
  )

  title <- "NCAA Bracket (Projected)"
  subtitle <- "Top 64 teams by SRS"

} else {
  # Tournament is active or complete - fetch from ESPN with history persistence
  cat("\n3. Fetching tournament bracket from ESPN with history persistence...\n")

  # Load existing history
  bracket_history <- load_bracket_history()
  if (is.null(bracket_history)) {
    bracket_history <- list(
      games = list(),           # Keyed by gameId
      lastUpdated = NULL,
      tournamentYear = 2026
    )
  }

  cat("History contains", length(bracket_history$games), "completed games\n")

  all_games <- list()  # Will contain all games indexed by gameId

  # Determine date range to fetch
  # After Selection Sunday, always fetch at least the first round dates to get bracket structure
  # Also fetch future dates to get scheduled games (Sweet 16, Elite 8, etc.) that have matchups set
  fetch_start <- TOURNAMENT_START
  # Always fetch the full tournament date range to capture scheduled future games
  fetch_end <- TOURNAMENT_END

  # Fetch games for each day from tournament start to fetch_end
  current_date <- fetch_start
  while (current_date <= fetch_end) {
    date_str <- format(current_date, "%Y%m%d")
    cat("Fetching games for", format(current_date, "%Y-%m-%d"), "...\n")

    scoreboard_url <- paste0(
      "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/scoreboard",
      "?groups=100&dates=", date_str
    )

    Sys.sleep(0.3)  # Rate limiting

    scoreboard_resp <- tryCatch({
      GET(scoreboard_url)
    }, error = function(e) {
      cat("Error fetching date", date_str, ":", e$message, "\n")
      NULL
    })

    if (!is.null(scoreboard_resp) && status_code(scoreboard_resp) == 200) {
      scoreboard_data <- content(scoreboard_resp, as = "parsed")

      if (!is.null(scoreboard_data$events)) {
        for (event in scoreboard_data$events) {
          game_id <- event$id

          # Check if already in history and game is complete - use cached data but recompute stats
          if (!is.null(bracket_history$games[[game_id]]) &&
              bracket_history$games[[game_id]]$gameStatus == "FINAL") {
            cat("  Using cached data for game", game_id, "\n")
            cached_game <- bracket_history$games[[game_id]]

            # Recompute team stats and comparisons using current lookup
            team1_key <- find_team_key(cached_game$team1$name, team_stats_lookup)
            team2_key <- find_team_key(cached_game$team2$name, team_stats_lookup)

            cached_game$team1 <- build_team_stats(
              team1_key, cached_game$team1$name, cached_game$team1$abbreviation,
              cached_game$team1$logo, cached_game$team1$seed, team_stats_lookup
            )
            cached_game$team2 <- build_team_stats(
              team2_key, cached_game$team2$name, cached_game$team2$abbreviation,
              cached_game$team2$logo, cached_game$team2$seed, team_stats_lookup
            )

            # Restore score and winner info from cache
            cached_game$team1$score <- bracket_history$games[[game_id]]$team1$score
            cached_game$team2$score <- bracket_history$games[[game_id]]$team2$score
            cached_game$team1$isWinner <- bracket_history$games[[game_id]]$team1$isWinner
            cached_game$team2$isWinner <- bracket_history$games[[game_id]]$team2$isWinner

            cached_game$comparisons <- build_comparisons(cached_game$team1, cached_game$team2)

            all_games[[game_id]] <- cached_game
            next
          }

          # Process this game fresh
          game_data <- process_tournament_game(event, team_stats_lookup)
          if (!is.null(game_data)) {
            all_games[[game_id]] <- game_data

            # Update history if game is complete
            if (game_data$gameStatus == "FINAL") {
              cat("  Saving completed game", game_id, "to history\n")
              bracket_history$games[[game_id]] <- game_data
            }
          }
        }
      }
    }

    current_date <- current_date + 1
  }

  cat("Total games processed:", length(all_games), "\n")

  # Save updated history
  bracket_history$lastUpdated <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  save_bracket_history(bracket_history)

  # Organize games into regions and rounds
  regions_data <- organize_bracket_data(all_games)

  # Build Final Four section
  final_four <- build_final_four(all_games)

  title <- "2026 NCAA Tournament Bracket"
  if (is_tournament_complete) {
    subtitle <- "Tournament Complete"
  } else {
    subtitle <- format(today, "%B %d, %Y")
  }
}

# ============================================================================
# STEP 4: Generate output JSON
# ============================================================================
cat("\n4. Generating output JSON...\n")

output_data <- list(
  sport = "CBB",
  visualizationType = "NCAA_BRACKET",
  title = title,
  subtitle = subtitle,
  description = paste0(
    "NCAA Tournament bracket with team statistics.\n\n",
    "Each matchup includes:\n",
    "- Team seeds, records, and AP rankings\n",
    "- Offensive stats: PPG, FG%, 3P%, eFG%, TS%, assists, turnovers\n",
    "- Defensive stats: Opp PPG, Opp FG%, steals, blocks\n",
    "- Overall: SRS, Net Rating, Strength of Schedule\n\n",
    "Stats from Sports Reference. Rankings among all D1 teams."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN / Sports Reference",
  tags = list(
    list(label = "tournament", layout = "left", color = "#4CAF50"),
    list(label = "march madness", layout = "left", color = "#FF9800")
  ),
  sortOrder = -1,
  season = 2026,
  tournamentStatus = tournament_status,
  regions = unname(regions_data),
  finalFour = final_four
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

cat("Generated bracket data\n")

# ============================================================================
# STEP 5: Upload to S3 and update DynamoDB
# ============================================================================
cat("\n5. Uploading to S3...\n")

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/cbb__bracket_stats.json"
  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  if (result != 0) stop("Failed to upload to S3")
  cat("Uploaded to S3:", s3_path, "\n")

  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  dynamodb_item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
    s3_key, utc_timestamp, title
  )
  dynamodb_cmd <- sprintf('aws dynamodb put-item --table-name %s --item %s', shQuote(dynamodb_table), shQuote(dynamodb_item))
  dynamodb_result <- system(dynamodb_cmd)
  if (dynamodb_result != 0) cat("Warning: Failed to update DynamoDB\n")
  else cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
} else {
  dev_output <- "/tmp/cbb_bracket_stats.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
}

cat("\n=== NCAA Bracket Stats generation complete ===\n")
