#!/usr/bin/env Rscript

library(httr)
library(dplyr)
library(tidyr)
library(jsonlite)
library(lubridate)

# Script runs in production mode by default

# Constants
MIN_GAMES_PLAYED <- 10
CURRENT_YEAR <- as.numeric(format(Sys.Date(), "%Y"))
CURRENT_MONTH <- as.numeric(format(Sys.Date(), "%m"))

# NHL season starts in October
# NHL API expects season format like 20242025
NHL_SEASON_END <- if (CURRENT_MONTH >= 10) CURRENT_YEAR + 1 else CURRENT_YEAR
NHL_SEASON_START <- NHL_SEASON_END - 1
NHL_SEASON_ID <- paste0(NHL_SEASON_START, NHL_SEASON_END)
NHL_SEASON_STRING <- paste0(NHL_SEASON_START, "-", substr(NHL_SEASON_END, 3, 4))

# Number of days to look ahead for matchups
DAYS_AHEAD <- 7

# Number of days to look behind for completed games with results
DAYS_BEHIND <- 3

# Maximum number of completed games to fetch full results for (rate limiting)
MAX_RESULTS_GAMES <- 50

# ============================================================================
# TIMING UTILITIES
# ============================================================================
script_start_time <- Sys.time()
step_timings <- list()

# Helper function to format duration nicely
format_duration <- function(duration) {
  secs <- as.numeric(duration, units = "secs")
  if (secs < 60) {
    sprintf("%.1f seconds", secs)
  } else if (secs < 3600) {
    sprintf("%.1f minutes (%.0f seconds)", secs / 60, secs)
  } else {
    sprintf("%.2f hours (%.0f minutes)", secs / 3600, secs / 60)
  }
}

# Helper function to start timing a step
start_timer <- function(step_name) {
  assign("current_step_name", step_name, envir = .GlobalEnv)
  assign("current_step_start", Sys.time(), envir = .GlobalEnv)
  cat("\n[TIMER] Starting:", step_name, "\n")
}

# Helper function to end timing a step
end_timer <- function() {
  if (exists("current_step_start", envir = .GlobalEnv)) {
    duration <- Sys.time() - get("current_step_start", envir = .GlobalEnv)
    step_name <- get("current_step_name", envir = .GlobalEnv)
    step_timings[[step_name]] <<- duration
    cat("[TIMER] Completed:", step_name, "in", format_duration(duration), "\n")
  }
}

# Helper function to safely check if a value is valid (not NA, NULL, or zero-length)
is_valid_value <- function(x) {
  !is.null(x) && length(x) > 0 && !is.na(x[1])
}

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

# NHL team abbreviation mapping
TEAM_ABBREVS <- c(
  "Anaheim Ducks" = "ANA", "Arizona Coyotes" = "ARI", "Boston Bruins" = "BOS",
  "Buffalo Sabres" = "BUF", "Calgary Flames" = "CGY", "Carolina Hurricanes" = "CAR",
  "Chicago Blackhawks" = "CHI", "Colorado Avalanche" = "COL", "Columbus Blue Jackets" = "CBJ",
  "Dallas Stars" = "DAL", "Detroit Red Wings" = "DET", "Edmonton Oilers" = "EDM",
  "Florida Panthers" = "FLA", "Los Angeles Kings" = "LAK", "Minnesota Wild" = "MIN",
  "Montreal Canadiens" = "MTL", "Montréal Canadiens" = "MTL", "Nashville Predators" = "NSH",
  "New Jersey Devils" = "NJD", "New York Islanders" = "NYI", "New York Rangers" = "NYR",
  "Ottawa Senators" = "OTT", "Philadelphia Flyers" = "PHI", "Pittsburgh Penguins" = "PIT",
  "San Jose Sharks" = "SJS", "Seattle Kraken" = "SEA", "St. Louis Blues" = "STL",
  "Tampa Bay Lightning" = "TBL", "Toronto Maple Leafs" = "TOR",
  "Utah Hockey Club" = "UTA", "Utah Mammoth" = "UTA",
  "Vancouver Canucks" = "VAN", "Vegas Golden Knights" = "VGK", "Washington Capitals" = "WSH",
  "Winnipeg Jets" = "WPG"
)

# Reverse mapping: abbreviation to full name
ABBREV_TO_NAME <- setNames(names(TEAM_ABBREVS), TEAM_ABBREVS)

# NHL division and conference mapping
TEAM_DIVISIONS <- c(
  "ANA" = "Pacific", "ARI" = "Central", "BOS" = "Atlantic",
  "BUF" = "Atlantic", "CGY" = "Pacific", "CAR" = "Metropolitan",
  "CHI" = "Central", "COL" = "Central", "CBJ" = "Metropolitan",
  "DAL" = "Central", "DET" = "Atlantic", "EDM" = "Pacific",
  "FLA" = "Atlantic", "LAK" = "Pacific", "MIN" = "Central",
  "MTL" = "Atlantic", "NSH" = "Central", "NJD" = "Metropolitan",
  "NYI" = "Metropolitan", "NYR" = "Metropolitan", "OTT" = "Atlantic",
  "PHI" = "Metropolitan", "PIT" = "Metropolitan", "SJS" = "Pacific",
  "SEA" = "Pacific", "STL" = "Central", "TBL" = "Atlantic",
  "TOR" = "Atlantic", "UTA" = "Central", "VAN" = "Pacific",
  "VGK" = "Pacific", "WSH" = "Metropolitan", "WPG" = "Central"
)

TEAM_CONFERENCES <- c(
  "ANA" = "Western", "ARI" = "Western", "BOS" = "Eastern",
  "BUF" = "Eastern", "CGY" = "Western", "CAR" = "Eastern",
  "CHI" = "Western", "COL" = "Western", "CBJ" = "Eastern",
  "DAL" = "Western", "DET" = "Eastern", "EDM" = "Western",
  "FLA" = "Eastern", "LAK" = "Western", "MIN" = "Western",
  "MTL" = "Eastern", "NSH" = "Western", "NJD" = "Eastern",
  "NYI" = "Eastern", "NYR" = "Eastern", "OTT" = "Eastern",
  "PHI" = "Eastern", "PIT" = "Eastern", "SJS" = "Western",
  "SEA" = "Western", "STL" = "Western", "TBL" = "Eastern",
  "TOR" = "Eastern", "UTA" = "Western", "VAN" = "Western",
  "VGK" = "Western", "WSH" = "Eastern", "WPG" = "Western"
)

# Helper function to compare game stats to season averages
compare_to_season_avg <- function(game_value, season_avg, stat_name, higher_is_better = TRUE) {
  if (is.na(game_value) || is.na(season_avg) || is.null(game_value) || is.null(season_avg)) {
    return(list(
      gameValue = if (!is.null(game_value) && !is.na(game_value)) round(game_value, 2) else NULL,
      seasonAvg = if (!is.null(season_avg) && !is.na(season_avg)) round(season_avg, 2) else NULL,
      difference = NULL,
      percentDiff = NULL,
      aboveAverage = NULL,
      label = NULL
    ))
  }

  diff <- game_value - season_avg
  pct_diff <- if (season_avg != 0) (diff / season_avg) * 100 else 0
  above_avg <- if (higher_is_better) diff > 0 else diff < 0

  label <- if (abs(pct_diff) < 5) {
    "near average"
  } else if (above_avg) {
    "above average"
  } else {
    "below average"
  }

  list(
    gameValue = round(game_value, 2),
    seasonAvg = round(season_avg, 2),
    difference = round(diff, 2),
    percentDiff = round(pct_diff, 1),
    aboveAverage = above_avg,
    label = label
  )
}

# Helper function to fetch game boxscore from NHL API
fetch_nhl_game_boxscore <- function(game_id) {
  add_api_delay()

  tryCatch({
    boxscore_url <- sprintf("https://api-web.nhle.com/v1/gamecenter/%s/boxscore", game_id)
    response <- GET(boxscore_url)

    if (status_code(response) != 200) {
      cat("Warning: Could not fetch boxscore for game", game_id, "(status:", status_code(response), ")\n")
      return(list(success = FALSE))
    }

    boxscore_data <- fromJSON(content(response, "text", encoding = "UTF-8"), flatten = TRUE)
    list(data = boxscore_data, success = TRUE)
  }, error = function(e) {
    cat("Warning: Could not fetch boxscore for game", game_id, ":", e$message, "\n")
    list(success = FALSE, error = e$message)
  })
}

