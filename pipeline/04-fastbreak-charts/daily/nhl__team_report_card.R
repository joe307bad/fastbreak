#!/usr/bin/env Rscript

# NHL Team Report Card — per-team player leaderboards grouped by position group.
# Mirrors mlb__team_report_card.R and nfl__team_report_card.R: every player in a
# group is ranked league-wide on five stats, those ranks average into a player
# composite, and the same stats aggregated over the team's whole group produce
# the group's team composite. The four group composites average into the team's
# overall composite.
#
# Position groups and their stats:
#  - Forwards:      points/60 + goals + shooting% + Corsi for% + plus-minus
#  - Defensemen:    points/60 + blocks/60 + hits/60 + Corsi for% + plus-minus
#  - Goaltenders:   save% + GAA + quality start% + shutouts + saves
#  - Special teams: the team row is power play and penalty kill conversion and
#                   the goals both units trade; the player table is the skaters
#                   producing on the power play, which is the only special-teams
#                   production the public feeds break out per player
#
# Everything comes from the NHL's own stats API (api.nhle.com /stats/rest and
# api-web.nhle.com), the same feed nhl__team_efficiency.R and
# nhl__player_scoring.R already use, plus ESPN for injuries and
# playoffstatus.com for playoff odds.
#
# Differences from the MLB card:
#  - A 10-game form panel and a last-10 ledger, same as MLB
#  - No WAR column: the NHL feeds carry no all-in-one value metric

library(dplyr)
library(tidyr)
library(jsonlite)
library(rvest)
library(httr)

TOP_N <- 10
LAST_TEN_COUNT <- 10
REGULAR_SEASON_GAMES <- 82

# A season is only used for the card once every team has played enough of it to
# rank on; before that the card reports the last completed season instead.
MIN_TEAM_GAMES_FOR_CURRENT_SEASON <- 5

# Qualification. A rate stat like points per 60 needs two things: enough games
# to be a sample, and enough ice time per game to be a regular. The games bar is
# a share of what the league has actually played so an early-season card still
# has players in every group; the ice time bar is absolute, because a fourth
# liner's per-60 is flattering at any point in the season.
MIN_SKATER_GAMES_SHARE <- 0.4
MIN_GOALIE_GAMES_SHARE <- 0.2
MIN_SKATER_TOI_PER_GAME <- 10
MIN_PP_POINTS <- 1

cat("=== NHL Team Report Card ===\n")

# ============================================================================
# Shared helpers (mirrors nfl__team_report_card.R)
# ============================================================================
# Null/blank coalesce. Deliberately only inspects scalars: the ESPN payload
# walk feeds whole lists through this, and is.na() on a list returns a vector
# that would blow up the `if`.
`%||%` <- function(a, b) {
  if (is.null(a) || length(a) == 0) return(b)
  if (is.atomic(a) && length(a) == 1) {
    if (is.na(a)) return(b)
    if (is.character(a) && !nzchar(a)) return(b)
  }
  a
}

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
  vals <- suppressWarnings(as.numeric(df[[col]]))
  rk <- if (lower_better) tied_rank(vals) else tied_rank(-vals)
  df[[paste0(col, "_rank")]] <- as.integer(rk$rank)
  df[[paste0(col, "_rankDisplay")]] <- rk$rankDisplay
  df
}

# Average percentile across a category's stat ranks (100 = best in the league on
# every stat). Surfaces well-rounded players instead of one-stat specialists.
add_composite_score <- function(df, stat_cols) {
  if (nrow(df) == 0) {
    df$composite_score <- numeric(0)
    return(df)
  }
  n <- nrow(df)
  score_matrix <- vapply(stat_cols, function(col) {
    ranks <- df[[paste0(col, "_rank")]]
    pct <- (n - ranks + 1) / n * 100
    pct[is.na(ranks)] <- NA_real_
    pct
  }, numeric(n))
  if (length(stat_cols) == 1) {
    df$composite_score <- as.numeric(score_matrix)
  } else {
    df$composite_score <- rowMeans(score_matrix, na.rm = TRUE)
  }
  df$composite_score[is.nan(df$composite_score)] <- NA_real_
  df
}

stat_entry <- function(row, col, label, digits = NULL, display_value = NULL) {
  if (is.null(row) || !col %in% names(row) || is.na(row[[col]])) {
    return(list(label = label, value = NULL, rank = NULL, rankDisplay = NULL, displayValue = NULL))
  }
  value <- as.numeric(row[[col]])
  if (!is.null(digits)) value <- round(value, digits)
  rank_val <- if (paste0(col, "_rank") %in% names(row)) row[[paste0(col, "_rank")]] else NA
  rank_display <- if (paste0(col, "_rankDisplay") %in% names(row)) row[[paste0(col, "_rankDisplay")]] else NA
  list(
    label = label,
    value = value,
    rank = if (length(rank_val) == 0 || is.na(rank_val)) NULL else as.integer(rank_val[[1]]),
    rankDisplay = if (length(rank_display) == 0 || is.na(rank_display)) NULL else as.character(rank_display[[1]]),
    displayValue = display_value
  )
}

composite_stat_entry <- function(row) {
  composite <- if (!is.null(row) && "composite_score" %in% names(row)) row$composite_score else NA
  if (is.null(row) || length(composite) == 0 || is.na(composite[[1]])) {
    return(list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL))
  }
  list(label = "Composite", value = round(composite[[1]], 1), rank = NULL, rankDisplay = NULL)
}

team_composite_stat_entry <- function(row) {
  composite <- if (!is.null(row) && "composite_score" %in% names(row)) as.numeric(row$composite_score) else NA_real_
  if (is.null(row) || length(composite) == 0 || is.na(composite[[1]])) {
    return(list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL))
  }
  rank_val <- if ("composite_score_rank" %in% names(row)) as.integer(row$composite_score_rank[[1]]) else NA_integer_
  rank_display <- if ("composite_score_rankDisplay" %in% names(row)) as.character(row$composite_score_rankDisplay[[1]]) else NA_character_
  list(
    label = "Composite",
    value = round(composite[[1]], 1),
    rank = if (is.na(rank_val)) NULL else rank_val,
    rankDisplay = if (is.na(rank_display)) NULL else rank_display
  )
}

build_player <- function(row, stat_cols, stat_labels, digits_map = list()) {
  stats <- setNames(
    lapply(stat_cols, function(col) stat_entry(row, col, stat_labels[[col]], digits_map[[col]])),
    stat_cols
  )
  stats$aggregate <- composite_stat_entry(row)
  list(
    playerId = as.character(row$player_id),
    name = as.character(row$player_name),
    position = if (!is.null(row$position) && !is.na(row$position)) as.character(row$position) else NULL,
    stats = stats
  )
}

top_team_players <- function(df, team, stat_cols, stat_labels, digits_map = list()) {
  team_df <- df %>%
    filter(team_code == team) %>%
    arrange(desc(composite_score)) %>%
    head(TOP_N)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) build_player(team_df[i, ], stat_cols, stat_labels, digits_map))
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
  stats$aggregate <- if (nrow(row) == 0) {
    list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL)
  } else {
    team_composite_stat_entry(row[1, ])
  }
  list(stats = stats)
}

build_rankings <- function(team_df, team_col, stat_col, rank_col, rankDisplay_col) {
  valid <- !is.na(team_df[[rank_col]]) & !is.na(team_df[[stat_col]])
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
    list(rank = df$rank[i], rankDisplay = df$rankDisplay[i], value = df$value[i], team = df$team[i])
  })
}

build_category_stat_rankings <- function(category, team_df, stat_cols) {
  setNames(
    lapply(stat_cols, function(col) {
      build_rankings(team_df, "team_code", col, paste0(col, "_rank"), paste0(col, "_rankDisplay"))
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
      player = as.character(row$player_name[[1]])
    )
  })
}

build_player_pool_rankings <- function(category, df, stat_cols) {
  setNames(
    lapply(stat_cols, function(col) build_player_rankings(df, col)),
    paste0(category, ".player.", stat_cols)
  )
}

safe_div <- function(numerator, denominator) {
  ifelse(is.na(denominator) | denominator == 0, NA_real_, numerator / denominator)
}

# ============================================================================
# NHL API access
# ============================================================================
# api-web.nhle.com answers with a 307 to the same path, which httr follows by
# default; a bare download would not.
nhl_get_json <- function(url, attempts = 3, simplify = TRUE) {
  for (attempt in seq_len(attempts)) {
    resp <- tryCatch(GET(url, timeout(60)), error = function(e) NULL)
    if (!is.null(resp) && status_code(resp) == 200) {
      payload <- tryCatch(
        fromJSON(content(resp, as = "text", encoding = "UTF-8"), simplifyVector = simplify),
        error = function(e) NULL
      )
      if (!is.null(payload)) return(payload)
    }
    if (attempt < attempts) Sys.sleep(2 * attempt)
  }
  NULL
}

