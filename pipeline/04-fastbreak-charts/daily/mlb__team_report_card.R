#!/usr/bin/env Rscript

# MLB Team Report Card — per-team player leaderboards grouped by role.
# Pulls qualified hitters, starters, relievers, and fielders from FanGraphs,
# ranks each player's advanced stats league-wide, and surfaces the top players
# on every team. Players can appear in multiple categories (e.g. two-way players).
#
# Stat groups mirror advanced metrics used elsewhere in the MLB pipeline:
#  - Hitters:    wRC+ (production) + xwOBA (contact quality) + xBA + Barrel%
#  - Starters:   K-BB% (command) + xFIP + SIERA + ERA (official)
#  - Relievers:  K-BB% (miss+command) + FIP + SV + SIERA + ERA (official)
#  - Fielders:   OAA (Statcast range) + DRS (comprehensive defense) + FRP

library(baseballr)
library(dplyr)
library(jsonlite)
library(rvest)
library(httr)

# ============================================================================
# Constants
# ============================================================================
current_year <- as.numeric(format(Sys.Date(), "%Y"))
current_month <- as.numeric(format(Sys.Date(), "%m"))
mlb_season <- if (current_month >= 3) current_year else current_year - 1
season_label <- paste0(mlb_season, "-", substr(mlb_season + 1, 3, 4))

TOP_N <- 10

MIN_PA <- 100
MIN_G_HIT <- 25

MIN_STARTER_IP <- 20
MIN_STARTER_GS <- 4

MIN_RELIEVER_IP <- 10
MAX_RELIEVER_GS <- 3

MIN_FIELDER_INN <- 200

FG_TO_APP_ABBREV <- c(
  "CHW" = "CWS",
  "TBR" = "TB",
  "WSN" = "WSH",
  "SDP" = "SD",
  "SFG" = "SF",
  "KCR" = "KC",
  "ATH" = "OAK"
)

team_divisions <- c(
  "ARI" = "NL West", "ATL" = "NL East", "BAL" = "AL East", "BOS" = "AL East",
  "CHC" = "NL Central", "CWS" = "AL Central", "CHW" = "AL Central",
  "CIN" = "NL Central", "CLE" = "AL Central", "COL" = "NL West", "DET" = "AL Central",
  "HOU" = "AL West", "KC" = "AL Central", "KCR" = "AL Central",
  "LAA" = "AL West", "LAD" = "NL West", "MIA" = "NL East", "MIL" = "NL Central",
  "MIN" = "AL Central", "NYM" = "NL East", "NYY" = "AL East",
  "OAK" = "AL West", "ATH" = "AL West",
  "PHI" = "NL East", "PIT" = "NL Central", "SD" = "NL West", "SDP" = "NL West",
  "SEA" = "AL West", "SF" = "NL West", "SFG" = "NL West",
  "STL" = "NL Central", "TB" = "AL East", "TBR" = "AL East",
  "TEX" = "AL West", "TOR" = "AL East", "WSH" = "NL East", "WSN" = "NL East"
)

team_leagues <- c(
  "ARI" = "National", "ATL" = "National", "BAL" = "American", "BOS" = "American",
  "CHC" = "National", "CWS" = "American", "CHW" = "American",
  "CIN" = "National", "CLE" = "American", "COL" = "National", "DET" = "American",
  "HOU" = "American", "KC" = "American", "KCR" = "American",
  "LAA" = "American", "LAD" = "National", "MIA" = "National", "MIL" = "National",
  "MIN" = "American", "NYM" = "National", "NYY" = "American",
  "OAK" = "American", "ATH" = "American",
  "PHI" = "National", "PIT" = "National", "SD" = "National", "SDP" = "National",
  "SEA" = "American", "SF" = "National", "SFG" = "National",
  "STL" = "National", "TB" = "American", "TBR" = "American",
  "TEX" = "American", "TOR" = "American", "WSH" = "National", "WSN" = "National"
)

team_names <- c(
  "ARI" = "Arizona Diamondbacks", "ATL" = "Atlanta Braves", "BAL" = "Baltimore Orioles",
  "BOS" = "Boston Red Sox", "CHC" = "Chicago Cubs", "CWS" = "Chicago White Sox",
  "CIN" = "Cincinnati Reds", "CLE" = "Cleveland Guardians", "COL" = "Colorado Rockies",
  "DET" = "Detroit Tigers", "HOU" = "Houston Astros", "KC" = "Kansas City Royals",
  "LAA" = "Los Angeles Angels", "LAD" = "Los Angeles Dodgers", "MIA" = "Miami Marlins",
  "MIL" = "Milwaukee Brewers", "MIN" = "Minnesota Twins", "NYM" = "New York Mets",
  "NYY" = "New York Yankees", "OAK" = "Oakland Athletics", "PHI" = "Philadelphia Phillies",
  "PIT" = "Pittsburgh Pirates", "SD" = "San Diego Padres", "SEA" = "Seattle Mariners",
  "SF" = "San Francisco Giants", "STL" = "St. Louis Cardinals", "TB" = "Tampa Bay Rays",
  "TEX" = "Texas Rangers", "TOR" = "Toronto Blue Jays", "WSH" = "Washington Nationals"
)

ALL_TEAMS <- names(team_names)

MLB_TEAM_NAME_TO_ABBREV <- c(
  "Braves" = "ATL", "Dodgers" = "LAD", "Yankees" = "NYY", "Brewers" = "MIL",
  "Rays" = "TB", "Guardians" = "CLE", "White Sox" = "CWS", "Cardinals" = "STL",
  "Mariners" = "SEA", "Athletics" = "OAK", "Phillies" = "PHI", "Rangers" = "TEX",
  "Cubs" = "CHC", "Nationals" = "WSH", "Padres" = "SD", "Blue Jays" = "TOR",
  "Pirates" = "PIT", "Diamondbacks" = "ARI", "D. Backs" = "ARI", "D-backs" = "ARI",
  "Astros" = "HOU", "Marlins" = "MIA", "Orioles" = "BAL", "Twins" = "MIN",
  "Reds" = "CIN", "Red Sox" = "BOS", "Angels" = "LAA", "Tigers" = "DET",
  "Mets" = "NYM", "Royals" = "KC", "Giants" = "SF", "Rockies" = "COL"
)

# ============================================================================
# Helpers
# ============================================================================
format_ordinal <- function(n) {
  if (is.na(n)) return(NA_character_)
  suffix <- if (n %% 100 %in% 11:13) {
    "th"
  } else if (n %% 10 == 1) {
    "st"
  } else if (n %% 10 == 2) {
    "nd"
  } else if (n %% 10 == 3) {
    "rd"
  } else {
    "th"
  }
  paste0(n, suffix)
}