# Helper function to build results data for a completed game
build_game_results <- function(game, home_season_stats, away_season_stats) {
  game_id <- game$game_id

  cat("  Fetching results for game", game_id, "...\n")

  # Determine winner
  home_won <- game$home_score > game$away_score
  winner <- if (home_won) game$home_team_abbrev else game$away_team_abbrev

  # Helper functions for safe value extraction
  safe_int <- function(x) if (length(x) > 0 && !is.na(x[1])) as.integer(x[1]) else NULL
  safe_num <- function(x) if (length(x) > 0 && !is.na(x[1])) as.numeric(x[1]) else NULL

  # Build basic result
  result <- list(
    finalScore = list(
      home = game$home_score,
      away = game$away_score,
      winner = winner,
      margin = abs(game$home_score - game$away_score),
      homeWon = home_won
    )
  )

  # Fetch boxscore data from NHL API
  boxscore <- fetch_nhl_game_boxscore(game_id)

  if (boxscore$success && !is.null(boxscore$data)) {
    data <- boxscore$data

    # Initialize boxscore stats storage
    home_box <- list(goals = game$home_score)
    away_box <- list(goals = game$away_score)

    # Extract SOG directly from team objects
    if (!is.null(data$homeTeam$sog)) {
      home_box$sog <- safe_int(data$homeTeam$sog)
    }
    if (!is.null(data$awayTeam$sog)) {
      away_box$sog <- safe_int(data$awayTeam$sog)
    }

    # Aggregate player stats from playerByGameStats
    aggregate_player_stats <- function(team_stats) {
      total_hits <- 0
      total_pim <- 0
      total_blocks <- 0
      total_pp_goals <- 0
      total_giveaways <- 0
      total_takeaways <- 0
      total_faceoff_wins <- 0
      total_faceoffs_taken <- 0

      # Process forwards, defense (skaters have these stats)
      for (group in c("forwards", "defense")) {
        players <- team_stats[[group]]
        if (!is.null(players) && length(players) > 0) {
          if (is.data.frame(players)) {
            for (i in seq_len(nrow(players))) {
              p <- players[i, ]
              if (!is.null(p$hits) && !is.na(p$hits)) total_hits <- total_hits + as.integer(p$hits)
              if (!is.null(p$pim) && !is.na(p$pim)) total_pim <- total_pim + as.integer(p$pim)
              if (!is.null(p$blockedShots) && !is.na(p$blockedShots)) total_blocks <- total_blocks + as.integer(p$blockedShots)
              if (!is.null(p$powerPlayGoals) && !is.na(p$powerPlayGoals)) total_pp_goals <- total_pp_goals + as.integer(p$powerPlayGoals)
              if (!is.null(p$giveaways) && !is.na(p$giveaways)) total_giveaways <- total_giveaways + as.integer(p$giveaways)
              if (!is.null(p$takeaways) && !is.na(p$takeaways)) total_takeaways <- total_takeaways + as.integer(p$takeaways)
              # Faceoff stats - estimate faceoffs from TOI and position (centers take most faceoffs)
              if (!is.null(p$faceoffWinningPctg) && !is.na(p$faceoffWinningPctg) && p$faceoffWinningPctg > 0) {
                # Estimate faceoffs taken based on TOI (rough approximation)
                # A center playing 20 min might take ~15-20 faceoffs
                toi_str <- p$toi
                if (!is.null(toi_str) && !is.na(toi_str)) {
                  toi_parts <- strsplit(as.character(toi_str), ":")[[1]]
                  if (length(toi_parts) == 2) {
                    toi_mins <- as.numeric(toi_parts[1]) + as.numeric(toi_parts[2]) / 60
                    estimated_faceoffs <- max(1, round(toi_mins * 0.8))  # Rough estimate
                    total_faceoff_wins <- total_faceoff_wins + (p$faceoffWinningPctg * estimated_faceoffs)
                    total_faceoffs_taken <- total_faceoffs_taken + estimated_faceoffs
                  }
                }
              }
            }
          } else if (is.list(players)) {
            for (p in players) {
              if (!is.null(p$hits) && !is.na(p$hits)) total_hits <- total_hits + as.integer(p$hits)
              if (!is.null(p$pim) && !is.na(p$pim)) total_pim <- total_pim + as.integer(p$pim)
              if (!is.null(p$blockedShots) && !is.na(p$blockedShots)) total_blocks <- total_blocks + as.integer(p$blockedShots)
              if (!is.null(p$powerPlayGoals) && !is.na(p$powerPlayGoals)) total_pp_goals <- total_pp_goals + as.integer(p$powerPlayGoals)
              if (!is.null(p$giveaways) && !is.na(p$giveaways)) total_giveaways <- total_giveaways + as.integer(p$giveaways)
              if (!is.null(p$takeaways) && !is.na(p$takeaways)) total_takeaways <- total_takeaways + as.integer(p$takeaways)
            }
          }
        }
      }

      # Process goalies for PIM and goalie stats
      goalies <- team_stats$goalies
      goalie_saves <- 0
      goalie_shots_against <- 0
      goalie_save_pct <- NULL
      goalie_goals_against <- 0

      if (!is.null(goalies) && length(goalies) > 0) {
        if (is.data.frame(goalies)) {
          for (i in seq_len(nrow(goalies))) {
            g <- goalies[i, ]
            if (!is.null(g$pim) && !is.na(g$pim)) total_pim <- total_pim + as.integer(g$pim)
            if (!is.null(g$saves) && !is.na(g$saves)) goalie_saves <- goalie_saves + as.integer(g$saves)
            if (!is.null(g$shotsAgainst) && !is.na(g$shotsAgainst)) goalie_shots_against <- goalie_shots_against + as.integer(g$shotsAgainst)
            if (!is.null(g$goalsAgainst) && !is.na(g$goalsAgainst)) goalie_goals_against <- goalie_goals_against + as.integer(g$goalsAgainst)
            # Use starter's save percentage
            if (!is.null(g$starter) && g$starter == TRUE && !is.null(g$savePctg) && !is.na(g$savePctg)) {
              goalie_save_pct <- round(as.numeric(g$savePctg), 3)
            }
          }
        } else if (is.list(goalies)) {
          for (g in goalies) {
            if (!is.null(g$pim) && !is.na(g$pim)) total_pim <- total_pim + as.integer(g$pim)
            if (!is.null(g$saves) && !is.na(g$saves)) goalie_saves <- goalie_saves + as.integer(g$saves)
            if (!is.null(g$shotsAgainst) && !is.na(g$shotsAgainst)) goalie_shots_against <- goalie_shots_against + as.integer(g$shotsAgainst)
            if (!is.null(g$goalsAgainst) && !is.na(g$goalsAgainst)) goalie_goals_against <- goalie_goals_against + as.integer(g$goalsAgainst)
            if (!is.null(g$starter) && g$starter == TRUE && !is.null(g$savePctg) && !is.na(g$savePctg)) {
              goalie_save_pct <- round(as.numeric(g$savePctg), 3)
            }
          }
        }
      }

      # Calculate team faceoff percentage
      team_faceoff_pct <- if (total_faceoffs_taken > 0) round(total_faceoff_wins / total_faceoffs_taken, 3) else NULL

      list(
        hits = total_hits,
        pim = total_pim,
        blocks = total_blocks,
        powerPlayGoals = total_pp_goals,
        giveaways = total_giveaways,
        takeaways = total_takeaways,
        faceoffWinPct = team_faceoff_pct,
        saves = goalie_saves,
        shotsAgainst = goalie_shots_against,
        savePct = goalie_save_pct,
        goalsAgainst = goalie_goals_against
      )
    }

    # Aggregate stats from player data
    if (!is.null(data$playerByGameStats)) {
      if (!is.null(data$playerByGameStats$homeTeam)) {
        home_agg <- aggregate_player_stats(data$playerByGameStats$homeTeam)
        home_box$hits <- home_agg$hits
        home_box$pim <- home_agg$pim
        home_box$blocks <- home_agg$blocks
        home_box$powerPlayGoals <- home_agg$powerPlayGoals
        home_box$giveaways <- home_agg$giveaways
        home_box$takeaways <- home_agg$takeaways
        home_box$faceoffWinPct <- home_agg$faceoffWinPct
        home_box$saves <- home_agg$saves
        home_box$savePct <- home_agg$savePct
      }
      if (!is.null(data$playerByGameStats$awayTeam)) {
        away_agg <- aggregate_player_stats(data$playerByGameStats$awayTeam)
        away_box$hits <- away_agg$hits
        away_box$pim <- away_agg$pim
        away_box$blocks <- away_agg$blocks
        away_box$powerPlayGoals <- away_agg$powerPlayGoals
        away_box$giveaways <- away_agg$giveaways
        away_box$takeaways <- away_agg$takeaways
        away_box$faceoffWinPct <- away_agg$faceoffWinPct
        away_box$saves <- away_agg$saves
        away_box$savePct <- away_agg$savePct
      }
    }

    # Build the teamBoxScore result
    result$teamBoxScore <- list(
      home = home_box,
      away = away_box
    )

    # Compare to season averages
    result$vsSeasonAvg <- list(
      home = list(
        goals = compare_to_season_avg(game$home_score, home_season_stats$goals_per_game, "goals"),
        goalsAgainst = compare_to_season_avg(game$away_score, home_season_stats$goals_against_per_game, "goalsAgainst", higher_is_better = FALSE)
      ),
      away = list(
        goals = compare_to_season_avg(game$away_score, away_season_stats$goals_per_game, "goals"),
        goalsAgainst = compare_to_season_avg(game$home_score, away_season_stats$goals_against_per_game, "goalsAgainst", higher_is_better = FALSE)
      )
    )

    # Extract period scores if available
    if (!is.null(data$periodDescriptor) || !is.null(data$summary)) {
      # Try to get period-by-period scoring from summary
      if (!is.null(data$summary) && !is.null(data$summary$linescore) && !is.null(data$summary$linescore$byPeriod)) {
        periods <- data$summary$linescore$byPeriod
        result$periodScores <- lapply(seq_along(periods), function(i) {
          p <- periods[[i]]
          list(
            period = i,
            home = safe_int(p$home),
            away = safe_int(p$away)
          )
        })
      }
    }
  }

  return(result)
}