# Every /stats/rest report takes the same season + game-type filter and answers
# with { data: [...] }.
nhl_report <- function(path, season_id, game_type = 2) {
  url <- sprintf(
    "https://api.nhle.com/stats/rest/en/%s?isAggregate=false&isGame=false&limit=-1&cayenneExp=seasonId=%s%%20and%%20gameTypeId=%d",
    path, season_id, game_type
  )
  payload <- nhl_get_json(url)
  if (is.null(payload) || is.null(payload$data) || length(payload$data) == 0) {
    return(NULL)
  }
  as_tibble(payload$data)
}

nhl_report_or_stop <- function(path, season_id) {
  out <- nhl_report(path, season_id)
  if (is.null(out)) stop(paste("NHL API returned no data for", path, season_id))
  out
}

nhl_report_or_warn <- function(path, season_id) {
  out <- nhl_report(path, season_id)
  if (is.null(out)) cat("Warning: no data from NHL API for", path, "\n")
  out
}

# A traded player's teamAbbrevs reads "NYI,NJD"; the last code is the team they
# are on now, which is the team whose card they belong on.
current_team <- function(team_abbrevs) {
  vapply(as.character(team_abbrevs), function(x) {
    if (is.na(x) || !nzchar(x)) return(NA_character_)
    parts <- trimws(strsplit(x, ",", fixed = TRUE)[[1]])
    parts[length(parts)]
  }, character(1), USE.NAMES = FALSE)
}

# ============================================================================
# Season + phase resolution
# ============================================================================
# NHL season ids are the two calendar years joined: 20262027 is 2026-27. The
# card always reports a season every team has played enough of to rank on, so
# until the new season clears MIN_TEAM_GAMES_FOR_CURRENT_SEASON it stays on the
# completed one and the phase says which situation the reader is looking at.
season_id_for <- function(start_year) paste0(start_year, start_year + 1)

calendar_start_year <- {
  y <- as.numeric(format(Sys.Date(), "%Y"))
  m <- as.numeric(format(Sys.Date(), "%m"))
  # The NHL season opens in October and is named for the year it opens.
  if (m >= 9) y else y - 1
}

season_team_games <- function(season_id) {
  summary <- nhl_report("team/summary", season_id)
  if (is.null(summary) || nrow(summary) == 0) return(list(summary = NULL, games = 0))
  games <- suppressWarnings(min(as.numeric(summary$gamesPlayed), na.rm = TRUE))
  if (!is.finite(games)) games <- 0
  list(summary = summary, games = games)
}

# Resolve in two steps rather than one loop, so a season that is merely
# unreachable can never be mistaken for one that is over: the phase is decided
# by which branch answered, not by whether playoff games exist for that season
# (every completed season has those).
resolve_season_context <- function() {
  current_year <- calendar_start_year
  current <- season_team_games(season_id_for(current_year))

  if (current$games >= MIN_TEAM_GAMES_FOR_CURRENT_SEASON) {
    in_postseason <- !is.null(nhl_report("team/summary", season_id_for(current_year), game_type = 3))
    return(list(
      start_year = current_year,
      season_id = season_id_for(current_year),
      team_summary = current$summary,
      phase = if (in_postseason) "POST" else "REG",
      started_season = NULL
    ))
  }

  # Not enough of the current season on the board yet. Report the most recent
  # completed one and say whether the new season has opened (EARLY) or not
  # (PRE) — either way the card is not describing the season in progress.
  for (year in (current_year - 1):(current_year - 3)) {
    prev <- season_team_games(season_id_for(year))
    if (prev$games > 0) {
      return(list(
        start_year = year,
        season_id = season_id_for(year),
        team_summary = prev$summary,
        phase = if (current$games > 0) "EARLY" else "PRE",
        started_season = current_year
      ))
    }
  }
  stop("Could not find an NHL season with enough completed games")
}

season_ctx <- resolve_season_context()
season_start_year <- season_ctx$start_year
season_id <- season_ctx$season_id
season_phase <- season_ctx$phase
started_season <- season_ctx$started_season
team_summary_raw <- season_ctx$team_summary

season_phase_label <- switch(
  season_phase,
  PRE = "Preseason",
  EARLY = "Early Season",
  REG = "Regular Season",
  POST = "Postseason",
  season_phase
)

season_label <- paste0(season_start_year, "-", substr(season_start_year + 1, 3, 4))
cat("Season:", season_label, "| phase:", season_phase_label, "\n")

games_played <- suppressWarnings(min(as.numeric(team_summary_raw$gamesPlayed), na.rm = TRUE))
if (!is.finite(games_played)) games_played <- 0
season_fraction <- max(min(games_played / REGULAR_SEASON_GAMES, 1), 1 / REGULAR_SEASON_GAMES)

scaled_min <- function(full_threshold) {
  max(1, round(full_threshold * season_fraction))
}

MIN_SKATER_GAMES <- max(2, ceiling(games_played * MIN_SKATER_GAMES_SHARE))
MIN_GOALIE_GAMES <- max(1, ceiling(games_played * MIN_GOALIE_GAMES_SHARE))

cat("Regular season games played (fewest by any team):", games_played,
    "| thresholds — skater games:", MIN_SKATER_GAMES,
    "| skater min/game:", MIN_SKATER_TOI_PER_GAME,
    "| goalie games:", MIN_GOALIE_GAMES, "\n")

# ============================================================================
# Team metadata
# ============================================================================
# The current 32 teams, their divisions and their standings position come from
# api-web's standings; the stats reports key on teamId, so the franchise list
# supplies the id -> tri-code bridge.
standings_payload <- nhl_get_json("https://api-web.nhle.com/v1/standings/now", simplify = FALSE)
if (is.null(standings_payload) || length(standings_payload$standings %||% list()) == 0) {
  stop("Could not load NHL standings")
}

standings <- bind_rows(lapply(standings_payload$standings, function(row) {
  tibble(
    team_code = row$teamAbbrev$default %||% NA_character_,
    team_name = row$teamName$default %||% NA_character_,
    conference = row$conferenceName %||% NA_character_,
    division = row$divisionName %||% NA_character_,
    division_sequence = as.integer(row$divisionSequence %||% NA),
    conference_sequence = as.integer(row$conferenceSequence %||% NA)
  )
})) %>%
  filter(!is.na(team_code)) %>%
  arrange(team_code)

ALL_TEAMS <- standings$team_code
team_names <- setNames(standings$team_name, standings$team_code)
team_divisions <- setNames(standings$division, standings$team_code)
team_conferences <- setNames(standings$conference, standings$team_code)

franchises <- nhl_get_json("https://api.nhle.com/stats/rest/en/team")
team_id_to_code <- if (!is.null(franchises$data)) {
  codes <- as_tibble(franchises$data) %>%
    filter(triCode %in% ALL_TEAMS) %>%
    distinct(id, triCode)
  setNames(codes$triCode, as.character(codes$id))
} else {
  stop("Could not load the NHL franchise list")
}

cat("Loaded", length(ALL_TEAMS), "NHL teams\n")

team_summary <- team_summary_raw %>%
  mutate(team_code = unname(team_id_to_code[as.character(teamId)])) %>%
  filter(!is.na(team_code), team_code %in% ALL_TEAMS)

# ============================================================================
# Records
# ============================================================================
team_records <- team_summary %>%
  transmute(
    team_code,
    W = as.integer(wins),
    L = as.integer(losses),
    OTL = as.integer(otLosses),
    point_pct = as.numeric(pointPct)
  ) %>%
  left_join(standings %>% select(team_code, division, conference, division_sequence),
            by = "team_code")

team_records <- rank_and_assign(team_records, "point_pct")
team_records <- team_records %>%
  mutate(
    division_rank = division_sequence,
    division_rank_display = as.character(division_sequence)
  )

cat("Records — teams:", nrow(team_records), "\n")

# ============================================================================
# Skater, goalie and team-unit stats
# ============================================================================
skater_summary <- nhl_report_or_stop("skater/summary", season_id)
skater_realtime <- nhl_report_or_warn("skater/realtime", season_id)
skater_percentages <- nhl_report_or_warn("skater/percentages", season_id)
goalie_summary <- nhl_report_or_stop("goalie/summary", season_id)
goalie_advanced <- nhl_report_or_warn("goalie/advanced", season_id)
team_powerplay <- nhl_report_or_warn("team/powerplay", season_id)
team_penaltykill <- nhl_report_or_warn("team/penaltykill", season_id)