scrape_mlb_playoff_probabilities <- function() {
  url <- "https://www.playoffstatus.com/mlb/mlbpostseasonprob.html"

  tryCatch({
    cat("Scraping MLB playoff probabilities from playoffstatus.com...\n")

    page <- read_html(url)
    tables <- page %>% html_elements("table")

    if (length(tables) == 0) {
      cat("Warning: No tables found on MLB playoff probability page\n")
      return(NULL)
    }

    prob_table <- NULL
    for (tbl in tables) {
      rows <- tbl %>% html_elements("tr")
      if (length(rows) > 20) {
        prob_table <- tbl
        break
      }
    }
    if (is.null(prob_table)) {
      prob_table <- tables[[1]]
    }

    rows <- prob_table %>% html_elements("tr")
    results <- list()

    for (row in rows) {
      cells <- row %>% html_elements("td")
      if (length(cells) < 9) next

      team_link <- cells[[1]] %>% html_element("a")
      team_name <- if (!is.na(team_link)) {
        team_link %>% html_text(trim = TRUE)
      } else {
        cells[[1]] %>% html_text(trim = TRUE)
      }
      team_name <- gsub("^\\s+|\\s+$", "", team_name)

      if (team_name == "" || grepl("Team|League|Record", team_name, ignore.case = TRUE)) {
        next
      }

      team_abbrev <- MLB_TEAM_NAME_TO_ABBREV[team_name]
      if (is.na(team_abbrev)) {
        for (name in names(MLB_TEAM_NAME_TO_ABBREV)) {
          if (grepl(name, team_name, ignore.case = TRUE)) {
            team_abbrev <- MLB_TEAM_NAME_TO_ABBREV[name]
            break
          }
        }
      }
      if (is.na(team_abbrev)) {
        cat("Warning: Could not map MLB team name:", team_name, "\n")
        next
      }

      parse_pct <- function(cell) {
        text <- cell %>% html_text(trim = TRUE)
        if (grepl("^<\\s*1", text)) return(0.5)
        text <- gsub("[%<>]", "", text)
        text <- gsub("\\s+", "", text)
        val <- suppressWarnings(as.numeric(text))
        if (is.na(val)) 0 else val
      }

      find_first_prob_col <- function(cells) {
        for (i in 5:length(cells)) {
          text <- cells[[i]] %>% html_text(trim = TRUE)
          if (nzchar(text) && (grepl("%", text) || grepl("^\\d", text) || grepl("^<", text))) {
            return(i)
          }
        }
        6L
      }

      # Wildcard Series (rightmost column) = general probability of making the postseason.
      # Do not use Division Series or earlier rounds — those are conditional on advancing further.
      league_text <- cells[[2]] %>% html_text(trim = TRUE)
      league <- if (grepl("nat", league_text, ignore.case = TRUE)) {
        "National"
      } else if (grepl("amer", league_text, ignore.case = TRUE)) {
        "American"
      } else {
        league_text
      }

      wins <- suppressWarnings(as.integer(cells[[3]] %>% html_text(trim = TRUE)))
      losses <- suppressWarnings(as.integer(cells[[4]] %>% html_text(trim = TRUE)))
      champ_prob <- parse_pct(cells[[find_first_prob_col(cells)]])
      wc_series_col <- length(cells)
      playoff_prob <- parse_pct(cells[[wc_series_col]])

      results[[team_abbrev]] <- list(
        playoffProb = playoff_prob,
        champProb = champ_prob,
        conference = league,
        wins = if (is.na(wins)) NULL else wins,
        losses = if (is.na(losses)) NULL else losses,
        winPct = if (!is.na(wins) && !is.na(losses) && (wins + losses) > 0) {
          wins / (wins + losses)
        } else {
          NULL
        }
      )
    }

    cat("Successfully scraped MLB playoff probabilities for", length(results), "teams\n")
    results
  }, error = function(e) {
    cat("Error scraping MLB playoff probabilities:", e$message, "\n")
    NULL
  })
}

mlb_games_back <- function(w, l, w_ref, l_ref) {
  if (any(is.na(c(w, l, w_ref, l_ref)))) return(NA_real_)
  ((w_ref - w) + (l - l_ref)) / 2
}

make_playoff_entry <- function(row, standings_section) {
  gb <- row$games_back
  if (length(gb) == 0 || is.na(gb)) {
    gb_out <- NULL
  } else if (gb <= 0) {
    gb_out <- 0
  } else {
    gb_out <- round(gb + 1e-9, 1)
  }

  list(
    team = row$team_code,
    playoffProb = row$playoff_prob,
    champProb = row$champ_prob,
    conference = row$league,
    winPct = row$win_pct,
    wins = if (is.na(row$wins)) NULL else as.integer(row$wins),
    losses = if (is.na(row$losses)) NULL else as.integer(row$losses),
    division = row$division,
    divisionRank = if (is.na(row$division_rank)) NULL else as.integer(row$division_rank),
    gamesBackFromPlayoff = gb_out,
    standingsSection = standings_section
  )
}

build_playoff_chances <- function(scrape_results, team_records_df) {
  if (is.null(scrape_results) || length(scrape_results) == 0) return(list())

  base_rows <- lapply(names(scrape_results), function(abbrev) {
    scraped <- scrape_results[[abbrev]]
    rec <- team_records_df %>% filter(team_code == abbrev)
    wins <- scraped$wins
    losses <- scraped$losses
    win_pct <- scraped$winPct
    division <- team_divisions[[abbrev]]
    league <- team_leagues[[abbrev]]
    division_rank <- NA_integer_
    if (nrow(rec) > 0) {
      wins <- rec$W[1]
      losses <- rec$L[1]
      win_pct <- rec$win_pct[1]
      division <- rec$division[1]
      league <- rec$league[1]
      division_rank <- as.integer(rec$division_rank[1])
    }
    tibble::tibble(
      team_code = abbrev,
      playoff_prob = scraped$playoffProb,
      champ_prob = scraped$champProb,
      league = league,
      wins = wins,
      losses = losses,
      win_pct = win_pct,
      division = division,
      division_rank = division_rank
    )
  })

  df <- dplyr::bind_rows(base_rows) %>%
    dplyr::filter(!is.na(team_code), !is.na(league))

  organize_mlb_league <- function(league_df, divisions) {
    if (nrow(league_df) == 0) return(list())

    div_leaders <- league_df %>% dplyr::filter(division_rank == 1)
    non_leaders <- league_df %>%
      dplyr::filter(division_rank > 1) %>%
      dplyr::arrange(dplyr::desc(win_pct), dplyr::desc(wins), losses)
    wc_winners <- non_leaders %>% dplyr::slice_head(n = 3)
    playoff_teams <- dplyr::bind_rows(div_leaders, wc_winners)

    cutoff_w <- NA_integer_
    cutoff_l <- NA_integer_
    if (nrow(playoff_teams) > 0) {
      cutoff_row <- playoff_teams %>%
        dplyr::arrange(win_pct, wins, dplyr::desc(losses)) %>%
        dplyr::slice_head(n = 1)
      cutoff_w <- cutoff_row$wins[1]
      cutoff_l <- cutoff_row$losses[1]
    }

    league_df <- league_df %>%
      dplyr::mutate(
        games_back = if (!is.na(cutoff_w)) {
          mapply(mlb_games_back, wins, losses, cutoff_w, cutoff_l)
        } else {
          NA_real_
        }
      )

    out <- list()
    for (div in divisions) {
      div_teams <- league_df %>%
        dplyr::filter(division == div) %>%
        dplyr::arrange(division_rank, dplyr::desc(win_pct), dplyr::desc(wins))
      if (nrow(div_teams) > 0) {
        for (i in seq_len(nrow(div_teams))) {
          out[[length(out) + 1]] <- make_playoff_entry(div_teams[i, ], div)
        }
      }
    }

    out
  }

  c(
    organize_mlb_league(df %>% dplyr::filter(league == "National"), c("NL East", "NL Central", "NL West")),
    organize_mlb_league(df %>% dplyr::filter(league == "American"), c("AL East", "AL Central", "AL West"))
  )
}

# ============================================================================
# Helpers (stats)
# ============================================================================
tied_rank <- function(x) {
  numeric_ranks <- rank(x, ties.method = "min", na.last = "keep")
  rank_counts <- table(numeric_ranks[!is.na(numeric_ranks)])
  display_ranks <- vapply(numeric_ranks, function(r) {
    if (is.na(r)) return(NA_character_)
    if (rank_counts[as.character(r)] > 1) paste0("T", r) else as.character(r)
  }, character(1))
  list(rank = numeric_ranks, rankDisplay = display_ranks)
}

rank_and_assign <- function(df, col, lower_better = FALSE) {
  vals <- df[[col]]
  rk <- if (lower_better) tied_rank(vals) else tied_rank(-vals)
  df[[paste0(col, "_rank")]] <- as.integer(rk$rank)
  df[[paste0(col, "_rankDisplay")]] <- rk$rankDisplay
  df
}

normalize_team <- function(team_abb) {
  if (is.na(team_abb) || grepl("Tms", team_abb)) return(NA_character_)
  primary <- strsplit(as.character(team_abb), ",")[[1]][1]
  if (primary %in% names(FG_TO_APP_ABBREV)) FG_TO_APP_ABBREV[[primary]] else primary
}

