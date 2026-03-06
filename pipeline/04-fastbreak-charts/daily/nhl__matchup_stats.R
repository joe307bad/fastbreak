#!/usr/bin/env Rscript

library(httr)
library(dplyr)
library(tidyr)
library(jsonlite)
library(lubridate)
library(rvest)

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

# Helper function to create tied ranks using dense ranking
# Dense ranking ensures ranks never exceed the number of unique values
# Example: values [10, 20, 20, 30] get ranks [1, 2, 2, 3] not [1, 2, 2, 4]
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

# Helper function for NST-specific rate limiting (longer delays to avoid bans)
add_nst_delay <- function() {
  delay <- runif(1, min = 5, max = 10)
  cat(sprintf("[NST] Waiting %.1f seconds for rate limiting...\n", delay))
  Sys.sleep(delay)
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

# Valid NHL abbreviations (used to filter out international/all-star teams)
VALID_NHL_ABBREVS <- unique(unname(TEAM_ABBREVS))

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

# Team name mapping for playoffstatus.com (short team names to abbreviations)
NHL_TEAM_NAME_TO_ABBREV <- c(
  "Ducks" = "ANA", "Coyotes" = "ARI",
  "Bruins" = "BOS", "Sabres" = "BUF",
  "Flames" = "CGY", "Hurricanes" = "CAR",
  "Blackhawks" = "CHI", "Avalanche" = "COL",
  "Blue Jackets" = "CBJ", "Stars" = "DAL",
  "Red Wings" = "DET", "Oilers" = "EDM",
  "Panthers" = "FLA", "Kings" = "LAK",
  "Wild" = "MIN", "Canadiens" = "MTL",
  "Predators" = "NSH", "Devils" = "NJD",
  "Islanders" = "NYI", "Rangers" = "NYR",
  "Senators" = "OTT", "Flyers" = "PHI",
  "Penguins" = "PIT", "Sharks" = "SJS",
  "Kraken" = "SEA", "Blues" = "STL",
  "Lightning" = "TBL", "Maple Leafs" = "TOR",
  "Utah" = "UTA", "Utah HC" = "UTA", "Hockey Club" = "UTA", "Mammoth" = "UTA",
  "Canucks" = "VAN", "Golden Knights" = "VGK",
  "Capitals" = "WSH", "Jets" = "WPG"
)

# Helper function to scrape NHL playoff probabilities from playoffstatus.com
scrape_nhl_playoff_probabilities <- function() {
  url <- "https://www.playoffstatus.com/nhl/nhlpostseasonprob.html"

  tryCatch({
    cat("Scraping NHL playoff probabilities from playoffstatus.com...\n")

    page <- read_html(url)

    # Find the main probability table
    tables <- page %>% html_elements("table")

    if (length(tables) == 0) {
      cat("Warning: No tables found on NHL playoff probability page\n")
      return(NULL)
    }

    # The probability table is typically the largest table on the page
    prob_table <- NULL
    for (tbl in tables) {
      rows <- tbl %>% html_elements("tr")
      if (length(rows) > 20) {  # Looking for the table with all 32 teams
        prob_table <- tbl
        break
      }
    }

    if (is.null(prob_table)) {
      # Fall back to first table
      prob_table <- tables[[1]]
    }

    rows <- prob_table %>% html_elements("tr")

    results <- list()

    for (row in rows) {
      cells <- row %>% html_elements("td")
      if (length(cells) >= 10) {
        # Extract team name from first cell (usually has a link)
        team_link <- cells[[1]] %>% html_element("a")
        team_name <- if (!is.na(team_link)) {
          team_link %>% html_text(trim = TRUE)
        } else {
          cells[[1]] %>% html_text(trim = TRUE)
        }

        # Clean up team name
        team_name <- gsub("^\\s+|\\s+$", "", team_name)

        # Skip header rows and international/all-star teams
        if (team_name == "" || grepl("Team|Conference", team_name, ignore.case = TRUE)) {
          next
        }
        if (grepl("^USA$|^Canada$|^Sweden$|^Finland$|^Latvia$|^Czechia$|^Czech Republic$|^Switzerland$|^Germany$|^Slovakia$|^Denmark$|^Norway$|^Austria$|^France$|^All-Star|^Atlantic$|^Metropolitan$|^Central$|^Pacific$|^Eastern$|^Western$|^World$|^North America$|4 Nations|Face-?Off", team_name, ignore.case = TRUE)) {
          next
        }

        # Map team name to abbreviation
        team_abbrev <- NHL_TEAM_NAME_TO_ABBREV[team_name]
        if (is.na(team_abbrev)) {
          # Try partial match (only check if known name appears in scraped name, not reverse)
          for (name in names(NHL_TEAM_NAME_TO_ABBREV)) {
            if (grepl(name, team_name, ignore.case = TRUE)) {
              team_abbrev <- NHL_TEAM_NAME_TO_ABBREV[name]
              break
            }
          }
        }

        if (is.na(team_abbrev)) {
          cat("Warning: Could not map NHL team name:", team_name, "\n")
          next
        }

        # Parse percentage values (remove % sign and convert)
        parse_pct <- function(cell) {
          text <- cell %>% html_text(trim = TRUE)
          text <- gsub("[%<>]", "", text)
          text <- gsub("\\s+", "", text)
          val <- suppressWarnings(as.numeric(text))
          if (is.na(val)) 0 else val
        }

        # NHL Columns: Team, Conf, W, L, OTL, Pts, CurrentSeries, StanleyCupWinners, StanleyCup, ConfChamp, Round2, Round1
        # Index:       1     2     3  4  5    6    7              8                  9           10        11      12
        # Note: Column 7 is "Current Series" (empty during regular season)
        # Probabilities decrease from right to left: Round1 (highest) -> StanleyCupWinners (lowest)
        champ_prob <- if (length(cells) >= 8) parse_pct(cells[[8]]) else 0        # Stanley Cup Winners (championship)
        finals_prob <- if (length(cells) >= 9) parse_pct(cells[[9]]) else 0       # Stanley Cup (finals)
        conf_champ_prob <- if (length(cells) >= 10) parse_pct(cells[[10]]) else 0 # Conference Championship
        round2_prob <- if (length(cells) >= 11) parse_pct(cells[[11]]) else 0     # Round 2
        playoff_prob <- if (length(cells) >= 12) parse_pct(cells[[12]]) else 0    # Round 1 / Playoff

        results[[team_abbrev]] <- list(
          playoffProb = playoff_prob,
          round2Prob = round2_prob,
          confChampProb = conf_champ_prob,
          finalsProb = finals_prob,
          champProb = champ_prob
        )
      }
    }

    cat("Successfully scraped NHL playoff probabilities for", length(results), "teams\n")
    return(results)

  }, error = function(e) {
    cat("Error scraping NHL playoff probabilities:", e$message, "\n")
    return(NULL)
  })
}

# Helper function to scrape team xG data from Natural Stat Trick
scrape_nst_team_xg <- function(from_date, to_date, season_id) {
  tryCatch({
    url <- sprintf(
      "https://www.naturalstattrick.com/teamtable.php?fromseason=%s&thruseason=%s&stype=2&sit=5v5&score=all&rate=n&team=all&loc=B&gpf=410&fd=%s&td=%s",
      season_id, season_id,
      format(from_date, "%Y-%m-%d"),
      format(to_date, "%Y-%m-%d")
    )

    add_nst_delay()
    page <- read_html(url)

    tables <- page %>% html_elements("table")
    if (length(tables) == 0) {
      cat("Warning: No tables found on NST page\n")
      return(NULL)
    }

    # Parse the first table (team stats table)
    tbl <- html_table(tables[[1]], fill = TRUE)

    if (nrow(tbl) == 0) {
      cat("Warning: Empty table from NST\n")
      return(NULL)
    }

    # Find relevant columns - NST uses "Team", "xGF", "xGA", "xGF%"
    col_names <- colnames(tbl)
    team_col <- which(col_names == "Team")[1]
    xgf_col <- which(col_names == "xGF")[1]
    xga_col <- which(col_names == "xGA")[1]
    xgf_pct_col <- which(col_names == "xGF%")[1]
    gp_col <- which(col_names == "GP")[1]

    if (is.na(team_col) || is.na(xgf_pct_col)) {
      cat("Warning: Could not find expected columns in NST table. Found:", paste(col_names, collapse = ", "), "\n")
      return(NULL)
    }

    result <- data.frame(
      team_name_nst = tbl[[team_col]],
      xgf = as.numeric(tbl[[xgf_col]]),
      xga = as.numeric(tbl[[xga_col]]),
      xgf_pct = as.numeric(tbl[[xgf_pct_col]]) / 100,  # Convert from percentage to decimal
      gp = if (!is.na(gp_col)) as.integer(tbl[[gp_col]]) else NA_integer_,
      stringsAsFactors = FALSE
    )

    # Map NST team names to abbreviations using TEAM_ABBREVS
    result$team_abbreviation <- sapply(result$team_name_nst, function(name) {
      # Direct match first
      abbrev <- TEAM_ABBREVS[name]
      if (!is.na(abbrev)) return(abbrev)

      # Fuzzy fallback - check if known name appears in NST name (not reverse, to avoid false matches)
      for (known_name in names(TEAM_ABBREVS)) {
        if (grepl(known_name, name, ignore.case = TRUE)) {
          return(TEAM_ABBREVS[known_name])
        }
      }
      NA_character_
    })

    result <- result %>% filter(!is.na(team_abbreviation))
    cat("Scraped xG data for", nrow(result), "teams from NST (", format(from_date), "to", format(to_date), ")\n")
    return(result)

  }, error = function(e) {
    cat("Error scraping NST team xG:", e$message, "\n")
    return(NULL)
  })
}

# Helper function to scrape per-game xG data from Natural Stat Trick
scrape_nst_game_xg <- function(nhl_game_id) {
  tryCatch({
    url <- sprintf("https://www.naturalstattrick.com/game.php?season=%s&game=%s&view=limited",
                   NHL_SEASON_ID, nhl_game_id)

    add_nst_delay()
    page <- read_html(url)

    tables <- page %>% html_elements("table")
    if (length(tables) == 0) {
      return(NULL)
    }

    # Look for 5v5 team summary table - typically has xGF/xGA columns
    for (tbl_el in tables) {
      tbl <- html_table(tbl_el, fill = TRUE)
      col_names <- colnames(tbl)

      if ("xGF" %in% col_names && "xGA" %in% col_names && nrow(tbl) >= 2) {
        # First row is usually away, second row is home (or vice versa - check Team col)
        away_row <- 1
        home_row <- 2

        return(list(
          away_xgf = as.numeric(tbl$xGF[away_row]),
          away_xga = as.numeric(tbl$xGA[away_row]),
          home_xgf = as.numeric(tbl$xGF[home_row]),
          home_xga = as.numeric(tbl$xGA[home_row])
        ))
      }
    }

    return(NULL)
  }, error = function(e) {
    cat("Warning: Could not scrape game xG for", nhl_game_id, ":", e$message, "\n")
    return(NULL)
  })
}

# Helper function to compare game stats to season averages
compare_to_season_avg <- function(game_value, season_avg, stat_name, higher_is_better = TRUE, decimals = 2) {
  if (is.na(game_value) || is.na(season_avg) || is.null(game_value) || is.null(season_avg)) {
    return(list(
      gameValue = if (!is.null(game_value) && !is.na(game_value)) round(game_value, decimals) else NULL,
      seasonAvg = if (!is.null(season_avg) && !is.na(season_avg)) round(season_avg, decimals) else NULL,
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
    gameValue = round(game_value, decimals),
    seasonAvg = round(season_avg, decimals),
    difference = round(diff, decimals),
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
              goalie_save_pct <- round(as.numeric(g$savePctg), 4)
            }
          }
        } else if (is.list(goalies)) {
          for (g in goalies) {
            if (!is.null(g$pim) && !is.na(g$pim)) total_pim <- total_pim + as.integer(g$pim)
            if (!is.null(g$saves) && !is.na(g$saves)) goalie_saves <- goalie_saves + as.integer(g$saves)
            if (!is.null(g$shotsAgainst) && !is.na(g$shotsAgainst)) goalie_shots_against <- goalie_shots_against + as.integer(g$shotsAgainst)
            if (!is.null(g$goalsAgainst) && !is.na(g$goalsAgainst)) goalie_goals_against <- goalie_goals_against + as.integer(g$goalsAgainst)
            if (!is.null(g$starter) && g$starter == TRUE && !is.null(g$savePctg) && !is.na(g$savePctg)) {
              goalie_save_pct <- round(as.numeric(g$savePctg), 4)
            }
          }
        }
      }

      # Calculate team faceoff percentage
      team_faceoff_pct <- if (total_faceoffs_taken > 0) round(total_faceoff_wins / total_faceoffs_taken, 4) else NULL

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
        goalsAgainst = compare_to_season_avg(game$away_score, home_season_stats$goals_against_per_game, "goalsAgainst", higher_is_better = FALSE),
        shots = compare_to_season_avg(home_box$sog, home_season_stats$shots_for_per_game, "shots"),
        shotsAgainst = compare_to_season_avg(away_box$sog, home_season_stats$shots_against_per_game, "shotsAgainst", higher_is_better = FALSE),
        hits = compare_to_season_avg(home_box$hits, home_season_stats$hits_per_game, "hits"),
        blocks = compare_to_season_avg(home_box$blocks, home_season_stats$blocks_per_game, "blocks"),
        giveaways = compare_to_season_avg(home_box$giveaways, home_season_stats$giveaways_per_game, "giveaways", higher_is_better = FALSE),
        takeaways = compare_to_season_avg(home_box$takeaways, home_season_stats$takeaways_per_game, "takeaways"),
        faceoffPct = compare_to_season_avg(home_box$faceoffWinPct, home_season_stats$faceoff_win_pct, "faceoffPct", decimals = 4),
        pim = compare_to_season_avg(home_box$pim, home_season_stats$pim_per_game, "pim", higher_is_better = FALSE),
        ppGoals = compare_to_season_avg(home_box$powerPlayGoals, home_season_stats$pp_goals_per_game, "ppGoals"),
        savePct = compare_to_season_avg(home_box$savePct, home_season_stats$save_pct_season, "savePct", decimals = 4)
      ),
      away = list(
        goals = compare_to_season_avg(game$away_score, away_season_stats$goals_per_game, "goals"),
        goalsAgainst = compare_to_season_avg(game$home_score, away_season_stats$goals_against_per_game, "goalsAgainst", higher_is_better = FALSE),
        shots = compare_to_season_avg(away_box$sog, away_season_stats$shots_for_per_game, "shots"),
        shotsAgainst = compare_to_season_avg(home_box$sog, away_season_stats$shots_against_per_game, "shotsAgainst", higher_is_better = FALSE),
        hits = compare_to_season_avg(away_box$hits, away_season_stats$hits_per_game, "hits"),
        blocks = compare_to_season_avg(away_box$blocks, away_season_stats$blocks_per_game, "blocks"),
        giveaways = compare_to_season_avg(away_box$giveaways, away_season_stats$giveaways_per_game, "giveaways", higher_is_better = FALSE),
        takeaways = compare_to_season_avg(away_box$takeaways, away_season_stats$takeaways_per_game, "takeaways"),
        faceoffPct = compare_to_season_avg(away_box$faceoffWinPct, away_season_stats$faceoff_win_pct, "faceoffPct", decimals = 4),
        pim = compare_to_season_avg(away_box$pim, away_season_stats$pim_per_game, "pim", higher_is_better = FALSE),
        ppGoals = compare_to_season_avg(away_box$powerPlayGoals, away_season_stats$pp_goals_per_game, "ppGoals"),
        savePct = compare_to_season_avg(away_box$savePct, away_season_stats$save_pct_season, "savePct", decimals = 4)
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

# Fetch realtime stats (hits, blocks, giveaways, takeaways)
realtime_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/team/realtime?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    NHL_SEASON_ID
  )
  cat("Fetching realtime stats from NHL API:", api_url, "\n")

  response <- GET(api_url)
  if (status_code(response) != 200) {
    cat("Warning: Realtime stats API returned status", status_code(response), "\n")
    NULL
  } else {
    result <- fromJSON(content(response, "text", encoding = "UTF-8"))
    if (!is.null(result$data) && nrow(result$data) > 0) {
      result$data
    } else {
      NULL
    }
  }
}, error = function(e) {
  cat("Warning: Could not fetch realtime stats:", e$message, "\n")
  NULL
})