cat("Loaded", nrow(skater_summary), "skaters,", nrow(goalie_summary), "goalies\n")

skaters <- skater_summary %>%
  transmute(
    player_id = as.character(playerId),
    player_name = as.character(skaterFullName),
    position = as.character(positionCode),
    team_code = current_team(teamAbbrevs),
    gp = as.integer(gamesPlayed),
    # timeOnIcePerGame is seconds per game; the card works in minutes.
    toi = as.numeric(timeOnIcePerGame) * as.integer(gamesPlayed) / 60,
    points = as.integer(points),
    goals = as.integer(goals),
    assists = as.integer(assists),
    shots = as.integer(shots),
    plus_minus = as.integer(plusMinus),
    shooting_pct = as.numeric(shootingPct) * 100,
    pp_points = as.integer(ppPoints),
    pp_goals = as.integer(ppGoals)
  ) %>%
  mutate(pp_assists = pp_points - pp_goals) %>%
  filter(!is.na(team_code), team_code %in% ALL_TEAMS)

if (!is.null(skater_realtime)) {
  skaters <- skaters %>%
    left_join(
      skater_realtime %>%
        transmute(
          player_id = as.character(playerId),
          blocks = as.integer(blockedShots),
          hits = as.integer(hits)
        ) %>%
        distinct(player_id, .keep_all = TRUE),
      by = "player_id"
    )
} else {
  skaters <- skaters %>% mutate(blocks = NA_integer_, hits = NA_integer_)
}

if (!is.null(skater_percentages)) {
  skaters <- skaters %>%
    left_join(
      skater_percentages %>%
        transmute(
          player_id = as.character(playerId),
          sat_pct = as.numeric(satPercentage) * 100
        ) %>%
        distinct(player_id, .keep_all = TRUE),
      by = "player_id"
    )
} else {
  skaters <- skaters %>% mutate(sat_pct = NA_real_)
}

skaters <- skaters %>%
  mutate(
    points_per_60 = safe_div(points * 60, toi),
    blocks_per_60 = safe_div(blocks * 60, toi),
    hits_per_60 = safe_div(hits * 60, toi),
    position_group = dplyr::case_when(
      position %in% c("C", "L", "R") ~ "forwards",
      position == "D" ~ "defensemen",
      TRUE ~ NA_character_
    )
  )

goalies <- goalie_summary %>%
  transmute(
    player_id = as.character(playerId),
    player_name = as.character(goalieFullName),
    position = "G",
    team_code = current_team(teamAbbrevs),
    gp = as.integer(gamesPlayed),
    gs = as.integer(gamesStarted),
    toi = as.numeric(timeOnIce) / 60,
    goals_against = as.integer(goalsAgainst),
    shots_against = as.integer(shotsAgainst),
    saves = as.integer(saves),
    shutouts = as.integer(shutouts),
    save_pct = as.numeric(savePct) * 100,
    gaa = as.numeric(goalsAgainstAverage)
  ) %>%
  filter(!is.na(team_code), team_code %in% ALL_TEAMS)

if (!is.null(goalie_advanced)) {
  goalies <- goalies %>%
    left_join(
      goalie_advanced %>%
        transmute(
          player_id = as.character(playerId),
          quality_starts = as.integer(qualityStart),
          quality_start_pct = as.numeric(qualityStartsPct) * 100
        ) %>%
        distinct(player_id, .keep_all = TRUE),
      by = "player_id"
    )
} else {
  goalies <- goalies %>% mutate(quality_starts = NA_integer_, quality_start_pct = NA_real_)
}

# ============================================================================
# Position group pools
# ============================================================================
skater_group_pool <- function(group_key) {
  skaters %>%
    filter(
      position_group == group_key,
      gp >= MIN_SKATER_GAMES,
      safe_div(toi, gp) >= MIN_SKATER_TOI_PER_GAME
    ) %>%
    select(player_id, player_name, team_code, position, gp, toi,
           points_per_60, goals, shooting_pct, sat_pct, plus_minus,
           blocks_per_60, hits_per_60)
}

# The team row aggregates every player in the group, qualified or not, so it
# describes the whole position group rather than just its regulars. Rates come
# from the group's pooled totals, and Corsi is weighted by ice time.
skater_group_team_pool <- function(group_key) {
  totals <- skaters %>%
    filter(position_group == group_key) %>%
    group_by(team_code) %>%
    summarise(
      # Named apart from the inputs: inside summarise() a column is replaced by
      # its own result as soon as it is assigned, so reusing `toi` for the sum
      # would leave the weighted Corsi below multiplying by a scalar total.
      toi_total = sum(toi, na.rm = TRUE),
      corsi_toi = sum(toi[!is.na(sat_pct)], na.rm = TRUE),
      corsi_weighted = sum(sat_pct * toi, na.rm = TRUE),
      points = sum(points, na.rm = TRUE),
      goals = sum(goals, na.rm = TRUE),
      shots = sum(shots, na.rm = TRUE),
      blocks = sum(blocks, na.rm = TRUE),
      hits = sum(hits, na.rm = TRUE),
      plus_minus = sum(plus_minus, na.rm = TRUE),
      .groups = "drop"
    ) %>%
    mutate(
      toi = toi_total,
      sat_pct = safe_div(corsi_weighted, corsi_toi),
      points_per_60 = safe_div(points * 60, toi),
      blocks_per_60 = safe_div(blocks * 60, toi),
      hits_per_60 = safe_div(hits * 60, toi),
      shooting_pct = safe_div(goals * 100, shots)
    ) %>%
    select(-toi_total, -corsi_toi, -corsi_weighted)
  tibble(team_code = ALL_TEAMS) %>% left_join(totals, by = "team_code")
}

goalie_pool <- goalies %>%
  filter(gp >= MIN_GOALIE_GAMES) %>%
  select(player_id, player_name, team_code, position, gp,
         save_pct, gaa, quality_start_pct, shutouts, saves)

team_goalies <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    goalies %>%
      group_by(team_code) %>%
      summarise(
        toi = sum(toi, na.rm = TRUE),
        goals_against = sum(goals_against, na.rm = TRUE),
        shots_against = sum(shots_against, na.rm = TRUE),
        saves = sum(saves, na.rm = TRUE),
        shutouts = sum(shutouts, na.rm = TRUE),
        quality_starts = sum(quality_starts, na.rm = TRUE),
        starts = sum(gs, na.rm = TRUE),
        .groups = "drop"
      ) %>%
      mutate(
        save_pct = safe_div(saves * 100, shots_against),
        gaa = safe_div(goals_against * 60, toi),
        quality_start_pct = safe_div(quality_starts * 100, starts)
      ),
    by = "team_code"
  )

# Special teams. Only power play production is broken out per skater in the
# public feeds, so that is the player table; the team row is both units.
power_play_pool <- skaters %>%
  filter(gp >= MIN_SKATER_GAMES, pp_points >= MIN_PP_POINTS) %>%
  arrange(desc(pp_points)) %>%
  select(player_id, player_name, team_code, position, pp_points, pp_goals, pp_assists)

team_special_teams <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    team_summary %>%
      transmute(
        team_code,
        power_play_pct = as.numeric(powerPlayPct) * 100,
        penalty_kill_pct = as.numeric(penaltyKillPct) * 100
      ),
    by = "team_code"
  )

if (!is.null(team_powerplay)) {
  team_special_teams <- team_special_teams %>%
    left_join(
      team_powerplay %>%
        mutate(team_code = unname(team_id_to_code[as.character(teamId)])) %>%
        filter(!is.na(team_code)) %>%
        transmute(team_code, pp_goals_for = as.integer(powerPlayGoalsFor)),
      by = "team_code"
    )
} else {
  team_special_teams <- team_special_teams %>% mutate(pp_goals_for = NA_integer_)
}

if (!is.null(team_penaltykill)) {
  team_special_teams <- team_special_teams %>%
    left_join(
      team_penaltykill %>%
        mutate(team_code = unname(team_id_to_code[as.character(teamId)])) %>%
        filter(!is.na(team_code)) %>%
        transmute(
          team_code,
          pp_goals_against = as.integer(ppGoalsAgainst),
          sh_goals_for = as.integer(shGoalsFor)
        ),
      by = "team_code"
    )
} else {
  team_special_teams <- team_special_teams %>%
    mutate(pp_goals_against = NA_integer_, sh_goals_for = NA_integer_)
}

forwards <- skater_group_pool("forwards")
defensemen <- skater_group_pool("defensemen")
team_forwards <- skater_group_team_pool("forwards")
team_defensemen <- skater_group_team_pool("defensemen")