cat("=== Loading NHL data for", NHL_SEASON_STRING, "season ===\n")
cat("[TIMER] Script started at:", format(script_start_time, "%Y-%m-%d %H:%M:%S"), "\n")

# ============================================================================
# STEP 1: Load team stats from NHL API
# ============================================================================
start_timer("STEP 1: Load team stats")
cat("\n1. Loading team stats from NHL API...\n")

team_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/team/summary?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    NHL_SEASON_ID
  )
  cat("Fetching from NHL API:", api_url, "\n")

  response <- GET(api_url)
  if (status_code(response) != 200) {
    stop(sprintf("NHL API returned status %d", status_code(response)))
  }

  result <- fromJSON(content(response, "text", encoding = "UTF-8"))
  if (is.null(result$data) || nrow(result$data) == 0) {
    stop("NHL API returned empty data")
  }
  result$data
}, error = function(e) {
  cat("Error loading team stats:", e$message, "\n")
  stop(e)
})

cat("Loaded stats for", nrow(team_stats), "teams\n")

# Process team stats
team_stats <- team_stats %>%
  mutate(
    team_id = teamId,
    team_name = teamFullName,
    team_abbreviation = TEAM_ABBREVS[teamFullName],
    games_played = as.numeric(gamesPlayed),
    wins = as.numeric(wins),
    losses = as.numeric(losses),
    ot_losses = as.numeric(otLosses),
    points = as.numeric(points),
    points_pct = as.numeric(pointPct),
    goals_for = as.numeric(goalsFor),
    goals_against = as.numeric(goalsAgainst),
    goals_per_game = as.numeric(goalsForPerGame),
    goals_against_per_game = as.numeric(goalsAgainstPerGame),
    goal_diff_per_game = goals_per_game - goals_against_per_game,
    shots_for_per_game = as.numeric(shotsForPerGame),
    shots_against_per_game = as.numeric(shotsAgainstPerGame),
    faceoff_win_pct = as.numeric(faceoffWinPct),
    power_play_pct = as.numeric(powerPlayPct),
    penalty_kill_pct = as.numeric(penaltyKillPct),
    division = TEAM_DIVISIONS[team_abbreviation],
    conference = TEAM_CONFERENCES[team_abbreviation]
  ) %>%
  filter(!is.na(team_abbreviation))

cat("Processed stats for", nrow(team_stats), "teams\n")

# Calculate ranks for team stats
cat("Calculating team stat ranks...\n")

# Offensive ranks (higher is better)
gpg_ranks <- tied_rank(-team_stats$goals_per_game)
sfpg_ranks <- tied_rank(-team_stats$shots_for_per_game)
pp_pct_ranks <- tied_rank(-team_stats$power_play_pct)
faceoff_ranks <- tied_rank(-team_stats$faceoff_win_pct)

# Defensive ranks (lower is better for goals against)
gapg_ranks <- tied_rank(team_stats$goals_against_per_game)
sapg_ranks <- tied_rank(team_stats$shots_against_per_game)
pk_pct_ranks <- tied_rank(-team_stats$penalty_kill_pct)

# Overall ranks
pts_pct_ranks <- tied_rank(-team_stats$points_pct)
goal_diff_ranks <- tied_rank(-team_stats$goal_diff_per_game)

team_stats <- team_stats %>%
  mutate(
    goals_per_game_rank = gpg_ranks$rank,
    goals_per_game_rankDisplay = gpg_ranks$rankDisplay,
    shots_for_per_game_rank = sfpg_ranks$rank,
    shots_for_per_game_rankDisplay = sfpg_ranks$rankDisplay,
    power_play_pct_rank = pp_pct_ranks$rank,
    power_play_pct_rankDisplay = pp_pct_ranks$rankDisplay,
    faceoff_win_pct_rank = faceoff_ranks$rank,
    faceoff_win_pct_rankDisplay = faceoff_ranks$rankDisplay,
    goals_against_per_game_rank = gapg_ranks$rank,
    goals_against_per_game_rankDisplay = gapg_ranks$rankDisplay,
    shots_against_per_game_rank = sapg_ranks$rank,
    shots_against_per_game_rankDisplay = sapg_ranks$rankDisplay,
    penalty_kill_pct_rank = pk_pct_ranks$rank,
    penalty_kill_pct_rankDisplay = pk_pct_ranks$rankDisplay,
    points_pct_rank = pts_pct_ranks$rank,
    points_pct_rankDisplay = pts_pct_ranks$rankDisplay,
    goal_diff_per_game_rank = goal_diff_ranks$rank,
    goal_diff_per_game_rankDisplay = goal_diff_ranks$rankDisplay
  )

end_timer()

# ============================================================================
# STEP 1b: Calculate 1-month trend rankings (last 4 weeks)
# ============================================================================
start_timer("STEP 1b: Fetch recent game data for trends")
cat("\n1b. Fetching recent game data for 1-month trends...\n")

# Calculate date range for last month (approximately 4 weeks)
today <- Sys.Date()
month_start_date <- today - days(28)

# Fetch team game-by-game stats for trend calculation
# Use the NHL API game log endpoint
month_games_data <- tryCatch({
  # Get game results from schedule API for the past month
  trend_games <- list()

  # Iterate through the date range to get games
  current_date <- month_start_date
  while (current_date <= today) {
    date_string <- format(current_date, "%Y-%m-%d")

    add_api_delay()
    schedule_url <- sprintf("https://api-web.nhle.com/v1/schedule/%s", date_string)

    schedule_resp <- tryCatch({
      GET(schedule_url)
    }, error = function(e) {
      NULL
    })

    if (!is.null(schedule_resp) && status_code(schedule_resp) == 200) {
      # Use flatten = TRUE to get a proper data frame structure
      schedule_data <- fromJSON(content(schedule_resp, "text", encoding = "UTF-8"), flatten = TRUE)

      if (!is.null(schedule_data$gameWeek) && is.data.frame(schedule_data$gameWeek) && nrow(schedule_data$gameWeek) > 0) {
        game_week_df <- schedule_data$gameWeek

        for (day_idx in seq_len(nrow(game_week_df))) {
          day_date <- game_week_df$date[day_idx]
          day_games <- game_week_df$games[[day_idx]]

          # Skip if no games for this day
          if (is.null(day_games) || length(day_games) == 0) next

          # day_games could be a data frame or a list
          if (is.data.frame(day_games)) {
            for (game_idx in seq_len(nrow(day_games))) {
              game <- day_games[game_idx, ]

              game_state <- game$gameState
              if (is.null(game_state) || is.na(game_state)) next

              if (game_state %in% c("OFF", "FINAL")) {
                home_abbrev <- game$homeTeam.abbrev
                away_abbrev <- game$awayTeam.abbrev

                if (is.null(home_abbrev) || is.null(away_abbrev)) next

                game_info <- list(
                  game_id = game$id,
                  game_date = day_date,
                  home_team_abbrev = home_abbrev,
                  away_team_abbrev = away_abbrev,
                  home_score = as.integer(game$homeTeam.score),
                  away_score = as.integer(game$awayTeam.score)
                )
                trend_games[[length(trend_games) + 1]] <- game_info
              }
            }
          }
        }
      }
    }

    current_date <- current_date + days(7)  # Move by week to reduce API calls
  }

  if (length(trend_games) > 0) {
    bind_rows(trend_games)
  } else {
    NULL
  }
}, error = function(e) {
  cat("Warning: Could not fetch recent games for trends:", e$message, "\n")
  NULL
})