if (!is.null(realtime_stats)) {
  cat("Loaded realtime stats for", nrow(realtime_stats), "teams\n")
}

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

# Merge realtime stats if available (hits, blocks, giveaways, takeaways)
if (!is.null(realtime_stats)) {
  realtime_processed <- realtime_stats %>%
    mutate(
      team_name_rt = teamFullName,
      hits_total = as.numeric(hits),
      blocked_shots_total = as.numeric(blockedShots),
      giveaways_total = as.numeric(giveaways),
      takeaways_total = as.numeric(takeaways),
      games_played_rt = as.numeric(gamesPlayed)
    ) %>%
    mutate(
      hits_per_game = hits_total / games_played_rt,
      blocks_per_game = blocked_shots_total / games_played_rt,
      giveaways_per_game = giveaways_total / games_played_rt,
      takeaways_per_game = takeaways_total / games_played_rt
    ) %>%
    select(team_name_rt, hits_per_game, blocks_per_game, giveaways_per_game, takeaways_per_game)

  team_stats <- team_stats %>%
    left_join(realtime_processed, by = c("team_name" = "team_name_rt"))

  cat("Merged realtime stats with team stats\n")
} else {
  # Add NA columns if realtime stats not available
  team_stats <- team_stats %>%
    mutate(
      hits_per_game = NA_real_,
      blocks_per_game = NA_real_,
      giveaways_per_game = NA_real_,
      takeaways_per_game = NA_real_
    )
}