`%||%` <- function(a, b) if (!is.null(a) && !is.na(a) && nzchar(a)) a else b

stat_entry <- function(row, col, label, digits = NULL, display_value = NULL) {
  if (is.null(row) || !col %in% names(row) || is.na(row[[col]])) {
    return(list(label = label, value = NULL, rank = NULL, rankDisplay = NULL, displayValue = NULL))
  }
  value <- as.numeric(row[[col]])
  if (!is.null(digits)) value <- round(value, digits)
  rank_val <- row[[paste0(col, "_rank")]]
  list(
    label = label,
    value = value,
    rank = if (is.na(rank_val)) NULL else as.integer(rank_val),
    rankDisplay = row[[paste0(col, "_rankDisplay")]],
    displayValue = display_value
  )
}

# Average percentile across category stat ranks (100 = best). Used to surface
# well-rounded players instead of one-stat specialists.
add_composite_score <- function(df, stat_cols) {
  if (nrow(df) == 0) {
    df$composite_score <- numeric(0)
    return(df)
  }
  n <- nrow(df)
  score_matrix <- vapply(stat_cols, function(col) {
    rank_col <- paste0(col, "_rank")
    ranks <- df[[rank_col]]
    pct <- (n - ranks + 1) / n * 100
    pct[is.na(ranks)] <- NA_real_
    pct
  }, numeric(n))
  if (length(stat_cols) == 1) {
    df$composite_score <- score_matrix
  } else {
    df$composite_score <- rowMeans(score_matrix, na.rm = TRUE)
  }
  df$composite_score[is.nan(df$composite_score)] <- NA_real_
  df
}

composite_stat_entry <- function(row) {
  if (is.null(row) || is.na(row$composite_score)) {
    return(list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL))
  }
  list(
    label = "Composite",
    value = round(row$composite_score, 1),
    rank = NULL,
    rankDisplay = NULL
  )
}

team_composite_stat_entry <- function(row) {
  if (is.null(row) || nrow(row) == 0 || is.na(row$composite_score)) {
    return(list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL))
  }
  rank_val <- row$composite_score_rank
  list(
    label = "Composite",
    value = round(row$composite_score, 1),
    rank = if (is.na(rank_val)) NULL else as.integer(rank_val),
    rankDisplay = row$composite_score_rankDisplay
  )
}

build_player <- function(row, stat_cols, stat_labels, digits_map = list()) {
  stats <- setNames(
    lapply(stat_cols, function(col) {
      stat_entry(row, col, stat_labels[[col]], digits_map[[col]])
    }),
    stat_cols
  )
  stats$aggregate <- composite_stat_entry(row)
  player_name <- resolve_player_name(row)
  war_val <- resolve_player_war(row)
  list(
    playerId = as.character(row$playerid),
    name = player_name,
    position = if (!is.null(row$Pos) && !is.na(row$Pos)) as.character(row$Pos) else NULL,
    war = war_val,
    stats = stats
  )
}

top_team_players <- function(df, team, stat_cols, stat_labels, digits_map = list()) {
  team_df <- df %>%
    filter(team_code == team) %>%
    arrange(desc(composite_score)) %>%
    head(TOP_N)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) {
    build_player(team_df[i, ], stat_cols, stat_labels, digits_map)
  })
}

build_team_category_stats <- function(team_df, team, stat_cols, stat_labels, digits_map = list()) {
  row <- team_df %>% filter(team_code == team)
  stats <- setNames(
    lapply(stat_cols, function(col) {
      if (nrow(row) == 0) {
        list(label = stat_labels[[col]], value = NULL, rank = NULL, rankDisplay = NULL)
      } else {
        stat_entry(row[1, ], col, stat_labels[[col]], digits_map[[col]])
      }
    }),
    stat_cols
  )
  if (nrow(row) == 0) {
    stats$aggregate <- list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL)
  } else {
    stats$aggregate <- team_composite_stat_entry(row[1, ])
  }
  list(stats = stats)
}

aggregate_pitching_team_stats <- function(df, stat_cols, weight_col = "IP") {
  if (nrow(df) == 0) {
    empty <- tibble(team_code = character())
    for (col in stat_cols) empty[[col]] <- numeric()
    return(empty)
  }
  df %>%
    group_by(team_code) %>%
    summarise(
      across(all_of(stat_cols), ~ weighted.mean(.x, .data[[weight_col]], na.rm = TRUE)),
      .groups = "drop"
    )
}

normalize_player_name <- function(x) {
  x <- tolower(trimws(as.character(x)))
  x <- iconv(x, to = "ASCII//TRANSLIT")
  x <- gsub("[^a-z ]", "", x)
  gsub("\\s+", " ", x)
}

resolve_player_name <- function(row) {
  pick_name <- function(col) {
    if (!col %in% names(row)) return(NA_character_)
    val <- row[[col]]
    if (length(val) == 0 || is.na(val)) return(NA_character_)
    name <- as.character(val[[1]])
    if (!nzchar(name)) NA_character_ else name
  }
  name <- pick_name("PlayerName")
  if (is.na(name)) name <- pick_name("PlayerName.x")
  if (is.na(name)) name <- pick_name("PlayerName.y")
  name
}

resolve_player_war <- function(row) {
  pick_war <- function(col) {
    if (!col %in% names(row)) return(NA_real_)
    val <- as.numeric(row[[col]])
    if (length(val) == 0 || is.na(val[[1]])) NA_real_ else val[[1]]
  }
  war <- pick_war("WAR")
  if (is.na(war)) war <- pick_war("WAR.y")
  if (is.na(war)) NULL else round(war, 2)
}

lookup_player_war <- function(name, lookup) {
  norm <- normalize_player_name(name)
  exact <- lookup %>% filter(normalize_player_name(PlayerName) == norm)
  if (nrow(exact) >= 1) {
    best <- exact[which.max(exact$WAR), , drop = FALSE]
    return(list(
      playerid = best$playerid[1],
      PlayerName = best$PlayerName[1],
      WAR = best$WAR[1]
    ))
  }
  list(playerid = NA, PlayerName = name, WAR = NA_real_)
}

espn_team_to_abbrev <- function(display_name) {
  for (suffix in names(MLB_TEAM_NAME_TO_ABBREV)) {
    if (grepl(paste0(suffix, "$"), display_name) || grepl(suffix, display_name, fixed = TRUE)) {
      return(MLB_TEAM_NAME_TO_ABBREV[[suffix]])
    }
  }
  NA_character_
}

injury_status_weight <- function(status) {
  st <- tolower(trimws(as.character(status)))
  if (!nzchar(st)) return(NA_real_)
  if (grepl("suspension", st)) return(NA_real_)
  if (grepl("developmental", st)) return(NA_real_)
  if (grepl("60-day|60 day", st)) return(1.0)
  if (grepl("15-day|15 day", st)) return(0.9)
  if (grepl("10-day|10 day", st)) return(0.85)
  if (grepl("7-day|7 day", st)) return(0.75)
  if (grepl("day-to-day|day to day", st)) return(0.5)
  if (grepl("^out$", st)) return(0.85)
  0.8
}