cat("Qualified pools — forwards:", nrow(forwards),
    "| defensemen:", nrow(defensemen),
    "| goalies:", nrow(goalie_pool),
    "| power play:", nrow(power_play_pool), "\n")

# ============================================================================
# Schedule, recent form and the last-10 ledger
# ============================================================================
# One call returns every game of the season with both scores, so the ledger and
# the form panel come out of the same source of truth.
PLAYED_GAME_STATES <- c(5, 6, 7)

games_payload <- nhl_get_json(sprintf(
  "https://api.nhle.com/stats/rest/en/game?cayenneExp=season=%s%%20and%%20gameType=2", season_id
))

team_game_rows <- if (is.null(games_payload$data) || length(games_payload$data) == 0) {
  cat("Warning: could not load the NHL schedule — game logs will be empty\n")
  tibble(
    team_code = character(), game_id = integer(), game_date = character(),
    opponent_code = character(), is_home = logical(), team_score = integer(),
    opponent_score = integer(), won = logical(), ot_loss = logical()
  )
} else {
  played <- as_tibble(games_payload$data) %>%
    filter(gameStateId %in% PLAYED_GAME_STATES,
           !is.na(homeScore), !is.na(visitingScore)) %>%
    mutate(
      home_code = unname(team_id_to_code[as.character(homeTeamId)]),
      away_code = unname(team_id_to_code[as.character(visitingTeamId)])
    ) %>%
    filter(!is.na(home_code), !is.na(away_code))

  bind_rows(
    played %>% transmute(
      team_code = home_code, game_id = id, game_date = as.character(gameDate),
      opponent_code = away_code, is_home = TRUE,
      team_score = as.integer(homeScore), opponent_score = as.integer(visitingScore),
      beyond_regulation = as.integer(period) > 3
    ),
    played %>% transmute(
      team_code = away_code, game_id = id, game_date = as.character(gameDate),
      opponent_code = home_code, is_home = FALSE,
      team_score = as.integer(visitingScore), opponent_score = as.integer(homeScore),
      beyond_regulation = as.integer(period) > 3
    )
  ) %>%
    mutate(
      won = team_score > opponent_score,
      ot_loss = !won & coalesce(beyond_regulation, FALSE)
    ) %>%
    filter(team_code %in% ALL_TEAMS)
}

cat("Schedule ledger — team-game rows:", nrow(team_game_rows), "\n")

recent_games <- team_game_rows %>%
  group_by(team_code) %>%
  arrange(desc(game_date), desc(game_id), .by_group = TRUE) %>%
  slice_head(n = LAST_TEN_COUNT) %>%
  ungroup()

TREND_STAT_COLS <- c("record", "goalDiffPerGame", "goalsPerGame", "goalsAllowedPerGame")

team_recent_trend <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    recent_games %>%
      group_by(team_code) %>%
      summarise(
        recent_wins = sum(won, na.rm = TRUE),
        recent_ot_losses = sum(ot_loss, na.rm = TRUE),
        recent_losses = sum(!won & !ot_loss, na.rm = TRUE),
        goalsPerGame = safe_div(sum(team_score, na.rm = TRUE), n()),
        goalsAllowedPerGame = safe_div(sum(opponent_score, na.rm = TRUE), n()),
        .groups = "drop"
      ),
    by = "team_code"
  ) %>%
  mutate(
    recent_wins = coalesce(recent_wins, 0L),
    recent_losses = coalesce(recent_losses, 0L),
    recent_ot_losses = coalesce(recent_ot_losses, 0L),
    # Points percentage, the NHL's own standings metric: two points for a win,
    # one for an overtime loss, out of two per game.
    record = safe_div(recent_wins * 2 + recent_ot_losses,
                      2 * (recent_wins + recent_losses + recent_ot_losses)),
    goalDiffPerGame = goalsPerGame - goalsAllowedPerGame
  )

build_game_log_for_team <- function(team) {
  empty <- list(
    label = paste("Last", LAST_TEN_COUNT, "Games"),
    columns = list("Opponent", "Opp", "Team", "Diff"),
    games = list(),
    record = list(wins = 0L, losses = 0L, ties = 0L, display = "0-0-0"),
    totalDifferential = 0L
  )
  rows <- recent_games %>%
    filter(team_code == team) %>%
    arrange(game_date, game_id)
  if (nrow(rows) == 0) return(empty)

  games <- lapply(seq_len(nrow(rows)), function(i) {
    row <- rows[i, ]
    loc <- if (isTRUE(row$is_home)) "vs" else "@"
    list(
      date = substr(as.character(row$game_date), 1, 10),
      location = loc,
      opponent = as.character(row$opponent_code),
      opponentLabel = paste(loc, row$opponent_code),
      teamScore = as.integer(row$team_score),
      opponentScore = as.integer(row$opponent_score),
      differential = as.integer(row$team_score - row$opponent_score),
      result = if (isTRUE(row$won)) "W" else "L",
      won = isTRUE(row$won),
      played = TRUE
    )
  })
  wins <- sum(rows$won, na.rm = TRUE)
  ot_losses <- sum(rows$ot_loss, na.rm = TRUE)
  losses <- nrow(rows) - wins - ot_losses
  list(
    label = paste("Last", LAST_TEN_COUNT, "Games"),
    columns = list("Opponent", "Opp", "Team", "Diff"),
    games = games,
    record = list(wins = as.integer(wins), losses = as.integer(losses),
                  ties = as.integer(ot_losses),
                  display = paste0(wins, "-", losses, "-", ot_losses)),
    totalDifferential = as.integer(sum(vapply(games, function(g) g$differential, integer(1))))
  )
}

# ============================================================================
# Below-replacement performers
# ============================================================================
# The NHL feeds carry no all-in-one value metric, so the stand-in for a
# below-replacement bat is a regular the team was outscored with at even
# strength.
below_replacement <- skaters %>%
  filter(
    gp >= MIN_SKATER_GAMES,
    safe_div(toi, gp) >= MIN_SKATER_TOI_PER_GAME,
    plus_minus < 0
  ) %>%
  transmute(player_id, player_name, team_code, position,
            toi = round(toi, 0), plus_minus)

team_skater_toi <- skaters %>%
  group_by(team_code) %>%
  summarise(team_total_toi = sum(toi, na.rm = TRUE), .groups = "drop")

team_below_replacement <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    below_replacement %>%
      group_by(team_code) %>%
      summarise(br_toi = sum(toi, na.rm = TRUE), .groups = "drop"),
    by = "team_code"
  ) %>%
  left_join(team_skater_toi, by = "team_code") %>%
  mutate(
    br_toi = coalesce(br_toi, 0),
    below_replacement_toi_pct = coalesce(safe_div(br_toi * 100, team_total_toi), 0)
  ) %>%
  select(team_code, below_replacement_toi_pct)

cat("Below-replacement skaters:", nrow(below_replacement), "\n")

# ============================================================================
# Injury report (ESPN)
# ============================================================================
injury_status_weight <- function(status) {
  st <- tolower(trimws(as.character(status)))
  if (!nzchar(st)) return(NA_real_)
  if (grepl("suspension|suspended", st)) return(NA_real_)
  if (grepl("^active$", st)) return(NA_real_)
  if (grepl("injured reserve|long term|^ir$", st)) return(1.0)
  if (grepl("out for season|season-ending", st)) return(1.0)
  if (grepl("^out$", st)) return(0.9)
  if (grepl("doubtful", st)) return(0.75)
  if (grepl("questionable", st)) return(0.4)
  if (grepl("day-to-day|day to day", st)) return(0.3)
  0.5
}