# Calculate month trend stats if we have game data
month_trend_stats <- NULL
if (!is.null(month_games_data) && nrow(month_games_data) > 0) {
  cat("Loaded", nrow(month_games_data), "recent games for trend analysis\n")

  # Build per-team stats from games
  home_stats <- month_games_data %>%
    group_by(team_abbreviation = home_team_abbrev) %>%
    summarise(
      games = n(),
      wins = sum(home_score > away_score, na.rm = TRUE),
      losses = sum(home_score < away_score, na.rm = TRUE),
      goals_for = sum(home_score, na.rm = TRUE),
      goals_against = sum(away_score, na.rm = TRUE),
      .groups = "drop"
    )

  away_stats <- month_games_data %>%
    group_by(team_abbreviation = away_team_abbrev) %>%
    summarise(
      games = n(),
      wins = sum(away_score > home_score, na.rm = TRUE),
      losses = sum(away_score < home_score, na.rm = TRUE),
      goals_for = sum(away_score, na.rm = TRUE),
      goals_against = sum(home_score, na.rm = TRUE),
      .groups = "drop"
    )

  # Combine home and away stats
  month_trend_stats <- bind_rows(home_stats, away_stats) %>%
    group_by(team_abbreviation) %>%
    summarise(
      games_played = sum(games, na.rm = TRUE),
      wins = sum(wins, na.rm = TRUE),
      losses = sum(losses, na.rm = TRUE),
      goals_for = sum(goals_for, na.rm = TRUE),
      goals_against = sum(goals_against, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    mutate(
      goals_per_game = goals_for / games_played,
      goals_against_per_game = goals_against / games_played,
      goal_diff_per_game = goals_per_game - goals_against_per_game,
      win_pct = wins / (wins + losses)
    )

  # Calculate trend rankings
  month_gpg_ranks <- tied_rank(-month_trend_stats$goals_per_game)
  month_gapg_ranks <- tied_rank(month_trend_stats$goals_against_per_game)
  month_diff_ranks <- tied_rank(-month_trend_stats$goal_diff_per_game)
  month_record_ranks <- tied_rank(-month_trend_stats$win_pct)

  month_trend_stats <- month_trend_stats %>%
    mutate(
      gpg_rank = month_gpg_ranks$rank,
      gpg_rankDisplay = month_gpg_ranks$rankDisplay,
      gapg_rank = month_gapg_ranks$rank,
      gapg_rankDisplay = month_gapg_ranks$rankDisplay,
      goal_diff_rank = month_diff_ranks$rank,
      goal_diff_rankDisplay = month_diff_ranks$rankDisplay,
      record_rank = month_record_ranks$rank,
      record_rankDisplay = month_record_ranks$rankDisplay
    )

  cat("Calculated month trend rankings for", nrow(month_trend_stats), "teams\n")
} else {
  cat("No recent game data available for trend analysis\n")
}

end_timer()

# ============================================================================
# STEP 2: Load player stats
# ============================================================================
start_timer("STEP 2: Load player stats")
cat("\n2. Loading player stats from NHL API...\n")

skater_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/skater/summary?isAggregate=false&isGame=false&limit=-1&cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    NHL_SEASON_ID
  )
  cat("Fetching skater stats from NHL API...\n")

  response <- GET(api_url)
  if (status_code(response) != 200) {
    stop(sprintf("NHL API returned status %d", status_code(response)))
  }

  result <- fromJSON(content(response, "text", encoding = "UTF-8"))
  if (is.null(result$data) || nrow(result$data) == 0) {
    stop("NHL API returned empty data")
  }
  result$data
}, error = function(e) {
  cat("Error loading skater stats:", e$message, "\n")
  NULL
})

if (!is.null(skater_stats)) {
  cat("Loaded stats for", nrow(skater_stats), "skaters\n")

  # Process player stats
  player_stats <- skater_stats %>%
    mutate(
      player_id = playerId,
      player_name = skaterFullName,
      team_abbreviation = teamAbbrevs,
      position = positionCode,
      games_played = as.numeric(gamesPlayed),
      goals = as.numeric(goals),
      assists = as.numeric(assists),
      points = as.numeric(points),
      plus_minus = as.numeric(plusMinus),
      pim = as.numeric(penaltyMinutes),
      shots = as.numeric(shots),
      shooting_pct = as.numeric(shootingPct),
      points_per_game = as.numeric(pointsPerGame),
      time_on_ice_per_game = as.numeric(timeOnIcePerGame)
    ) %>%
    filter(games_played >= MIN_GAMES_PLAYED)

  cat("Filtered to", nrow(player_stats), "players with", MIN_GAMES_PLAYED, "+ games\n")

  # Calculate player ranks
  player_pts_ranks <- tied_rank(-player_stats$points)
  player_goals_ranks <- tied_rank(-player_stats$goals)
  player_assists_ranks <- tied_rank(-player_stats$assists)
  player_pm_ranks <- tied_rank(-player_stats$plus_minus)

  player_stats <- player_stats %>%
    mutate(
      points_rank = player_pts_ranks$rank,
      points_rankDisplay = player_pts_ranks$rankDisplay,
      goals_rank = player_goals_ranks$rank,
      goals_rankDisplay = player_goals_ranks$rankDisplay,
      assists_rank = player_assists_ranks$rank,
      assists_rankDisplay = player_assists_ranks$rankDisplay,
      plus_minus_rank = player_pm_ranks$rank,
      plus_minus_rankDisplay = player_pm_ranks$rankDisplay
    )
} else {
  player_stats <- NULL
}

end_timer()

# ============================================================================
# STEP 3: Get team standings
# ============================================================================
start_timer("STEP 3: Fetch team standings")
cat("\n3. Fetching team standings...\n")

standings_data <- tryCatch({
  standings_url <- "https://api-web.nhle.com/v1/standings/now"
  add_api_delay()

  response <- GET(standings_url)
  if (status_code(response) != 200) {
    stop(sprintf("NHL API returned status %d", status_code(response)))
  }

  result <- fromJSON(content(response, "text", encoding = "UTF-8"), flatten = TRUE)
  result$standings
}, error = function(e) {
  cat("Warning: Could not fetch standings:", e$message, "\n")
  NULL
})

if (!is.null(standings_data) && nrow(standings_data) > 0) {
  cat("Loaded standings for", nrow(standings_data), "teams\n")

  # Process standings
  standings <- standings_data %>%
    mutate(
      team_abbreviation = teamAbbrev.default,
      conference_rank = conferenceSequence,
      division_rank = divisionSequence,
      league_rank = leagueSequence,
      streak_code = streakCode,
      streak_count = streakCount,
      last_10_wins = l10Wins,
      last_10_losses = l10Losses,
      last_10_ot_losses = l10OtLosses
    ) %>%
    select(team_abbreviation, conference_rank, division_rank, league_rank,
           streak_code, streak_count, last_10_wins, last_10_losses, last_10_ot_losses)

  # Join standings with team stats
  team_stats <- team_stats %>%
    left_join(standings, by = "team_abbreviation")

  cat("Merged standings with team stats\n")
}

end_timer()

# ============================================================================
# STEP 4: Get games (past DAYS_BEHIND days + next DAYS_AHEAD days)
# ============================================================================
start_timer("STEP 4: Fetch games schedule")
cat("\n4. Fetching NHL games for past", DAYS_BEHIND, "days and next", DAYS_AHEAD, "days...\n")

# Get today's date and calculate date range
start_date <- today - days(DAYS_BEHIND)
end_date <- today + days(DAYS_AHEAD)

# Fetch schedule from NHL API
# Optimization: Each API call returns ~1 week of games, so we only need 2 calls
# to cover our 11-day range (3 days back + 7 days ahead)
all_games <- list()
seen_game_ids <- character(0)  # Track game IDs to avoid duplicates

# Make 2 API calls: one at the start of range, one at the end
schedule_dates <- c(format(start_date, "%Y-%m-%d"), format(end_date, "%Y-%m-%d"))