fetch_espn_mlb_injuries <- function() {
  url <- "https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/injuries"
  safe_scalar <- function(x, default = NA_character_) {
    if (is.null(x) || length(x) == 0) return(default)
    if (length(x) > 1) x <- x[[1]]
    if (is.na(x) || !nzchar(as.character(x))) return(default)
    as.character(x)
  }
  tryCatch({
    payload <- fromJSON(url, simplifyVector = FALSE)
    groups <- payload$injuries
    if (is.null(groups)) groups <- list()
    rows <- list()
    for (group in groups) {
      team_code <- espn_team_to_abbrev(safe_scalar(group$displayName, ""))
      if (is.na(team_code)) next
      entries <- group$injuries
      if (is.null(entries)) entries <- list()
      for (entry in entries) {
        status <- safe_scalar(entry$status, "")
        weight <- injury_status_weight(status)
        if (is.na(weight)) next
        athlete <- entry$athlete
        if (is.null(athlete)) athlete <- list()
        position <- if (is.null(athlete$position)) NA_character_ else safe_scalar(athlete$position$abbreviation)
        rows[[length(rows) + 1]] <- list(
          team_code = team_code,
          entry_id = safe_scalar(entry$id),
          athlete_id = safe_scalar(athlete$id),
          athlete_name = safe_scalar(athlete$displayName),
          status = status,
          position = position,
          short_comment = safe_scalar(entry$shortComment),
          status_weight = weight
        )
      }
    }
    if (length(rows) == 0) {
      return(tibble(
        team_code = character(),
        entry_id = character(),
        athlete_id = character(),
        athlete_name = character(),
        status = character(),
        position = character(),
        short_comment = character(),
        status_weight = numeric(),
        playerid = character(),
        PlayerName = character(),
        WAR = numeric(),
        impact = numeric()
      ))
    }
    bind_rows(rows)
  }, error = function(e) {
    cat("Warning: could not load ESPN injury report:", e$message, "\n")
    tibble(
      team_code = character(),
      entry_id = character(),
      athlete_id = character(),
      athlete_name = character(),
      status = character(),
      position = character(),
      short_comment = character(),
      status_weight = numeric(),
      playerid = character(),
      PlayerName = character(),
      WAR = numeric(),
      impact = numeric()
    )
  })
}

build_injury_player <- function(row) {
  war_val <- if (!is.na(row$WAR)) round(as.numeric(row$WAR), 2) else NULL
  impact_val <- if (!is.na(row$impact)) round(as.numeric(row$impact), 2) else NULL
  player_id <- if (!is.na(row$playerid)) {
    as.character(row$playerid)
  } else if (!is.na(row$entry_id)) {
    row$entry_id
  } else {
    row$athlete_id
  }
  list(
    playerId = player_id,
    name = as.character(row$athlete_name),
    position = if (!is.na(row$position) && nzchar(row$position)) as.character(row$position) else NULL,
    status = if (!is.na(row$status) && nzchar(row$status)) as.character(row$status) else NULL,
    war = war_val,
    stats = list(
      impact = list(
        label = "Impact",
        value = impact_val,
        rank = NULL,
        rankDisplay = NULL
      ),
      aggregate = list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL)
    )
  )
}

team_injured_players <- function(df, team) {
  team_df <- df %>%
    filter(team_code == team) %>%
    arrange(desc(impact), desc(WAR), athlete_name)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) {
    build_injury_player(team_df[i, ])
  })
}

# ============================================================================
# Load FanGraphs data
# ============================================================================
cat("=== MLB Team Report Card ===\n")
cat("Season:", season_label, "\n")

batter_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_batter_leaders(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading batter stats:", e$message, "\n")
  stop(e)
})

pitcher_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_pitcher_leaders(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading pitcher stats:", e$message, "\n")
  stop(e)
})

fielder_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_fielder_leaders(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading fielder stats:", e$message, "\n")
  stop(e)
})

team_batting_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_team_batter(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      lg = "all",
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading team batting stats:", e$message, "\n")
  stop(e)
})

team_fielding_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_team_fielder(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      lg = "all",
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading team fielding stats:", e$message, "\n")
  stop(e)
})

team_pitching_stats <- tryCatch({
  suppressWarnings(suppressMessages(
    fg_team_pitcher(
      startseason = as.character(mlb_season),
      endseason = as.character(mlb_season),
      lg = "all",
      qual = "0"
    )
  ))
}, error = function(e) {
  cat("Error loading team pitching stats:", e$message, "\n")
  stop(e)
})

cat("Loaded", nrow(batter_stats), "batters,",
    nrow(pitcher_stats), "pitchers,",
    nrow(fielder_stats), "fielders,",
    nrow(team_batting_stats), "team batting rows,",
    nrow(team_fielding_stats), "team fielding rows,",
    nrow(team_pitching_stats), "team pitching rows\n")

team_records <- team_pitching_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    W = as.integer(W),
    L = as.integer(L),
    win_pct = W / (W + L)
  ) %>%
  filter(!is.na(team_code), !is.na(W), !is.na(L), !is.na(win_pct))

team_records <- rank_and_assign(team_records, "win_pct")

team_records <- team_records %>%
  mutate(
    division = vapply(team_code, function(t) team_divisions[[t]], character(1)),
    league = vapply(team_code, function(t) team_leagues[[t]], character(1))
  ) %>%
  group_by(division) %>%
  arrange(desc(win_pct), desc(W)) %>%
  mutate(
    division_rank = as.integer(rank(-win_pct, ties.method = "min")),
    division_rank_display = vapply(division_rank, format_ordinal, character(1))
  ) %>%
  ungroup()

playoff_scrape <- scrape_mlb_playoff_probabilities()
playoff_chances_json <- build_playoff_chances(playoff_scrape, team_records)

# ============================================================================
# Prepare qualified pools + league-wide ranks
# ============================================================================
hitters <- batter_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    PA = as.integer(PA),
    G = as.integer(G),
    wRC_plus = as.numeric(wRC_plus),
    xwOBA = as.numeric(xwOBA),
    xBA = as.numeric(xAVG),
    Barrel_pct = round(as.numeric(Barrel_pct) * 100, 1),
    WAR = as.numeric(WAR),
    Pos = coalesce(as.character(position), as.character(positionDB))
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(PA), !is.na(G),
    !is.na(wRC_plus), !is.na(xwOBA),
    !is.na(xBA), !is.na(Barrel_pct),
    PA >= MIN_PA,
    G >= MIN_G_HIT
  )

starters <- pitcher_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    IP = as.numeric(IP),
    GS = as.integer(GS),
    G = as.integer(G),
    `K-BB_pct` = as.numeric(`K-BB_pct`),
    xFIP = as.numeric(xFIP),
    SIERA = as.numeric(SIERA),
    ERA = as.numeric(ERA),
    FIP = as.numeric(FIP),
    WAR = as.numeric(WAR),
    Pos = "P"
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(IP), !is.na(GS), !is.na(G),
    !is.na(`K-BB_pct`), !is.na(xFIP), !is.na(SIERA), !is.na(ERA),
    IP >= MIN_STARTER_IP,
    GS >= MIN_STARTER_GS
  )

relievers <- pitcher_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    IP = as.numeric(IP),
    GS = as.integer(GS),
    G = as.integer(G),
    SV = coalesce(as.integer(SV), 0L),
    `K-BB_pct` = as.numeric(`K-BB_pct`),
    FIP = as.numeric(FIP),
    SIERA = as.numeric(SIERA),
    ERA = as.numeric(ERA),
    WAR = as.numeric(WAR),
    Pos = "P"
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(IP), !is.na(GS), !is.na(G),
    !is.na(`K-BB_pct`), !is.na(FIP), !is.na(SIERA), !is.na(ERA),
    IP >= MIN_RELIEVER_IP,
    GS <= MAX_RELIEVER_GS,
    G > GS
  )

war_lookup <- bind_rows(
  batter_stats %>% transmute(playerid, PlayerName, WAR = as.numeric(WAR)),
  pitcher_stats %>% transmute(playerid, PlayerName, WAR = as.numeric(WAR))
) %>%
  group_by(playerid) %>%
  summarise(PlayerName = first(PlayerName), WAR = max(WAR, na.rm = TRUE), .groups = "drop")

cat("Loading ESPN MLB injury report...\n")
injury_players_raw <- fetch_espn_mlb_injuries()
injury_players <- injury_players_raw %>%
  rowwise() %>%
  mutate(
    war_match = list(lookup_player_war(athlete_name, war_lookup)),
    playerid = war_match$playerid,
    PlayerName = war_match$PlayerName,
    WAR = war_match$WAR,
    impact = pmax(coalesce(WAR, 0), 0) * status_weight
  ) %>%
  ungroup() %>%
  select(-war_match)