fetch_espn_injuries <- function(url, name_lookup) {
  empty <- tibble(
    team_code = character(), entry_id = character(), athlete_id = character(),
    athlete_name = character(), status = character(), position = character(),
    status_weight = numeric()
  )
  safe_scalar <- function(x, default = NA_character_) {
    if (is.null(x) || length(x) == 0) return(default)
    if (length(x) > 1) x <- x[[1]]
    if (is.na(x) || !nzchar(as.character(x))) return(default)
    as.character(x)
  }
  # ESPN 403s R's built-in url-connection User-Agent (what a bare
  # jsonlite::fromJSON(url) sends) but serves the identical request over
  # libcurl. Do not add a browser User-Agent — ESPN rejects those too.
  fetch_payload <- function(attempts = 3) {
    for (attempt in seq_len(attempts)) {
      resp <- tryCatch(GET(url, timeout(60)), error = function(e) NULL)
      if (!is.null(resp) && status_code(resp) == 200) {
        payload <- tryCatch(
          fromJSON(content(resp, as = "text", encoding = "UTF-8"), simplifyVector = FALSE),
          error = function(e) NULL
        )
        if (!is.null(payload)) return(payload)
      }
      if (attempt < attempts) Sys.sleep(2 * attempt)
    }
    NULL
  }
  tryCatch({
    payload <- fetch_payload()
    if (is.null(payload)) stop("ESPN injury endpoint unreachable after retries")
    rows <- list()
    for (group in (payload$injuries %||% list())) {
      display_name <- safe_scalar(group$displayName, "")
      team_code <- name_lookup[[normalize_team_name(display_name)]] %||% NA_character_
      if (is.na(team_code)) {
        cat("Warning: could not map ESPN team:", display_name, "\n")
        next
      }
      for (entry in (group$injuries %||% list())) {
        status <- safe_scalar(entry$status, "")
        weight <- injury_status_weight(status)
        if (is.na(weight)) next
        athlete <- entry$athlete
        if (is.null(athlete)) athlete <- list()
        rows[[length(rows) + 1]] <- list(
          team_code = team_code,
          entry_id = safe_scalar(entry$id),
          athlete_id = safe_scalar(athlete$id),
          athlete_name = safe_scalar(athlete$displayName),
          status = status,
          position = if (is.null(athlete$position)) NA_character_ else safe_scalar(athlete$position$abbreviation),
          status_weight = weight
        )
      }
    }
    if (length(rows) == 0) return(empty)
    bind_rows(rows)
  }, error = function(e) {
    cat("Warning: could not load ESPN injury report:", e$message, "\n")
    empty
  })
}

# ESPN writes "Montreal Canadiens" where the NHL writes "Montreal" with an
# accent, so both sides of the match are folded to plain ASCII first.
normalize_team_name <- function(x) {
  plain <- iconv(as.character(x), from = "UTF-8", to = "ASCII//TRANSLIT")
  plain[is.na(plain)] <- as.character(x)[is.na(plain)]
  tolower(trimws(gsub("[^A-Za-z0-9 ]", "", plain)))
}

cat("Loading ESPN NHL injury report...\n")
espn_name_lookup <- as.list(setNames(standings$team_code,
                                     normalize_team_name(standings$team_name)))
injury_players_raw <- fetch_espn_injuries(
  "https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/injuries",
  espn_name_lookup
)

# Ice time share stands in for MLB's WAR: it is the clearest measure of how
# much of a team's hockey the injured player was actually playing.
player_toi_share <- bind_rows(
  skaters %>% select(player_name, team_code, toi),
  goalies %>% select(player_name, team_code, toi)
) %>%
  left_join(team_skater_toi, by = "team_code") %>%
  transmute(
    athlete_key = tolower(trimws(player_name)),
    toi_share = coalesce(safe_div(toi * 100, team_total_toi), 0)
  ) %>%
  group_by(athlete_key) %>%
  summarise(toi_share = max(toi_share, na.rm = TRUE), .groups = "drop")

injury_players <- if (nrow(injury_players_raw) == 0) {
  cat("Warning: injury report unavailable — continuing without injury data\n")
  injury_players_raw %>% mutate(toi_share = numeric(), impact = numeric())
} else {
  injury_players_raw %>%
    mutate(athlete_key = tolower(trimws(athlete_name))) %>%
    left_join(player_toi_share, by = "athlete_key") %>%
    mutate(
      toi_share = coalesce(toi_share, 0),
      impact = toi_share * status_weight
    ) %>%
    select(-athlete_key)
}

team_injuries <- tibble(team_code = ALL_TEAMS) %>%
  left_join(
    injury_players %>%
      group_by(team_code) %>%
      summarise(
        injured_count = n(),
        injury_toi_share = sum(impact, na.rm = TRUE),
        .groups = "drop"
      ),
    by = "team_code"
  ) %>%
  mutate(
    injured_count = coalesce(as.integer(injured_count), 0L),
    injury_toi_share = coalesce(injury_toi_share, 0)
  )

cat("Injury report — players:", nrow(injury_players),
    "| teams with injuries:", sum(team_injuries$injured_count > 0), "\n")

