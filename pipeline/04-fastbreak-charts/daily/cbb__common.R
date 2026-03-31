#!/usr/bin/env Rscript
# Common functions shared between cbb__bracket_stats.R and cbb__matchup_stats.R

library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)
library(readr)

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

# ============================================================================
# Data Loading Functions
# ============================================================================

#' Load and process all CBB data from sports-ref CSVs
#' @param data_dir Directory containing the CSV files
#' @return List with combined_data and team_stats_lookup
load_cbb_data <- function(data_dir) {
  cat("Loading data from sports-ref CSVs...\n")

  # --- RATINGS (AP rankings, efficiency ratings) ---
  ratings_path <- file.path(data_dir, "sports-ref-ratings.csv")
  ratings_raw <- read_csv(ratings_path, skip = 2, col_names = FALSE, show_col_types = FALSE)
  colnames(ratings_raw) <- c("Rk", "School", "Conf", "X4", "AP_Rank", "W", "L", "Pts", "Opp",
                              "MOV", "X11", "SOS", "X13", "OSRS", "DSRS", "SRS",
                              "ORtg", "DRtg", "NRtg")

  ratings_data <- ratings_raw %>%
    filter(!is.na(Rk) & Rk != "") %>%
    mutate(
      Rk = as.numeric(Rk),
      AP_Rank = as.numeric(AP_Rank),
      W = as.numeric(W),
      L = as.numeric(L),
      MOV = as.numeric(MOV),
      SOS = as.numeric(SOS),
      OSRS = as.numeric(OSRS),
      DSRS = as.numeric(DSRS),
      SRS = as.numeric(SRS),
      ORtg = as.numeric(ORtg),
      DRtg = as.numeric(DRtg),
      NRtg = as.numeric(NRtg)
    ) %>%
    select(Rk, School, Conf, AP_Rank, W, L, MOV, SOS, OSRS, DSRS, SRS, ORtg, DRtg, NRtg)

  cat("Loaded", nrow(ratings_data), "teams from ratings\n")

  # --- SCHOOL STATS (team's own stats - offense) ---
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
      FG = as.numeric(FG),
      FGA = as.numeric(FGA),
      FG_Pct = as.numeric(FG_Pct),
      ThreeP = as.numeric(ThreeP),
      ThreePA = as.numeric(ThreePA),
      ThreeP_Pct = as.numeric(ThreeP_Pct),
      FT = as.numeric(FT),
      FTA = as.numeric(FTA),
      FT_Pct = as.numeric(FT_Pct),
      ORB = as.numeric(ORB),
      TRB = as.numeric(TRB),
      AST = as.numeric(AST),
      STL = as.numeric(STL),
      BLK = as.numeric(BLK),
      TOV = as.numeric(TOV),
      PF = as.numeric(PF),
      PPG = round(Pts_Tm / G, 1),
      OPP_PPG = round(Pts_Opp / G, 1),
      RPG = round(TRB / G, 1),
      ORPG = round(ORB / G, 1),
      APG = round(AST / G, 1),
      SPG = round(STL / G, 1),
      BPG = round(BLK / G, 1),
      TPG = round(TOV / G, 1),
      FG_Pct = round(FG_Pct * 100, 1),
      ThreeP_Pct = round(ThreeP_Pct * 100, 1),
      FT_Pct = round(FT_Pct * 100, 1)
    ) %>%
    select(School, G, PPG, OPP_PPG, RPG, ORPG, APG, SPG, BPG, TPG, FG_Pct, ThreeP_Pct, FT_Pct)

  cat("Loaded", nrow(school_stats_data), "teams from school stats\n")

  # --- SCHOOL STATS OPP (opponent stats - what they allow/defense) ---
  opp_stats_path <- file.path(data_dir, "sports-ref-school-stats-opp.csv")
  opp_stats_raw <- read_csv(opp_stats_path, skip = 2, col_names = FALSE, show_col_types = FALSE)
  colnames(opp_stats_raw) <- c("Rk", "School", "G", "W", "L", "WL_Pct", "SRS", "SOS", "X9",
                                "Conf_W", "Conf_L", "X12", "Home_W", "Home_L", "X15",
                                "Away_W", "Away_L", "X18", "Pts_Tm", "Pts_Opp", "X21",
                                "Opp_MP", "Opp_FG", "Opp_FGA", "Opp_FG_Pct", "Opp_ThreeP", "Opp_ThreePA", "Opp_ThreeP_Pct",
                                "Opp_FT", "Opp_FTA", "Opp_FT_Pct", "Opp_ORB", "Opp_TRB", "Opp_AST", "Opp_STL", "Opp_BLK", "Opp_TOV", "Opp_PF")

  opp_stats_data <- opp_stats_raw %>%
    filter(!is.na(Rk) & Rk != "" & Rk != "Rk") %>%
    mutate(
      G = as.numeric(G),
      Opp_FG = as.numeric(Opp_FG),
      Opp_FGA = as.numeric(Opp_FGA),
      Opp_FG_Pct = as.numeric(Opp_FG_Pct),
      Opp_ThreeP = as.numeric(Opp_ThreeP),
      Opp_ThreePA = as.numeric(Opp_ThreePA),
      Opp_ThreeP_Pct = as.numeric(Opp_ThreeP_Pct),
      Opp_FT = as.numeric(Opp_FT),
      Opp_FTA = as.numeric(Opp_FTA),
      Opp_FT_Pct = as.numeric(Opp_FT_Pct),
      Opp_TRB = as.numeric(Opp_TRB),
      Opp_AST = as.numeric(Opp_AST),
      Opp_TOV = as.numeric(Opp_TOV),
      Opp_RPG = round(Opp_TRB / G, 1),
      Opp_APG = round(Opp_AST / G, 1),
      Opp_TPG = round(Opp_TOV / G, 1),
      Opp_FG_Pct = round(Opp_FG_Pct * 100, 1),
      Opp_ThreeP_Pct = round(Opp_ThreeP_Pct * 100, 1),
      Opp_FT_Pct = round(Opp_FT_Pct * 100, 1)
    ) %>%
    select(School, Opp_RPG, Opp_APG, Opp_TPG, Opp_FG_Pct, Opp_ThreeP_Pct, Opp_FT_Pct)

  cat("Loaded", nrow(opp_stats_data), "teams from opponent stats\n")

  # --- ADVANCED STATS OPP (opponent advanced stats) ---
  adv_opp_path <- file.path(data_dir, "sports-ref-school-advanced-stats-opp.csv")
  adv_opp_raw <- read_csv(adv_opp_path, skip = 2, col_names = FALSE, show_col_types = FALSE)
  colnames(adv_opp_raw) <- c("Rk", "School", "G", "W", "L", "WL_Pct", "SRS", "SOS", "X9",
                              "Conf_W", "Conf_L", "X12", "Home_W", "Home_L", "X15",
                              "Away_W", "Away_L", "X18", "Pts_Tm", "Pts_Opp", "X21",
                              "Opp_Pace", "Opp_ORtg", "Opp_FTr", "Opp_ThreePAr", "Opp_TS_Pct",
                              "Opp_TRB_Pct", "Opp_AST_Pct", "Opp_STL_Pct", "Opp_BLK_Pct",
                              "Opp_eFG_Pct", "Opp_TOV_Pct", "Opp_ORB_Pct", "Opp_FT_FGA")

  adv_opp_data <- adv_opp_raw %>%
    filter(!is.na(Rk) & Rk != "" & Rk != "Rk") %>%
    mutate(
      Opp_Pace = as.numeric(Opp_Pace),
      Opp_ORtg = as.numeric(Opp_ORtg),
      Opp_eFG_Pct = as.numeric(Opp_eFG_Pct),
      Opp_TS_Pct = as.numeric(Opp_TS_Pct),
      Opp_TOV_Pct = as.numeric(Opp_TOV_Pct),
      Opp_ORB_Pct = as.numeric(Opp_ORB_Pct),
      Opp_FTr = as.numeric(Opp_FTr),
      Opp_ThreePAr = as.numeric(Opp_ThreePAr)
    ) %>%
    select(School, Opp_Pace, Opp_ORtg, Opp_eFG_Pct, Opp_TS_Pct, Opp_TOV_Pct, Opp_ORB_Pct, Opp_FTr, Opp_ThreePAr)

  cat("Loaded", nrow(adv_opp_data), "teams from opponent advanced stats\n")

  # --- ADVANCED STATS (team's own advanced stats) ---
  adv_stats_path <- file.path(data_dir, "sports-ref-school-advanced-stats.csv")
  adv_stats_raw <- read_csv(adv_stats_path, skip = 2, col_names = FALSE, show_col_types = FALSE)
  colnames(adv_stats_raw) <- c("Rk", "School", "G", "W", "L", "WL_Pct", "SRS", "SOS", "X9",
                                "Conf_W", "Conf_L", "X12", "Home_W", "Home_L", "X15",
                                "Away_W", "Away_L", "X18", "Pts_Tm", "Pts_Opp", "X21",
                                "Pace", "Adv_ORtg", "FTr", "ThreePAr", "TS_Pct",
                                "TRB_Pct", "AST_Pct", "STL_Pct", "BLK_Pct",
                                "eFG_Pct", "TOV_Pct", "ORB_Pct", "FT_FGA")

  adv_stats_data <- adv_stats_raw %>%
    filter(!is.na(Rk) & Rk != "" & Rk != "Rk") %>%
    mutate(
      Pace = as.numeric(Pace),
      eFG_Pct = as.numeric(eFG_Pct),
      TS_Pct = as.numeric(TS_Pct),
      TOV_Pct = as.numeric(TOV_Pct),
      ORB_Pct = as.numeric(ORB_Pct),
      FTr = as.numeric(FTr),
      ThreePAr = as.numeric(ThreePAr)
    ) %>%
    select(School, Pace, eFG_Pct, TS_Pct, TOV_Pct, ORB_Pct, FTr, ThreePAr)

  cat("Loaded", nrow(adv_stats_data), "teams from advanced stats\n")

  # Merge all data
  combined_data <- ratings_data %>%
    left_join(school_stats_data, by = "School") %>%
    left_join(opp_stats_data, by = "School") %>%
    left_join(adv_opp_data, by = "School") %>%
    left_join(adv_stats_data, by = "School")

  cat("Combined data has", nrow(combined_data), "teams\n")

  # Calculate ranks
  ppg_ranks <- tied_rank(-combined_data$PPG)
  mov_ranks <- tied_rank(-combined_data$MOV)
  sos_ranks <- tied_rank(-combined_data$SOS)
  osrs_ranks <- tied_rank(-combined_data$OSRS)
  srs_ranks <- tied_rank(-combined_data$SRS)
  ortg_ranks <- tied_rank(-combined_data$ORtg)
  nrtg_ranks <- tied_rank(-combined_data$NRtg)
  rpg_ranks <- tied_rank(-combined_data$RPG)
  orpg_ranks <- tied_rank(-combined_data$ORPG)
  apg_ranks <- tied_rank(-combined_data$APG)
  spg_ranks <- tied_rank(-combined_data$SPG)
  bpg_ranks <- tied_rank(-combined_data$BPG)
  fg_pct_ranks <- tied_rank(-combined_data$FG_Pct)
  threep_pct_ranks <- tied_rank(-combined_data$ThreeP_Pct)
  ft_pct_ranks <- tied_rank(-combined_data$FT_Pct)

  opp_ppg_ranks <- tied_rank(combined_data$OPP_PPG)
  dsrs_ranks <- tied_rank(-combined_data$DSRS)
  drtg_ranks <- tied_rank(combined_data$DRtg)
  opp_fg_pct_ranks <- tied_rank(combined_data$Opp_FG_Pct)
  opp_threep_pct_ranks <- tied_rank(combined_data$Opp_ThreeP_Pct)
  opp_efg_pct_ranks <- tied_rank(combined_data$Opp_eFG_Pct)
  opp_ts_pct_ranks <- tied_rank(combined_data$Opp_TS_Pct)
  opp_rpg_ranks <- tied_rank(combined_data$Opp_RPG)
  opp_apg_ranks <- tied_rank(combined_data$Opp_APG)

  tpg_ranks <- tied_rank(combined_data$TPG)
  opp_tov_pct_ranks <- tied_rank(-combined_data$Opp_TOV_Pct)
  opp_orb_pct_ranks <- tied_rank(combined_data$Opp_ORB_Pct)

  pace_ranks <- tied_rank(-combined_data$Pace)
  efg_pct_ranks <- tied_rank(-combined_data$eFG_Pct)
  ts_pct_ranks <- tied_rank(-combined_data$TS_Pct)
  orb_pct_ranks <- tied_rank(-combined_data$ORB_Pct)
  tov_pct_ranks <- tied_rank(combined_data$TOV_Pct)

  combined_data <- combined_data %>%
    mutate(
      PPG_rank = ppg_ranks$rank, PPG_rankDisplay = ppg_ranks$rankDisplay,
      MOV_rank = mov_ranks$rank, MOV_rankDisplay = mov_ranks$rankDisplay,
      SOS_rank = sos_ranks$rank, SOS_rankDisplay = sos_ranks$rankDisplay,
      OSRS_rank = osrs_ranks$rank, OSRS_rankDisplay = osrs_ranks$rankDisplay,
      SRS_rank = srs_ranks$rank, SRS_rankDisplay = srs_ranks$rankDisplay,
      ORtg_rank = ortg_ranks$rank, ORtg_rankDisplay = ortg_ranks$rankDisplay,
      NRtg_rank = nrtg_ranks$rank, NRtg_rankDisplay = nrtg_ranks$rankDisplay,
      RPG_rank = rpg_ranks$rank, RPG_rankDisplay = rpg_ranks$rankDisplay,
      ORPG_rank = orpg_ranks$rank, ORPG_rankDisplay = orpg_ranks$rankDisplay,
      APG_rank = apg_ranks$rank, APG_rankDisplay = apg_ranks$rankDisplay,
      SPG_rank = spg_ranks$rank, SPG_rankDisplay = spg_ranks$rankDisplay,
      BPG_rank = bpg_ranks$rank, BPG_rankDisplay = bpg_ranks$rankDisplay,
      TPG_rank = tpg_ranks$rank, TPG_rankDisplay = tpg_ranks$rankDisplay,
      FG_Pct_rank = fg_pct_ranks$rank, FG_Pct_rankDisplay = fg_pct_ranks$rankDisplay,
      ThreeP_Pct_rank = threep_pct_ranks$rank, ThreeP_Pct_rankDisplay = threep_pct_ranks$rankDisplay,
      FT_Pct_rank = ft_pct_ranks$rank, FT_Pct_rankDisplay = ft_pct_ranks$rankDisplay,
      Pace_rank = pace_ranks$rank, Pace_rankDisplay = pace_ranks$rankDisplay,
      eFG_Pct_rank = efg_pct_ranks$rank, eFG_Pct_rankDisplay = efg_pct_ranks$rankDisplay,
      TS_Pct_rank = ts_pct_ranks$rank, TS_Pct_rankDisplay = ts_pct_ranks$rankDisplay,
      ORB_Pct_rank = orb_pct_ranks$rank, ORB_Pct_rankDisplay = orb_pct_ranks$rankDisplay,
      TOV_Pct_rank = tov_pct_ranks$rank, TOV_Pct_rankDisplay = tov_pct_ranks$rankDisplay,
      OPP_PPG_rank = opp_ppg_ranks$rank, OPP_PPG_rankDisplay = opp_ppg_ranks$rankDisplay,
      DSRS_rank = dsrs_ranks$rank, DSRS_rankDisplay = dsrs_ranks$rankDisplay,
      DRtg_rank = drtg_ranks$rank, DRtg_rankDisplay = drtg_ranks$rankDisplay,
      Opp_FG_Pct_rank = opp_fg_pct_ranks$rank, Opp_FG_Pct_rankDisplay = opp_fg_pct_ranks$rankDisplay,
      Opp_ThreeP_Pct_rank = opp_threep_pct_ranks$rank, Opp_ThreeP_Pct_rankDisplay = opp_threep_pct_ranks$rankDisplay,
      Opp_eFG_Pct_rank = opp_efg_pct_ranks$rank, Opp_eFG_Pct_rankDisplay = opp_efg_pct_ranks$rankDisplay,
      Opp_TS_Pct_rank = opp_ts_pct_ranks$rank, Opp_TS_Pct_rankDisplay = opp_ts_pct_ranks$rankDisplay,
      Opp_RPG_rank = opp_rpg_ranks$rank, Opp_RPG_rankDisplay = opp_rpg_ranks$rankDisplay,
      Opp_APG_rank = opp_apg_ranks$rank, Opp_APG_rankDisplay = opp_apg_ranks$rankDisplay,
      Opp_TOV_Pct_rank = opp_tov_pct_ranks$rank, Opp_TOV_Pct_rankDisplay = opp_tov_pct_ranks$rankDisplay,
      Opp_ORB_Pct_rank = opp_orb_pct_ranks$rank, Opp_ORB_Pct_rankDisplay = opp_orb_pct_ranks$rankDisplay
    )

  # Create lookup
  team_stats_lookup <- split(combined_data, seq_len(nrow(combined_data)))
  names(team_stats_lookup) <- tolower(combined_data$School)

  cat("Calculated ranks for all stats\n")

  return(list(
    combined_data = combined_data,
    team_stats_lookup = team_stats_lookup,
    ratings_data = ratings_data
  ))
}

