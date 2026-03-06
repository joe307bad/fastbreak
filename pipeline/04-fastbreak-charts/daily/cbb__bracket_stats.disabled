#!/usr/bin/env Rscript

library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)
library(readr)

# ============================================================================
# Constants
# ============================================================================
TOURNAMENT_START <- as.Date("2026-03-17")
TOURNAMENT_END <- as.Date("2026-04-06")

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

cat("=== NCAA Bracket Stats Generation ===\n")
cat("Date:", format(Sys.Date(), "%Y-%m-%d"), "\n")

# ============================================================================
# STEP 1: Load data from all sports-ref CSVs
# ============================================================================
cat("\n1. Loading data from sports-ref CSVs...\n")

script_dir <- getwd()
data_dir <- file.path(script_dir, "data/cbb/2026")

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

# ============================================================================
# STEP 2: Merge all data sources and calculate ranks
# ============================================================================
cat("\n2. Merging all data sources and calculating ranks...\n")

combined_data <- ratings_data %>%
  left_join(school_stats_data, by = "School") %>%
  left_join(opp_stats_data, by = "School") %>%
  left_join(adv_opp_data, by = "School") %>%
  left_join(adv_stats_data, by = "School")

cat("Combined data has", nrow(combined_data), "teams\n")

# Calculate ranks for all stats
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

team_stats_lookup <- split(combined_data, seq_len(nrow(combined_data)))
names(team_stats_lookup) <- tolower(combined_data$School)

cat("Calculated ranks for all stats\n")

# ============================================================================
# STEP 3: Determine tournament status and build bracket
# ============================================================================
cat("\n3. Determining tournament status...\n")

today <- Sys.Date()
is_pre_tournament <- today < TOURNAMENT_START
is_tournament_active <- today >= TOURNAMENT_START && today <= TOURNAMENT_END
is_tournament_complete <- today > TOURNAMENT_END

if (is_pre_tournament) {
  tournament_status <- "PROJECTED"
  cat("Tournament has not started. Building projected bracket from top 64 teams.\n")
} else if (is_tournament_active) {
  tournament_status <- "IN_PROGRESS"
  cat("Tournament is active. Fetching live bracket data.\n")
} else {
  tournament_status <- "COMPLETED"
  cat("Tournament is complete.\n")
}

# ============================================================================
# Helper Functions
# ============================================================================

build_team_stats <- function(team_key, team_name, team_abbrev, team_logo, seed) {
  if (!is.null(team_key) && team_key %in% names(team_stats_lookup)) {
    td <- team_stats_lookup[[team_key]]
    if (!is.null(td) && nrow(td) > 0) {
      return(list(
        seed = as.integer(seed),
        name = td$School,
        abbreviation = team_abbrev,
        logo = team_logo,
        score = NULL,
        isWinner = FALSE,
        conference = td$Conf,
        apRank = if (!is.na(td$AP_Rank)) as.integer(td$AP_Rank) else NULL,
        srsRank = as.integer(td$Rk),
        wins = as.integer(td$W),
        losses = as.integer(td$L),
        teamStats = list(
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
          pace = list(value = round(td$Pace, 1), rank = as.integer(td$Pace_rank), rankDisplay = td$Pace_rankDisplay),
          effectiveFGPct = list(value = round(td$eFG_Pct * 100, 1), rank = as.integer(td$eFG_Pct_rank), rankDisplay = td$eFG_Pct_rankDisplay),
          trueShooting = list(value = round(td$TS_Pct * 100, 1), rank = as.integer(td$TS_Pct_rank), rankDisplay = td$TS_Pct_rankDisplay),
          turnoverPct = list(value = round(td$TOV_Pct * 100, 1), rank = as.integer(td$TOV_Pct_rank), rankDisplay = td$TOV_Pct_rankDisplay),
          offRebPct = list(value = round(td$ORB_Pct * 100, 1), rank = as.integer(td$ORB_Pct_rank), rankDisplay = td$ORB_Pct_rankDisplay),
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
          srs = list(value = round(td$SRS, 2), rank = as.integer(td$SRS_rank), rankDisplay = td$SRS_rankDisplay),
          netRating = list(value = round(td$NRtg, 2), rank = as.integer(td$NRtg_rank), rankDisplay = td$NRtg_rankDisplay),
          strengthOfSchedule = list(value = round(td$SOS, 2), rank = as.integer(td$SOS_rank), rankDisplay = td$SOS_rankDisplay)
        )
      ))
    }
  }
  return(list(
    seed = as.integer(seed),
    name = team_name,
    abbreviation = team_abbrev,
    logo = team_logo,
    score = NULL,
    isWinner = FALSE,
    conference = NULL,
    apRank = NULL,
    srsRank = NULL,
    wins = NULL,
    losses = NULL,
    teamStats = NULL
  ))
}