# ============================================================================
# Playoff chances (playoffstatus.com)
# ============================================================================
# Short team names as playoffstatus.com prints them, plus the alternates it
# sometimes uses. Mirrors the table in nhl__matchup_stats.R.
PLAYOFF_NAME_TO_CODE <- c(
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

# Rows for countries and divisions show up around all-star and best-on-best
# breaks; none of them are teams.
NON_TEAM_ROW_PATTERN <- paste0(
  "^USA$|^Canada$|^Sweden$|^Finland$|^Latvia$|^Czechia$|^Czech Republic$|",
  "^Switzerland$|^Germany$|^Slovakia$|^Denmark$|^Norway$|^Austria$|^France$|",
  "^All-Star|^Atlantic$|^Metropolitan$|^Central$|^Pacific$|^Eastern$|^Western$|",
  "^World$|^North America$|4 Nations|Face-?Off"
)

scrape_playoff_probabilities <- function() {
  url <- "https://www.playoffstatus.com/nhl/nhlpostseasonprob.html"
  tryCatch({
    cat("Scraping NHL playoff probabilities from playoffstatus.com...\n")
    page <- read_html(url)
    tables <- page %>% html_elements("table")
    if (length(tables) == 0) {
      cat("Warning: no tables found on the playoff probability page\n")
      return(NULL)
    }
    prob_table <- NULL
    for (tbl in tables) {
      if (length(tbl %>% html_elements("tr")) > 20) {
        prob_table <- tbl
        break
      }
    }
    if (is.null(prob_table)) prob_table <- tables[[1]]

    parse_pct <- function(cell) {
      text <- cell %>% html_text(trim = TRUE)
      text <- gsub("[%<>]", "", text)
      text <- gsub("\\s+", "", text)
      val <- suppressWarnings(as.numeric(text))
      if (is.na(val)) 0 else val
    }

    results <- list()
    for (row in (prob_table %>% html_elements("tr"))) {
      cells <- row %>% html_elements("td")
      if (length(cells) < 12) next
      team_link <- cells[[1]] %>% html_element("a")
      team_name <- if (!is.na(team_link)) {
        team_link %>% html_text(trim = TRUE)
      } else {
        cells[[1]] %>% html_text(trim = TRUE)
      }
      team_name <- trimws(team_name)
      if (!nzchar(team_name) || grepl("Team|Conference", team_name, ignore.case = TRUE)) next
      if (grepl(NON_TEAM_ROW_PATTERN, team_name, ignore.case = TRUE)) next

      team_code <- PLAYOFF_NAME_TO_CODE[team_name]
      if (is.na(team_code)) {
        for (name in names(PLAYOFF_NAME_TO_CODE)) {
          if (grepl(name, team_name, ignore.case = TRUE)) {
            team_code <- PLAYOFF_NAME_TO_CODE[name]
            break
          }
        }
      }
      if (is.na(team_code)) {
        cat("Warning: could not map playoffstatus team name:", team_name, "\n")
        next
      }

      # Columns: Team, Conf, W, L, OTL, Pts, CurrentSeries, Cup winner, Cup
      # final, Conf final, Round 2, Round 1 — probabilities rise right to left.
      results[[unname(team_code)]] <- list(
        champProb = parse_pct(cells[[8]]),
        playoffProb = parse_pct(cells[[12]])
      )
    }
    cat("Scraped playoff probabilities for", length(results), "teams\n")
    results
  }, error = function(e) {
    cat("Warning: could not scrape playoff probabilities:", e$message, "\n")
    NULL
  })
}

# Playoff odds describe the season in progress. When the card has fallen back to
# the completed season (PRE/EARLY) those odds belong to a different season than
# every other number on the page, so they are left off rather than mixed in.
playoff_probs <- if (season_phase %in% c("REG", "POST")) {
  scrape_playoff_probabilities()
} else {
  cat("Skipping playoff odds: card covers the completed", season_label,
      "season, not the one those odds are for\n")
  NULL
}

# The sheet groups teams by conference and then by the division each one sits
# in, which is what standingsSection names. Hockey standings are run on points
# rather than games back, so no games-back column is published.
build_playoff_chances <- function() {
  if (is.null(playoff_probs) || length(playoff_probs) == 0) return(list())
  rows <- team_records %>%
    arrange(conference, division, division_rank) %>%
    filter(team_code %in% names(playoff_probs))
  if (nrow(rows) == 0) return(list())
  lapply(seq_len(nrow(rows)), function(i) {
    row <- rows[i, ]
    probs <- playoff_probs[[row$team_code]]
    list(
      team = row$team_code,
      teamCode = row$team_code,
      teamName = unname(team_names[row$team_code]),
      playoffProb = probs$playoffProb,
      champProb = probs$champProb,
      conference = row$conference,
      division = row$division,
      divisionRank = as.integer(row$division_rank),
      wins = as.integer(row$W),
      losses = as.integer(row$L),
      winPct = round(row$point_pct, 4),
      standingsSection = row$division
    )
  })
}

playoff_chances <- build_playoff_chances()
cat("Playoff chances entries:", length(playoff_chances), "\n")

# ============================================================================
# Category definitions
# ============================================================================
# One spec per category drives ranking, composites, the per-team payload and the
# league-wide ranking sheets, so a stat is only ever declared once.
CATEGORY_SPECS <- list(
  list(
    key = "forwards",
    label = "Forwards",
    description = paste0(
      "Centers and wingers ranked by points per 60 minutes, goals, shooting ",
      "percentage, Corsi for percentage (the share of shot attempts their team ",
      "takes while they are on the ice), and plus-minus. Qualified at ",
      MIN_SKATER_GAMES, "+ games."
    ),
    pool = quote(forwards),
    team_df = quote(team_forwards),
    stat_cols = c("points_per_60", "goals", "shooting_pct", "sat_pct", "plus_minus"),
    lower_better = character(0),
    labels = c(points_per_60 = "P/60", goals = "G", shooting_pct = "S%",
               sat_pct = "CF%", plus_minus = "+/-"),
    digits = list(points_per_60 = 2, goals = 0, shooting_pct = 1,
                  sat_pct = 1, plus_minus = 0)
  ),
  list(
    key = "defensemen",
    label = "Defensemen",
    description = paste0(
      "Defensemen ranked by points per 60 minutes, blocked shots and hits per ",
      "60, Corsi for percentage, and plus-minus. Qualified at ",
      MIN_SKATER_GAMES, "+ games."
    ),
    pool = quote(defensemen),
    team_df = quote(team_defensemen),
    stat_cols = c("points_per_60", "blocks_per_60", "hits_per_60", "sat_pct", "plus_minus"),
    lower_better = character(0),
    labels = c(points_per_60 = "P/60", blocks_per_60 = "BLK/60",
               hits_per_60 = "HIT/60", sat_pct = "CF%", plus_minus = "+/-"),
    digits = list(points_per_60 = 2, blocks_per_60 = 1, hits_per_60 = 1,
                  sat_pct = 1, plus_minus = 0)
  ),
  list(
    key = "goalies",
    label = "Goaltenders",
    description = paste0(
      "Goaltenders ranked by save percentage, goals against average (lower is ",
      "better), quality start rate, shutouts, and total saves. Qualified at ",
      MIN_GOALIE_GAMES, "+ games."
    ),
    pool = quote(goalie_pool),
    team_df = quote(team_goalies),
    stat_cols = c("save_pct", "gaa", "quality_start_pct", "shutouts", "saves"),
    lower_better = c("gaa"),
    labels = c(save_pct = "SV%", gaa = "GAA", quality_start_pct = "QS%",
               shutouts = "SO", saves = "SV"),
    digits = list(save_pct = 1, gaa = 2, quality_start_pct = 1, shutouts = 0, saves = 0)
  ),
  list(
    key = "specialTeams",
    label = "Special Teams",
    description = paste0(
      "The team row is both units: power play and penalty kill conversion, ",
      "power play goals scored, power play goals allowed (fewer is better), and ",
      "shorthanded goals scored. The player table is the skaters producing on ",
      "the power play — the only special-teams production the public feeds ",
      "break out per player. Qualified at ", MIN_SKATER_GAMES, "+ games and ",
      MIN_PP_POINTS, "+ power play point."
    ),
    pool = quote(power_play_pool),
    team_df = quote(team_special_teams),
    stat_cols = c("pp_points", "pp_goals", "pp_assists"),
    lower_better = character(0),
    labels = c(pp_points = "PPP", pp_goals = "PPG", pp_assists = "PPA"),
    digits = list(pp_points = 0, pp_goals = 0, pp_assists = 0),
    team_stat_cols = c("power_play_pct", "penalty_kill_pct", "pp_goals_for",
                       "pp_goals_against", "sh_goals_for"),
    team_lower_better = c("pp_goals_against"),
    team_labels = c(power_play_pct = "PP%", penalty_kill_pct = "PK%",
                    pp_goals_for = "PPGF", pp_goals_against = "PPGA",
                    sh_goals_for = "SHGF"),
    team_digits = list(power_play_pct = 1, penalty_kill_pct = 1, pp_goals_for = 0,
                       pp_goals_against = 0, sh_goals_for = 0)
  )
)

# The four groups feed the overall composite. Recent form, below-replacement ice
# time and the injury report are diagnostics, not quality ratings, so they sit
# outside it exactly as on the MLB card.
POSITION_GROUP_KEYS <- vapply(CATEGORY_SPECS, function(spec) spec$key, character(1))

spec_team_stat_cols <- function(spec) spec$team_stat_cols %||% spec$stat_cols
spec_team_lower_better <- function(spec) {
  if (!is.null(spec$team_stat_cols)) spec$team_lower_better %||% character(0) else spec$lower_better
}
spec_team_labels <- function(spec) {
  if (!is.null(spec$team_stat_cols)) spec$team_labels else spec$labels
}
spec_team_digits <- function(spec) {
  if (!is.null(spec$team_stat_cols)) spec$team_digits else spec$digits
}

# ============================================================================
# Rank + composite every pool
# ============================================================================
player_pools <- list()
team_pools <- list()

for (spec in CATEGORY_SPECS) {
  pool <- eval(spec$pool)
  team_df <- eval(spec$team_df)

  for (col in spec$stat_cols) {
    pool <- rank_and_assign(pool, col, lower_better = col %in% spec$lower_better)
  }
  pool <- add_composite_score(pool, spec$stat_cols)

  team_cols <- spec_team_stat_cols(spec)
  team_lower <- spec_team_lower_better(spec)
  team_df <- team_df %>% filter(team_code %in% ALL_TEAMS)
  for (col in team_cols) {
    team_df <- rank_and_assign(team_df, col, lower_better = col %in% team_lower)
  }
  team_df <- add_composite_score(team_df, team_cols)
  team_df <- rank_and_assign(team_df, "composite_score")

  player_pools[[spec$key]] <- pool
  team_pools[[spec$key]] <- team_df
}

team_below_replacement <- rank_and_assign(
  team_below_replacement, "below_replacement_toi_pct", lower_better = TRUE
)
team_below_replacement <- add_composite_score(team_below_replacement, c("below_replacement_toi_pct"))
team_below_replacement <- rank_and_assign(team_below_replacement, "composite_score")

below_replacement <- rank_and_assign(below_replacement, "toi")
below_replacement <- rank_and_assign(below_replacement, "plus_minus", lower_better = TRUE)

team_injuries <- rank_and_assign(team_injuries, "injured_count")
team_injuries <- rank_and_assign(team_injuries, "injury_toi_share")
team_injuries <- add_composite_score(team_injuries, c("injured_count", "injury_toi_share"))
team_injuries <- rank_and_assign(team_injuries, "composite_score")

# Recent form ranks on its own stats but has no composite: a 10-game sample is
# a form read, not a rating, and it stays out of the overall composite.
team_recent_trend <- rank_and_assign(team_recent_trend, "record")
team_recent_trend <- rank_and_assign(team_recent_trend, "goalDiffPerGame")
team_recent_trend <- rank_and_assign(team_recent_trend, "goalsPerGame")
team_recent_trend <- rank_and_assign(team_recent_trend, "goalsAllowedPerGame", lower_better = TRUE)

category_composite <- function(team_df, team) {
  row <- team_df %>% filter(team_code == team)
  if (nrow(row) == 0 || is.na(row$composite_score[1])) NA_real_ else row$composite_score[1]
}

team_overall <- tibble(team_code = ALL_TEAMS) %>%
  rowwise() %>%
  mutate(
    overall_composite = mean(
      vapply(POSITION_GROUP_KEYS, function(key) category_composite(team_pools[[key]], team_code), numeric(1)),
      na.rm = TRUE
    )
  ) %>%
  ungroup() %>%
  mutate(overall_composite = if_else(is.nan(overall_composite), NA_real_, overall_composite)) %>%
  filter(!is.na(overall_composite))

team_overall <- rank_and_assign(team_overall, "overall_composite")

# ============================================================================
# Extra category payload builders
# ============================================================================
trend_labels <- c(record = "Record", goalDiffPerGame = "Goal Diff/G",
                  goalsPerGame = "GF/G", goalsAllowedPerGame = "GA/G")
trend_digits <- list(record = 3, goalDiffPerGame = 2, goalsPerGame = 2,
                     goalsAllowedPerGame = 2)

# No aggregate entry: recent form is a read, not a rating, so the panel shows
# the stat rows and no composite.
build_trend_category_stats <- function(team) {
  row <- team_recent_trend %>% filter(team_code == team)
  stats <- setNames(
    lapply(TREND_STAT_COLS, function(col) {
      if (nrow(row) == 0) {
        return(list(label = trend_labels[[col]], value = NULL, rank = NULL,
                    rankDisplay = NULL, displayValue = NULL))
      }
      display <- if (col == "record") {
        paste0(row$recent_wins[1], "-", row$recent_losses[1], "-", row$recent_ot_losses[1])
      } else {
        NULL
      }
      stat_entry(row[1, ], col, trend_labels[[col]], trend_digits[[col]], display)
    }),
    TREND_STAT_COLS
  )
  list(stats = stats)
}

below_replacement_labels <- c(toi = "TOI", plus_minus = "+/-")
below_replacement_digits <- list(toi = 0, plus_minus = 0)
below_replacement_team_labels <- c(below_replacement_toi_pct = "BR TOI%")
below_replacement_team_digits <- list(below_replacement_toi_pct = 1)
injury_labels <- c(injured_count = "Injured", injury_toi_share = "TOI Lost")
injury_digits <- list(injured_count = 0, injury_toi_share = 1)

below_replacement_team_players <- function(team) {
  team_df <- below_replacement %>%
    filter(team_code == team) %>%
    arrange(plus_minus, desc(toi)) %>%
    head(TOP_N)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) {
    build_player(team_df[i, ], c("toi", "plus_minus"),
                 below_replacement_labels, below_replacement_digits)
  })
}