# ============================================================================
# Team Name Matching Functions
# ============================================================================

#' Find matching team key in lookup from ESPN display name
#' ESPN uses names like "Duke Blue Devils", Sports Reference uses "Duke"
#' @param espn_name ESPN team display name
#' @param team_stats_lookup Named list of team statistics
#' @return Matching key or NULL
find_team_key <- function(espn_name, team_stats_lookup) {
  if (is.null(espn_name)) return(NULL)

  espn_lower <- tolower(espn_name)
  lookup_names <- names(team_stats_lookup)

  # Direct match
  if (espn_lower %in% lookup_names) return(espn_lower)

  # Common ESPN to Sports-Ref mappings
  name_mappings <- list(
    "uconn huskies" = "connecticut",
    "lsu tigers" = "louisiana state",
    "smu mustangs" = "southern methodist",
    "vcu rams" = "virginia commonwealth",
    "byu cougars" = "brigham young",
    "ucf knights" = "ucf",
    "unc tar heels" = "north carolina",
    "ole miss rebels" = "mississippi",
    "pitt panthers" = "pittsburgh",
    "usc trojans" = "southern california",
    "umass minutemen" = "massachusetts",
    "unlv rebels" = "nevada-las vegas",
    "utep miners" = "texas-el paso",
    "tcu horned frogs" = "texas christian",
    "st. john's red storm" = "st. john's (ny)",
    "miami hurricanes" = "miami (fl)",
    "hawai'i rainbow warriors" = "hawaii",
    "queens university royals" = "queens (nc)"
  )

  if (espn_lower %in% names(name_mappings)) {
    mapped <- name_mappings[[espn_lower]]
    if (mapped %in% lookup_names) return(mapped)
  }

  # Try first word of ESPN name (e.g., "Duke Blue Devils" -> "duke")
  first_word <- strsplit(espn_lower, " ")[[1]][1]
  if (first_word %in% lookup_names) return(first_word)

  # Try matching ESPN name that starts with a lookup name
  for (lookup_name in lookup_names) {
    if (startsWith(espn_lower, paste0(lookup_name, " "))) {
      return(lookup_name)
    }
  }

  # Try matching lookup name that's contained in ESPN name
  for (lookup_name in lookup_names) {
    if (grepl(paste0("\\b", lookup_name, "\\b"), espn_lower)) {
      return(lookup_name)
    }
  }

  # Try matching after stripping parenthetical suffixes from lookup names
  # Sports-Ref uses "Miami (FL)", "St. John's (NY)", etc.
  for (lookup_name in lookup_names) {
    base_name <- trimws(gsub("\\s*\\([^)]+\\)$", "", lookup_name))
    if (base_name != lookup_name && nchar(base_name) >= 4) {
      if (startsWith(espn_lower, paste0(base_name, " ")) || espn_lower == base_name) {
        return(lookup_name)
      }
    }
  }

  # Try matching with normalized apostrophes/special characters
  espn_normalized <- gsub("\u2019|\u2018|'", "'", espn_lower)
  espn_normalized <- gsub("[^a-z0-9 ]", "", espn_normalized)
  for (lookup_name in lookup_names) {
    lookup_normalized <- gsub("[^a-z0-9 ]", "", lookup_name)
    if (startsWith(espn_normalized, paste0(lookup_normalized, " ")) || espn_normalized == lookup_normalized) {
      return(lookup_name)
    }
  }

  return(NULL)
}