team_injury_totals <- injury_players %>%
  group_by(team_code) %>%
  summarise(
    injured_count = n(),
    injury_war = sum(impact, na.rm = TRUE),
    .groups = "drop"
  )

team_injuries <- tibble(team_code = ALL_TEAMS) %>%
  left_join(team_injury_totals, by = "team_code") %>%
  mutate(
    injured_count = coalesce(as.integer(injured_count), 0L),
    injury_war = coalesce(injury_war, 0)
  )

team_injuries <- rank_and_assign(team_injuries, "injured_count", lower_better = FALSE)
team_injuries <- rank_and_assign(team_injuries, "injury_war", lower_better = FALSE)
team_injuries <- add_composite_score(team_injuries, c("injured_count", "injury_war"))
team_injuries <- rank_and_assign(team_injuries, "composite_score")

cat(
  "Injury report — players:", nrow(injury_players),
  "| teams with injuries:", sum(team_injuries$injured_count > 0),
  "\n"
)

# ============================================================================
# 10-week trend (ESPN completed games)
# ============================================================================
TREND_WEEKS <- 10
TREND_DAYS <- TREND_WEEKS * 7

safe_num <- function(x) {
  if (is.null(x) || length(x) == 0) return(NA_real_)
  val <- suppressWarnings(as.numeric(x))
  if (is.na(val)) NA_real_ else val
}

add_api_delay <- function() Sys.sleep(0.5)

espn_to_app_abbrev <- function(abbrev) {
  if (abbrev %in% names(FG_TO_APP_ABBREV)) FG_TO_APP_ABBREV[[abbrev]] else abbrev
}

extract_box_score_stats <- function(competitor) {
  stats <- list()
  if (!is.null(competitor$statistics)) {
    for (s in competitor$statistics) {
      if (!is.null(s$name) && !is.null(s$displayValue)) {
        stats[[s$name]] <- safe_num(s$displayValue)
      }
    }
  }
  stats
}

competitor_hits <- function(competitor, box_stats) {
  val <- safe_num(competitor$hits)
  if (!is.na(val)) return(val)
  safe_num(box_stats[["hits"]])
}

fetch_game_batting_home_runs <- function(event_id) {
  url <- paste0(
    "https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/summary?event=",
    event_id
  )
  add_api_delay()
  resp <- tryCatch(GET(url), error = function(e) NULL)
  if (is.null(resp) || status_code(resp) != 200) return(list())

  data <- tryCatch(content(resp, as = "parsed"), error = function(e) NULL)
  if (is.null(data) || is.null(data$boxscore) || is.null(data$boxscore$teams)) return(list())

  hrs_by_team <- list()
  for (t in data$boxscore$teams) {
    if (is.null(t$team$abbreviation)) next
    abbrev <- espn_to_app_abbrev(t$team$abbreviation)
    hrs <- NA_real_
    if (!is.null(t$statistics)) {
      for (sg in t$statistics) {
        if (!identical(sg$name, "batting") || is.null(sg$stats)) next
        for (s in sg$stats) {
          if (identical(s$name, "homeRuns")) {
            hrs <- safe_num(s$value)
            if (is.na(hrs)) hrs <- safe_num(s$displayValue)
            break
          }
        }
      }
    }
    hrs_by_team[[abbrev]] <- hrs
  }
  hrs_by_team
}

fetch_recent_trend_games <- function() {
  cat("Fetching ESPN completed games for 10-week trend...\n")
  fetch_end <- Sys.Date() - 1
  fetch_start <- fetch_end - TREND_DAYS + 1
  trend_games <- list()
  fetch_date <- fetch_start

  while (fetch_date <= fetch_end) {
    date_str <- format(fetch_date, "%Y%m%d")
    url <- paste0(
      "https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard?dates=",
      date_str
    )
    add_api_delay()
    resp <- tryCatch(GET(url), error = function(e) NULL)
    if (!is.null(resp) && status_code(resp) == 200) {
      data <- content(resp, as = "parsed")
      if (!is.null(data$events)) {
        for (ev in data$events) {
          comp <- ev$competitions[[1]]
          if (length(comp$competitors) != 2) next
          if (!isTRUE(comp$status$type$completed)) next

          home <- NULL
          away <- NULL
          for (ct in comp$competitors) {
            if (ct$homeAway == "home") home <- ct else away <- ct
          }
          if (is.null(home) || is.null(away)) next

          home_score <- safe_num(home$score)
          away_score <- safe_num(away$score)
          if (is.na(home_score) || is.na(away_score)) next

          home_abbrev <- espn_to_app_abbrev(home$team$abbreviation)
          away_abbrev <- espn_to_app_abbrev(away$team$abbreviation)
          if (is.na(home_abbrev) || is.na(away_abbrev)) next

          home_box <- extract_box_score_stats(home)
          away_box <- extract_box_score_stats(away)
          hr_by_team <- fetch_game_batting_home_runs(ev$id)

          trend_games[[length(trend_games) + 1]] <- list(
            team_code = home_abbrev,
            runs_scored = home_score,
            runs_allowed = away_score,
            won = home_score > away_score,
            hits = competitor_hits(home, home_box),
            hrs = safe_num(hr_by_team[[home_abbrev]])
          )
          trend_games[[length(trend_games) + 1]] <- list(
            team_code = away_abbrev,
            runs_scored = away_score,
            runs_allowed = home_score,
            won = away_score > home_score,
            hits = competitor_hits(away, away_box),
            hrs = safe_num(hr_by_team[[away_abbrev]])
          )
        }
      }
    }
    fetch_date <- fetch_date + 1
  }
  trend_games
}

trend_games_list <- fetch_recent_trend_games()
cat("Fetched", length(trend_games_list), "team-game entries for 10-week trend\n")