for (date_string in schedule_dates) {

  add_api_delay()
  schedule_url <- sprintf("https://api-web.nhle.com/v1/schedule/%s", date_string)

  cat("Fetching games for week of", date_string, "...\n")

  schedule_resp <- tryCatch({
    GET(schedule_url)
  }, error = function(e) {
    cat("Error fetching schedule for", date_string, ":", e$message, "\n")
    NULL
  })

  if (!is.null(schedule_resp) && status_code(schedule_resp) == 200) {
    # Use flatten = TRUE to get a proper data frame structure
    schedule_data <- fromJSON(content(schedule_resp, "text", encoding = "UTF-8"), flatten = TRUE)

    if (!is.null(schedule_data$gameWeek) && is.data.frame(schedule_data$gameWeek) && nrow(schedule_data$gameWeek) > 0) {
      game_week_df <- schedule_data$gameWeek

      for (day_idx in seq_len(nrow(game_week_df))) {
        day_date <- game_week_df$date[day_idx]

        # Filter: Only process games within our target date range
        day_date_parsed <- as.Date(day_date)
        if (day_date_parsed < start_date || day_date_parsed > end_date) {
          next
        }

        day_games <- game_week_df$games[[day_idx]]

        # Skip if no games for this day
        if (is.null(day_games) || length(day_games) == 0) next

        # day_games could be a data frame or a list
        if (is.data.frame(day_games)) {
          for (game_idx in seq_len(nrow(day_games))) {
            game <- day_games[game_idx, ]

            # Skip duplicate games (API returns overlapping weeks)
            game_id_str <- as.character(game$id)
            if (game_id_str %in% seen_game_ids) next
            seen_game_ids <- c(seen_game_ids, game_id_str)

            # Determine game status
            game_state <- game$gameState
            if (is.null(game_state) || is.na(game_state)) next

            game_completed <- game_state %in% c("OFF", "FINAL")

            # Extract team info (flattened column names)
            home_abbrev <- game$homeTeam.abbrev
            away_abbrev <- game$awayTeam.abbrev

            if (is.null(home_abbrev) || is.null(away_abbrev)) next

            # Extract scores for completed games
            home_score <- if (game_completed && !is.null(game$homeTeam.score)) as.integer(game$homeTeam.score) else NULL
            away_score <- if (game_completed && !is.null(game$awayTeam.score)) as.integer(game$awayTeam.score) else NULL

            # Build game date/time string
            game_date_time <- if (!is.null(game$startTimeUTC) && !is.na(game$startTimeUTC)) {
              game$startTimeUTC
            } else {
              paste0(day_date, "T00:00:00Z")
            }

            # Extract venue info
            venue_name <- if (!is.null(game$venue.default) && !is.na(game$venue.default)) game$venue.default else NA

            game_info <- list(
              game_id = as.character(game$id),
              game_date = game_date_time,
              game_state = game_state,
              game_completed = game_completed,
              home_team_id = as.character(game$homeTeam.id),
              home_team_name = ABBREV_TO_NAME[home_abbrev],
              home_team_abbrev = home_abbrev,
              home_team_logo = game$homeTeam.logo,
              home_score = home_score,
              away_team_id = as.character(game$awayTeam.id),
              away_team_name = ABBREV_TO_NAME[away_abbrev],
              away_team_abbrev = away_abbrev,
              away_team_logo = game$awayTeam.logo,
              away_score = away_score,
              venue = venue_name
            )

            all_games[[length(all_games) + 1]] <- game_info
          }
        }
      }
    }
  }
}

# Separate completed and upcoming games
completed_games <- Filter(function(g) isTRUE(g$game_completed), all_games)
upcoming_games <- Filter(function(g) !isTRUE(g$game_completed), all_games)

cat("Found", length(completed_games), "completed games\n")
cat("Found", length(upcoming_games), "upcoming games\n")
cat("Total:", length(all_games), "games\n")

if (length(all_games) == 0) {
  cat("No games found. Exiting.\n")
  quit(status = 0)
}

end_timer()

# ============================================================================
# STEP 4b: Fetch betting odds from ESPN API
# ============================================================================
start_timer("STEP 4b: Fetch betting odds")
cat("\n4b. Fetching betting odds from ESPN API...\n")

# Create a map to store odds by game date and teams
odds_map <- list()

# Fetch odds from ESPN for each date in range
current_date <- start_date
while (current_date <= end_date) {
  date_string <- format(current_date, "%Y%m%d")

  add_api_delay()
  espn_url <- paste0(
    "https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard?dates=",
    date_string
  )

  espn_resp <- tryCatch({
    GET(espn_url)
  }, error = function(e) {
    NULL
  })

  if (!is.null(espn_resp) && status_code(espn_resp) == 200) {
    espn_data <- content(espn_resp, as = "parsed")

    if (!is.null(espn_data$events) && length(espn_data$events) > 0) {
      for (event in espn_data$events) {
        if (!is.null(event$competitions) && length(event$competitions) > 0) {
          competition <- event$competitions[[1]]

          # Find home and away teams
          home_abbrev <- NULL
          away_abbrev <- NULL
          for (team in competition$competitors) {
            if (team$homeAway == "home") {
              home_abbrev <- team$team$abbreviation
            } else {
              away_abbrev <- team$team$abbreviation
            }
          }

          # Extract odds if available
          if (!is.null(competition$odds) && length(competition$odds) > 0) {
            odds <- competition$odds[[1]]

            # Extract spread (puckline for NHL)
            home_spread <- NA
            if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$spreadOdds)) {
              home_spread <- as.numeric(odds$homeTeamOdds$spreadOdds)
            } else if (!is.null(odds$spread)) {
              home_spread <- as.numeric(odds$spread)
            }

            odds_data <- list(
              provider = if (!is.null(odds$provider)) odds$provider$name else NA,
              spread = home_spread,
              over_under = if (!is.null(odds$overUnder)) as.numeric(odds$overUnder) else NA,
              home_moneyline = if (!is.null(odds$homeTeamOdds) && !is.null(odds$homeTeamOdds$moneyLine)) {
                as.numeric(odds$homeTeamOdds$moneyLine)
              } else NA,
              away_moneyline = if (!is.null(odds$awayTeamOdds) && !is.null(odds$awayTeamOdds$moneyLine)) {
                as.numeric(odds$awayTeamOdds$moneyLine)
              } else NA
            )

            # Store by matchup key
            if (!is.null(home_abbrev) && !is.null(away_abbrev)) {
              matchup_key <- paste0(format(current_date, "%Y-%m-%d"), "_", away_abbrev, "_", home_abbrev)
              odds_map[[matchup_key]] <- odds_data
            }
          }
        }
      }
    }
  }

  current_date <- current_date + days(1)
}

cat("Fetched odds for", length(odds_map), "games\n")

# Merge odds into all_games
for (i in seq_along(all_games)) {
  game <- all_games[[i]]
  game_date <- as.Date(substr(game$game_date, 1, 10))
  matchup_key <- paste0(format(game_date, "%Y-%m-%d"), "_", game$away_team_abbrev, "_", game$home_team_abbrev)

  if (!is.null(odds_map[[matchup_key]])) {
    all_games[[i]]$odds <- odds_map[[matchup_key]]
  }
}

end_timer()

# ============================================================================
# STEP 5: Build matchup data for each game
# ============================================================================
start_timer("STEP 5: Build matchup data")
cat("\n5. Building matchup statistics...\n")