build_injury_player <- function(row) {
  impact_val <- if (!is.na(row$impact)) round(as.numeric(row$impact), 1) else NULL
  player_id <- if (!is.na(row$athlete_id)) {
    as.character(row$athlete_id)
  } else if (!is.na(row$entry_id)) {
    as.character(row$entry_id)
  } else {
    as.character(row$athlete_name)
  }
  list(
    playerId = player_id,
    name = as.character(row$athlete_name),
    position = if (!is.na(row$position) && nzchar(row$position)) as.character(row$position) else NULL,
    status = if (!is.na(row$status) && nzchar(row$status)) as.character(row$status) else NULL,
    stats = list(
      impact = list(label = "Impact", value = impact_val, rank = NULL, rankDisplay = NULL),
      aggregate = list(label = "Composite", value = NULL, rank = NULL, rankDisplay = NULL)
    )
  )
}

team_injured_players <- function(team) {
  team_df <- injury_players %>%
    filter(team_code == team) %>%
    arrange(desc(impact), athlete_name)
  if (nrow(team_df) == 0) return(list())
  lapply(seq_len(nrow(team_df)), function(i) build_injury_player(team_df[i, ]))
}

# ============================================================================
# Build per-team report cards
# ============================================================================
CATEGORY_ORDER <- c("recentTrend", POSITION_GROUP_KEYS, "belowReplacement", "injuries")

build_position_category <- function(spec, team) {
  team_cols <- spec_team_stat_cols(spec)
  list(
    label = spec$label,
    description = spec$description,
    statKeys = as.list(team_cols),
    playerStatKeys = as.list(spec$stat_cols),
    compositeRankingKey = paste0(spec$key, "Composite"),
    team = build_team_category_stats(
      team_pools[[spec$key]], team, team_cols,
      spec_team_labels(spec), spec_team_digits(spec)
    ),
    players = top_team_players(
      player_pools[[spec$key]], team, spec$stat_cols, spec$labels, spec$digits
    )
  )
}

teams_json <- lapply(ALL_TEAMS, function(team) {
  categories <- setNames(
    lapply(CATEGORY_SPECS, function(spec) build_position_category(spec, team)),
    POSITION_GROUP_KEYS
  )

  categories$recentTrend <- list(
    label = paste(LAST_TEN_COUNT, "Game Trend"),
    description = paste0(
      "The team's last ", LAST_TEN_COUNT, " games: record (wins-losses-OT ",
      "losses, ranked on points percentage), goal differential per game, and ",
      "goals scored and allowed per game. Ranked against every other team's ",
      "last ", LAST_TEN_COUNT, "."
    ),
    statKeys = as.list(TREND_STAT_COLS),
    playerStatKeys = list(),
    showPlayerRankAndComposite = FALSE,
    showTeamComposite = FALSE,
    team = build_trend_category_stats(team),
    players = list()
  )

  categories$belowReplacement <- list(
    label = "Below-replacement performers",
    description = paste0(
      "Regulars the team was outscored with: a negative plus-minus, worst ",
      "first, among skaters clearing the same bar as the position groups (",
      MIN_SKATER_GAMES, "+ games at ", MIN_SKATER_TOI_PER_GAME,
      "+ minutes a game). BR TOI% is the share of the team's skater ice time ",
      "those players absorbed. Rank 1 = smallest share."
    ),
    statKeys = list("below_replacement_toi_pct"),
    playerStatKeys = list("toi", "plus_minus"),
    showPlayerRankAndComposite = FALSE,
    # WAR is MLB-only; the shared layout would otherwise render an empty column.
    showWarColumn = FALSE,
    showTeamComposite = FALSE,
    team = build_team_category_stats(
      team_below_replacement, team, c("below_replacement_toi_pct"),
      below_replacement_team_labels, below_replacement_team_digits
    ),
    players = below_replacement_team_players(team)
  )

  categories$injuries <- list(
    label = "Injury Report",
    description = paste0(
      "Current ESPN injury designations weighted by severity (injured reserve ",
      "= 100%, out = 90%, doubtful = 75%, questionable = 40%, day-to-day = ",
      "30%) and by the player's share of the team's ice time. Higher composite ",
      "= more injury impact. Rank 1 = most injured."
    ),
    statKeys = list("injured_count", "injury_toi_share"),
    playerStatKeys = list("impact"),
    showPlayerRankAndComposite = FALSE,
    showStatusColumn = TRUE,
    compositeRankingKey = "injuriesComposite",
    team = build_team_category_stats(
      team_injuries, team, c("injured_count", "injury_toi_share"),
      injury_labels, injury_digits
    ),
    players = team_injured_players(team)
  )

  record_row <- team_records %>% filter(team_code == team)
  overall_row <- team_overall %>% filter(team_code == team)
  playoff_prob <- if (!is.null(playoff_probs) && !is.null(playoff_probs[[team]])) {
    playoff_probs[[team]]$playoffProb
  } else {
    NULL
  }

  list(
    teamCode = team,
    teamName = unname(team_names[team]),
    division = unname(team_divisions[team]),
    league = unname(team_conferences[team]),
    wins = if (nrow(record_row) > 0) as.integer(record_row$W[1]) else NULL,
    losses = if (nrow(record_row) > 0) as.integer(record_row$L[1]) else NULL,
    # The NHL has no ties; an overtime loss is the third number in a record.
    ties = if (nrow(record_row) > 0) as.integer(record_row$OTL[1]) else NULL,
    recordRank = if (nrow(record_row) > 0 && !is.na(record_row$point_pct_rank[1])) as.integer(record_row$point_pct_rank[1]) else NULL,
    recordRankDisplay = if (nrow(record_row) > 0 && !is.na(record_row$point_pct_rankDisplay[1])) record_row$point_pct_rankDisplay[1] else NULL,
    divisionRank = if (nrow(record_row) > 0) as.integer(record_row$division_rank[1]) else NULL,
    divisionRankDisplay = if (nrow(record_row) > 0) record_row$division_rank_display[1] else NULL,
    overallComposite = if (nrow(overall_row) > 0) round(overall_row$overall_composite[1], 1) else NULL,
    overallCompositeRank = if (nrow(overall_row) > 0) as.integer(overall_row$overall_composite_rank[1]) else NULL,
    overallCompositeRankDisplay = if (nrow(overall_row) > 0) overall_row$overall_composite_rankDisplay[1] else NULL,
    playoffProb = playoff_prob,
    gameLog = build_game_log_for_team(team),
    categoryOrder = as.list(CATEGORY_ORDER),
    categories = categories
  )
})

names(teams_json) <- ALL_TEAMS

# ============================================================================
# League-wide ranking sheets
# ============================================================================
composite_rankings <- setNames(
  lapply(POSITION_GROUP_KEYS, function(key) {
    build_rankings(team_pools[[key]], "team_code", "composite_score",
                   "composite_score_rank", "composite_score_rankDisplay")
  }),
  paste0(POSITION_GROUP_KEYS, "Composite")
)