team_trend <- tibble(team_code = ALL_TEAMS)
if (length(trend_games_list) > 0) {
  trend_df <- bind_rows(trend_games_list) %>%
    filter(team_code %in% ALL_TEAMS) %>%
    group_by(team_code) %>%
    summarise(
      games_played = n(),
      wins = sum(won, na.rm = TRUE),
      losses = sum(!won, na.rm = TRUE),
      runs_per_game = mean(runs_scored, na.rm = TRUE),
      runs_allowed_per_game = mean(runs_allowed, na.rm = TRUE),
      run_diff_per_game = mean(runs_scored - runs_allowed, na.rm = TRUE),
      hits_per_game = mean(hits, na.rm = TRUE),
      hrs_per_game = mean(hrs, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    mutate(win_pct = wins / pmax(wins + losses, 1))

  for (stat in c("win_pct", "runs_per_game", "run_diff_per_game", "hits_per_game", "hrs_per_game")) {
    trend_df <- rank_and_assign(trend_df, stat, lower_better = FALSE)
  }
  trend_df <- rank_and_assign(trend_df, "runs_allowed_per_game", lower_better = TRUE)

  team_trend <- team_trend %>%
    left_join(trend_df, by = "team_code")

  cat("Calculated 10-week trend rankings for", sum(!is.na(team_trend$win_pct)), "teams\n")
}

trend_labels <- list(
  record = "Record",
  runDiffPerGame = "Run Diff/G",
  runsPerGame = "Runs/G",
  runsAllowedPerGame = "RA/G",
  hitsPerGame = "Hits/G",
  hrsPerGame = "HR/G"
)
trend_digits <- list(
  runDiffPerGame = 2,
  runsPerGame = 2,
  runsAllowedPerGame = 2,
  hitsPerGame = 2,
  hrsPerGame = 2
)
trend_stat_cols <- list(
  runDiffPerGame = "run_diff_per_game",
  runsPerGame = "runs_per_game",
  runsAllowedPerGame = "runs_allowed_per_game",
  hitsPerGame = "hits_per_game",
  hrsPerGame = "hrs_per_game"
)

build_trend_category_stats <- function(trend_df, team) {
  row <- trend_df %>% filter(team_code == team)
  empty_stat <- function(label) {
    list(label = label, value = NULL, rank = NULL, rankDisplay = NULL, displayValue = NULL)
  }
  if (nrow(row) == 0 || is.na(row$win_pct[1])) {
    return(list(stats = setNames(
      lapply(names(trend_labels), function(key) empty_stat(trend_labels[[key]])),
      names(trend_labels)
    )))
  }
  row <- row[1, ]
  stats <- list(
    record = list(
      label = trend_labels$record,
      value = round(row$win_pct, 3),
      rank = as.integer(row$win_pct_rank),
      rankDisplay = row$win_pct_rankDisplay,
      displayValue = paste0(row$wins, "-", row$losses)
    )
  )
  for (key in names(trend_stat_cols)) {
    col <- trend_stat_cols[[key]]
    stats[[key]] <- stat_entry(row, col, trend_labels[[key]], trend_digits[[key]])
  }
  list(stats = stats)
}

build_trend_rankings <- function(trend_df) {
  if (nrow(trend_df) == 0 || all(is.na(trend_df$win_pct))) return(list())
  ranked_df <- trend_df %>% filter(!is.na(win_pct))
  stat_rankings <- setNames(
    lapply(names(trend_stat_cols), function(key) {
      col <- trend_stat_cols[[key]]
      build_rankings(
        ranked_df, "team_code", col,
        paste0(col, "_rank"), paste0(col, "_rankDisplay")
      )
    }),
    paste0("recentTrend.", names(trend_stat_cols))
  )
  c(
    list(
      "recentTrend.record" = build_rankings(
        ranked_df, "team_code", "win_pct", "win_pct_rank", "win_pct_rankDisplay"
      )
    ),
    stat_rankings
  )
}

fielders <- fielder_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    Inn = as.numeric(Inn),
    OAA = as.numeric(OAA),
    DRS = as.numeric(DRS),
    FRP = as.numeric(FRP),
    Pos = coalesce(as.character(Position), as.character(Pos))
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(Inn),
    !is.na(OAA), !is.na(DRS), !is.na(FRP),
    Inn >= MIN_FIELDER_INN
  ) %>%
  left_join(war_lookup %>% select(playerid, WAR), by = "playerid")

for (col in c("wRC_plus", "xwOBA", "xBA", "Barrel_pct")) hitters <- rank_and_assign(hitters, col)
for (col in c("K-BB_pct", "xFIP", "SIERA", "ERA")) {
  starters <- rank_and_assign(starters, col, lower_better = col %in% c("xFIP", "SIERA", "ERA"))
}
for (col in c("K-BB_pct", "FIP", "SIERA", "ERA")) {
  relievers <- rank_and_assign(relievers, col, lower_better = col %in% c("FIP", "SIERA", "ERA"))
}
relievers <- rank_and_assign(relievers, "SV")
for (col in c("OAA", "DRS", "FRP")) fielders <- rank_and_assign(fielders, col)

team_hitting <- team_batting_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    wRC_plus = as.numeric(wRC_plus),
    xwOBA = as.numeric(xwOBA),
    xBA = as.numeric(xAVG),
    Barrel_pct = round(as.numeric(Barrel_pct) * 100, 1)
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(wRC_plus), !is.na(xwOBA),
    !is.na(xBA), !is.na(Barrel_pct)
  )

team_fielding <- team_fielding_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    OAA = as.numeric(OAA),
    DRS = as.numeric(DRS),
    FRP = as.numeric(FRP)
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(OAA), !is.na(DRS), !is.na(FRP)
  )

team_starters <- aggregate_pitching_team_stats(starters, c("K-BB_pct", "xFIP", "SIERA", "ERA"))

team_bullpen_saves <- team_pitching_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    SV = as.integer(SV),
    G = as.integer(G),
    SV_per_G = if_else(!is.na(G) & G > 0, as.numeric(SV) / G, NA_real_)
  ) %>%
  filter(!is.na(team_code)) %>%
  select(team_code, SV, SV_per_G)

team_relievers <- aggregate_pitching_team_stats(relievers, c("K-BB_pct", "FIP", "SIERA", "ERA")) %>%
  left_join(team_bullpen_saves, by = "team_code")

for (col in c("wRC_plus", "xwOBA", "xBA", "Barrel_pct")) team_hitting <- rank_and_assign(team_hitting, col)
for (col in c("K-BB_pct", "xFIP", "SIERA", "ERA")) {
  team_starters <- rank_and_assign(team_starters, col, lower_better = col %in% c("xFIP", "SIERA", "ERA"))
}
for (col in c("K-BB_pct", "FIP", "SIERA", "ERA")) {
  team_relievers <- rank_and_assign(team_relievers, col, lower_better = col %in% c("FIP", "SIERA", "ERA"))
}
team_relievers <- rank_and_assign(team_relievers, "SV_per_G")
for (col in c("OAA", "DRS", "FRP")) team_fielding <- rank_and_assign(team_fielding, col)

hitters <- add_composite_score(hitters, c("wRC_plus", "xwOBA", "xBA", "Barrel_pct"))
starters <- add_composite_score(starters, c("K-BB_pct", "xFIP", "SIERA", "ERA"))
relievers <- add_composite_score(relievers, c("K-BB_pct", "FIP", "SV", "SIERA", "ERA"))
fielders <- add_composite_score(fielders, c("OAA", "DRS", "FRP"))
team_hitting <- add_composite_score(team_hitting, c("wRC_plus", "xwOBA", "xBA", "Barrel_pct"))
team_starters <- add_composite_score(team_starters, c("K-BB_pct", "xFIP", "SIERA", "ERA"))
team_relievers <- add_composite_score(team_relievers, c("K-BB_pct", "FIP", "SV_per_G", "SIERA", "ERA"))
team_fielding <- add_composite_score(team_fielding, c("OAA", "DRS", "FRP"))
team_hitting <- rank_and_assign(team_hitting, "composite_score")
team_starters <- rank_and_assign(team_starters, "composite_score")
team_relievers <- rank_and_assign(team_relievers, "composite_score")
team_fielding <- rank_and_assign(team_fielding, "composite_score")

category_composite <- function(team_df, team) {
  row <- team_df %>% filter(team_code == team)
  if (nrow(row) == 0 || is.na(row$composite_score[1])) {
    NA_real_
  } else {
    row$composite_score[1]
  }
}

team_overall <- tibble(team_code = ALL_TEAMS) %>%
  rowwise() %>%
  mutate(
    overall_composite = mean(
      c(
        category_composite(team_hitting, team_code),
        category_composite(team_starters, team_code),
        category_composite(team_relievers, team_code),
        category_composite(team_fielding, team_code)
      ),
      na.rm = TRUE
    )
  ) %>%
  ungroup() %>%
  mutate(overall_composite = if_else(is.nan(overall_composite), NA_real_, overall_composite)) %>%
  filter(!is.na(overall_composite))

team_overall <- rank_and_assign(team_overall, "overall_composite")

build_rankings <- function(team_df, team_col, stat_col, rank_col, rankDisplay_col) {
  valid <- !is.na(team_df[[rank_col]])
  if (!any(valid)) return(list())

  df <- data.frame(
    team = team_df[[team_col]][valid],
    rank = as.integer(team_df[[rank_col]][valid]),
    rankDisplay = as.character(team_df[[rankDisplay_col]][valid]),
    value = round(as.numeric(team_df[[stat_col]][valid]), 4),
    stringsAsFactors = FALSE
  )
  df <- df[order(df$rank), ]

  lapply(seq_len(nrow(df)), function(i) {
    list(
      rank = df$rank[i],
      rankDisplay = df$rankDisplay[i],
      value = df$value[i],
      team = df$team[i]
    )
  })
}

build_category_stat_rankings <- function(category, team_df, stat_cols) {
  setNames(
    lapply(stat_cols, function(col) {
      build_rankings(
        team_df, "team_code", col,
        paste0(col, "_rank"), paste0(col, "_rankDisplay")
      )
    }),
    paste0(category, ".", stat_cols)
  )
}