# ============================================================================
# Team Stats Building Functions
# ============================================================================

#' Build team stats object from lookup
#' @param team_key Lowercase team name for lookup
#' @param team_name Display name
#' @param team_abbrev Abbreviation
#' @param team_logo Logo URL
#' @param seed Tournament seed (optional, for bracket)
#' @param team_stats_lookup Named list of team statistics
#' @return Team data object with stats
build_team_stats <- function(team_key, team_name, team_abbrev, team_logo, seed = NULL, team_stats_lookup) {
  if (!is.null(team_key) && team_key %in% names(team_stats_lookup)) {
    td <- team_stats_lookup[[team_key]]
    if (!is.null(td) && nrow(td) > 0) {
      result <- list(
        name = td$School,
        abbreviation = team_abbrev,
        logo = team_logo,
        conference = td$Conf,
        apRank = if (!is.na(td$AP_Rank)) as.integer(td$AP_Rank) else NULL,
        srsRank = as.integer(td$Rk),
        wins = as.integer(td$W),
        losses = as.integer(td$L),
        teamStats = list(
          # Offensive stats
          pointsPerGame = list(value = round(td$PPG, 1), rank = as.integer(td$PPG_rank), rankDisplay = td$PPG_rankDisplay),
          marginOfVictory = list(value = round(td$MOV, 2), rank = as.integer(td$MOV_rank), rankDisplay = td$MOV_rankDisplay),
          offensiveRating = list(value = round(td$ORtg, 2), rank = as.integer(td$ORtg_rank), rankDisplay = td$ORtg_rankDisplay),
          offensiveSRS = list(value = round(td$OSRS, 2), rank = as.integer(td$OSRS_rank), rankDisplay = td$OSRS_rankDisplay),
          fieldGoalPct = list(value = round(td$FG_Pct, 1), rank = as.integer(td$FG_Pct_rank), rankDisplay = td$FG_Pct_rankDisplay),
          threePointPct = list(value = round(td$ThreeP_Pct, 1), rank = as.integer(td$ThreeP_Pct_rank), rankDisplay = td$ThreeP_Pct_rankDisplay),
          freeThrowPct = list(value = round(td$FT_Pct, 1), rank = as.integer(td$FT_Pct_rank), rankDisplay = td$FT_Pct_rankDisplay),
          reboundsPerGame = list(value = round(td$RPG, 1), rank = as.integer(td$RPG_rank), rankDisplay = td$RPG_rankDisplay),
          offReboundsPerGame = list(value = round(td$ORPG, 1), rank = as.integer(td$ORPG_rank), rankDisplay = td$ORPG_rankDisplay),
          assistsPerGame = list(value = round(td$APG, 1), rank = as.integer(td$APG_rank), rankDisplay = td$APG_rankDisplay),
          turnoversPerGame = list(value = round(td$TPG, 1), rank = as.integer(td$TPG_rank), rankDisplay = td$TPG_rankDisplay),

          # Team advanced stats
          pace = list(value = round(td$Pace, 1), rank = as.integer(td$Pace_rank), rankDisplay = td$Pace_rankDisplay),
          effectiveFGPct = list(value = round(td$eFG_Pct * 100, 1), rank = as.integer(td$eFG_Pct_rank), rankDisplay = td$eFG_Pct_rankDisplay),
          trueShooting = list(value = round(td$TS_Pct * 100, 1), rank = as.integer(td$TS_Pct_rank), rankDisplay = td$TS_Pct_rankDisplay),
          turnoverPct = list(value = round(td$TOV_Pct * 100, 1), rank = as.integer(td$TOV_Pct_rank), rankDisplay = td$TOV_Pct_rankDisplay),
          offRebPct = list(value = round(td$ORB_Pct * 100, 1), rank = as.integer(td$ORB_Pct_rank), rankDisplay = td$ORB_Pct_rankDisplay),

          # Defensive stats (what they allow)
          oppPointsPerGame = list(value = round(td$OPP_PPG, 1), rank = as.integer(td$OPP_PPG_rank), rankDisplay = td$OPP_PPG_rankDisplay),
          defensiveRating = list(value = round(td$DRtg, 2), rank = as.integer(td$DRtg_rank), rankDisplay = td$DRtg_rankDisplay),
          defensiveSRS = list(value = round(td$DSRS, 2), rank = as.integer(td$DSRS_rank), rankDisplay = td$DSRS_rankDisplay),
          oppFieldGoalPct = list(value = round(td$Opp_FG_Pct, 1), rank = as.integer(td$Opp_FG_Pct_rank), rankDisplay = td$Opp_FG_Pct_rankDisplay),
          oppThreePointPct = list(value = round(td$Opp_ThreeP_Pct, 1), rank = as.integer(td$Opp_ThreeP_Pct_rank), rankDisplay = td$Opp_ThreeP_Pct_rankDisplay),
          oppEffectiveFGPct = list(value = round(td$Opp_eFG_Pct * 100, 1), rank = as.integer(td$Opp_eFG_Pct_rank), rankDisplay = td$Opp_eFG_Pct_rankDisplay),
          oppTrueShooting = list(value = round(td$Opp_TS_Pct * 100, 1), rank = as.integer(td$Opp_TS_Pct_rank), rankDisplay = td$Opp_TS_Pct_rankDisplay),
          oppReboundsPerGame = list(value = round(td$Opp_RPG, 1), rank = as.integer(td$Opp_RPG_rank), rankDisplay = td$Opp_RPG_rankDisplay),
          forcedTurnoverPct = list(value = round(td$Opp_TOV_Pct * 100, 1), rank = as.integer(td$Opp_TOV_Pct_rank), rankDisplay = td$Opp_TOV_Pct_rankDisplay),
          oppOffRebPct = list(value = round(td$Opp_ORB_Pct * 100, 1), rank = as.integer(td$Opp_ORB_Pct_rank), rankDisplay = td$Opp_ORB_Pct_rankDisplay),
          stealsPerGame = list(value = round(td$SPG, 1), rank = as.integer(td$SPG_rank), rankDisplay = td$SPG_rankDisplay),
          blocksPerGame = list(value = round(td$BPG, 1), rank = as.integer(td$BPG_rank), rankDisplay = td$BPG_rankDisplay),

          # Overall
          srs = list(value = round(td$SRS, 2), rank = as.integer(td$SRS_rank), rankDisplay = td$SRS_rankDisplay),
          netRating = list(value = round(td$NRtg, 2), rank = as.integer(td$NRtg_rank), rankDisplay = td$NRtg_rankDisplay),
          strengthOfSchedule = list(value = round(td$SOS, 2), rank = as.integer(td$SOS_rank), rankDisplay = td$SOS_rankDisplay)
        )
      )

      # Add seed if provided (for bracket)
      if (!is.null(seed)) {
        result$seed <- as.integer(seed)
        result$score <- NULL
        result$isWinner <- FALSE
      }

      return(result)
    }
  }

  # Return minimal object if team not found
  result <- list(
    name = team_name,
    abbreviation = team_abbrev,
    logo = team_logo,
    conference = NULL,
    apRank = NULL,
    srsRank = NULL,
    wins = NULL,
    losses = NULL,
    teamStats = NULL
  )

  if (!is.null(seed)) {
    result$seed <- as.integer(seed)
    result$score <- NULL
    result$isWinner <- FALSE
  }

  return(result)
}