# Helper function to build NHL comparison data
build_nhl_comparisons <- function(home_stats, away_stats, home_team, away_team) {
  # Offensive comparison (side-by-side team offense)
  off_comparison <- list()
  off_stats <- list(
    list(key = "goalsPerGame", label = "Goals/Game", value_home = home_stats$goals_per_game, rank_home = home_stats$goals_per_game_rank, rankDisplay_home = home_stats$goals_per_game_rankDisplay, value_away = away_stats$goals_per_game, rank_away = away_stats$goals_per_game_rank, rankDisplay_away = away_stats$goals_per_game_rankDisplay),
    list(key = "shotsForPerGame", label = "Shots/Game", value_home = home_stats$shots_for_per_game, rank_home = home_stats$shots_for_per_game_rank, rankDisplay_home = home_stats$shots_for_per_game_rankDisplay, value_away = away_stats$shots_for_per_game, rank_away = away_stats$shots_for_per_game_rank, rankDisplay_away = away_stats$shots_for_per_game_rankDisplay),
    list(key = "powerPlayPct", label = "Power Play %", value_home = home_stats$power_play_pct, rank_home = home_stats$power_play_pct_rank, rankDisplay_home = home_stats$power_play_pct_rankDisplay, value_away = away_stats$power_play_pct, rank_away = away_stats$power_play_pct_rank, rankDisplay_away = away_stats$power_play_pct_rankDisplay),
    list(key = "faceoffWinPct", label = "Faceoff Win %", value_home = home_stats$faceoff_win_pct, rank_home = home_stats$faceoff_win_pct_rank, rankDisplay_home = home_stats$faceoff_win_pct_rankDisplay, value_away = away_stats$faceoff_win_pct, rank_away = away_stats$faceoff_win_pct_rank, rankDisplay_away = away_stats$faceoff_win_pct_rankDisplay)
  )

  for (stat in off_stats) {
    off_comparison[[stat$key]] <- list(
      label = stat$label,
      home = list(
        value = if (!is.na(stat$value_home)) round(stat$value_home, 2) else NULL,
        rank = if (!is.na(stat$rank_home)) as.integer(stat$rank_home) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_home)) stat$rankDisplay_home else NULL
      ),
      away = list(
        value = if (!is.na(stat$value_away)) round(stat$value_away, 2) else NULL,
        rank = if (!is.na(stat$rank_away)) as.integer(stat$rank_away) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_away)) stat$rankDisplay_away else NULL
      )
    )
  }

  # Defensive comparison (side-by-side team defense)
  def_comparison <- list()
  def_stats <- list(
    list(key = "goalsAgainstPerGame", label = "Goals Against/Game", value_home = home_stats$goals_against_per_game, rank_home = home_stats$goals_against_per_game_rank, rankDisplay_home = home_stats$goals_against_per_game_rankDisplay, value_away = away_stats$goals_against_per_game, rank_away = away_stats$goals_against_per_game_rank, rankDisplay_away = away_stats$goals_against_per_game_rankDisplay),
    list(key = "shotsAgainstPerGame", label = "Shots Against/Game", value_home = home_stats$shots_against_per_game, rank_home = home_stats$shots_against_per_game_rank, rankDisplay_home = home_stats$shots_against_per_game_rankDisplay, value_away = away_stats$shots_against_per_game, rank_away = away_stats$shots_against_per_game_rank, rankDisplay_away = away_stats$shots_against_per_game_rankDisplay),
    list(key = "penaltyKillPct", label = "Penalty Kill %", value_home = home_stats$penalty_kill_pct, rank_home = home_stats$penalty_kill_pct_rank, rankDisplay_home = home_stats$penalty_kill_pct_rankDisplay, value_away = away_stats$penalty_kill_pct, rank_away = away_stats$penalty_kill_pct_rank, rankDisplay_away = away_stats$penalty_kill_pct_rankDisplay)
  )

  for (stat in def_stats) {
    def_comparison[[stat$key]] <- list(
      label = stat$label,
      home = list(
        value = if (!is.na(stat$value_home)) round(stat$value_home, 2) else NULL,
        rank = if (!is.na(stat$rank_home)) as.integer(stat$rank_home) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_home)) stat$rankDisplay_home else NULL
      ),
      away = list(
        value = if (!is.na(stat$value_away)) round(stat$value_away, 2) else NULL,
        rank = if (!is.na(stat$rank_away)) as.integer(stat$rank_away) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_away)) stat$rankDisplay_away else NULL
      )
    )
  }

  # Offense vs defense matchups
  off_vs_def_matchups <- list(
    list(
      key = "goalsPerGame",
      off_label = "Goals/Game",
      def_label = "Goals Against/Game",
      off_stat = "goals_per_game",
      def_stat = "goals_against_per_game",
      off_rank = "goals_per_game_rank",
      def_rank = "goals_against_per_game_rank",
      off_rankDisplay = "goals_per_game_rankDisplay",
      def_rankDisplay = "goals_against_per_game_rankDisplay"
    ),
    list(
      key = "powerPlay",
      off_label = "Power Play %",
      def_label = "Penalty Kill %",
      off_stat = "power_play_pct",
      def_stat = "penalty_kill_pct",
      off_rank = "power_play_pct_rank",
      def_rank = "penalty_kill_pct_rank",
      off_rankDisplay = "power_play_pct_rankDisplay",
      def_rankDisplay = "penalty_kill_pct_rankDisplay"
    ),
    list(
      key = "shots",
      off_label = "Shots/Game",
      def_label = "Shots Against/Game",
      off_stat = "shots_for_per_game",
      def_stat = "shots_against_per_game",
      off_rank = "shots_for_per_game_rank",
      def_rank = "shots_against_per_game_rank",
      off_rankDisplay = "shots_for_per_game_rankDisplay",
      def_rankDisplay = "shots_against_per_game_rankDisplay"
    )
  )

  # Home offense vs away defense
  home_off_vs_away_def <- list()
  for (matchup in off_vs_def_matchups) {
    off_val <- home_stats[[matchup$off_stat]]
    def_val <- away_stats[[matchup$def_stat]]
    off_rank <- home_stats[[matchup$off_rank]]
    def_rank <- away_stats[[matchup$def_rank]]

    if (!is.na(off_val) && !is.na(def_val)) {
      advantage <- 0
      if (!is.na(off_rank) && !is.na(def_rank)) {
        if (off_rank < def_rank) {
          advantage <- -1  # Offense advantage
        } else if (off_rank > def_rank) {
          advantage <- 1   # Defense advantage
        }
      }

      home_off_vs_away_def[[matchup$key]] <- list(
        statKey = matchup$key,
        offLabel = matchup$off_label,
        defLabel = matchup$def_label,
        offense = list(
          team = home_team,
          value = round(off_val, 2),
          rank = as.integer(off_rank),
          rankDisplay = home_stats[[matchup$off_rankDisplay]]
        ),
        defense = list(
          team = away_team,
          value = round(def_val, 2),
          rank = as.integer(def_rank),
          rankDisplay = away_stats[[matchup$def_rankDisplay]]
        ),
        advantage = advantage
      )
    }
  }

  # Away offense vs home defense
  away_off_vs_home_def <- list()
  for (matchup in off_vs_def_matchups) {
    off_val <- away_stats[[matchup$off_stat]]
    def_val <- home_stats[[matchup$def_stat]]
    off_rank <- away_stats[[matchup$off_rank]]
    def_rank <- home_stats[[matchup$def_rank]]

    if (!is.na(off_val) && !is.na(def_val)) {
      advantage <- 0
      if (!is.na(off_rank) && !is.na(def_rank)) {
        if (off_rank < def_rank) {
          advantage <- -1  # Offense advantage
        } else if (off_rank > def_rank) {
          advantage <- 1   # Defense advantage
        }
      }

      away_off_vs_home_def[[matchup$key]] <- list(
        statKey = matchup$key,
        offLabel = matchup$off_label,
        defLabel = matchup$def_label,
        offense = list(
          team = away_team,
          value = round(off_val, 2),
          rank = as.integer(off_rank),
          rankDisplay = away_stats[[matchup$off_rankDisplay]]
        ),
        defense = list(
          team = home_team,
          value = round(def_val, 2),
          rank = as.integer(def_rank),
          rankDisplay = home_stats[[matchup$def_rankDisplay]]
        ),
        advantage = advantage
      )
    }
  }

  return(list(
    sideBySide = list(
      offense = off_comparison,
      defense = def_comparison
    ),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  ))
}

matchups_json <- list()
results_fetched <- 0
total_results_time <- 0