team_stat_rankings <- do.call(c, lapply(CATEGORY_SPECS, function(spec) {
  build_category_stat_rankings(spec$key, team_pools[[spec$key]], spec_team_stat_cols(spec))
}))

player_stat_rankings <- do.call(c, lapply(CATEGORY_SPECS, function(spec) {
  build_player_pool_rankings(spec$key, player_pools[[spec$key]], spec$stat_cols)
}))

rankings_json <- c(
  list(
    record = build_rankings(team_records, "team_code", "point_pct",
                            "point_pct_rank", "point_pct_rankDisplay"),
    overallComposite = build_rankings(
      team_overall, "team_code", "overall_composite",
      "overall_composite_rank", "overall_composite_rankDisplay"
    ),
    injuriesComposite = build_rankings(
      team_injuries, "team_code", "composite_score",
      "composite_score_rank", "composite_score_rankDisplay"
    ),
    belowReplacementComposite = build_rankings(
      team_below_replacement, "team_code", "composite_score",
      "composite_score_rank", "composite_score_rankDisplay"
    )
  ),
  composite_rankings,
  team_stat_rankings,
  build_category_stat_rankings("recentTrend", team_recent_trend, TREND_STAT_COLS),
  build_category_stat_rankings("injuries", team_injuries, c("injured_count", "injury_toi_share")),
  build_category_stat_rankings("belowReplacement", team_below_replacement, c("below_replacement_toi_pct")),
  player_stat_rankings,
  build_player_pool_rankings("belowReplacement", below_replacement, c("toi", "plus_minus"))
)

# ============================================================================
# Output JSON
# ============================================================================
phase_tag_label <- switch(
  season_phase,
  PRE = "preseason",
  EARLY = "early season",
  REG = "regular season",
  POST = "postseason",
  "regular season"
)

phase_note <- switch(
  season_phase,
  PRE = paste0(
    "The ", started_season %||% (season_start_year + 1),
    "-", substr((started_season %||% (season_start_year + 1)) + 1, 3, 4),
    " season has not opened yet, so this card covers the completed ",
    season_label, " season."
  ),
  EARLY = paste0(
    "The new season has opened but no team has played the ",
    MIN_TEAM_GAMES_FOR_CURRENT_SEASON,
    " games a league-wide ranking needs, so this card still covers the ",
    "completed ", season_label, " season."
  ),
  REG = paste0("The ", season_label, " regular season is in progress."),
  POST = paste0("The ", season_label, " postseason has begun."),
  ""
)

output_data <- list(
  sport = "NHL",
  visualizationType = "NHL_TEAM_REPORT_CARD",
  title = paste0("NHL Team Report Cards - ", season_label),
  subtitle = "Each team's top players graded by position group.",
  description = paste0(
    "Per-team player report cards from the NHL's own stats API. Each team ",
    "surfaces its top ", TOP_N, " players in four groups — forwards, ",
    "defensemen, goaltenders and the power play — ranked by a composite of ",
    "their stats, with NHL-wide rank badges and a team row for the whole ",
    "group.\n\n",
    phase_note, "\n\n",
    "STATS:\n\n",
    " • Composite: Average percentile across a group's stats (100 = best in ",
    "the league on every one). Team composites rank the same stats aggregated ",
    "over the whole group; the overall composite averages the four group ",
    "composites. Higher is better.\n\n",
    " • P/60: Points per 60 minutes of ice time, so a second-liner and a ",
    "first-liner are measured the same way. Higher is better.\n\n",
    " • S%: Shooting percentage — goals per shot on goal. Higher is better.\n\n",
    " • CF%: Corsi for percentage — the share of all shot attempts that belong ",
    "to the player's team while they are on the ice, which is the standard ",
    "public proxy for puck possession. Above 50 means the team out-attempts ",
    "its opponent with that player out there. Higher is better.\n\n",
    " • +/-: Goals for minus goals against at even strength while the player ",
    "is on the ice. Higher is better.\n\n",
    " • BLK/60 and HIT/60: Blocked shots and hits per 60 minutes, the two ",
    "defensive events the public feeds count per player. Higher is better.\n\n",
    " • SV% / GAA / QS% / SO: Save percentage, goals against average (lower is ",
    "better), the share of starts that were quality starts, and shutouts. ",
    "SV is total saves, which rewards the goalie carrying the workload.\n\n",
    " • Special teams: The team row is PP% and PK% conversion, power play goals ",
    "scored (PPGF), power play goals allowed (PPGA, fewer is better) and ",
    "shorthanded goals scored (SHGF). The player table is power play points, ",
    "goals and assists, which is the only special-teams production the public ",
    "feeds break out per player.\n\n",
    " • ", LAST_TEN_COUNT, " Game Trend: The team's last ", LAST_TEN_COUNT,
    " games only, ranked against every other team's last ", LAST_TEN_COUNT,
    ". A form read, so it stays out of the overall composite.\n\n",
    " • Below-replacement performers: Regulars the team was outscored with — a ",
    "negative plus-minus among skaters clearing the same bar as the position ",
    "groups. BR TOI% is the share of the team's skater ice time they ",
    "absorbed — rank 1 = smallest.\n\n",
    " • Injury Report: ESPN injury designations weighted by severity and by the ",
    "player's share of the team's ice time. Rank 1 is the most injured team.\n\n",
    " • Last ", LAST_TEN_COUNT, " Games: The most recent ", LAST_TEN_COUNT,
    " results in order. Each row shows the opponent (vs = home, @ = away), ",
    "both scores, and the goal differential. A record reads wins-losses-OT ",
    "losses, the NHL way.\n\n",
    "Player and team stats cover the regular season so rates stay comparable ",
    "across the league."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "NHL Stats API • PlayoffStatus.com • ESPN injuries",
  season = season_start_year,
  seasonLabel = season_label,
  seasonPhase = season_phase,
  seasonPhaseLabel = season_phase_label,
  topN = TOP_N,
  categoryOrder = as.list(CATEGORY_ORDER),
  rankings = rankings_json,
  playoffChances = playoff_chances,
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "player", layout = "left", color = "#2196F3"),
    list(label = phase_tag_label, layout = "right", color = "#9C27B0")
  ),
  sortOrder = 1,
  teams = teams_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

# ============================================================================
# Sanity sample
# ============================================================================
sample_team <- if ("PIT" %in% ALL_TEAMS) "PIT" else ALL_TEAMS[1]
sample <- output_data$teams[[sample_team]]
cat("\nSample —", sample_team, "record:", sample$wins, "-", sample$losses, "-", sample$ties,
    "| overall composite:", sample$overallComposite,
    "rank:", sample$overallCompositeRank, "\n")
for (key in CATEGORY_ORDER) {
  cat_data <- sample$categories[[key]]
  top <- if (length(cat_data$players) > 0) cat_data$players[[1]]$name else "(none)"
  cat("  ", cat_data$label, "— players:", length(cat_data$players),
      "| top:", top,
      "| team composite:", cat_data$team$stats$aggregate$value %||% NA, "\n")
}
cat("  Game log:", length(sample$gameLog$games), "games | record:",
    sample$gameLog$record$display, "| total diff:", sample$gameLog$totalDifferential, "\n")
cat("Teams with player data:", sum(vapply(teams_json, function(t) {
  any(vapply(t$categories, function(c) length(c$players) > 0, logical(1)))
}, logical(1))), "/", length(teams_json), "\n")
cat("Ranking sheets:", length(rankings_json), "\n")

if (nzchar(Sys.getenv("FASTBREAK_LOCAL_JSON"))) {
  file.copy(tmp_file, Sys.getenv("FASTBREAK_LOCAL_JSON"), overwrite = TRUE)
  cat("Wrote local JSON copy:", Sys.getenv("FASTBREAK_LOCAL_JSON"), "\n")
}

# ============================================================================
# Upload
# ============================================================================
s3_bucket <- Sys.getenv("AWS_S3_BUCKET")
if (!nzchar(s3_bucket)) stop("AWS_S3_BUCKET environment variable is not set")

env <- toupper(Sys.getenv("ENV", "DEV"))
s3_key <- if (env == "PROD") {
  "prod/nhl__team_report_card.json"
} else {
  "dev/nhl__team_report_card.json"
}

s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
if (system(cmd) != 0) stop("Failed to upload to S3")
cat("\nUploaded to S3:", s3_path, "\n")

dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")

dynamodb_item <- sprintf(
  '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
  s3_key, utc_timestamp, output_data$title
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

cat("\n=== NHL Team Report Card generation complete ===\n")