# Fetch penalty stats for PIM per game
penalty_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/team/penalties?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    NHL_SEASON_ID
  )
  cat("Fetching penalty stats from NHL API:", api_url, "\n")

  response <- GET(api_url)
  if (status_code(response) != 200) {
    cat("Warning: Penalty stats API returned status", status_code(response), "\n")
    NULL
  } else {
    result <- fromJSON(content(response, "text", encoding = "UTF-8"))
    if (!is.null(result$data) && nrow(result$data) > 0) {
      result$data
    } else {
      NULL
    }
  }
}, error = function(e) {
  cat("Warning: Could not fetch penalty stats:", e$message, "\n")
  NULL
})

if (!is.null(penalty_stats)) {
  cat("Loaded penalty stats for", nrow(penalty_stats), "teams\n")
  penalty_processed <- penalty_stats %>%
    mutate(
      team_name_pen = teamFullName,
      pim_total = as.numeric(penaltyMinutes),
      games_played_pen = as.numeric(gamesPlayed),
      pim_per_game = pim_total / games_played_pen
    ) %>%
    select(team_name_pen, pim_per_game)

  team_stats <- team_stats %>%
    left_join(penalty_processed, by = c("team_name" = "team_name_pen"))
  cat("Merged penalty stats with team stats\n")
} else {
  team_stats <- team_stats %>%
    mutate(pim_per_game = NA_real_)
}

# Fetch powerplay stats for PP goals per game
powerplay_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/team/powerplay?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    NHL_SEASON_ID
  )
  cat("Fetching powerplay stats from NHL API:", api_url, "\n")

  response <- GET(api_url)
  if (status_code(response) != 200) {
    cat("Warning: Powerplay stats API returned status", status_code(response), "\n")
    NULL
  } else {
    result <- fromJSON(content(response, "text", encoding = "UTF-8"))
    if (!is.null(result$data) && nrow(result$data) > 0) {
      result$data
    } else {
      NULL
    }
  }
}, error = function(e) {
  cat("Warning: Could not fetch powerplay stats:", e$message, "\n")
  NULL
})

if (!is.null(powerplay_stats)) {
  cat("Loaded powerplay stats for", nrow(powerplay_stats), "teams\n")
  pp_processed <- powerplay_stats %>%
    mutate(
      team_name_pp = teamFullName,
      pp_goals_per_game = as.numeric(ppGoalsPerGame)  # Pre-computed by NHL API
    ) %>%
    select(team_name_pp, pp_goals_per_game)

  team_stats <- team_stats %>%
    left_join(pp_processed, by = c("team_name" = "team_name_pp"))
  cat("Merged powerplay stats with team stats\n")
} else {
  team_stats <- team_stats %>%
    mutate(pp_goals_per_game = NA_real_)
}

# Fetch goalie stats for save %
goalie_stats <- tryCatch({
  api_url <- sprintf(
    "https://api.nhle.com/stats/rest/en/team/summary?cayenneExp=seasonId=%s%%20and%%20gameTypeId=2",
    NHL_SEASON_ID
  )
  # The summary endpoint already has savePct - let's check if we have it
  # If not, we use a dedicated goalie endpoint
  NULL  # We'll handle this differently - use the existing data if available
}, error = function(e) {
  NULL
})

# Save % is typically calculated per goalie, not per team
# We can estimate team save % from goals against and shots against if needed
# For now, set to NA - would require additional goalie data
team_stats <- team_stats %>%
  mutate(save_pct_season = NA_real_)

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

# New stat ranks (from realtime, penalty, and powerplay endpoints)
# Higher is better: hits, blocks, takeaways, pp_goals
hits_ranks <- tied_rank(-team_stats$hits_per_game)
blocks_ranks <- tied_rank(-team_stats$blocks_per_game)
takeaways_ranks <- tied_rank(-team_stats$takeaways_per_game)
pp_goals_ranks <- tied_rank(-team_stats$pp_goals_per_game)

# Lower is better: giveaways, pim
giveaways_ranks <- tied_rank(team_stats$giveaways_per_game)
pim_ranks <- tied_rank(team_stats$pim_per_game)

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
    goal_diff_per_game_rankDisplay = goal_diff_ranks$rankDisplay,
    # New stat ranks
    hits_per_game_rank = hits_ranks$rank,
    hits_per_game_rankDisplay = hits_ranks$rankDisplay,
    blocks_per_game_rank = blocks_ranks$rank,
    blocks_per_game_rankDisplay = blocks_ranks$rankDisplay,
    takeaways_per_game_rank = takeaways_ranks$rank,
    takeaways_per_game_rankDisplay = takeaways_ranks$rankDisplay,
    giveaways_per_game_rank = giveaways_ranks$rank,
    giveaways_per_game_rankDisplay = giveaways_ranks$rankDisplay,
    pim_per_game_rank = pim_ranks$rank,
    pim_per_game_rankDisplay = pim_ranks$rankDisplay,
    pp_goals_per_game_rank = pp_goals_ranks$rank,
    pp_goals_per_game_rankDisplay = pp_goals_ranks$rankDisplay
  )

# Note: xG% ranks will be added after STEP 1c when NST data is available.
# Initialize xG columns with NA so downstream code works regardless.
team_stats <- team_stats %>%
  mutate(
    xgf_pct = NA_real_,
    xgf_pct_rank = NA_integer_,
    xgf_pct_rankDisplay = NA_character_
  )

end_timer()

# ============================================================================
# STEP 1b: Calculate 1-month trend rankings (last 4 weeks)
# ============================================================================
start_timer("STEP 1b: Fetch recent game data for trends")
cat("\n1b. Fetching recent game data for 10-week trends...\n")