for (game in all_games) {
  game_status_label <- if (isTRUE(game$game_completed)) "COMPLETED" else "UPCOMING"
  cat("Processing matchup:", game$away_team_abbrev, "@", game$home_team_abbrev, "(", game_status_label, ")\n")

  # Get team stats for both teams
  home_stats_row <- team_stats %>% filter(team_abbreviation == game$home_team_abbrev)
  away_stats_row <- team_stats %>% filter(team_abbreviation == game$away_team_abbrev)

  if (nrow(home_stats_row) == 0 || nrow(away_stats_row) == 0) {
    cat("Skipping game - missing team stats\n")
    next
  }

  # Get top players for both teams
  home_players <- NULL
  away_players <- NULL
  if (!is.null(player_stats)) {
    # Handle multi-team players by checking if the team abbreviation contains the team
    home_players <- player_stats %>%
      filter(grepl(game$home_team_abbrev, team_abbreviation, fixed = TRUE)) %>%
      arrange(desc(points)) %>%
      head(7)

    away_players <- player_stats %>%
      filter(grepl(game$away_team_abbrev, team_abbreviation, fixed = TRUE)) %>%
      arrange(desc(points)) %>%
      head(7)
  }

  # Build team data
  home_team_data <- list(
    id = game$home_team_id,
    name = game$home_team_name,
    abbreviation = game$home_team_abbrev,
    logo = game$home_team_logo,
    wins = if (is_valid_value(home_stats_row$wins)) as.integer(home_stats_row$wins) else NULL,
    losses = if (is_valid_value(home_stats_row$losses)) as.integer(home_stats_row$losses) else NULL,
    otLosses = if (is_valid_value(home_stats_row$ot_losses)) as.integer(home_stats_row$ot_losses) else NULL,
    points = if (is_valid_value(home_stats_row$points)) as.integer(home_stats_row$points) else NULL,
    conferenceRank = if (is_valid_value(home_stats_row$conference_rank)) as.integer(home_stats_row$conference_rank) else NULL,
    divisionRank = if (is_valid_value(home_stats_row$division_rank)) as.integer(home_stats_row$division_rank) else NULL,
    division = home_stats_row$division,
    conference = home_stats_row$conference,
    streak = if (is_valid_value(home_stats_row$streak_code)) paste0(home_stats_row$streak_code, home_stats_row$streak_count) else NULL,
    last10 = if (is_valid_value(home_stats_row$last_10_wins)) paste0(home_stats_row$last_10_wins, "-", home_stats_row$last_10_losses, "-", home_stats_row$last_10_ot_losses) else NULL,
    stats = list(
      gamesPlayed = as.integer(home_stats_row$games_played),
      goalsPerGame = round(home_stats_row$goals_per_game, 2),
      goalsPerGameRank = as.integer(home_stats_row$goals_per_game_rank),
      goalsPerGameRankDisplay = home_stats_row$goals_per_game_rankDisplay,
      goalsAgainstPerGame = round(home_stats_row$goals_against_per_game, 2),
      goalsAgainstPerGameRank = as.integer(home_stats_row$goals_against_per_game_rank),
      goalsAgainstPerGameRankDisplay = home_stats_row$goals_against_per_game_rankDisplay,
      goalDiffPerGame = round(home_stats_row$goal_diff_per_game, 2),
      goalDiffPerGameRank = as.integer(home_stats_row$goal_diff_per_game_rank),
      goalDiffPerGameRankDisplay = home_stats_row$goal_diff_per_game_rankDisplay,
      shotsForPerGame = round(home_stats_row$shots_for_per_game, 2),
      shotsForPerGameRank = as.integer(home_stats_row$shots_for_per_game_rank),
      shotsForPerGameRankDisplay = home_stats_row$shots_for_per_game_rankDisplay,
      shotsAgainstPerGame = round(home_stats_row$shots_against_per_game, 2),
      shotsAgainstPerGameRank = as.integer(home_stats_row$shots_against_per_game_rank),
      shotsAgainstPerGameRankDisplay = home_stats_row$shots_against_per_game_rankDisplay,
      powerPlayPct = round(home_stats_row$power_play_pct, 2),
      powerPlayPctRank = as.integer(home_stats_row$power_play_pct_rank),
      powerPlayPctRankDisplay = home_stats_row$power_play_pct_rankDisplay,
      penaltyKillPct = round(home_stats_row$penalty_kill_pct, 2),
      penaltyKillPctRank = as.integer(home_stats_row$penalty_kill_pct_rank),
      penaltyKillPctRankDisplay = home_stats_row$penalty_kill_pct_rankDisplay,
      faceoffWinPct = round(home_stats_row$faceoff_win_pct, 2),
      faceoffWinPctRank = as.integer(home_stats_row$faceoff_win_pct_rank),
      faceoffWinPctRankDisplay = home_stats_row$faceoff_win_pct_rankDisplay,
      pointsPct = round(home_stats_row$points_pct, 3),
      pointsPctRank = as.integer(home_stats_row$points_pct_rank),
      pointsPctRankDisplay = home_stats_row$points_pct_rankDisplay
    )
  )

  # Add month trend for home team
  if (!is.null(month_trend_stats)) {
    home_month_trend <- month_trend_stats %>%
      filter(team_abbreviation == game$home_team_abbrev)

    if (nrow(home_month_trend) > 0) {
      home_team_data$stats$monthTrend <- list(
        gamesPlayed = as.integer(home_month_trend$games_played),
        record = list(
          wins = as.integer(home_month_trend$wins),
          losses = as.integer(home_month_trend$losses),
          rank = as.integer(home_month_trend$record_rank),
          rankDisplay = home_month_trend$record_rankDisplay
        ),
        goalsPerGame = list(
          value = round(home_month_trend$goals_per_game, 2),
          rank = as.integer(home_month_trend$gpg_rank),
          rankDisplay = home_month_trend$gpg_rankDisplay
        ),
        goalsAgainstPerGame = list(
          value = round(home_month_trend$goals_against_per_game, 2),
          rank = as.integer(home_month_trend$gapg_rank),
          rankDisplay = home_month_trend$gapg_rankDisplay
        ),
        goalDiffPerGame = list(
          value = round(home_month_trend$goal_diff_per_game, 2),
          rank = as.integer(home_month_trend$goal_diff_rank),
          rankDisplay = home_month_trend$goal_diff_rankDisplay
        )
      )
    }
  }

  away_team_data <- list(
    id = game$away_team_id,
    name = game$away_team_name,
    abbreviation = game$away_team_abbrev,
    logo = game$away_team_logo,
    wins = if (is_valid_value(away_stats_row$wins)) as.integer(away_stats_row$wins) else NULL,
    losses = if (is_valid_value(away_stats_row$losses)) as.integer(away_stats_row$losses) else NULL,
    otLosses = if (is_valid_value(away_stats_row$ot_losses)) as.integer(away_stats_row$ot_losses) else NULL,
    points = if (is_valid_value(away_stats_row$points)) as.integer(away_stats_row$points) else NULL,
    conferenceRank = if (is_valid_value(away_stats_row$conference_rank)) as.integer(away_stats_row$conference_rank) else NULL,
    divisionRank = if (is_valid_value(away_stats_row$division_rank)) as.integer(away_stats_row$division_rank) else NULL,
    division = away_stats_row$division,
    conference = away_stats_row$conference,
    streak = if (is_valid_value(away_stats_row$streak_code)) paste0(away_stats_row$streak_code, away_stats_row$streak_count) else NULL,
    last10 = if (is_valid_value(away_stats_row$last_10_wins)) paste0(away_stats_row$last_10_wins, "-", away_stats_row$last_10_losses, "-", away_stats_row$last_10_ot_losses) else NULL,
    stats = list(
      gamesPlayed = as.integer(away_stats_row$games_played),
      goalsPerGame = round(away_stats_row$goals_per_game, 2),
      goalsPerGameRank = as.integer(away_stats_row$goals_per_game_rank),
      goalsPerGameRankDisplay = away_stats_row$goals_per_game_rankDisplay,
      goalsAgainstPerGame = round(away_stats_row$goals_against_per_game, 2),
      goalsAgainstPerGameRank = as.integer(away_stats_row$goals_against_per_game_rank),
      goalsAgainstPerGameRankDisplay = away_stats_row$goals_against_per_game_rankDisplay,
      goalDiffPerGame = round(away_stats_row$goal_diff_per_game, 2),
      goalDiffPerGameRank = as.integer(away_stats_row$goal_diff_per_game_rank),
      goalDiffPerGameRankDisplay = away_stats_row$goal_diff_per_game_rankDisplay,
      shotsForPerGame = round(away_stats_row$shots_for_per_game, 2),
      shotsForPerGameRank = as.integer(away_stats_row$shots_for_per_game_rank),
      shotsForPerGameRankDisplay = away_stats_row$shots_for_per_game_rankDisplay,
      shotsAgainstPerGame = round(away_stats_row$shots_against_per_game, 2),
      shotsAgainstPerGameRank = as.integer(away_stats_row$shots_against_per_game_rank),
      shotsAgainstPerGameRankDisplay = away_stats_row$shots_against_per_game_rankDisplay,
      powerPlayPct = round(away_stats_row$power_play_pct, 2),
      powerPlayPctRank = as.integer(away_stats_row$power_play_pct_rank),
      powerPlayPctRankDisplay = away_stats_row$power_play_pct_rankDisplay,
      penaltyKillPct = round(away_stats_row$penalty_kill_pct, 2),
      penaltyKillPctRank = as.integer(away_stats_row$penalty_kill_pct_rank),
      penaltyKillPctRankDisplay = away_stats_row$penalty_kill_pct_rankDisplay,
      faceoffWinPct = round(away_stats_row$faceoff_win_pct, 2),
      faceoffWinPctRank = as.integer(away_stats_row$faceoff_win_pct_rank),
      faceoffWinPctRankDisplay = away_stats_row$faceoff_win_pct_rankDisplay,
      pointsPct = round(away_stats_row$points_pct, 3),
      pointsPctRank = as.integer(away_stats_row$points_pct_rank),
      pointsPctRankDisplay = away_stats_row$points_pct_rankDisplay
    )
  )

  # Add month trend for away team
  if (!is.null(month_trend_stats)) {
    away_month_trend <- month_trend_stats %>%
      filter(team_abbreviation == game$away_team_abbrev)

    if (nrow(away_month_trend) > 0) {
      away_team_data$stats$monthTrend <- list(
        gamesPlayed = as.integer(away_month_trend$games_played),
        record = list(
          wins = as.integer(away_month_trend$wins),
          losses = as.integer(away_month_trend$losses),
          rank = as.integer(away_month_trend$record_rank),
          rankDisplay = away_month_trend$record_rankDisplay
        ),
        goalsPerGame = list(
          value = round(away_month_trend$goals_per_game, 2),
          rank = as.integer(away_month_trend$gpg_rank),
          rankDisplay = away_month_trend$gpg_rankDisplay
        ),
        goalsAgainstPerGame = list(
          value = round(away_month_trend$goals_against_per_game, 2),
          rank = as.integer(away_month_trend$gapg_rank),
          rankDisplay = away_month_trend$gapg_rankDisplay
        ),
        goalDiffPerGame = list(
          value = round(away_month_trend$goal_diff_per_game, 2),
          rank = as.integer(away_month_trend$goal_diff_rank),
          rankDisplay = away_month_trend$goal_diff_rankDisplay
        )
      )
    }
  }

  # Build player data
  home_players_data <- if (!is.null(home_players) && nrow(home_players) > 0) {
    lapply(seq_len(nrow(home_players)), function(i) {
      p <- home_players[i, ]
      na_to_null <- function(x) if (length(x) == 0 || is.null(x) || is.na(x)) NULL else x

      list(
        name = p$player_name,
        position = p$position,
        gamesPlayed = as.integer(p$games_played),
        goals = list(
          value = as.integer(p$goals),
          rank = na_to_null(as.integer(p$goals_rank)),
          rankDisplay = na_to_null(p$goals_rankDisplay)
        ),
        assists = list(
          value = as.integer(p$assists),
          rank = na_to_null(as.integer(p$assists_rank)),
          rankDisplay = na_to_null(p$assists_rankDisplay)
        ),
        points = list(
          value = as.integer(p$points),
          rank = na_to_null(as.integer(p$points_rank)),
          rankDisplay = na_to_null(p$points_rankDisplay)
        ),
        plusMinus = list(
          value = as.integer(p$plus_minus),
          rank = na_to_null(as.integer(p$plus_minus_rank)),
          rankDisplay = na_to_null(p$plus_minus_rankDisplay)
        ),
        pointsPerGame = round(p$points_per_game, 2)
      )
    })
  } else {
    list()
  }

  away_players_data <- if (!is.null(away_players) && nrow(away_players) > 0) {
    lapply(seq_len(nrow(away_players)), function(i) {
      p <- away_players[i, ]
      na_to_null <- function(x) if (length(x) == 0 || is.null(x) || is.na(x)) NULL else x

      list(
        name = p$player_name,
        position = p$position,
        gamesPlayed = as.integer(p$games_played),
        goals = list(
          value = as.integer(p$goals),
          rank = na_to_null(as.integer(p$goals_rank)),
          rankDisplay = na_to_null(p$goals_rankDisplay)
        ),
        assists = list(
          value = as.integer(p$assists),
          rank = na_to_null(as.integer(p$assists_rank)),
          rankDisplay = na_to_null(p$assists_rankDisplay)
        ),
        points = list(
          value = as.integer(p$points),
          rank = na_to_null(as.integer(p$points_rank)),
          rankDisplay = na_to_null(p$points_rankDisplay)
        ),
        plusMinus = list(
          value = as.integer(p$plus_minus),
          rank = na_to_null(as.integer(p$plus_minus_rank)),
          rankDisplay = na_to_null(p$plus_minus_rankDisplay)
        ),
        pointsPerGame = round(p$points_per_game, 2)
      )
    })
  } else {
    list()
  }

  # Build comparison data
  comparisons <- build_nhl_comparisons(
    home_stats_row,
    away_stats_row,
    game$home_team_abbrev,
    game$away_team_abbrev
  )

  # Build matchup object
  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = paste0(game$away_team_name, " @ ", game$home_team_name),
    gameState = game$game_state,
    gameCompleted = isTRUE(game$game_completed),
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    homePlayers = home_players_data,
    awayPlayers = away_players_data,
    comparisons = comparisons
  )

  # Add venue if available
  if (!is.null(game$venue) && !is.na(game$venue)) {
    matchup$location <- list(
      stadium = game$venue
    )
  }

  # Add odds if available
  if (!is.null(game$odds)) {
    matchup$odds <- list(
      provider = if (!is.na(game$odds$provider)) game$odds$provider else NULL,
      spread = if (!is.na(game$odds$spread)) game$odds$spread else NULL,
      overUnder = if (!is.na(game$odds$over_under)) game$odds$over_under else NULL,
      homeMoneyline = if (!is.na(game$odds$home_moneyline)) game$odds$home_moneyline else NULL,
      awayMoneyline = if (!is.na(game$odds$away_moneyline)) game$odds$away_moneyline else NULL
    )
  }

  # Add results for completed games (with rate limiting)
  if (isTRUE(game$game_completed) && results_fetched < MAX_RESULTS_GAMES) {
    result_start <- Sys.time()
    cat("  -> Game completed, fetching results... (", results_fetched + 1, ")\n")
    matchup$results <- build_game_results(game, home_stats_row, away_stats_row)
    result_duration <- as.numeric(Sys.time() - result_start, units = "secs")
    total_results_time <- total_results_time + result_duration
    cat("  -> Results fetched in", sprintf("%.1f seconds", result_duration), "\n")
    results_fetched <- results_fetched + 1
  }

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