calc_advantage <- function(off_rank, def_rank) {
  if (is.na(off_rank) || is.na(def_rank)) return(0)
  if (off_rank < def_rank) return(-1)  # Offense has advantage
  if (off_rank > def_rank) return(1)   # Defense has advantage
  return(0)  # Even
}

build_comparisons <- function(team1_data, team2_data) {
  if (is.null(team1_data$teamStats) || is.null(team2_data$teamStats)) {
    return(NULL)
  }

  t1 <- team1_data
  t2 <- team2_data
  t1s <- team1_data$teamStats
  t2s <- team2_data$teamStats

  # Side by side comparisons
  off_comparison <- list(
    pointsPerGame = list(statKey = "pointsPerGame", label = "PPG", team1 = t1s$pointsPerGame, team2 = t2s$pointsPerGame),
    fieldGoalPct = list(statKey = "fieldGoalPct", label = "FG%", team1 = t1s$fieldGoalPct, team2 = t2s$fieldGoalPct),
    threePointPct = list(statKey = "threePointPct", label = "3P%", team1 = t1s$threePointPct, team2 = t2s$threePointPct),
    effectiveFGPct = list(statKey = "effectiveFGPct", label = "eFG%", team1 = t1s$effectiveFGPct, team2 = t2s$effectiveFGPct),
    trueShooting = list(statKey = "trueShooting", label = "TS%", team1 = t1s$trueShooting, team2 = t2s$trueShooting),
    assistsPerGame = list(statKey = "assistsPerGame", label = "APG", team1 = t1s$assistsPerGame, team2 = t2s$assistsPerGame),
    turnoversPerGame = list(statKey = "turnoversPerGame", label = "TO/G", team1 = t1s$turnoversPerGame, team2 = t2s$turnoversPerGame),
    offReboundsPerGame = list(statKey = "offReboundsPerGame", label = "ORPG", team1 = t1s$offReboundsPerGame, team2 = t2s$offReboundsPerGame),
    pace = list(statKey = "pace", label = "Pace", team1 = t1s$pace, team2 = t2s$pace)
  )

  def_comparison <- list(
    oppPointsPerGame = list(statKey = "oppPointsPerGame", label = "Opp PPG", team1 = t1s$oppPointsPerGame, team2 = t2s$oppPointsPerGame),
    oppFieldGoalPct = list(statKey = "oppFieldGoalPct", label = "Opp FG%", team1 = t1s$oppFieldGoalPct, team2 = t2s$oppFieldGoalPct),
    oppThreePointPct = list(statKey = "oppThreePointPct", label = "Opp 3P%", team1 = t1s$oppThreePointPct, team2 = t2s$oppThreePointPct),
    oppEffectiveFGPct = list(statKey = "oppEffectiveFGPct", label = "Opp eFG%", team1 = t1s$oppEffectiveFGPct, team2 = t2s$oppEffectiveFGPct),
    oppTrueShooting = list(statKey = "oppTrueShooting", label = "Opp TS%", team1 = t1s$oppTrueShooting, team2 = t2s$oppTrueShooting),
    stealsPerGame = list(statKey = "stealsPerGame", label = "SPG", team1 = t1s$stealsPerGame, team2 = t2s$stealsPerGame),
    blocksPerGame = list(statKey = "blocksPerGame", label = "BPG", team1 = t1s$blocksPerGame, team2 = t2s$blocksPerGame),
    forcedTurnoverPct = list(statKey = "forcedTurnoverPct", label = "Forced TO%", team1 = t1s$forcedTurnoverPct, team2 = t2s$forcedTurnoverPct)
  )

  overall_comparison <- list(
    srs = list(statKey = "srs", label = "SRS", team1 = t1s$srs, team2 = t2s$srs),
    netRating = list(statKey = "netRating", label = "Net Rating", team1 = t1s$netRating, team2 = t2s$netRating),
    strengthOfSchedule = list(statKey = "strengthOfSchedule", label = "SOS", team1 = t1s$strengthOfSchedule, team2 = t2s$strengthOfSchedule),
    marginOfVictory = list(statKey = "marginOfVictory", label = "MOV", team1 = t1s$marginOfVictory, team2 = t2s$marginOfVictory),
    reboundsPerGame = list(statKey = "reboundsPerGame", label = "RPG", team1 = t1s$reboundsPerGame, team2 = t2s$reboundsPerGame)
  )

  # Team1 offense vs Team2 defense
  team1_off_vs_team2_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame", offLabel = "PPG", defLabel = "Opp PPG Allowed",
      offense = list(team = t1$abbreviation, value = t1s$pointsPerGame$value, rank = t1s$pointsPerGame$rank, rankDisplay = t1s$pointsPerGame$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$oppPointsPerGame$value, rank = t2s$oppPointsPerGame$rank, rankDisplay = t2s$oppPointsPerGame$rankDisplay),
      advantage = calc_advantage(t1s$pointsPerGame$rank, t2s$oppPointsPerGame$rank)
    ),
    rating = list(
      statKey = "rating", offLabel = "Offensive Rating", defLabel = "Defensive Rating",
      offense = list(team = t1$abbreviation, value = t1s$offensiveRating$value, rank = t1s$offensiveRating$rank, rankDisplay = t1s$offensiveRating$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$defensiveRating$value, rank = t2s$defensiveRating$rank, rankDisplay = t2s$defensiveRating$rankDisplay),
      advantage = calc_advantage(t1s$offensiveRating$rank, t2s$defensiveRating$rank)
    ),
    fieldGoalPct = list(
      statKey = "fieldGoalPct", offLabel = "FG%", defLabel = "Opp FG% Allowed",
      offense = list(team = t1$abbreviation, value = t1s$fieldGoalPct$value, rank = t1s$fieldGoalPct$rank, rankDisplay = t1s$fieldGoalPct$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$oppFieldGoalPct$value, rank = t2s$oppFieldGoalPct$rank, rankDisplay = t2s$oppFieldGoalPct$rankDisplay),
      advantage = calc_advantage(t1s$fieldGoalPct$rank, t2s$oppFieldGoalPct$rank)
    ),
    threePointPct = list(
      statKey = "threePointPct", offLabel = "3P%", defLabel = "Opp 3P% Allowed",
      offense = list(team = t1$abbreviation, value = t1s$threePointPct$value, rank = t1s$threePointPct$rank, rankDisplay = t1s$threePointPct$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$oppThreePointPct$value, rank = t2s$oppThreePointPct$rank, rankDisplay = t2s$oppThreePointPct$rankDisplay),
      advantage = calc_advantage(t1s$threePointPct$rank, t2s$oppThreePointPct$rank)
    ),
    effectiveFGPct = list(
      statKey = "effectiveFGPct", offLabel = "eFG%", defLabel = "Opp eFG% Allowed",
      offense = list(team = t1$abbreviation, value = t1s$effectiveFGPct$value, rank = t1s$effectiveFGPct$rank, rankDisplay = t1s$effectiveFGPct$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$oppEffectiveFGPct$value, rank = t2s$oppEffectiveFGPct$rank, rankDisplay = t2s$oppEffectiveFGPct$rankDisplay),
      advantage = calc_advantage(t1s$effectiveFGPct$rank, t2s$oppEffectiveFGPct$rank)
    ),
    trueShooting = list(
      statKey = "trueShooting", offLabel = "TS%", defLabel = "Opp TS% Allowed",
      offense = list(team = t1$abbreviation, value = t1s$trueShooting$value, rank = t1s$trueShooting$rank, rankDisplay = t1s$trueShooting$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$oppTrueShooting$value, rank = t2s$oppTrueShooting$rank, rankDisplay = t2s$oppTrueShooting$rankDisplay),
      advantage = calc_advantage(t1s$trueShooting$rank, t2s$oppTrueShooting$rank)
    ),
    turnoverPct = list(
      statKey = "turnoverPct", offLabel = "TO%", defLabel = "Forced TO%",
      offense = list(team = t1$abbreviation, value = t1s$turnoverPct$value, rank = t1s$turnoverPct$rank, rankDisplay = t1s$turnoverPct$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$forcedTurnoverPct$value, rank = t2s$forcedTurnoverPct$rank, rankDisplay = t2s$forcedTurnoverPct$rankDisplay),
      advantage = calc_advantage(t1s$turnoverPct$rank, t2s$forcedTurnoverPct$rank)
    ),
    offRebPct = list(
      statKey = "offRebPct", offLabel = "ORB%", defLabel = "Opp ORB% Allowed",
      offense = list(team = t1$abbreviation, value = t1s$offRebPct$value, rank = t1s$offRebPct$rank, rankDisplay = t1s$offRebPct$rankDisplay),
      defense = list(team = t2$abbreviation, value = t2s$oppOffRebPct$value, rank = t2s$oppOffRebPct$rank, rankDisplay = t2s$oppOffRebPct$rankDisplay),
      advantage = calc_advantage(t1s$offRebPct$rank, t2s$oppOffRebPct$rank)
    )
  )

  # Team2 offense vs Team1 defense
  team2_off_vs_team1_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame", offLabel = "PPG", defLabel = "Opp PPG Allowed",
      offense = list(team = t2$abbreviation, value = t2s$pointsPerGame$value, rank = t2s$pointsPerGame$rank, rankDisplay = t2s$pointsPerGame$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$oppPointsPerGame$value, rank = t1s$oppPointsPerGame$rank, rankDisplay = t1s$oppPointsPerGame$rankDisplay),
      advantage = calc_advantage(t2s$pointsPerGame$rank, t1s$oppPointsPerGame$rank)
    ),
    rating = list(
      statKey = "rating", offLabel = "Offensive Rating", defLabel = "Defensive Rating",
      offense = list(team = t2$abbreviation, value = t2s$offensiveRating$value, rank = t2s$offensiveRating$rank, rankDisplay = t2s$offensiveRating$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$defensiveRating$value, rank = t1s$defensiveRating$rank, rankDisplay = t1s$defensiveRating$rankDisplay),
      advantage = calc_advantage(t2s$offensiveRating$rank, t1s$defensiveRating$rank)
    ),
    fieldGoalPct = list(
      statKey = "fieldGoalPct", offLabel = "FG%", defLabel = "Opp FG% Allowed",
      offense = list(team = t2$abbreviation, value = t2s$fieldGoalPct$value, rank = t2s$fieldGoalPct$rank, rankDisplay = t2s$fieldGoalPct$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$oppFieldGoalPct$value, rank = t1s$oppFieldGoalPct$rank, rankDisplay = t1s$oppFieldGoalPct$rankDisplay),
      advantage = calc_advantage(t2s$fieldGoalPct$rank, t1s$oppFieldGoalPct$rank)
    ),
    threePointPct = list(
      statKey = "threePointPct", offLabel = "3P%", defLabel = "Opp 3P% Allowed",
      offense = list(team = t2$abbreviation, value = t2s$threePointPct$value, rank = t2s$threePointPct$rank, rankDisplay = t2s$threePointPct$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$oppThreePointPct$value, rank = t1s$oppThreePointPct$rank, rankDisplay = t1s$oppThreePointPct$rankDisplay),
      advantage = calc_advantage(t2s$threePointPct$rank, t1s$oppThreePointPct$rank)
    ),
    effectiveFGPct = list(
      statKey = "effectiveFGPct", offLabel = "eFG%", defLabel = "Opp eFG% Allowed",
      offense = list(team = t2$abbreviation, value = t2s$effectiveFGPct$value, rank = t2s$effectiveFGPct$rank, rankDisplay = t2s$effectiveFGPct$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$oppEffectiveFGPct$value, rank = t1s$oppEffectiveFGPct$rank, rankDisplay = t1s$oppEffectiveFGPct$rankDisplay),
      advantage = calc_advantage(t2s$effectiveFGPct$rank, t1s$oppEffectiveFGPct$rank)
    ),
    trueShooting = list(
      statKey = "trueShooting", offLabel = "TS%", defLabel = "Opp TS% Allowed",
      offense = list(team = t2$abbreviation, value = t2s$trueShooting$value, rank = t2s$trueShooting$rank, rankDisplay = t2s$trueShooting$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$oppTrueShooting$value, rank = t1s$oppTrueShooting$rank, rankDisplay = t1s$oppTrueShooting$rankDisplay),
      advantage = calc_advantage(t2s$trueShooting$rank, t1s$oppTrueShooting$rank)
    ),
    turnoverPct = list(
      statKey = "turnoverPct", offLabel = "TO%", defLabel = "Forced TO%",
      offense = list(team = t2$abbreviation, value = t2s$turnoverPct$value, rank = t2s$turnoverPct$rank, rankDisplay = t2s$turnoverPct$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$forcedTurnoverPct$value, rank = t1s$forcedTurnoverPct$rank, rankDisplay = t1s$forcedTurnoverPct$rankDisplay),
      advantage = calc_advantage(t2s$turnoverPct$rank, t1s$forcedTurnoverPct$rank)
    ),
    offRebPct = list(
      statKey = "offRebPct", offLabel = "ORB%", defLabel = "Opp ORB% Allowed",
      offense = list(team = t2$abbreviation, value = t2s$offRebPct$value, rank = t2s$offRebPct$rank, rankDisplay = t2s$offRebPct$rankDisplay),
      defense = list(team = t1$abbreviation, value = t1s$oppOffRebPct$value, rank = t1s$oppOffRebPct$rank, rankDisplay = t1s$oppOffRebPct$rankDisplay),
      advantage = calc_advantage(t2s$offRebPct$rank, t1s$oppOffRebPct$rank)
    )
  )

  return(list(
    sideBySide = list(offense = off_comparison, defense = def_comparison, overall = overall_comparison),
    team1OffVsTeam2Def = team1_off_vs_team2_def,
    team2OffVsTeam1Def = team2_off_vs_team1_def
  ))
}