# ============================================================================
# Comparison Building Functions
# ============================================================================

get_stat_or_null <- function(stats, stat_name) {
  if (is.null(stats) || is.null(stats[[stat_name]])) return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  return(stats[[stat_name]])
}

calc_advantage <- function(off_rank, def_rank) {
  if (is.null(off_rank) || is.null(def_rank) || is.na(off_rank) || is.na(def_rank)) return(0)
  if (off_rank < def_rank) return(-1)  # Offense has advantage
  if (off_rank > def_rank) return(1)   # Defense has advantage
  return(0)  # Even
}

#' Build matchup comparisons between two teams
#' @param team1_data First team data (with teamStats field)
#' @param team2_data Second team data (with teamStats field)
#' @return Comparisons object with sideBySide, homeOffVsAwayDef, awayOffVsHomeDef
build_comparisons <- function(team1_data, team2_data) {
  t1_stats <- team1_data$teamStats
  t2_stats <- team2_data$teamStats

  if (is.null(t1_stats) || is.null(t2_stats)) {
    return(NULL)
  }

  # Side by side offensive comparison
  off_comparison <- list(
    pointsPerGame = list(label = "Points/Game", home = get_stat_or_null(t1_stats, "pointsPerGame"), away = get_stat_or_null(t2_stats, "pointsPerGame")),
    offensiveRating = list(label = "Offensive Rating", home = get_stat_or_null(t1_stats, "offensiveRating"), away = get_stat_or_null(t2_stats, "offensiveRating")),
    offensiveSRS = list(label = "Offensive SRS", home = get_stat_or_null(t1_stats, "offensiveSRS"), away = get_stat_or_null(t2_stats, "offensiveSRS")),
    fieldGoalPct = list(label = "FG%", home = get_stat_or_null(t1_stats, "fieldGoalPct"), away = get_stat_or_null(t2_stats, "fieldGoalPct")),
    effectiveFGPct = list(label = "eFG%", home = get_stat_or_null(t1_stats, "effectiveFGPct"), away = get_stat_or_null(t2_stats, "effectiveFGPct")),
    trueShooting = list(label = "TS%", home = get_stat_or_null(t1_stats, "trueShooting"), away = get_stat_or_null(t2_stats, "trueShooting")),
    threePointPct = list(label = "3P%", home = get_stat_or_null(t1_stats, "threePointPct"), away = get_stat_or_null(t2_stats, "threePointPct")),
    freeThrowPct = list(label = "FT%", home = get_stat_or_null(t1_stats, "freeThrowPct"), away = get_stat_or_null(t2_stats, "freeThrowPct")),
    assistsPerGame = list(label = "Assists/Game", home = get_stat_or_null(t1_stats, "assistsPerGame"), away = get_stat_or_null(t2_stats, "assistsPerGame")),
    turnoversPerGame = list(label = "Turnovers/Game", home = get_stat_or_null(t1_stats, "turnoversPerGame"), away = get_stat_or_null(t2_stats, "turnoversPerGame")),
    turnoverPct = list(label = "TO%", home = get_stat_or_null(t1_stats, "turnoverPct"), away = get_stat_or_null(t2_stats, "turnoverPct")),
    offReboundsPerGame = list(label = "Off Reb/Game", home = get_stat_or_null(t1_stats, "offReboundsPerGame"), away = get_stat_or_null(t2_stats, "offReboundsPerGame")),
    offRebPct = list(label = "ORB%", home = get_stat_or_null(t1_stats, "offRebPct"), away = get_stat_or_null(t2_stats, "offRebPct")),
    pace = list(label = "Pace", home = get_stat_or_null(t1_stats, "pace"), away = get_stat_or_null(t2_stats, "pace"))
  )

  # Side by side defensive comparison
  def_comparison <- list(
    oppPointsPerGame = list(label = "Opp Points/Game", home = get_stat_or_null(t1_stats, "oppPointsPerGame"), away = get_stat_or_null(t2_stats, "oppPointsPerGame")),
    defensiveRating = list(label = "Defensive Rating", home = get_stat_or_null(t1_stats, "defensiveRating"), away = get_stat_or_null(t2_stats, "defensiveRating")),
    defensiveSRS = list(label = "Defensive SRS", home = get_stat_or_null(t1_stats, "defensiveSRS"), away = get_stat_or_null(t2_stats, "defensiveSRS")),
    oppFieldGoalPct = list(label = "Opp FG%", home = get_stat_or_null(t1_stats, "oppFieldGoalPct"), away = get_stat_or_null(t2_stats, "oppFieldGoalPct")),
    oppThreePointPct = list(label = "Opp 3P%", home = get_stat_or_null(t1_stats, "oppThreePointPct"), away = get_stat_or_null(t2_stats, "oppThreePointPct")),
    oppEffectiveFGPct = list(label = "Opp eFG%", home = get_stat_or_null(t1_stats, "oppEffectiveFGPct"), away = get_stat_or_null(t2_stats, "oppEffectiveFGPct")),
    oppTrueShooting = list(label = "Opp TS%", home = get_stat_or_null(t1_stats, "oppTrueShooting"), away = get_stat_or_null(t2_stats, "oppTrueShooting")),
    stealsPerGame = list(label = "Steals/Game", home = get_stat_or_null(t1_stats, "stealsPerGame"), away = get_stat_or_null(t2_stats, "stealsPerGame")),
    blocksPerGame = list(label = "Blocks/Game", home = get_stat_or_null(t1_stats, "blocksPerGame"), away = get_stat_or_null(t2_stats, "blocksPerGame")),
    forcedTurnoverPct = list(label = "Forced TO%", home = get_stat_or_null(t1_stats, "forcedTurnoverPct"), away = get_stat_or_null(t2_stats, "forcedTurnoverPct"))
  )

  # Side by side overall comparison
  overall_comparison <- list(
    srs = list(label = "Simple Rating System", home = get_stat_or_null(t1_stats, "srs"), away = get_stat_or_null(t2_stats, "srs")),
    netRating = list(label = "Net Rating", home = get_stat_or_null(t1_stats, "netRating"), away = get_stat_or_null(t2_stats, "netRating")),
    strengthOfSchedule = list(label = "Strength of Schedule", home = get_stat_or_null(t1_stats, "strengthOfSchedule"), away = get_stat_or_null(t2_stats, "strengthOfSchedule")),
    marginOfVictory = list(label = "Margin of Victory", home = get_stat_or_null(t1_stats, "marginOfVictory"), away = get_stat_or_null(t2_stats, "marginOfVictory")),
    reboundsPerGame = list(label = "Rebounds/Game", home = get_stat_or_null(t1_stats, "reboundsPerGame"), away = get_stat_or_null(t2_stats, "reboundsPerGame"))
  )

  # Team1 Offense vs Team2 Defense
  t1_ppg <- get_stat_or_null(t1_stats, "pointsPerGame")
  t1_ortg <- get_stat_or_null(t1_stats, "offensiveRating")
  t1_fg_pct <- get_stat_or_null(t1_stats, "fieldGoalPct")
  t1_3p_pct <- get_stat_or_null(t1_stats, "threePointPct")
  t1_efg_pct <- get_stat_or_null(t1_stats, "effectiveFGPct")
  t1_ts_pct <- get_stat_or_null(t1_stats, "trueShooting")
  t1_tov_pct <- get_stat_or_null(t1_stats, "turnoverPct")
  t1_orb_pct <- get_stat_or_null(t1_stats, "offRebPct")

  t2_opp_ppg <- get_stat_or_null(t2_stats, "oppPointsPerGame")
  t2_drtg <- get_stat_or_null(t2_stats, "defensiveRating")
  t2_opp_fg_pct <- get_stat_or_null(t2_stats, "oppFieldGoalPct")
  t2_opp_3p_pct <- get_stat_or_null(t2_stats, "oppThreePointPct")
  t2_opp_efg_pct <- get_stat_or_null(t2_stats, "oppEffectiveFGPct")
  t2_opp_ts_pct <- get_stat_or_null(t2_stats, "oppTrueShooting")
  t2_forced_tov_pct <- get_stat_or_null(t2_stats, "forcedTurnoverPct")
  t2_opp_orb_pct <- get_stat_or_null(t2_stats, "oppOffRebPct")

  # Team2 Offense vs Team1 Defense
  t2_ppg <- get_stat_or_null(t2_stats, "pointsPerGame")
  t2_ortg <- get_stat_or_null(t2_stats, "offensiveRating")
  t2_fg_pct <- get_stat_or_null(t2_stats, "fieldGoalPct")
  t2_3p_pct <- get_stat_or_null(t2_stats, "threePointPct")
  t2_efg_pct <- get_stat_or_null(t2_stats, "effectiveFGPct")
  t2_ts_pct <- get_stat_or_null(t2_stats, "trueShooting")
  t2_tov_pct <- get_stat_or_null(t2_stats, "turnoverPct")
  t2_orb_pct <- get_stat_or_null(t2_stats, "offRebPct")

  t1_opp_ppg <- get_stat_or_null(t1_stats, "oppPointsPerGame")
  t1_drtg <- get_stat_or_null(t1_stats, "defensiveRating")
  t1_opp_fg_pct <- get_stat_or_null(t1_stats, "oppFieldGoalPct")
  t1_opp_3p_pct <- get_stat_or_null(t1_stats, "oppThreePointPct")
  t1_opp_efg_pct <- get_stat_or_null(t1_stats, "oppEffectiveFGPct")
  t1_opp_ts_pct <- get_stat_or_null(t1_stats, "oppTrueShooting")
  t1_forced_tov_pct <- get_stat_or_null(t1_stats, "forcedTurnoverPct")
  t1_opp_orb_pct <- get_stat_or_null(t1_stats, "oppOffRebPct")

  home_off_vs_away_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame", offLabel = "Points/Game", defLabel = "Opp Points Allowed",
      offense = list(team = team1_data$abbreviation, value = t1_ppg$value, rank = t1_ppg$rank, rankDisplay = t1_ppg$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_opp_ppg$value, rank = t2_opp_ppg$rank, rankDisplay = t2_opp_ppg$rankDisplay),
      advantage = calc_advantage(t1_ppg$rank, t2_opp_ppg$rank)
    ),
    rating = list(
      statKey = "rating", offLabel = "Offensive Rating", defLabel = "Defensive Rating",
      offense = list(team = team1_data$abbreviation, value = t1_ortg$value, rank = t1_ortg$rank, rankDisplay = t1_ortg$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_drtg$value, rank = t2_drtg$rank, rankDisplay = t2_drtg$rankDisplay),
      advantage = calc_advantage(t1_ortg$rank, t2_drtg$rank)
    ),
    fieldGoalPct = list(
      statKey = "fieldGoalPct", offLabel = "FG%", defLabel = "Opp FG% Allowed",
      offense = list(team = team1_data$abbreviation, value = t1_fg_pct$value, rank = t1_fg_pct$rank, rankDisplay = t1_fg_pct$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_opp_fg_pct$value, rank = t2_opp_fg_pct$rank, rankDisplay = t2_opp_fg_pct$rankDisplay),
      advantage = calc_advantage(t1_fg_pct$rank, t2_opp_fg_pct$rank)
    ),
    threePointPct = list(
      statKey = "threePointPct", offLabel = "3P%", defLabel = "Opp 3P% Allowed",
      offense = list(team = team1_data$abbreviation, value = t1_3p_pct$value, rank = t1_3p_pct$rank, rankDisplay = t1_3p_pct$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_opp_3p_pct$value, rank = t2_opp_3p_pct$rank, rankDisplay = t2_opp_3p_pct$rankDisplay),
      advantage = calc_advantage(t1_3p_pct$rank, t2_opp_3p_pct$rank)
    ),
    effectiveFGPct = list(
      statKey = "effectiveFGPct", offLabel = "eFG%", defLabel = "Opp eFG% Allowed",
      offense = list(team = team1_data$abbreviation, value = t1_efg_pct$value, rank = t1_efg_pct$rank, rankDisplay = t1_efg_pct$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_opp_efg_pct$value, rank = t2_opp_efg_pct$rank, rankDisplay = t2_opp_efg_pct$rankDisplay),
      advantage = calc_advantage(t1_efg_pct$rank, t2_opp_efg_pct$rank)
    ),
    trueShooting = list(
      statKey = "trueShooting", offLabel = "TS%", defLabel = "Opp TS% Allowed",
      offense = list(team = team1_data$abbreviation, value = t1_ts_pct$value, rank = t1_ts_pct$rank, rankDisplay = t1_ts_pct$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_opp_ts_pct$value, rank = t2_opp_ts_pct$rank, rankDisplay = t2_opp_ts_pct$rankDisplay),
      advantage = calc_advantage(t1_ts_pct$rank, t2_opp_ts_pct$rank)
    ),
    turnoverPct = list(
      statKey = "turnoverPct", offLabel = "TO%", defLabel = "Forced TO%",
      offense = list(team = team1_data$abbreviation, value = t1_tov_pct$value, rank = t1_tov_pct$rank, rankDisplay = t1_tov_pct$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_forced_tov_pct$value, rank = t2_forced_tov_pct$rank, rankDisplay = t2_forced_tov_pct$rankDisplay),
      advantage = calc_advantage(t1_tov_pct$rank, t2_forced_tov_pct$rank)
    ),
    offRebPct = list(
      statKey = "offRebPct", offLabel = "ORB%", defLabel = "Opp ORB% Allowed",
      offense = list(team = team1_data$abbreviation, value = t1_orb_pct$value, rank = t1_orb_pct$rank, rankDisplay = t1_orb_pct$rankDisplay),
      defense = list(team = team2_data$abbreviation, value = t2_opp_orb_pct$value, rank = t2_opp_orb_pct$rank, rankDisplay = t2_opp_orb_pct$rankDisplay),
      advantage = calc_advantage(t1_orb_pct$rank, t2_opp_orb_pct$rank)
    )
  )

  away_off_vs_home_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame", offLabel = "Points/Game", defLabel = "Opp Points Allowed",
      offense = list(team = team2_data$abbreviation, value = t2_ppg$value, rank = t2_ppg$rank, rankDisplay = t2_ppg$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_opp_ppg$value, rank = t1_opp_ppg$rank, rankDisplay = t1_opp_ppg$rankDisplay),
      advantage = calc_advantage(t2_ppg$rank, t1_opp_ppg$rank)
    ),
    rating = list(
      statKey = "rating", offLabel = "Offensive Rating", defLabel = "Defensive Rating",
      offense = list(team = team2_data$abbreviation, value = t2_ortg$value, rank = t2_ortg$rank, rankDisplay = t2_ortg$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_drtg$value, rank = t1_drtg$rank, rankDisplay = t1_drtg$rankDisplay),
      advantage = calc_advantage(t2_ortg$rank, t1_drtg$rank)
    ),
    fieldGoalPct = list(
      statKey = "fieldGoalPct", offLabel = "FG%", defLabel = "Opp FG% Allowed",
      offense = list(team = team2_data$abbreviation, value = t2_fg_pct$value, rank = t2_fg_pct$rank, rankDisplay = t2_fg_pct$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_opp_fg_pct$value, rank = t1_opp_fg_pct$rank, rankDisplay = t1_opp_fg_pct$rankDisplay),
      advantage = calc_advantage(t2_fg_pct$rank, t1_opp_fg_pct$rank)
    ),
    threePointPct = list(
      statKey = "threePointPct", offLabel = "3P%", defLabel = "Opp 3P% Allowed",
      offense = list(team = team2_data$abbreviation, value = t2_3p_pct$value, rank = t2_3p_pct$rank, rankDisplay = t2_3p_pct$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_opp_3p_pct$value, rank = t1_opp_3p_pct$rank, rankDisplay = t1_opp_3p_pct$rankDisplay),
      advantage = calc_advantage(t2_3p_pct$rank, t1_opp_3p_pct$rank)
    ),
    effectiveFGPct = list(
      statKey = "effectiveFGPct", offLabel = "eFG%", defLabel = "Opp eFG% Allowed",
      offense = list(team = team2_data$abbreviation, value = t2_efg_pct$value, rank = t2_efg_pct$rank, rankDisplay = t2_efg_pct$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_opp_efg_pct$value, rank = t1_opp_efg_pct$rank, rankDisplay = t1_opp_efg_pct$rankDisplay),
      advantage = calc_advantage(t2_efg_pct$rank, t1_opp_efg_pct$rank)
    ),
    trueShooting = list(
      statKey = "trueShooting", offLabel = "TS%", defLabel = "Opp TS% Allowed",
      offense = list(team = team2_data$abbreviation, value = t2_ts_pct$value, rank = t2_ts_pct$rank, rankDisplay = t2_ts_pct$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_opp_ts_pct$value, rank = t1_opp_ts_pct$rank, rankDisplay = t1_opp_ts_pct$rankDisplay),
      advantage = calc_advantage(t2_ts_pct$rank, t1_opp_ts_pct$rank)
    ),
    turnoverPct = list(
      statKey = "turnoverPct", offLabel = "TO%", defLabel = "Forced TO%",
      offense = list(team = team2_data$abbreviation, value = t2_tov_pct$value, rank = t2_tov_pct$rank, rankDisplay = t2_tov_pct$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_forced_tov_pct$value, rank = t1_forced_tov_pct$rank, rankDisplay = t1_forced_tov_pct$rankDisplay),
      advantage = calc_advantage(t2_tov_pct$rank, t1_forced_tov_pct$rank)
    ),
    offRebPct = list(
      statKey = "offRebPct", offLabel = "ORB%", defLabel = "Opp ORB% Allowed",
      offense = list(team = team2_data$abbreviation, value = t2_orb_pct$value, rank = t2_orb_pct$rank, rankDisplay = t2_orb_pct$rankDisplay),
      defense = list(team = team1_data$abbreviation, value = t1_opp_orb_pct$value, rank = t1_opp_orb_pct$rank, rankDisplay = t1_opp_orb_pct$rankDisplay),
      advantage = calc_advantage(t2_orb_pct$rank, t1_opp_orb_pct$rank)
    )
  )

  return(list(
    sideBySide = list(offense = off_comparison, defense = def_comparison, overall = overall_comparison),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  ))
}

cat("CBB Common functions loaded\n")
