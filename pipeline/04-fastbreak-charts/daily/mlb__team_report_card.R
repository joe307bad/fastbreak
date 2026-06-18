#!/usr/bin/env Rscript

# MLB Team Report Card — per-team player leaderboards grouped by role.
# Pulls qualified hitters, starters, relievers, and fielders from FanGraphs,
# ranks each player's advanced stats league-wide, and surfaces the top players
# on every team. Players can appear in multiple categories (e.g. two-way players).
#
# Stat pairs mirror the advanced metrics used elsewhere in the MLB pipeline:
#  - Hitters:    wRC+ (production) + xwOBA (contact quality)
#  - Starters:   K-BB% (command) + xFIP (skill-based run prevention)
#  - Relievers:  K-BB% (miss+command) + FIP (run prevention) + SV (closing value)
#  - Fielders:   OAA (Statcast range) + DRS (comprehensive defense)

library(baseballr)
library(dplyr)
library(jsonlite)
library(rvest)

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

stat_entry <- function(row, col, label, digits = NULL) {
  if (is.null(row) || !col %in% names(row) || is.na(row[[col]])) {
    return(list(label = label, value = NULL, rank = NULL, rankDisplay = NULL))
  }
  value <- as.numeric(row[[col]])
  if (!is.null(digits)) value <- round(value, digits)
  rank_val <- row[[paste0(col, "_rank")]]
  list(
    label = label,
    value = value,
    rank = if (is.na(rank_val)) NULL else as.integer(rank_val),
    rankDisplay = row[[paste0(col, "_rankDisplay")]]
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
  war_val <- if ("WAR" %in% names(row) && !is.na(row$WAR)) round(as.numeric(row$WAR), 2) else NULL
  list(
    playerId = as.character(row$playerid),
    name = as.character(row$PlayerName),
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

aggregate_pitching_team_stats <- function(df, k_bb_col = "K-BB_pct", fip_col = "xFIP") {
  df %>%
    group_by(team_code) %>%
    summarise(
      !!k_bb_col := weighted.mean(.data[[k_bb_col]], IP, na.rm = TRUE),
      !!fip_col := weighted.mean(.data[[fip_col]], IP, na.rm = TRUE),
      .groups = "drop"
    )
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
    WAR = as.numeric(WAR),
    Pos = coalesce(as.character(position), as.character(positionDB))
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(PA), !is.na(G),
    !is.na(wRC_plus), !is.na(xwOBA),
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
    FIP = as.numeric(FIP),
    WAR = as.numeric(WAR),
    Pos = "P"
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(IP), !is.na(GS), !is.na(G),
    !is.na(`K-BB_pct`), !is.na(xFIP),
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
    WAR = as.numeric(WAR),
    Pos = "P"
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(IP), !is.na(GS), !is.na(G),
    !is.na(`K-BB_pct`), !is.na(FIP),
    IP >= MIN_RELIEVER_IP,
    GS <= MAX_RELIEVER_GS,
    G > GS
  )

war_lookup <- bind_rows(
  batter_stats %>% transmute(playerid, WAR = as.numeric(WAR)),
  pitcher_stats %>% transmute(playerid, WAR = as.numeric(WAR))
) %>%
  group_by(playerid) %>%
  summarise(WAR = max(WAR, na.rm = TRUE), .groups = "drop")

fielders <- fielder_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    Inn = as.numeric(Inn),
    OAA = as.numeric(OAA),
    DRS = as.numeric(DRS),
    Pos = coalesce(as.character(Position), as.character(Pos))
  ) %>%
  filter(
    !is.na(team_code),
    !is.na(Inn),
    !is.na(OAA), !is.na(DRS),
    Inn >= MIN_FIELDER_INN
  ) %>%
  left_join(war_lookup, by = "playerid")

for (col in c("wRC_plus", "xwOBA")) hitters <- rank_and_assign(hitters, col)
for (col in c("K-BB_pct", "xFIP")) {
  starters <- rank_and_assign(starters, col, lower_better = col == "xFIP")
}
for (col in c("K-BB_pct", "FIP")) {
  relievers <- rank_and_assign(relievers, col, lower_better = col == "FIP")
}
relievers <- rank_and_assign(relievers, "SV")
for (col in c("OAA", "DRS")) fielders <- rank_and_assign(fielders, col)

team_hitting <- team_batting_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    wRC_plus = as.numeric(wRC_plus),
    xwOBA = as.numeric(xwOBA)
  ) %>%
  filter(!is.na(team_code), !is.na(wRC_plus), !is.na(xwOBA))

team_fielding <- team_fielding_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    OAA = as.numeric(OAA),
    DRS = as.numeric(DRS)
  ) %>%
  filter(!is.na(team_code), !is.na(OAA), !is.na(DRS))

team_starters <- aggregate_pitching_team_stats(starters, "K-BB_pct", "xFIP")

team_bullpen_saves <- team_pitching_stats %>%
  mutate(
    team_code = vapply(team_name_abb, normalize_team, character(1)),
    SV = as.integer(SV),
    G = as.integer(G),
    SV_per_G = if_else(!is.na(G) & G > 0, as.numeric(SV) / G, NA_real_)
  ) %>%
  filter(!is.na(team_code)) %>%
  select(team_code, SV, SV_per_G)

team_relievers <- aggregate_pitching_team_stats(relievers, "K-BB_pct", "FIP") %>%
  left_join(team_bullpen_saves, by = "team_code")