# ============================================================================
# STEP 4: Build projected bracket (pre-tournament) or fetch live data
# ============================================================================

if (is_pre_tournament) {
  cat("\n4. Building projected bracket from top 64 teams by SRS...\n")

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

      team1_data <- build_team_stats(team1_key, team1_row$School, team1_abbrev, NULL, seed1)
      team2_data <- build_team_stats(team2_key, team2_row$School, team2_abbrev, NULL, seed2)
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
  # Tournament is active or complete - fetch from ESPN
  cat("\n4. Fetching tournament bracket from ESPN...\n")

  scoreboard_url <- "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/scoreboard?groups=100"
  cat("Fetching from:", scoreboard_url, "\n")

  scoreboard_resp <- tryCatch({
    GET(scoreboard_url)
  }, error = function(e) {
    cat("Error fetching scoreboard:", e$message, "\n")
    NULL
  })

  if (!is.null(scoreboard_resp) && status_code(scoreboard_resp) == 200) {
    scoreboard_data <- content(scoreboard_resp, as = "parsed")

    # Initialize regions
    regions_data <- list()
    for (region_name in names(REGION_COLORS)) {
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

    # Process each event
    if (!is.null(scoreboard_data$events)) {
      for (event in scoreboard_data$events) {
        if (length(event$competitions) == 0) next
        competition <- event$competitions[[1]]

        teams <- competition$competitors
        if (length(teams) != 2) next

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
        team1_seed <- if (!is.null(team1$curatedRank$current)) team1$curatedRank$current else 0
        team2_seed <- if (!is.null(team2$curatedRank$current)) team2$curatedRank$current else 0

        team1_key <- tolower(team1_name)
        team2_key <- tolower(team2_name)

        team1_data <- build_team_stats(team1_key, team1_name, team1_abbrev, team1_logo, team1_seed)
        team2_data <- build_team_stats(team2_key, team2_name, team2_abbrev, team2_logo, team2_seed)

        # Add scores for completed/in-progress games
        if (game_status %in% c("FINAL", "IN_PROGRESS")) {
          team1_data$score <- as.integer(team1$score)
          team2_data$score <- as.integer(team2$score)
          if (game_status == "FINAL") {
            team1_data$isWinner <- team1$winner
            team2_data$isWinner <- team2$winner
          }
        }

        comparisons <- build_comparisons(team1_data, team2_data)

        # Build box score for completed games
        box_score <- NULL
        if (game_status == "FINAL") {
          # Fetch detailed game summary for box score
          summary_url <- paste0("https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/summary?event=", event$id)
          Sys.sleep(0.5)  # Rate limiting
          summary_resp <- tryCatch({
            GET(summary_url)
          }, error = function(e) NULL)

          if (!is.null(summary_resp) && status_code(summary_resp) == 200) {
            summary_data <- content(summary_resp, as = "parsed")
            if (!is.null(summary_data$boxscore) && !is.null(summary_data$boxscore$teams)) {
              bs_teams <- summary_data$boxscore$teams
              if (length(bs_teams) >= 2) {
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
                box_score <- list(
                  team1 = parse_team_box(bs_teams[[1]]),
                  team2 = parse_team_box(bs_teams[[2]])
                )
              }
            }
          }
        }

        # Build game object
        game_date <- event$date
        if (grepl("T\\d{2}:\\d{2}Z$", game_date)) game_date <- sub("Z$", ":00Z", game_date)

        game <- list(
          gameId = event$id,
          gameNumber = as.integer(gsub("[^0-9]", "", event$id)) %% 100,
          gameDate = game_date,
          gameStatus = game_status,
          team1 = team1_data,
          team2 = team2_data,
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

        # TODO: Determine region and round from event metadata
        # For now, add to East region Round of 64 as placeholder
        regions_data[["East"]]$rounds[[1]]$games[[length(regions_data[["East"]]$rounds[[1]]$games) + 1]] <- game
      }
    }

    cat("Processed", length(scoreboard_data$events), "tournament games\n")
  } else {
    cat("Failed to fetch tournament data. Using empty bracket.\n")
    regions_data <- list()
    for (region_name in names(REGION_COLORS)) {
      regions_data[[region_name]] <- list(
        name = region_name,
        colorHex = REGION_COLORS[[region_name]],
        rounds = list()
      )
    }
  }

  # Final Four placeholder
  final_four <- list(
    semifinal1 = NULL,
    semifinal2 = NULL,
    championship = NULL
  )

  title <- "2026 NCAA Tournament Bracket"
  if (is_tournament_complete) {
    subtitle <- "Tournament Complete"
  } else {
    subtitle <- format(today, "%B %d, %Y")
  }
}

# ============================================================================
# STEP 5: Generate output JSON
# ============================================================================
cat("\n5. Generating output JSON...\n")

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
# STEP 6: Upload to S3 and update DynamoDB
# ============================================================================
cat("\n6. Uploading to S3...\n")

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