build_player_rankings <- function(df, stat_col) {
  rank_col <- paste0(stat_col, "_rank")
  rankDisplay_col <- paste0(stat_col, "_rankDisplay")
  valid <- !is.na(df[[rank_col]]) & !is.na(df[[stat_col]])
  if (!any(valid)) return(list())
  subset <- df[valid, , drop = FALSE]
  subset <- subset[order(subset[[rank_col]]), , drop = FALSE]
  lapply(seq_len(nrow(subset)), function(i) {
    row <- subset[i, , drop = FALSE]
    list(
      rank = as.integer(row[[rank_col]][[1]]),
      rankDisplay = as.character(row[[rankDisplay_col]][[1]]),
      value = round(as.numeric(row[[stat_col]][[1]]), 4),
      team = as.character(row$team_code[[1]]),
      player = resolve_player_name(row)
    )
  })
}

build_player_pool_rankings <- function(category, df, stat_cols) {
  setNames(
    lapply(stat_cols, function(col) {
      build_player_rankings(df, col)
    }),
    paste0(category, ".player.", stat_cols)
  )
}

cat("Qualified pools — hitters:", nrow(hitters),
    "| starters:", nrow(starters),
    "| relievers:", nrow(relievers),
    "| fielders:", nrow(fielders), "\n")

hitter_labels <- c(wRC_plus = "wRC+", xwOBA = "xwOBA", xBA = "xBA", Barrel_pct = "Barrel%")
starter_labels <- c(`K-BB_pct` = "K-BB%", xFIP = "xFIP", SIERA = "SIERA", ERA = "ERA")
reliever_labels <- c(`K-BB_pct` = "K-BB%", FIP = "FIP", SV = "SV", SIERA = "SIERA", ERA = "ERA")
fielder_labels <- c(OAA = "OAA", DRS = "DRS", FRP = "FRP")

hitter_digits <- list(wRC_plus = 0, xwOBA = 3, xBA = 3, Barrel_pct = 1)
starter_digits <- list(`K-BB_pct` = 1, xFIP = 2, SIERA = 2, ERA = 2)
reliever_digits <- list(`K-BB_pct` = 1, FIP = 2, SV = 0, SIERA = 2, ERA = 2)
reliever_team_labels <- c(`K-BB_pct` = "K-BB%", FIP = "FIP", SV_per_G = "SV/G", SIERA = "SIERA", ERA = "ERA")
reliever_team_digits <- list(`K-BB_pct` = 1, FIP = 2, SV_per_G = 2, SIERA = 2, ERA = 2)
fielder_digits <- list(OAA = 1, DRS = 1, FRP = 0)
injury_labels <- c(injured_count = "Injured", injury_war = "WAR Lost")
injury_digits <- list(injured_count = 0, injury_war = 2)

# Scale K-BB% to percentage points for display consistency with other MLB charts.
scale_k_bb <- function(df) {
  if ("K-BB_pct" %in% names(df)) {
    df$`K-BB_pct` <- round(df$`K-BB_pct` * 100, 1)
  }
  df
}
starters <- scale_k_bb(starters)
relievers <- scale_k_bb(relievers)
team_starters <- scale_k_bb(team_starters)
team_relievers <- scale_k_bb(team_relievers)

# ============================================================================
# Build per-team report cards
# ============================================================================
teams_json <- lapply(ALL_TEAMS, function(team) {
  hitters_list <- top_team_players(
    hitters, team,
    c("wRC_plus", "xwOBA", "xBA", "Barrel_pct"), hitter_labels, hitter_digits
  )
  starters_list <- top_team_players(
    starters, team,
    c("K-BB_pct", "xFIP", "SIERA", "ERA"), starter_labels, starter_digits
  )
  relievers_list <- top_team_players(
    relievers, team,
    c("K-BB_pct", "FIP", "SV", "SIERA", "ERA"), reliever_labels, reliever_digits
  )
  fielders_list <- top_team_players(
    fielders, team,
    c("OAA", "DRS", "FRP"), fielder_labels, fielder_digits
  )
  injuries_list <- team_injured_players(injury_players, team)

  record_row <- team_records %>% filter(team_code == team)
  overall_row <- team_overall %>% filter(team_code == team)
  playoff_row <- if (!is.null(playoff_scrape)) playoff_scrape[[team]] else NULL
  division_row <- team_records %>% filter(team_code == team)
  team_wins <- if (nrow(record_row) > 0) record_row$W[1] else NULL
  team_losses <- if (nrow(record_row) > 0) record_row$L[1] else NULL
  team_record_rank <- if (nrow(record_row) > 0) as.integer(record_row$win_pct_rank[1]) else NULL
  team_record_rank_display <- if (nrow(record_row) > 0) record_row$win_pct_rankDisplay[1] else NULL
  team_division_rank <- if (nrow(division_row) > 0) as.integer(division_row$division_rank[1]) else NULL
  team_division_rank_display <- if (nrow(division_row) > 0) division_row$division_rank_display[1] else NULL
  team_overall_composite <- if (nrow(overall_row) > 0) round(overall_row$overall_composite[1], 1) else NULL
  team_overall_rank <- if (nrow(overall_row) > 0) as.integer(overall_row$overall_composite_rank[1]) else NULL
  team_overall_rank_display <- if (nrow(overall_row) > 0) overall_row$overall_composite_rankDisplay[1] else NULL
  team_playoff_prob <- if (!is.null(playoff_row)) playoff_row$playoffProb else NULL

  list(
    teamCode = team,
    teamName = team_names[[team]],
    division = team_divisions[[team]],
    league = team_leagues[[team]],
    wins = team_wins,
    losses = team_losses,
    recordRank = team_record_rank,
    recordRankDisplay = team_record_rank_display,
    divisionRank = team_division_rank,
    divisionRankDisplay = team_division_rank_display,
    overallComposite = team_overall_composite,
    overallCompositeRank = team_overall_rank,
    overallCompositeRankDisplay = team_overall_rank_display,
    playoffProb = team_playoff_prob,
    categories = list(
      recentTrend = list(
        label = "10 Week Trend",
        description = paste0(
          "Recent team performance over the last ", TREND_WEEKS,
          " weeks from ESPN completed games. Includes record, run differential, ",
          "and per-game offense metrics."
        ),
        team = build_trend_category_stats(team_trend, team),
        players = list()
      ),
      hitters = list(
        label = "Hitters",
        description = "Top offensive producers by wRC+, contact quality (xwOBA), expected average (xBA), and barrel rate.",
        team = build_team_category_stats(
          team_hitting, team,
          c("wRC_plus", "xwOBA", "xBA", "Barrel_pct"), hitter_labels, hitter_digits
        ),
        players = hitters_list
      ),
      starters = list(
        label = "Starting Pitchers",
        description = "Rotation arms ranked by K-BB%, expected FIP, SIERA, and official ERA.",
        team = build_team_category_stats(
          team_starters, team,
          c("K-BB_pct", "xFIP", "SIERA", "ERA"), starter_labels, starter_digits
        ),
        players = starters_list
      ),
      relievers = list(
        label = "Bullpen",
        description = "Top relievers by K-BB%, FIP, saves (SV), SIERA, and official ERA. Team SV/G from official pitching totals.",
        team = build_team_category_stats(
          team_relievers, team,
          c("K-BB_pct", "FIP", "SV_per_G", "SIERA", "ERA"), reliever_team_labels, reliever_team_digits
        ),
        players = relievers_list
      ),
      fielders = list(
        label = "Fielders",
        description = "Defensive standouts by Outs Above Average, Defensive Runs Saved, and Fielding Runs (FRP).",
        team = build_team_category_stats(
          team_fielding, team,
          c("OAA", "DRS", "FRP"), fielder_labels, fielder_digits
        ),
        players = fielders_list
      ),
      injuries = list(
        label = "Injury Report",
        description = paste0(
          "Current injured players weighted by FanGraphs WAR and IL severity ",
          "(60-day = 100%, 15-day = 90%, 10-day = 85%, day-to-day = 50%). ",
          "Higher composite = more injury impact. Rank 1 = most injured."
        ),
        team = build_team_category_stats(
          team_injuries, team,
          c("injured_count", "injury_war"), injury_labels, injury_digits
        ),
        players = injuries_list
      )
    )
  )
})