for (col in c("wRC_plus", "xwOBA")) team_hitting <- rank_and_assign(team_hitting, col)
for (col in c("K-BB_pct", "xFIP")) {
  team_starters <- rank_and_assign(team_starters, col, lower_better = col == "xFIP")
}
for (col in c("K-BB_pct", "FIP")) {
  team_relievers <- rank_and_assign(team_relievers, col, lower_better = col == "FIP")
}
team_relievers <- rank_and_assign(team_relievers, "SV_per_G")
for (col in c("OAA", "DRS")) team_fielding <- rank_and_assign(team_fielding, col)

hitters <- add_composite_score(hitters, c("wRC_plus", "xwOBA"))
starters <- add_composite_score(starters, c("K-BB_pct", "xFIP"))
relievers <- add_composite_score(relievers, c("K-BB_pct", "FIP", "SV"))
fielders <- add_composite_score(fielders, c("OAA", "DRS"))
team_hitting <- add_composite_score(team_hitting, c("wRC_plus", "xwOBA"))
team_starters <- add_composite_score(team_starters, c("K-BB_pct", "xFIP"))
team_relievers <- add_composite_score(team_relievers, c("K-BB_pct", "FIP", "SV_per_G"))
team_fielding <- add_composite_score(team_fielding, c("OAA", "DRS"))
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

cat("Qualified pools — hitters:", nrow(hitters),
    "| starters:", nrow(starters),
    "| relievers:", nrow(relievers),
    "| fielders:", nrow(fielders), "\n")

hitter_labels <- c(wRC_plus = "wRC+", xwOBA = "xwOBA")
starter_labels <- c(`K-BB_pct` = "K-BB%", xFIP = "xFIP")
reliever_labels <- c(`K-BB_pct` = "K-BB%", FIP = "FIP", SV = "SV")
fielder_labels <- c(OAA = "OAA", DRS = "DRS")

hitter_digits <- list(wRC_plus = 0, xwOBA = 3)
starter_digits <- list(`K-BB_pct` = 1, xFIP = 2)
reliever_digits <- list(`K-BB_pct` = 1, FIP = 2, SV = 0)
reliever_team_labels <- c(`K-BB_pct` = "K-BB%", FIP = "FIP", SV_per_G = "SV/G")
reliever_team_digits <- list(`K-BB_pct` = 1, FIP = 2, SV_per_G = 2)
fielder_digits <- list(OAA = 1, DRS = 1)

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
    c("wRC_plus", "xwOBA"), hitter_labels, hitter_digits
  )
  starters_list <- top_team_players(
    starters, team,
    c("K-BB_pct", "xFIP"), starter_labels, starter_digits
  )
  relievers_list <- top_team_players(
    relievers, team,
    c("K-BB_pct", "FIP", "SV"), reliever_labels, reliever_digits
  )
  fielders_list <- top_team_players(
    fielders, team,
    c("OAA", "DRS"), fielder_labels, fielder_digits
  )

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
      hitters = list(
        label = "Hitters",
        description = "Top offensive producers by wRC+ and contact quality (xwOBA).",
        team = build_team_category_stats(
          team_hitting, team,
          c("wRC_plus", "xwOBA"), hitter_labels, hitter_digits
        ),
        players = hitters_list
      ),
      starters = list(
        label = "Starting Pitchers",
        description = "Rotation arms ranked by K-BB% and expected FIP.",
        team = build_team_category_stats(
          team_starters, team,
          c("K-BB_pct", "xFIP"), starter_labels, starter_digits
        ),
        players = starters_list
      ),
      relievers = list(
        label = "Bullpen",
        description = "Top relievers by K-BB%, FIP, and saves (SV). Team SV/G from official pitching totals.",
        team = build_team_category_stats(
          team_relievers, team,
          c("K-BB_pct", "FIP", "SV_per_G"), reliever_team_labels, reliever_team_digits
        ),
        players = relievers_list
      ),
      fielders = list(
        label = "Fielders",
        description = "Defensive standouts by Outs Above Average and Defensive Runs Saved.",
        team = build_team_category_stats(
          team_fielding, team,
          c("OAA", "DRS"), fielder_labels, fielder_digits
        ),
        players = fielders_list
      )
    )
  )
})

names(teams_json) <- ALL_TEAMS

rankings_json <- list(
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
    "ranked by composite score across two category stats, with MLB-wide rank ",
    "badges and team-level summaries.\n\n",
    "STATS:\n\n",
    " • WAR: Wins Above Replacement — total player value. Higher is better.\n\n",
    " • wRC+: Park- and league-adjusted offensive production (100 = average). ",
    "Higher is better.\n\n",
    " • xwOBA: Expected weighted on-base average from contact quality. ",
    "Higher is better.\n\n",
    " • K-BB%: Strikeout rate minus walk rate. Higher is better.\n\n",
    " • xFIP / FIP: Fielding independent pitching. Lower is better.\n\n",
    " • SV: Saves — total for individual relievers.\n\n",
    " • SV/G: Team saves per game (bullpen closing output). Higher is better.\n\n",
    " • OAA: Outs Above Average from Statcast range. Higher is better.\n\n",
    " • DRS: Defensive Runs Saved. Higher is better.\n\n",
    " • Composite: Average percentile across each category's stats (bullpen ",
    "includes K-BB%, FIP, and SV/G). Higher is better."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "FanGraphs",
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