# Calculate date range for last 10 weeks (70 days) — covers both month trend and weekly Points%
today <- Sys.Date()
month_start_date <- today - days(70)

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

                # Skip international/non-NHL teams (4 Nations Face-Off etc.)
                if (!(home_abbrev %in% VALID_NHL_ABBREVS) || !(away_abbrev %in% VALID_NHL_ABBREVS)) next

                # Capture period type (REG, OT, SO) for OTL point calculation
                period_type <- tryCatch({
                  pt <- game$gameOutcome.lastPeriodType
                  if (is.null(pt) || is.na(pt)) "REG" else as.character(pt)
                }, error = function(e) "REG")

                game_info <- list(
                  game_id = game$id,
                  game_date = day_date,
                  home_team_abbrev = home_abbrev,
                  away_team_abbrev = away_abbrev,
                  home_score = as.integer(game$homeTeam.score),
                  away_score = as.integer(game$awayTeam.score),
                  period_type = period_type
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
# Filter to last 28 days for month trend (full dataset covers 70 days for weekly Points%)
month_trend_stats <- NULL
month_cutoff <- as.character(today - days(28))
if (!is.null(month_games_data) && nrow(month_games_data) > 0) {
  month_games_filtered <- month_games_data %>% filter(game_date >= month_cutoff)
  cat("Loaded", nrow(month_games_filtered), "recent games (last 28 days) for trend analysis\n")

  # Build per-team stats from games
  home_stats <- month_games_filtered %>%
    group_by(team_abbreviation = home_team_abbrev) %>%
    summarise(
      games = n(),
      wins = sum(home_score > away_score, na.rm = TRUE),
      losses = sum(home_score < away_score, na.rm = TRUE),
      goals_for = sum(home_score, na.rm = TRUE),
      goals_against = sum(away_score, na.rm = TRUE),
      .groups = "drop"
    )

  away_stats <- month_games_filtered %>%
    group_by(team_abbreviation = away_team_abbrev) %>%
    summarise(
      games = n(),
      wins = sum(away_score > home_score, na.rm = TRUE),
      losses = sum(away_score < home_score, na.rm = TRUE),
      goals_for = sum(away_score, na.rm = TRUE),
      goals_against = sum(home_score, na.rm = TRUE),
      .groups = "drop"
    )

  # Combine home and away stats, filtering out international teams (4 Nations Face-Off etc.)
  month_trend_stats <- bind_rows(home_stats, away_stats) %>%
    filter(team_abbreviation %in% VALID_NHL_ABBREVS) %>%
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

# Compute per-team per-week Points% (W*2 + OTL) / (GP*2) for last 10 weeks
# Uses the same 10-week window structure as weekly xG%
NUM_TREND_WEEKS <- 10
weekly_points_pct <- list()

if (!is.null(month_games_data) && nrow(month_games_data) > 0) {
  trend_start_pts <- today - weeks(NUM_TREND_WEEKS)

  for (week_num in 1:NUM_TREND_WEEKS) {
    week_end <- trend_start_pts + weeks(week_num)
    if (week_end > today) week_end <- today
    week_start <- week_end - days(6)

    week_key <- paste0("week-", week_num)

    # Filter games to this 7-day window
    week_games <- month_games_data %>%
      filter(game_date >= as.character(week_start) & game_date <= as.character(week_end))

    if (nrow(week_games) == 0) next

    # Build per-team W/L/OTL from this week's games (exclude international teams)
    all_teams <- unique(c(week_games$home_team_abbrev, week_games$away_team_abbrev))
    all_teams <- all_teams[all_teams %in% VALID_NHL_ABBREVS]

    for (team in all_teams) {
      team_home <- week_games %>% filter(home_team_abbrev == team)
      team_away <- week_games %>% filter(away_team_abbrev == team)

      wins <- 0L
      otl <- 0L
      gp <- 0L

      # Home games
      if (nrow(team_home) > 0) {
        for (g in seq_len(nrow(team_home))) {
          gp <- gp + 1L
          if (team_home$home_score[g] > team_home$away_score[g]) {
            wins <- wins + 1L
          } else if (team_home$period_type[g] %in% c("OT", "SO")) {
            otl <- otl + 1L
          }
        }
      }

      # Away games
      if (nrow(team_away) > 0) {
        for (g in seq_len(nrow(team_away))) {
          gp <- gp + 1L
          if (team_away$away_score[g] > team_away$home_score[g]) {
            wins <- wins + 1L
          } else if (team_away$period_type[g] %in% c("OT", "SO")) {
            otl <- otl + 1L
          }
        }
      }

      if (gp > 0) {
        pts_pct <- (wins * 2 + otl) / (gp * 2)
        if (is.null(weekly_points_pct[[team]])) {
          weekly_points_pct[[team]] <- list()
        }
        weekly_points_pct[[team]][[week_key]] <- round(pts_pct * 100, 1)
      }
    }
  }

  cat("Computed weekly Points% for", length(weekly_points_pct), "teams\n")
}

end_timer()

# ============================================================================
# STEP 1a: Scrape playoff probabilities
# ============================================================================
start_timer("STEP 1a: Scrape playoff probabilities")
cat("\n1a. Scraping NHL playoff probabilities...\n")

playoff_probabilities <- scrape_nhl_playoff_probabilities()

if (!is.null(playoff_probabilities)) {
  # Filter to only known NHL team abbreviations
  playoff_probabilities <- playoff_probabilities[names(playoff_probabilities) %in% VALID_NHL_ABBREVS]
  cat("Playoff probabilities available for", length(playoff_probabilities), "teams\n")
} else {
  cat("Warning: Playoff probabilities not available\n")
}

end_timer()

# ============================================================================
# STEP 1c: Fetch xG data from Natural Stat Trick
# ============================================================================
start_timer("STEP 1c: Fetch NST xG data")
cat("\n1c. Fetching Expected Goals (xG) data from Natural Stat Trick...\n")

# Season start date for NHL (first game typically early October)
nhl_season_start <- as.Date(paste0(NHL_SEASON_START, "-10-01"))

# 1. Season-to-date xG
nst_season_xg <- scrape_nst_team_xg(nhl_season_start, today, NHL_SEASON_ID)

# 2. Last 10 weeks (70 days) xG for month trend
nst_last_10_weeks_start <- today - days(70)
nst_last_10_weeks_xg <- scrape_nst_team_xg(nst_last_10_weeks_start, today, NHL_SEASON_ID)

# Calculate last-10-weeks xG ranks
if (!is.null(nst_last_10_weeks_xg) && nrow(nst_last_10_weeks_xg) > 0) {
  last_10_weeks_xgf_pct_ranks <- tied_rank(-nst_last_10_weeks_xg$xgf_pct)
  nst_last_10_weeks_xg <- nst_last_10_weeks_xg %>%
    mutate(
      xgf_pct_rank = last_10_weeks_xgf_pct_ranks$rank,
      xgf_pct_rankDisplay = last_10_weeks_xgf_pct_ranks$rankDisplay
    )
  cat("Calculated last-10-weeks xG% ranks for", nrow(nst_last_10_weeks_xg), "teams\n")
}

# 3. Week-by-week xG% for last 10 weeks only
NUM_TREND_WEEKS <- 10
cum_xgf_pct_by_week <- list()
weekly_xgf_pct <- list()

# Start 10 weeks back from today
trend_start <- today - weeks(NUM_TREND_WEEKS)

for (week_num in 1:NUM_TREND_WEEKS) {
  week_end <- trend_start + weeks(week_num)
  if (week_end > today) week_end <- today

  week_key <- paste0("week-", week_num)

  # Cumulative: season start through this week
  cum_data <- scrape_nst_team_xg(nhl_season_start, week_end, NHL_SEASON_ID)
  if (!is.null(cum_data) && nrow(cum_data) > 0) {
    for (i in seq_len(nrow(cum_data))) {
      team <- cum_data$team_abbreviation[i]
      if (!is.null(team) && !is.na(team)) {
        if (is.null(cum_xgf_pct_by_week[[team]])) {
          cum_xgf_pct_by_week[[team]] <- list()
        }
        cum_xgf_pct_by_week[[team]][[week_key]] <- round(cum_data$xgf_pct[i] * 100, 1)
      }
    }
  }

  # Weekly snapshot: just this week's 7-day window
  week_start <- week_end - days(6)
  snap_data <- scrape_nst_team_xg(week_start, week_end, NHL_SEASON_ID)
  if (!is.null(snap_data) && nrow(snap_data) > 0) {
    for (i in seq_len(nrow(snap_data))) {
      team <- snap_data$team_abbreviation[i]
      if (!is.null(team) && !is.na(team)) {
        if (is.null(weekly_xgf_pct[[team]])) {
          weekly_xgf_pct[[team]] <- list()
        }
        weekly_xgf_pct[[team]][[week_key]] <- round(snap_data$xgf_pct[i] * 100, 1)
      }
    }
  }
}

cat("Computed week-by-week xG% for last", NUM_TREND_WEEKS, "weeks\n")

# 4. Compute 10th-best xG% reference line per week (from cumulative data)
tenth_xgf_pct_by_week <- list()
if (length(cum_xgf_pct_by_week) > 0) {
  all_week_keys <- unique(unlist(lapply(cum_xgf_pct_by_week, names)))
  all_week_keys <- sort(all_week_keys)

  for (wk in all_week_keys) {
    week_values <- c()
    for (team in names(cum_xgf_pct_by_week)) {
      val <- cum_xgf_pct_by_week[[team]][[wk]]
      if (!is.null(val) && !is.na(val)) {
        week_values <- c(week_values, val)
      }
    }
    if (length(week_values) >= 25) {
      sorted_vals <- sort(week_values, decreasing = TRUE)
      if (length(sorted_vals) >= 10) {
        tenth_xgf_pct_by_week[[wk]] <- sorted_vals[10]
      }
    }
  }
  cat("Computed 10th-best xG% reference line for", length(tenth_xgf_pct_by_week), "weeks\n")
}

# 5. Compute league-wide min/max for cumulative xG% chart scaling
# so all matchup charts use the same Y-axis range
league_cum_xg_stats <- NULL
if (length(cum_xgf_pct_by_week) > 0) {
  all_cum_vals <- c()
  for (team in names(cum_xgf_pct_by_week)) {
    for (wk in names(cum_xgf_pct_by_week[[team]])) {
      val <- cum_xgf_pct_by_week[[team]][[wk]]
      if (!is.null(val) && !is.na(val)) {
        all_cum_vals <- c(all_cum_vals, val)
      }
    }
  }
  if (length(all_cum_vals) > 0) {
    league_cum_xg_stats <- list(
      minCumXgPct = round(min(all_cum_vals), 1),
      maxCumXgPct = round(max(all_cum_vals), 1)
    )
    cat("League cumulative xG% bounds: [", league_cum_xg_stats$minCumXgPct, "-",
        league_cum_xg_stats$maxCumXgPct, "]\n")
  }
}

# Join season xG% data to team_stats and compute ranks
if (!is.null(nst_season_xg) && nrow(nst_season_xg) > 0) {
  nst_join <- nst_season_xg %>%
    select(team_abbreviation, xgf_pct_nst = xgf_pct)

  team_stats <- team_stats %>%
    left_join(nst_join, by = "team_abbreviation") %>%
    mutate(
      xgf_pct = ifelse(!is.na(xgf_pct_nst), xgf_pct_nst, xgf_pct)
    ) %>%
    select(-xgf_pct_nst)

  # Compute xG% ranks
  xgf_pct_ranks <- tied_rank(-team_stats$xgf_pct)
  team_stats <- team_stats %>%
    mutate(
      xgf_pct_rank = xgf_pct_ranks$rank,
      xgf_pct_rankDisplay = xgf_pct_ranks$rankDisplay
    )
  cat("Joined season xG% data and computed ranks for", sum(!is.na(team_stats$xgf_pct)), "teams\n")
}

end_timer()

# Compute league-wide averages and bounds for xG% vs Points% scatter plot
# so all matchup charts use consistent quadrant placement and axis scaling
league_xg_vs_pts <- NULL
if (length(weekly_xgf_pct) > 0 && length(weekly_points_pct) > 0) {
  all_xg_vals <- c()
  all_pts_vals <- c()

  for (team in names(weekly_xgf_pct)) {
    for (wk in names(weekly_xgf_pct[[team]])) {
      xg_val <- weekly_xgf_pct[[team]][[wk]]
      pts_val <- weekly_points_pct[[team]][[wk]]
      if (!is.null(xg_val) && !is.null(pts_val) && !is.na(xg_val) && !is.na(pts_val)) {
        all_xg_vals <- c(all_xg_vals, xg_val)
        all_pts_vals <- c(all_pts_vals, pts_val)
      }
    }
  }

  if (length(all_xg_vals) > 0) {
    league_xg_vs_pts <- list(
      avgXgPct = round(mean(all_xg_vals), 1),
      avgPointsPct = round(mean(all_pts_vals), 1),
      minXgPct = round(min(all_xg_vals), 1),
      maxXgPct = round(max(all_xg_vals), 1),
      minPointsPct = round(min(all_pts_vals), 1),
      maxPointsPct = round(max(all_pts_vals), 1)
    )
    cat("League xG% vs Points% bounds: xG [", league_xg_vs_pts$minXgPct, "-",
        league_xg_vs_pts$maxXgPct, "], Points [", league_xg_vs_pts$minPointsPct, "-",
        league_xg_vs_pts$maxPointsPct, "]\n")
  }
}

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
  player_gp_ranks <- tied_rank(-player_stats$games_played)
  player_ppg_ranks <- tied_rank(-player_stats$points_per_game)

  player_stats <- player_stats %>%
    mutate(
      points_rank = player_pts_ranks$rank,
      points_rankDisplay = player_pts_ranks$rankDisplay,
      goals_rank = player_goals_ranks$rank,
      goals_rankDisplay = player_goals_ranks$rankDisplay,
      assists_rank = player_assists_ranks$rank,
      assists_rankDisplay = player_assists_ranks$rankDisplay,
      plus_minus_rank = player_pm_ranks$rank,
      plus_minus_rankDisplay = player_pm_ranks$rankDisplay,
      games_played_rank = player_gp_ranks$rank,
      games_played_rankDisplay = player_gp_ranks$rankDisplay,
      points_per_game_rank = player_ppg_ranks$rank,
      points_per_game_rankDisplay = player_ppg_ranks$rankDisplay
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
# Each API call returns ~1 week of games, so we step through the range
# in 7-day increments to ensure full coverage with no gaps
all_games <- list()
seen_game_ids <- character(0)  # Track game IDs to avoid duplicates

# Generate dates every 7 days across the range to ensure no gaps
schedule_dates <- character(0)
d <- start_date
while (d <= end_date) {
  schedule_dates <- c(schedule_dates, format(d, "%Y-%m-%d"))
  d <- d + days(7)
}
# Always include end_date to cover the tail
if (as.Date(tail(schedule_dates, 1)) < end_date) {
  schedule_dates <- c(schedule_dates, format(end_date, "%Y-%m-%d"))
}

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
    list(key = "pointsPct", label = "Points %", value_home = home_stats$points_pct, rank_home = home_stats$points_pct_rank, rankDisplay_home = home_stats$points_pct_rankDisplay, value_away = away_stats$points_pct, rank_away = away_stats$points_pct_rank, rankDisplay_away = away_stats$points_pct_rankDisplay),
    list(key = "goalsPerGame", label = "Goals/Game", value_home = home_stats$goals_per_game, rank_home = home_stats$goals_per_game_rank, rankDisplay_home = home_stats$goals_per_game_rankDisplay, value_away = away_stats$goals_per_game, rank_away = away_stats$goals_per_game_rank, rankDisplay_away = away_stats$goals_per_game_rankDisplay),
    list(key = "goalDiffPerGame", label = "Goal Diff/Game", value_home = home_stats$goal_diff_per_game, rank_home = home_stats$goal_diff_per_game_rank, rankDisplay_home = home_stats$goal_diff_per_game_rankDisplay, value_away = away_stats$goal_diff_per_game, rank_away = away_stats$goal_diff_per_game_rank, rankDisplay_away = away_stats$goal_diff_per_game_rankDisplay),
    list(key = "shotsForPerGame", label = "Shots/Game", value_home = home_stats$shots_for_per_game, rank_home = home_stats$shots_for_per_game_rank, rankDisplay_home = home_stats$shots_for_per_game_rankDisplay, value_away = away_stats$shots_for_per_game, rank_away = away_stats$shots_for_per_game_rank, rankDisplay_away = away_stats$shots_for_per_game_rankDisplay),
    list(key = "powerPlayPct", label = "Power Play %", value_home = home_stats$power_play_pct, rank_home = home_stats$power_play_pct_rank, rankDisplay_home = home_stats$power_play_pct_rankDisplay, value_away = away_stats$power_play_pct, rank_away = away_stats$power_play_pct_rank, rankDisplay_away = away_stats$power_play_pct_rankDisplay),
    list(key = "ppGoalsPerGame", label = "PP Goals/Game", value_home = home_stats$pp_goals_per_game, rank_home = home_stats$pp_goals_per_game_rank, rankDisplay_home = home_stats$pp_goals_per_game_rankDisplay, value_away = away_stats$pp_goals_per_game, rank_away = away_stats$pp_goals_per_game_rank, rankDisplay_away = away_stats$pp_goals_per_game_rankDisplay),
    list(key = "faceoffWinPct", label = "Faceoff Win %", value_home = home_stats$faceoff_win_pct, rank_home = home_stats$faceoff_win_pct_rank, rankDisplay_home = home_stats$faceoff_win_pct_rankDisplay, value_away = away_stats$faceoff_win_pct, rank_away = away_stats$faceoff_win_pct_rank, rankDisplay_away = away_stats$faceoff_win_pct_rankDisplay),
    list(key = "hitsPerGame", label = "Hits/Game", value_home = home_stats$hits_per_game, rank_home = home_stats$hits_per_game_rank, rankDisplay_home = home_stats$hits_per_game_rankDisplay, value_away = away_stats$hits_per_game, rank_away = away_stats$hits_per_game_rank, rankDisplay_away = away_stats$hits_per_game_rankDisplay),
    list(key = "takeawaysPerGame", label = "Takeaways/Game", value_home = home_stats$takeaways_per_game, rank_home = home_stats$takeaways_per_game_rank, rankDisplay_home = home_stats$takeaways_per_game_rankDisplay, value_away = away_stats$takeaways_per_game, rank_away = away_stats$takeaways_per_game_rank, rankDisplay_away = away_stats$takeaways_per_game_rankDisplay),
    list(key = "xgfPct", label = "xG% (5v5)", value_home = home_stats$xgf_pct, rank_home = home_stats$xgf_pct_rank, rankDisplay_home = home_stats$xgf_pct_rankDisplay, value_away = away_stats$xgf_pct, rank_away = away_stats$xgf_pct_rank, rankDisplay_away = away_stats$xgf_pct_rankDisplay)
  )

  for (stat in off_stats) {
    off_comparison[[stat$key]] <- list(
      label = stat$label,
      home = list(
        value = if (!is.na(stat$value_home)) round(stat$value_home, 4) else NULL,
        rank = if (!is.na(stat$rank_home)) as.integer(stat$rank_home) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_home)) stat$rankDisplay_home else NULL
      ),
      away = list(
        value = if (!is.na(stat$value_away)) round(stat$value_away, 4) else NULL,
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
    list(key = "penaltyKillPct", label = "Penalty Kill %", value_home = home_stats$penalty_kill_pct, rank_home = home_stats$penalty_kill_pct_rank, rankDisplay_home = home_stats$penalty_kill_pct_rankDisplay, value_away = away_stats$penalty_kill_pct, rank_away = away_stats$penalty_kill_pct_rank, rankDisplay_away = away_stats$penalty_kill_pct_rankDisplay),
    list(key = "blocksPerGame", label = "Blocks/Game", value_home = home_stats$blocks_per_game, rank_home = home_stats$blocks_per_game_rank, rankDisplay_home = home_stats$blocks_per_game_rankDisplay, value_away = away_stats$blocks_per_game, rank_away = away_stats$blocks_per_game_rank, rankDisplay_away = away_stats$blocks_per_game_rankDisplay),
    list(key = "giveawaysPerGame", label = "Giveaways/Game", value_home = home_stats$giveaways_per_game, rank_home = home_stats$giveaways_per_game_rank, rankDisplay_home = home_stats$giveaways_per_game_rankDisplay, value_away = away_stats$giveaways_per_game, rank_away = away_stats$giveaways_per_game_rank, rankDisplay_away = away_stats$giveaways_per_game_rankDisplay),
    list(key = "pimPerGame", label = "PIM/Game", value_home = home_stats$pim_per_game, rank_home = home_stats$pim_per_game_rank, rankDisplay_home = home_stats$pim_per_game_rankDisplay, value_away = away_stats$pim_per_game, rank_away = away_stats$pim_per_game_rank, rankDisplay_away = away_stats$pim_per_game_rankDisplay)
  )

  for (stat in def_stats) {
    def_comparison[[stat$key]] <- list(
      label = stat$label,
      home = list(
        value = if (!is.na(stat$value_home)) round(stat$value_home, 4) else NULL,
        rank = if (!is.na(stat$rank_home)) as.integer(stat$rank_home) else NULL,
        rankDisplay = if (!is.na(stat$rankDisplay_home)) stat$rankDisplay_home else NULL
      ),
      away = list(
        value = if (!is.na(stat$value_away)) round(stat$value_away, 4) else NULL,
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
      key = "shots",
      off_label = "Shots/Game",
      def_label = "Shots Against/Game",
      off_stat = "shots_for_per_game",
      def_stat = "shots_against_per_game",
      off_rank = "shots_for_per_game_rank",
      def_rank = "shots_against_per_game_rank",
      off_rankDisplay = "shots_for_per_game_rankDisplay",
      def_rankDisplay = "shots_against_per_game_rankDisplay"
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
      key = "ppGoals",
      off_label = "PP Goals/G",
      def_label = "Penalty Kill %",
      off_stat = "pp_goals_per_game",
      def_stat = "penalty_kill_pct",
      off_rank = "pp_goals_per_game_rank",
      def_rank = "penalty_kill_pct_rank",
      off_rankDisplay = "pp_goals_per_game_rankDisplay",
      def_rankDisplay = "penalty_kill_pct_rankDisplay"
    ),
    list(
      key = "faceoffs",
      off_label = "Faceoff %",
      def_label = "Faceoff %",
      off_stat = "faceoff_win_pct",
      def_stat = "faceoff_win_pct",
      off_rank = "faceoff_win_pct_rank",
      def_rank = "faceoff_win_pct_rank",
      off_rankDisplay = "faceoff_win_pct_rankDisplay",
      def_rankDisplay = "faceoff_win_pct_rankDisplay"
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
          value = round(off_val, 4),
          rank = as.integer(off_rank),
          rankDisplay = home_stats[[matchup$off_rankDisplay]]
        ),
        defense = list(
          team = away_team,
          value = round(def_val, 4),
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
          value = round(off_val, 4),
          rank = as.integer(off_rank),
          rankDisplay = away_stats[[matchup$off_rankDisplay]]
        ),
        defense = list(
          team = home_team,
          value = round(def_val, 4),
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
      powerPlayPct = round(home_stats_row$power_play_pct, 4),
      powerPlayPctRank = as.integer(home_stats_row$power_play_pct_rank),
      powerPlayPctRankDisplay = home_stats_row$power_play_pct_rankDisplay,
      penaltyKillPct = round(home_stats_row$penalty_kill_pct, 4),
      penaltyKillPctRank = as.integer(home_stats_row$penalty_kill_pct_rank),
      penaltyKillPctRankDisplay = home_stats_row$penalty_kill_pct_rankDisplay,
      faceoffWinPct = round(home_stats_row$faceoff_win_pct, 4),
      faceoffWinPctRank = as.integer(home_stats_row$faceoff_win_pct_rank),
      faceoffWinPctRankDisplay = home_stats_row$faceoff_win_pct_rankDisplay,
      pointsPct = round(home_stats_row$points_pct, 4),
      pointsPctRank = as.integer(home_stats_row$points_pct_rank),
      pointsPctRankDisplay = home_stats_row$points_pct_rankDisplay,
      xgfPct = if (!is.na(home_stats_row$xgf_pct)) round(home_stats_row$xgf_pct, 4) else NULL,
      xgfPctRank = if (!is.na(home_stats_row$xgf_pct_rank)) as.integer(home_stats_row$xgf_pct_rank) else NULL,
      xgfPctRankDisplay = if (!is.na(home_stats_row$xgf_pct_rankDisplay)) home_stats_row$xgf_pct_rankDisplay else NULL
    )
  )

  # Add week-by-week xG% data for home team
  if (!is.null(cum_xgf_pct_by_week[[game$home_team_abbrev]])) {
    home_team_data$stats$cumXgfPctByWeek <- cum_xgf_pct_by_week[[game$home_team_abbrev]]
  }
  if (!is.null(weekly_xgf_pct[[game$home_team_abbrev]])) {
    home_team_data$stats$weeklyXgfPct <- weekly_xgf_pct[[game$home_team_abbrev]]
  }
  if (!is.null(weekly_points_pct[[game$home_team_abbrev]])) {
    home_team_data$stats$weeklyPointsPct <- weekly_points_pct[[game$home_team_abbrev]]
  }

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

      # Add last-10-weeks xG% to home team's monthTrend
      if (!is.null(nst_last_10_weeks_xg) && nrow(nst_last_10_weeks_xg) > 0) {
        home_trend_xg <- nst_last_10_weeks_xg %>% filter(team_abbreviation == game$home_team_abbrev)
        if (nrow(home_trend_xg) > 0) {
          home_team_data$stats$monthTrend$xgfPct <- list(
            value = round(home_trend_xg$xgf_pct, 4),
            rank = as.integer(home_trend_xg$xgf_pct_rank),
            rankDisplay = home_trend_xg$xgf_pct_rankDisplay
          )
        }
      }
    }
  }

  # Add playoff probability for home team
  if (!is.null(playoff_probabilities) && !is.null(playoff_probabilities[[game$home_team_abbrev]])) {
    home_prob <- playoff_probabilities[[game$home_team_abbrev]]
    home_team_data$playoffProbability <- list(
      playoffProb = home_prob$playoffProb,
      confChampProb = home_prob$confChampProb,
      finalsProb = home_prob$finalsProb,
      champProb = home_prob$champProb
    )
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
      powerPlayPct = round(away_stats_row$power_play_pct, 4),
      powerPlayPctRank = as.integer(away_stats_row$power_play_pct_rank),
      powerPlayPctRankDisplay = away_stats_row$power_play_pct_rankDisplay,
      penaltyKillPct = round(away_stats_row$penalty_kill_pct, 4),
      penaltyKillPctRank = as.integer(away_stats_row$penalty_kill_pct_rank),
      penaltyKillPctRankDisplay = away_stats_row$penalty_kill_pct_rankDisplay,
      faceoffWinPct = round(away_stats_row$faceoff_win_pct, 4),
      faceoffWinPctRank = as.integer(away_stats_row$faceoff_win_pct_rank),
      faceoffWinPctRankDisplay = away_stats_row$faceoff_win_pct_rankDisplay,
      pointsPct = round(away_stats_row$points_pct, 4),
      pointsPctRank = as.integer(away_stats_row$points_pct_rank),
      pointsPctRankDisplay = away_stats_row$points_pct_rankDisplay,
      xgfPct = if (!is.na(away_stats_row$xgf_pct)) round(away_stats_row$xgf_pct, 4) else NULL,
      xgfPctRank = if (!is.na(away_stats_row$xgf_pct_rank)) as.integer(away_stats_row$xgf_pct_rank) else NULL,
      xgfPctRankDisplay = if (!is.na(away_stats_row$xgf_pct_rankDisplay)) away_stats_row$xgf_pct_rankDisplay else NULL
    )
  )

  # Add week-by-week xG% data for away team
  if (!is.null(cum_xgf_pct_by_week[[game$away_team_abbrev]])) {
    away_team_data$stats$cumXgfPctByWeek <- cum_xgf_pct_by_week[[game$away_team_abbrev]]
  }
  if (!is.null(weekly_xgf_pct[[game$away_team_abbrev]])) {
    away_team_data$stats$weeklyXgfPct <- weekly_xgf_pct[[game$away_team_abbrev]]
  }
  if (!is.null(weekly_points_pct[[game$away_team_abbrev]])) {
    away_team_data$stats$weeklyPointsPct <- weekly_points_pct[[game$away_team_abbrev]]
  }

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

      # Add last-10-weeks xG% to away team's monthTrend
      if (!is.null(nst_last_10_weeks_xg) && nrow(nst_last_10_weeks_xg) > 0) {
        away_trend_xg <- nst_last_10_weeks_xg %>% filter(team_abbreviation == game$away_team_abbrev)
        if (nrow(away_trend_xg) > 0) {
          away_team_data$stats$monthTrend$xgfPct <- list(
            value = round(away_trend_xg$xgf_pct, 4),
            rank = as.integer(away_trend_xg$xgf_pct_rank),
            rankDisplay = away_trend_xg$xgf_pct_rankDisplay
          )
        }
      }
    }
  }

  # Add playoff probability for away team
  if (!is.null(playoff_probabilities) && !is.null(playoff_probabilities[[game$away_team_abbrev]])) {
    away_prob <- playoff_probabilities[[game$away_team_abbrev]]
    away_team_data$playoffProbability <- list(
      playoffProb = away_prob$playoffProb,
      confChampProb = away_prob$confChampProb,
      finalsProb = away_prob$finalsProb,
      champProb = away_prob$champProb
    )
  }

  # Build player data
  home_players_data <- if (!is.null(home_players) && nrow(home_players) > 0) {
    lapply(seq_len(nrow(home_players)), function(i) {
      p <- home_players[i, ]
      na_to_null <- function(x) if (length(x) == 0 || is.null(x) || is.na(x)) NULL else x

      list(
        name = p$player_name,
        position = p$position,
        gamesPlayed = list(
          value = as.integer(p$games_played),
          rank = na_to_null(as.integer(p$games_played_rank)),
          rankDisplay = na_to_null(p$games_played_rankDisplay)
        ),
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
        pointsPerGame = list(
          value = round(p$points_per_game, 2),
          rank = na_to_null(as.integer(p$points_per_game_rank)),
          rankDisplay = na_to_null(p$points_per_game_rankDisplay)
        )
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
        gamesPlayed = list(
          value = as.integer(p$games_played),
          rank = na_to_null(as.integer(p$games_played_rank)),
          rankDisplay = na_to_null(p$games_played_rankDisplay)
        ),
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
        pointsPerGame = list(
          value = round(p$points_per_game, 2),
          rank = na_to_null(as.integer(p$points_per_game_rank)),
          rankDisplay = na_to_null(p$points_per_game_rankDisplay)
        )
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

  # Add 10th xG% reference line at matchup level
  if (length(tenth_xgf_pct_by_week) > 0) {
    matchup$tenthXgfPctByWeek <- tenth_xgf_pct_by_week
  }

  # Add league-wide xG% vs Points% stats for consistent scatter plot scaling
  if (!is.null(league_xg_vs_pts)) {
    matchup$leagueXgVsPointsStats <- league_xg_vs_pts
  }

  # Add league-wide cumulative xG% bounds for consistent line chart scaling
  if (!is.null(league_cum_xg_stats)) {
    matchup$leagueCumXgStats <- league_cum_xg_stats
  }

  # Add results for completed games (with rate limiting)
  if (isTRUE(game$game_completed) && results_fetched < MAX_RESULTS_GAMES) {
    result_start <- Sys.time()
    cat("  -> Game completed, fetching results... (", results_fetched + 1, ")\n")
    matchup$results <- build_game_results(game, home_stats_row, away_stats_row)

    # Add per-game xG from NST
    game_xg <- scrape_nst_game_xg(game$game_id)
    if (!is.null(game_xg)) {
      matchup$results$teamBoxScore$home$xgf <- round(game_xg$home_xgf, 2)
      matchup$results$teamBoxScore$home$xga <- round(game_xg$home_xga, 2)
      matchup$results$teamBoxScore$away$xgf <- round(game_xg$away_xgf, 2)
      matchup$results$teamBoxScore$away$xga <- round(game_xg$away_xga, 2)
      cat("  -> Added per-game xG data from NST\n")
    }

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

# Build rankings dictionary: for each stat, an array of all teams sorted by rank
build_rankings <- function(team_stats, stat_col, rank_col, rankDisplay_col) {
  valid <- !is.na(team_stats[[rank_col]])
  if (!any(valid)) return(list())

  df <- data.frame(
    team = team_stats$team_abbreviation[valid],
    rank = as.integer(team_stats[[rank_col]][valid]),
    rankDisplay = team_stats[[rankDisplay_col]][valid],
    value = round(team_stats[[stat_col]][valid], 4),
    stringsAsFactors = FALSE
  )
  df <- df[order(df$rank), ]

  lapply(seq_len(nrow(df)), function(i) {
    list(rank = df$rank[i], rankDisplay = df$rankDisplay[i], value = df$value[i], team = df$team[i])
  })
}

rankings <- list(
  goalsPerGame = build_rankings(team_stats, "goals_per_game", "goals_per_game_rank", "goals_per_game_rankDisplay"),
  goalsAgainstPerGame = build_rankings(team_stats, "goals_against_per_game", "goals_against_per_game_rank", "goals_against_per_game_rankDisplay"),
  goalDiffPerGame = build_rankings(team_stats, "goal_diff_per_game", "goal_diff_per_game_rank", "goal_diff_per_game_rankDisplay"),
  shotsForPerGame = build_rankings(team_stats, "shots_for_per_game", "shots_for_per_game_rank", "shots_for_per_game_rankDisplay"),
  shotsAgainstPerGame = build_rankings(team_stats, "shots_against_per_game", "shots_against_per_game_rank", "shots_against_per_game_rankDisplay"),
  powerPlayPct = build_rankings(team_stats, "power_play_pct", "power_play_pct_rank", "power_play_pct_rankDisplay"),
  penaltyKillPct = build_rankings(team_stats, "penalty_kill_pct", "penalty_kill_pct_rank", "penalty_kill_pct_rankDisplay"),
  faceoffWinPct = build_rankings(team_stats, "faceoff_win_pct", "faceoff_win_pct_rank", "faceoff_win_pct_rankDisplay"),
  pointsPct = build_rankings(team_stats, "points_pct", "points_pct_rank", "points_pct_rankDisplay"),
  hitsPerGame = build_rankings(team_stats, "hits_per_game", "hits_per_game_rank", "hits_per_game_rankDisplay"),
  blocksPerGame = build_rankings(team_stats, "blocks_per_game", "blocks_per_game_rank", "blocks_per_game_rankDisplay"),
  takeawaysPerGame = build_rankings(team_stats, "takeaways_per_game", "takeaways_per_game_rank", "takeaways_per_game_rankDisplay"),
  giveawaysPerGame = build_rankings(team_stats, "giveaways_per_game", "giveaways_per_game_rank", "giveaways_per_game_rankDisplay"),
  pimPerGame = build_rankings(team_stats, "pim_per_game", "pim_per_game_rank", "pim_per_game_rankDisplay"),
  ppGoalsPerGame = build_rankings(team_stats, "pp_goals_per_game", "pp_goals_per_game_rank", "pp_goals_per_game_rankDisplay")
)

# Add xG% rankings if available
if ("xgf_pct_rank" %in% names(team_stats)) {
  rankings$xgfPct <- build_rankings(team_stats, "xgf_pct", "xgf_pct_rank", "xgf_pct_rankDisplay")
}

# Add one-month trend rankings if available
if (!is.null(month_trend_stats) && nrow(month_trend_stats) > 0) {
  rankings$trendRecord <- build_rankings(month_trend_stats, "win_pct", "record_rank", "record_rankDisplay")
  rankings$trendGoalsPerGame <- build_rankings(month_trend_stats, "goals_per_game", "gpg_rank", "gpg_rankDisplay")
  rankings$trendGoalsAgainstPerGame <- build_rankings(month_trend_stats, "goals_against_per_game", "gapg_rank", "gapg_rankDisplay")
  rankings$trendGoalDiffPerGame <- build_rankings(month_trend_stats, "goal_diff_per_game", "goal_diff_rank", "goal_diff_rankDisplay")
}

# Add trend xG% rankings if available
if (!is.null(nst_last_10_weeks_xg) && nrow(nst_last_10_weeks_xg) > 0 && "xgf_pct_rank" %in% names(nst_last_10_weeks_xg)) {
  rankings$trendXgfPct <- build_rankings(nst_last_10_weeks_xg, "xgf_pct", "xgf_pct_rank", "xgf_pct_rankDisplay")
}

# Build playoff chances list if available
playoff_chances_list <- list()
if (!is.null(playoff_probabilities) && length(playoff_probabilities) > 0) {
  po_df <- do.call(rbind, lapply(names(playoff_probabilities), function(team) {
    prob <- playoff_probabilities[[team]]
    data.frame(
      team = team,
      playoffProb = if (!is.null(prob$playoffProb)) prob$playoffProb else 0,
      champProb = if (!is.null(prob$champProb)) prob$champProb else 0,
      stringsAsFactors = FALSE
    )
  }))
  po_df <- po_df[order(-po_df$champProb, -po_df$playoffProb), ]
  playoff_chances_list <- lapply(seq_len(nrow(po_df)), function(i) {
    list(team = po_df$team[i], playoffProb = po_df$playoffProb[i], champProb = po_df$champProb[i])
  })
}

output_data <- list(
  sport = "NHL",
  visualizationType = "NHL_MATCHUP",
  title = paste0("NHL Matchups - ", format(start_date, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("Games from the past", DAYS_BEHIND, "days and next", DAYS_AHEAD, "days with results and stats"),
  description = paste0("Detailed NHL matchup statistics including team performance metrics, player stats, and recent form.\n\nPLAYOFFS:\n\n \u2022 16 teams make the Stanley Cup Playoffs (8 per conference)\n\n \u2022 Top 3 teams in each division qualify automatically (12 total)\n\n \u2022 2 wild card spots per conference go to the next best records regardless of division\n\n \u2022 All rounds are best-of-7 series\n\n \u2022 4 rounds: First Round, Second Round, Conference Finals, Stanley Cup Final\n\nTEAM STATS:\n\n \u2022 Goals Per Game: Average goals scored per game\n\n \u2022 Goals Against Per Game: Average goals allowed per game (lower is better)\n\n \u2022 Goal Differential: Goals For - Goals Against per game\n\n \u2022 Shots Per Game: Average shots on goal per game\n\n \u2022 Power Play %: Success rate on power plays\n\n \u2022 Penalty Kill %: Success rate killing penalties\n\n \u2022 Faceoff Win %: Percentage of faceoffs won\n\nCHARTS:\n\n \u2022 xG% (Expected Goals Percentage): Measures the share of expected goals a team generates at 5v5. Unlike actual goals, xG is based on shot quality (location, type, and game context), making it a better indicator of underlying team strength because it filters out luck and goaltending variance. Teams with a high xG% are consistently creating more dangerous chances than they allow, regardless of whether those chances happen to go in.\n\n \u2022 Points %: A team's points earned as a percentage of points available, calculated as (W\u00d72 + OTL) / (GP\u00d72). This captures actual results including overtime/shootout loser points, giving a complete picture of standings performance. Together, xG% vs Points% reveals whether a team's results match their underlying play\u2014teams with high xG% but low Points% are likely underperforming their talent (unlucky), while the reverse suggests overperformance that may not be sustainable.\n\nPLAYER STATS:\n\n \u2022 Goals: Total goals scored\n\n \u2022 Assists: Total assists\n\n \u2022 Points: Goals + Assists\n\n \u2022 Plus/Minus: Goal differential when on ice\n\nAll stats are season totals through the current date. Players must have played at least ", MIN_GAMES_PLAYED, " games to be included."),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "NHL Stats API / PlayoffStatus.com / Natural Stat Trick",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  rankings = rankings,
  playoffChances = playoff_chances_list,
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