names(teams_json) <- ALL_TEAMS

rankings_json <- c(
  list(
    record = build_rankings(
      team_records, "team_code", "win_pct", "win_pct_rank", "win_pct_rankDisplay"
    ),
    overallComposite = build_rankings(
      team_overall,
      "team_code",
      "overall_composite",
      "overall_composite_rank",
      "overall_composite_rankDisplay"
    ),
    hittersComposite = build_rankings(
      team_hitting,
      "team_code",
      "composite_score",
      "composite_score_rank",
      "composite_score_rankDisplay"
    ),
    startersComposite = build_rankings(
      team_starters,
      "team_code",
      "composite_score",
      "composite_score_rank",
      "composite_score_rankDisplay"
    ),
    relieversComposite = build_rankings(
      team_relievers,
      "team_code",
      "composite_score",
      "composite_score_rank",
      "composite_score_rankDisplay"
    ),
    fieldersComposite = build_rankings(
      team_fielding,
      "team_code",
      "composite_score",
      "composite_score_rank",
      "composite_score_rankDisplay"
    ),
    injuriesComposite = build_rankings(
      team_injuries,
      "team_code",
      "composite_score",
      "composite_score_rank",
      "composite_score_rankDisplay"
    )
  ),
  build_category_stat_rankings(
    "hitters", team_hitting, c("wRC_plus", "xwOBA", "xBA", "Barrel_pct")
  ),
  build_category_stat_rankings(
    "starters", team_starters, c("K-BB_pct", "xFIP", "SIERA", "ERA")
  ),
  build_category_stat_rankings(
    "relievers", team_relievers, c("K-BB_pct", "FIP", "SV_per_G", "SIERA", "ERA")
  ),
  build_category_stat_rankings(
    "fielders", team_fielding, c("OAA", "DRS", "FRP")
  ),
  build_category_stat_rankings(
    "injuries", team_injuries, c("injured_count", "injury_war")
  ),
  build_trend_rankings(team_trend),
  build_player_pool_rankings(
    "hitters", hitters, c("wRC_plus", "xwOBA", "xBA", "Barrel_pct")
  ),
  build_player_pool_rankings(
    "starters", starters, c("K-BB_pct", "xFIP", "SIERA", "ERA")
  ),
  build_player_pool_rankings(
    "relievers", relievers, c("K-BB_pct", "FIP", "SV", "SIERA", "ERA")
  ),
  build_player_pool_rankings(
    "fielders", fielders, c("OAA", "DRS", "FRP")
  )
)

# ============================================================================
# Output JSON
# ============================================================================
output_data <- list(
  sport = "MLB",
  visualizationType = "MLB_TEAM_REPORT_CARD",
  title = paste0("MLB Team Report Cards - ", season_label),
  subtitle = paste0("Top ", TOP_N, " players per team by role (sorted by composite score)"),
  description = paste0(
    "Per-team player report cards from FanGraphs advanced stats. Each team ",
    "surfaces its top ", TOP_N, " hitters, starters, relievers, and fielders ",
    "ranked by composite score across category stats, with MLB-wide rank ",
    "badges and team-level summaries.\n\n",
    "STATS:\n\n",
    " • WAR: Wins Above Replacement — total player value. Higher is better.\n\n",
    " • wRC+: Park- and league-adjusted offensive production (100 = average). ",
    "Higher is better.\n\n",
    " • xwOBA: Expected weighted on-base average from contact quality. ",
    "Higher is better.\n\n",
    " • xBA: Expected batting average from Statcast batted-ball quality. ",
    "Higher is better.\n\n",
    " • Barrel%: Percent of batted balls hit with optimal launch angle and ",
    "exit velocity. Higher is better.\n\n",
    " • K-BB%: Strikeout rate minus walk rate. Higher is better.\n\n",
    " • ERA: Official earned run average. Lower is better.\n\n",
    " • xFIP / FIP / SIERA: Fielding independent pitching metrics. Lower is better.\n\n",
    " • SV: Saves — total for individual relievers.\n\n",
    " • SV/G: Team saves per game (bullpen closing output). Higher is better.\n\n",
    " • OAA: Outs Above Average from Statcast range. Higher is better.\n\n",
    " • DRS: Defensive Runs Saved. Higher is better.\n\n",
    " • FRP: FanGraphs Fielding Runs above positional average. Higher is better.\n\n",
    " • Composite: Average percentile across each category's stats (bullpen ",
    "includes K-BB%, FIP, SV/G, SIERA, and ERA). Higher is better.\n\n",
    " • Injury Report: ESPN daily injury list weighted by FanGraphs WAR and ",
    "IL severity. Team composite averages injured-count and WAR-lost ",
    "percentiles (more injuries and more WAR lost = higher score). Rank 1 ",
    "is the most injured team.\n\n",
    " • WAR Lost: Sum of max(WAR, 0) × status weight for injured players. ",
    "Higher means more impact.\n\n",
    " • Impact: Per-player weighted WAR lost from that injury.\n\n",
    " • 10 Week Trend: Recent team performance from ESPN completed games ",
    "over the last 10 weeks. Includes record, run differential per game, ",
    "runs scored/allowed per game, hits per game, and home runs per game."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs • ESPN",
  season = mlb_season,
  topN = TOP_N,
  rankings = rankings_json,
  playoffChances = playoff_chances_json,
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 1,
  teams = teams_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

# Quick sanity sample
lad_hitters <- output_data$teams$LAD$categories$hitters$players
pit_hitters <- output_data$teams$PIT$categories$hitters$players
lad_team_hit <- output_data$teams$LAD$categories$hitters$team$stats$wRC_plus
cat("\nSample — LAD top hitter:", if (length(lad_hitters) > 0) lad_hitters[[1]]$name else "(none)", "\n")
cat("Sample — LAD team wRC+:", lad_team_hit$value, "rank:", lad_team_hit$rank, "\n")
pit_names <- vapply(pit_hitters, function(p) p$name, character(1))
cat("PIT hitters include Konnor Griffin:", "Konnor Griffin" %in% pit_names, "\n")
if ("Konnor Griffin" %in% pit_names) {
  kg <- pit_hitters[[which(pit_names == "Konnor Griffin")]]
  cat("  Griffin WAR:", kg$war, "composite:", kg$stats$aggregate$value, "\n")
}
cat("Teams with data:", sum(vapply(teams_json, function(t) {
  any(vapply(t$categories, function(c) length(c$players) > 0, logical(1)))
}, logical(1))), "/", length(teams_json), "\n")

# ============================================================================
# Upload
# ============================================================================
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (!nzchar(s3_bucket)) stop("AWS_S3_BUCKET environment variable is not set")

env <- toupper(Sys.getenv("ENV", "DEV"))
s3_key <- if (env == "PROD") {
  "prod/mlb__team_report_card.json"
} else {
  "dev/mlb__team_report_card.json"
}

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
if (system(cmd) != 0) stop("Failed to upload to S3")
cat("\nUploaded to S3:", s3_path, "\n")

dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
chart_title <- output_data$title

dynamodb_item <- sprintf(
  '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
  s3_key, utc_timestamp, chart_title
)
ddb_cmd <- sprintf(
  "aws dynamodb put-item --table-name %s --item %s",
  shQuote(dynamodb_table), shQuote(dynamodb_item)
)
if (system(ddb_cmd) != 0) {
  warning("Failed to update DynamoDB timestamp (non-fatal)")
} else {
  cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
}

cat("\n=== MLB Team Report Card generation complete ===\n")