end_timer()

# ============================================================================
# STEP 6: Generate output JSON
# ============================================================================
start_timer("STEP 6: Generate output JSON")
cat("\n6. Generating output JSON...\n")

output_data <- list(
  sport = "NHL",
  visualizationType = "NHL_MATCHUP",
  title = paste0("NHL Matchups - ", format(start_date, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("Games from the past", DAYS_BEHIND, "days and next", DAYS_AHEAD, "days with results and stats"),
  description = paste0("Detailed NHL matchup statistics including team performance metrics, player stats, and recent form.\n\nTEAM STATS:\n\n \u2022 Goals Per Game: Average goals scored per game\n\n \u2022 Goals Against Per Game: Average goals allowed per game (lower is better)\n\n \u2022 Goal Differential: Goals For - Goals Against per game\n\n \u2022 Shots Per Game: Average shots on goal per game\n\n \u2022 Power Play %: Success rate on power plays\n\n \u2022 Penalty Kill %: Success rate killing penalties\n\n \u2022 Faceoff Win %: Percentage of faceoffs won\n\nPLAYER STATS:\n\n \u2022 Goals: Total goals scored\n\n \u2022 Assists: Total assists\n\n \u2022 Points: Goals + Assists\n\n \u2022 Plus/Minus: Goal differential when on ice\n\nAll stats are season totals through the current date. Players must have played at least ", MIN_GAMES_PLAYED, " games to be included."),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "NHL Stats API",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
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
  is_prod <- tolower(Sys.getenv("PROD")) == "true"

  s3_key <- if (is_prod) {
    "nhl__matchup_stats.json"
  } else {
    "dev/nhl__matchup_stats.json"
  }

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
  chart_title <- paste0("NHL Matchups - ", format(start_date, "%b %d"), " - ", format(end_date, "%b %d"))
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
  dev_output <- "/tmp/nhl_matchup_test.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
  cat("To upload to S3, set AWS_S3_BUCKET environment variable\n")
}

end_timer()

# ============================================================================
# TIMING SUMMARY
# ============================================================================
script_end_time <- Sys.time()
total_duration <- script_end_time - script_start_time

cat("\n")
cat("============================================================\n")
cat("                    TIMING SUMMARY                          \n")
cat("============================================================\n")
cat("Script started:", format(script_start_time, "%Y-%m-%d %H:%M:%S"), "\n")
cat("Script ended:  ", format(script_end_time, "%Y-%m-%d %H:%M:%S"), "\n")
cat("------------------------------------------------------------\n")
cat("Step breakdown:\n")
for (step_name in names(step_timings)) {
  cat(sprintf("  %-45s %s\n", step_name, format_duration(step_timings[[step_name]])))
}
cat("------------------------------------------------------------\n")
if (results_fetched > 0) {
  cat(sprintf("Results fetched: %d games in %.1f seconds (avg: %.1f sec/game)\n",
              results_fetched, total_results_time, total_results_time / results_fetched))
}
cat("------------------------------------------------------------\n")
cat(sprintf("TOTAL RUNTIME: %s\n", format_duration(total_duration)))
cat("============================================================\n")

cat("\n=== NHL Matchup Stats generation complete ===\n")
