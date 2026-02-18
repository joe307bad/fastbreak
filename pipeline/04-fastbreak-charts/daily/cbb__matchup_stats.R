#!/usr/bin/env Rscript

library(dplyr)
library(tidyr)
library(jsonlite)
library(httr)
library(readr)

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

cat("=== Loading CBB data ===\n")

# ============================================================================
# STEP 1: Load data from all sports-ref CSVs
# ============================================================================
cat("\n1. Loading data from sports-ref CSVs...\n")

# Get the script directory to find the CSV
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
    # Calculate per-game stats
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
    # Calculate per-game defensive stats (what they ALLOW)
    Opp_RPG = round(Opp_TRB / G, 1),
    Opp_APG = round(Opp_AST / G, 1),
    Opp_TPG = round(Opp_TOV / G, 1),  # Turnovers forced
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
# STEP 2: Get top 25 AP ranked teams
# ============================================================================
cat("\n2. Getting top 25 AP ranked teams...\n")

top_25_teams <- ratings_data %>%
  filter(!is.na(AP_Rank)) %>%
  arrange(AP_Rank) %>%
  head(25)

cat("Found", nrow(top_25_teams), "AP ranked teams\n")
print(top_25_teams %>% select(AP_Rank, School, Conf))

# Create a list of team names for matching
top_team_names <- tolower(top_25_teams$School)

# ============================================================================
# STEP 3: Merge all data sources
# ============================================================================
cat("\n3. Merging all data sources...\n")

combined_data <- ratings_data %>%
  left_join(school_stats_data, by = "School") %>%
  left_join(opp_stats_data, by = "School") %>%
  left_join(adv_opp_data, by = "School") %>%
  left_join(adv_stats_data, by = "School")

cat("Combined data has", nrow(combined_data), "teams\n")

# ============================================================================
# STEP 4: Calculate ranks for all stats
# ============================================================================
cat("\n4. Calculating stat ranks...\n")

# Offensive stats - higher is better (negate for ranking)
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

# Defensive stats - lower allowed is better
opp_ppg_ranks <- tied_rank(combined_data$OPP_PPG)
dsrs_ranks <- tied_rank(-combined_data$DSRS)
drtg_ranks <- tied_rank(combined_data$DRtg)
opp_fg_pct_ranks <- tied_rank(combined_data$Opp_FG_Pct)
opp_threep_pct_ranks <- tied_rank(combined_data$Opp_ThreeP_Pct)
opp_efg_pct_ranks <- tied_rank(combined_data$Opp_eFG_Pct)
opp_ts_pct_ranks <- tied_rank(combined_data$Opp_TS_Pct)
opp_rpg_ranks <- tied_rank(combined_data$Opp_RPG)
opp_apg_ranks <- tied_rank(combined_data$Opp_APG)

# Lower turnovers is better for offense, higher forced turnovers is better for defense
tpg_ranks <- tied_rank(combined_data$TPG)
opp_tov_pct_ranks <- tied_rank(-combined_data$Opp_TOV_Pct)
opp_orb_pct_ranks <- tied_rank(combined_data$Opp_ORB_Pct)  # Lower opp ORB% is better defense

# Team advanced stats - higher is better for efficiency metrics
pace_ranks <- tied_rank(-combined_data$Pace)  # Higher pace = faster tempo
efg_pct_ranks <- tied_rank(-combined_data$eFG_Pct)
ts_pct_ranks <- tied_rank(-combined_data$TS_Pct)
orb_pct_ranks <- tied_rank(-combined_data$ORB_Pct)
tov_pct_ranks <- tied_rank(combined_data$TOV_Pct)  # Lower is better

combined_data <- combined_data %>%
  mutate(
    # Offensive ranks
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

    # Team advanced stats ranks
    Pace_rank = pace_ranks$rank, Pace_rankDisplay = pace_ranks$rankDisplay,
    eFG_Pct_rank = efg_pct_ranks$rank, eFG_Pct_rankDisplay = efg_pct_ranks$rankDisplay,
    TS_Pct_rank = ts_pct_ranks$rank, TS_Pct_rankDisplay = ts_pct_ranks$rankDisplay,
    ORB_Pct_rank = orb_pct_ranks$rank, ORB_Pct_rankDisplay = orb_pct_ranks$rankDisplay,
    TOV_Pct_rank = tov_pct_ranks$rank, TOV_Pct_rankDisplay = tov_pct_ranks$rankDisplay,

    # Defensive ranks
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

# Create lookup for team stats
team_stats_lookup <- split(combined_data, seq_len(nrow(combined_data)))
names(team_stats_lookup) <- tolower(combined_data$School)

cat("Calculated ranks for all stats\n")

# ============================================================================
# STEP 5: Fetch competitions from ESPN API
# ============================================================================
cat("\n5. Fetching CBB games from ESPN API...\n")

today <- Sys.Date()
end_date <- today + DAYS_AHEAD
start_date_str <- format(today, "%Y%m%d")
end_date_str <- format(end_date, "%Y%m%d")

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
  cat("Failed to fetch scoreboard data.\n")
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
# STEP 6: Filter competitions involving top 25 teams
# ============================================================================
cat("\n6. Filtering competitions involving top 25 teams...\n")

find_matching_top_team <- function(espn_location) {
  if (is.null(espn_location)) return(NULL)
  espn_lower <- tolower(espn_location)
  if (espn_lower %in% top_team_names) return(espn_lower)

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
    if (mapped %in% top_team_names) return(mapped)
  }
  return(NULL)
}

matchups <- list()

for (event in events) {
  if (is.null(event$competitions) || length(event$competitions) == 0) next

  competition <- event$competitions[[1]]
  teams <- competition$competitors
  if (length(teams) != 2) next

  home_team <- NULL
  away_team <- NULL
  for (team in teams) {
    if (team$homeAway == "home") home_team <- team
    else away_team <- team
  }
  if (is.null(home_team) || is.null(away_team)) next

  home_location <- home_team$team$location
  away_location <- away_team$team$location
  home_top_match <- find_matching_top_team(home_location)
  away_top_match <- find_matching_top_team(away_location)

  # Only include if BOTH teams are AP top 25 teams
  if (is.null(home_top_match) || is.null(away_top_match)) next

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
      stadium = stadium_name, city = city, state = state,
      fullLocation = if (length(location_parts) > 0) paste(location_parts, collapse = ", ") else NA
    )
  }

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

  game_date <- event$date
  if (grepl("T\\d{2}:\\d{2}Z$", game_date)) game_date <- sub("Z$", ":00Z", game_date)

  matchups[[length(matchups) + 1]] <- list(
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
}

cat("Found", length(matchups), "games with both teams in AP Top 25\n")

if (length(matchups) == 0) {
  cat("No games with both teams in AP Top 25 found. Exiting.\n")
  quit(status = 0)
}

# ============================================================================
# STEP 7: Build matchup comparison data
# ============================================================================
cat("\n7. Building matchup comparisons...\n")

build_team_stats <- function(team_key, team_name, team_abbrev, team_logo) {
  if (!is.null(team_key) && team_key %in% names(team_stats_lookup)) {
    td <- team_stats_lookup[[team_key]]
    if (!is.null(td) && nrow(td) > 0) {
      return(list(
        name = td$School,
        abbreviation = team_abbrev,
        logo = team_logo,
        conference = td$Conf,
        apRank = if (!is.na(td$AP_Rank)) as.integer(td$AP_Rank) else NULL,
        srsRank = as.integer(td$Rk),
        wins = as.integer(td$W),
        losses = as.integer(td$L),
        stats = list(
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
      ))
    }
  }
  return(list(
    name = team_name, abbreviation = team_abbrev, logo = team_logo,
    conference = NULL, apRank = NULL, srsRank = NULL, wins = NULL, losses = NULL, stats = NULL
  ))
}

get_stat_or_null <- function(stats, stat_name) {
  if (is.null(stats) || is.null(stats[[stat_name]])) return(list(value = NULL, rank = NULL, rankDisplay = NULL))
  return(stats[[stat_name]])
}

build_cbb_comparisons <- function(home_team_data, away_team_data) {
  home_stats <- home_team_data$stats
  away_stats <- away_team_data$stats

  off_comparison <- list(
    pointsPerGame = list(label = "Points/Game", home = get_stat_or_null(home_stats, "pointsPerGame"), away = get_stat_or_null(away_stats, "pointsPerGame")),
    offensiveRating = list(label = "Offensive Rating", home = get_stat_or_null(home_stats, "offensiveRating"), away = get_stat_or_null(away_stats, "offensiveRating")),
    offensiveSRS = list(label = "Offensive SRS", home = get_stat_or_null(home_stats, "offensiveSRS"), away = get_stat_or_null(away_stats, "offensiveSRS")),
    fieldGoalPct = list(label = "FG%", home = get_stat_or_null(home_stats, "fieldGoalPct"), away = get_stat_or_null(away_stats, "fieldGoalPct")),
    effectiveFGPct = list(label = "eFG%", home = get_stat_or_null(home_stats, "effectiveFGPct"), away = get_stat_or_null(away_stats, "effectiveFGPct")),
    trueShooting = list(label = "TS%", home = get_stat_or_null(home_stats, "trueShooting"), away = get_stat_or_null(away_stats, "trueShooting")),
    threePointPct = list(label = "3P%", home = get_stat_or_null(home_stats, "threePointPct"), away = get_stat_or_null(away_stats, "threePointPct")),
    freeThrowPct = list(label = "FT%", home = get_stat_or_null(home_stats, "freeThrowPct"), away = get_stat_or_null(away_stats, "freeThrowPct")),
    assistsPerGame = list(label = "Assists/Game", home = get_stat_or_null(home_stats, "assistsPerGame"), away = get_stat_or_null(away_stats, "assistsPerGame")),
    turnoversPerGame = list(label = "Turnovers/Game", home = get_stat_or_null(home_stats, "turnoversPerGame"), away = get_stat_or_null(away_stats, "turnoversPerGame")),
    turnoverPct = list(label = "TO%", home = get_stat_or_null(home_stats, "turnoverPct"), away = get_stat_or_null(away_stats, "turnoverPct")),
    offReboundsPerGame = list(label = "Off Reb/Game", home = get_stat_or_null(home_stats, "offReboundsPerGame"), away = get_stat_or_null(away_stats, "offReboundsPerGame")),
    offRebPct = list(label = "ORB%", home = get_stat_or_null(home_stats, "offRebPct"), away = get_stat_or_null(away_stats, "offRebPct")),
    pace = list(label = "Pace", home = get_stat_or_null(home_stats, "pace"), away = get_stat_or_null(away_stats, "pace"))
  )

  def_comparison <- list(
    oppPointsPerGame = list(label = "Opp Points/Game", home = get_stat_or_null(home_stats, "oppPointsPerGame"), away = get_stat_or_null(away_stats, "oppPointsPerGame")),
    defensiveRating = list(label = "Defensive Rating", home = get_stat_or_null(home_stats, "defensiveRating"), away = get_stat_or_null(away_stats, "defensiveRating")),
    defensiveSRS = list(label = "Defensive SRS", home = get_stat_or_null(home_stats, "defensiveSRS"), away = get_stat_or_null(away_stats, "defensiveSRS")),
    oppFieldGoalPct = list(label = "Opp FG%", home = get_stat_or_null(home_stats, "oppFieldGoalPct"), away = get_stat_or_null(away_stats, "oppFieldGoalPct")),
    oppThreePointPct = list(label = "Opp 3P%", home = get_stat_or_null(home_stats, "oppThreePointPct"), away = get_stat_or_null(away_stats, "oppThreePointPct")),
    oppEffectiveFGPct = list(label = "Opp eFG%", home = get_stat_or_null(home_stats, "oppEffectiveFGPct"), away = get_stat_or_null(away_stats, "oppEffectiveFGPct")),
    oppTrueShooting = list(label = "Opp TS%", home = get_stat_or_null(home_stats, "oppTrueShooting"), away = get_stat_or_null(away_stats, "oppTrueShooting")),
    stealsPerGame = list(label = "Steals/Game", home = get_stat_or_null(home_stats, "stealsPerGame"), away = get_stat_or_null(away_stats, "stealsPerGame")),
    blocksPerGame = list(label = "Blocks/Game", home = get_stat_or_null(home_stats, "blocksPerGame"), away = get_stat_or_null(away_stats, "blocksPerGame")),
    forcedTurnoverPct = list(label = "Forced TO%", home = get_stat_or_null(home_stats, "forcedTurnoverPct"), away = get_stat_or_null(away_stats, "forcedTurnoverPct"))
  )

  overall_comparison <- list(
    srs = list(label = "Simple Rating System", home = get_stat_or_null(home_stats, "srs"), away = get_stat_or_null(away_stats, "srs")),
    netRating = list(label = "Net Rating", home = get_stat_or_null(home_stats, "netRating"), away = get_stat_or_null(away_stats, "netRating")),
    strengthOfSchedule = list(label = "Strength of Schedule", home = get_stat_or_null(home_stats, "strengthOfSchedule"), away = get_stat_or_null(away_stats, "strengthOfSchedule")),
    marginOfVictory = list(label = "Margin of Victory", home = get_stat_or_null(home_stats, "marginOfVictory"), away = get_stat_or_null(away_stats, "marginOfVictory")),
    reboundsPerGame = list(label = "Rebounds/Game", home = get_stat_or_null(home_stats, "reboundsPerGame"), away = get_stat_or_null(away_stats, "reboundsPerGame"))
  )

  calc_advantage <- function(off_rank, def_rank) {
    if (is.null(off_rank) || is.null(def_rank)) return(0)
    if (off_rank < def_rank) return(-1)
    if (off_rank > def_rank) return(1)
    return(0)
  }

  # Matchup: Home Offense vs Away Defense
  home_ppg <- get_stat_or_null(home_stats, "pointsPerGame")
  home_ortg <- get_stat_or_null(home_stats, "offensiveRating")
  home_fg_pct <- get_stat_or_null(home_stats, "fieldGoalPct")
  home_3p_pct <- get_stat_or_null(home_stats, "threePointPct")
  home_efg_pct <- get_stat_or_null(home_stats, "effectiveFGPct")
  home_ts_pct <- get_stat_or_null(home_stats, "trueShooting")
  home_tov_pct <- get_stat_or_null(home_stats, "turnoverPct")
  home_orb_pct <- get_stat_or_null(home_stats, "offRebPct")

  away_opp_ppg <- get_stat_or_null(away_stats, "oppPointsPerGame")
  away_drtg <- get_stat_or_null(away_stats, "defensiveRating")
  away_opp_fg_pct <- get_stat_or_null(away_stats, "oppFieldGoalPct")
  away_opp_3p_pct <- get_stat_or_null(away_stats, "oppThreePointPct")
  away_opp_efg_pct <- get_stat_or_null(away_stats, "oppEffectiveFGPct")
  away_opp_ts_pct <- get_stat_or_null(away_stats, "oppTrueShooting")
  away_forced_tov_pct <- get_stat_or_null(away_stats, "forcedTurnoverPct")
  away_opp_orb_pct <- get_stat_or_null(away_stats, "oppOffRebPct")

  # Matchup: Away Offense vs Home Defense
  away_ppg <- get_stat_or_null(away_stats, "pointsPerGame")
  away_ortg <- get_stat_or_null(away_stats, "offensiveRating")
  away_fg_pct <- get_stat_or_null(away_stats, "fieldGoalPct")
  away_3p_pct <- get_stat_or_null(away_stats, "threePointPct")
  away_efg_pct <- get_stat_or_null(away_stats, "effectiveFGPct")
  away_ts_pct <- get_stat_or_null(away_stats, "trueShooting")
  away_tov_pct <- get_stat_or_null(away_stats, "turnoverPct")
  away_orb_pct <- get_stat_or_null(away_stats, "offRebPct")

  home_opp_ppg <- get_stat_or_null(home_stats, "oppPointsPerGame")
  home_drtg <- get_stat_or_null(home_stats, "defensiveRating")
  home_opp_fg_pct <- get_stat_or_null(home_stats, "oppFieldGoalPct")
  home_opp_3p_pct <- get_stat_or_null(home_stats, "oppThreePointPct")
  home_opp_efg_pct <- get_stat_or_null(home_stats, "oppEffectiveFGPct")
  home_opp_ts_pct <- get_stat_or_null(home_stats, "oppTrueShooting")
  home_forced_tov_pct <- get_stat_or_null(home_stats, "forcedTurnoverPct")
  home_opp_orb_pct <- get_stat_or_null(home_stats, "oppOffRebPct")

  home_off_vs_away_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame", offLabel = "Points/Game", defLabel = "Opp Points Allowed",
      offense = list(team = home_team_data$abbreviation, value = home_ppg$value, rank = home_ppg$rank, rankDisplay = home_ppg$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_opp_ppg$value, rank = away_opp_ppg$rank, rankDisplay = away_opp_ppg$rankDisplay),
      advantage = calc_advantage(home_ppg$rank, away_opp_ppg$rank)
    ),
    rating = list(
      statKey = "rating", offLabel = "Offensive Rating", defLabel = "Defensive Rating",
      offense = list(team = home_team_data$abbreviation, value = home_ortg$value, rank = home_ortg$rank, rankDisplay = home_ortg$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_drtg$value, rank = away_drtg$rank, rankDisplay = away_drtg$rankDisplay),
      advantage = calc_advantage(home_ortg$rank, away_drtg$rank)
    ),
    fieldGoalPct = list(
      statKey = "fieldGoalPct", offLabel = "FG%", defLabel = "Opp FG% Allowed",
      offense = list(team = home_team_data$abbreviation, value = home_fg_pct$value, rank = home_fg_pct$rank, rankDisplay = home_fg_pct$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_opp_fg_pct$value, rank = away_opp_fg_pct$rank, rankDisplay = away_opp_fg_pct$rankDisplay),
      advantage = calc_advantage(home_fg_pct$rank, away_opp_fg_pct$rank)
    ),
    threePointPct = list(
      statKey = "threePointPct", offLabel = "3P%", defLabel = "Opp 3P% Allowed",
      offense = list(team = home_team_data$abbreviation, value = home_3p_pct$value, rank = home_3p_pct$rank, rankDisplay = home_3p_pct$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_opp_3p_pct$value, rank = away_opp_3p_pct$rank, rankDisplay = away_opp_3p_pct$rankDisplay),
      advantage = calc_advantage(home_3p_pct$rank, away_opp_3p_pct$rank)
    ),
    effectiveFGPct = list(
      statKey = "effectiveFGPct", offLabel = "eFG%", defLabel = "Opp eFG% Allowed",
      offense = list(team = home_team_data$abbreviation, value = home_efg_pct$value, rank = home_efg_pct$rank, rankDisplay = home_efg_pct$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_opp_efg_pct$value, rank = away_opp_efg_pct$rank, rankDisplay = away_opp_efg_pct$rankDisplay),
      advantage = calc_advantage(home_efg_pct$rank, away_opp_efg_pct$rank)
    ),
    trueShooting = list(
      statKey = "trueShooting", offLabel = "TS%", defLabel = "Opp TS% Allowed",
      offense = list(team = home_team_data$abbreviation, value = home_ts_pct$value, rank = home_ts_pct$rank, rankDisplay = home_ts_pct$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_opp_ts_pct$value, rank = away_opp_ts_pct$rank, rankDisplay = away_opp_ts_pct$rankDisplay),
      advantage = calc_advantage(home_ts_pct$rank, away_opp_ts_pct$rank)
    ),
    turnoverPct = list(
      statKey = "turnoverPct", offLabel = "TO%", defLabel = "Forced TO%",
      offense = list(team = home_team_data$abbreviation, value = home_tov_pct$value, rank = home_tov_pct$rank, rankDisplay = home_tov_pct$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_forced_tov_pct$value, rank = away_forced_tov_pct$rank, rankDisplay = away_forced_tov_pct$rankDisplay),
      advantage = calc_advantage(home_tov_pct$rank, away_forced_tov_pct$rank)
    ),
    offRebPct = list(
      statKey = "offRebPct", offLabel = "ORB%", defLabel = "Opp ORB% Allowed",
      offense = list(team = home_team_data$abbreviation, value = home_orb_pct$value, rank = home_orb_pct$rank, rankDisplay = home_orb_pct$rankDisplay),
      defense = list(team = away_team_data$abbreviation, value = away_opp_orb_pct$value, rank = away_opp_orb_pct$rank, rankDisplay = away_opp_orb_pct$rankDisplay),
      advantage = calc_advantage(home_orb_pct$rank, away_opp_orb_pct$rank)
    )
  )

  away_off_vs_home_def <- list(
    pointsPerGame = list(
      statKey = "pointsPerGame", offLabel = "Points/Game", defLabel = "Opp Points Allowed",
      offense = list(team = away_team_data$abbreviation, value = away_ppg$value, rank = away_ppg$rank, rankDisplay = away_ppg$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_opp_ppg$value, rank = home_opp_ppg$rank, rankDisplay = home_opp_ppg$rankDisplay),
      advantage = calc_advantage(away_ppg$rank, home_opp_ppg$rank)
    ),
    rating = list(
      statKey = "rating", offLabel = "Offensive Rating", defLabel = "Defensive Rating",
      offense = list(team = away_team_data$abbreviation, value = away_ortg$value, rank = away_ortg$rank, rankDisplay = away_ortg$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_drtg$value, rank = home_drtg$rank, rankDisplay = home_drtg$rankDisplay),
      advantage = calc_advantage(away_ortg$rank, home_drtg$rank)
    ),
    fieldGoalPct = list(
      statKey = "fieldGoalPct", offLabel = "FG%", defLabel = "Opp FG% Allowed",
      offense = list(team = away_team_data$abbreviation, value = away_fg_pct$value, rank = away_fg_pct$rank, rankDisplay = away_fg_pct$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_opp_fg_pct$value, rank = home_opp_fg_pct$rank, rankDisplay = home_opp_fg_pct$rankDisplay),
      advantage = calc_advantage(away_fg_pct$rank, home_opp_fg_pct$rank)
    ),
    threePointPct = list(
      statKey = "threePointPct", offLabel = "3P%", defLabel = "Opp 3P% Allowed",
      offense = list(team = away_team_data$abbreviation, value = away_3p_pct$value, rank = away_3p_pct$rank, rankDisplay = away_3p_pct$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_opp_3p_pct$value, rank = home_opp_3p_pct$rank, rankDisplay = home_opp_3p_pct$rankDisplay),
      advantage = calc_advantage(away_3p_pct$rank, home_opp_3p_pct$rank)
    ),
    effectiveFGPct = list(
      statKey = "effectiveFGPct", offLabel = "eFG%", defLabel = "Opp eFG% Allowed",
      offense = list(team = away_team_data$abbreviation, value = away_efg_pct$value, rank = away_efg_pct$rank, rankDisplay = away_efg_pct$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_opp_efg_pct$value, rank = home_opp_efg_pct$rank, rankDisplay = home_opp_efg_pct$rankDisplay),
      advantage = calc_advantage(away_efg_pct$rank, home_opp_efg_pct$rank)
    ),
    trueShooting = list(
      statKey = "trueShooting", offLabel = "TS%", defLabel = "Opp TS% Allowed",
      offense = list(team = away_team_data$abbreviation, value = away_ts_pct$value, rank = away_ts_pct$rank, rankDisplay = away_ts_pct$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_opp_ts_pct$value, rank = home_opp_ts_pct$rank, rankDisplay = home_opp_ts_pct$rankDisplay),
      advantage = calc_advantage(away_ts_pct$rank, home_opp_ts_pct$rank)
    ),
    turnoverPct = list(
      statKey = "turnoverPct", offLabel = "TO%", defLabel = "Forced TO%",
      offense = list(team = away_team_data$abbreviation, value = away_tov_pct$value, rank = away_tov_pct$rank, rankDisplay = away_tov_pct$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_forced_tov_pct$value, rank = home_forced_tov_pct$rank, rankDisplay = home_forced_tov_pct$rankDisplay),
      advantage = calc_advantage(away_tov_pct$rank, home_forced_tov_pct$rank)
    ),
    offRebPct = list(
      statKey = "offRebPct", offLabel = "ORB%", defLabel = "Opp ORB% Allowed",
      offense = list(team = away_team_data$abbreviation, value = away_orb_pct$value, rank = away_orb_pct$rank, rankDisplay = away_orb_pct$rankDisplay),
      defense = list(team = home_team_data$abbreviation, value = home_opp_orb_pct$value, rank = home_opp_orb_pct$rank, rankDisplay = home_opp_orb_pct$rankDisplay),
      advantage = calc_advantage(away_orb_pct$rank, home_opp_orb_pct$rank)
    )
  )

  return(list(
    sideBySide = list(offense = off_comparison, defense = def_comparison, overall = overall_comparison),
    homeOffVsAwayDef = home_off_vs_away_def,
    awayOffVsHomeDef = away_off_vs_home_def
  ))
}

matchups_json <- list()
for (game in matchups) {
  cat("Processing:", game$away_team_abbrev, "@", game$home_team_abbrev, "\n")

  home_team_data <- build_team_stats(game$home_top_team_key, game$home_team_name, game$home_team_abbrev, game$home_team_logo)
  away_team_data <- build_team_stats(game$away_top_team_key, game$away_team_name, game$away_team_abbrev, game$away_team_logo)
  comparisons <- build_cbb_comparisons(home_team_data, away_team_data)

  matchup <- list(
    gameId = game$game_id,
    gameDate = game$game_date,
    gameName = game$game_name,
    homeTeam = home_team_data,
    awayTeam = away_team_data,
    comparisons = comparisons
  )

  if (!is.null(game$location)) matchup$location <- game$location
  if (!is.null(game$odds)) matchup$odds <- game$odds

  matchups_json[[length(matchups_json) + 1]] <- matchup
}

# ============================================================================
# STEP 8: Generate output JSON
# ============================================================================
cat("\n8. Generating output JSON...\n")

output_data <- list(
  sport = "CBB",
  visualizationType = "CBB_MATCHUP",
  title = paste0("College Basketball Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d")),
  subtitle = paste("AP Top 25 vs Top 25 matchups for the next", DAYS_AHEAD, "days"),
  description = paste0(
    "Matchup statistics for games where BOTH teams are AP Top 25.\n\n",
    "OFFENSIVE STATS:\n",
    " - PPG, FG%, 3P%, FT%, Assists, Off Rebounds, Turnovers\n",
    " - Offensive Rating, Offensive SRS\n\n",
    "DEFENSIVE STATS (what they allow):\n",
    " - Opp PPG, Opp FG%, Opp 3P%, Opp eFG%, Opp TS%\n",
    " - Defensive Rating, Defensive SRS\n",
    " - Steals, Blocks, Forced Turnover %\n\n",
    "OVERALL:\n",
    " - SRS, Net Rating, Strength of Schedule, Margin of Victory\n\n",
    "All stats from Sports Reference. Rankings are among all D1 teams."
  ),
  lastUpdated = format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC"),
  source = "ESPN / Sports Reference",
  tags = list(
    list(label = "team", layout = "left", color = "#4CAF50"),
    list(label = "top 25 vs top 25", layout = "left", color = "#FF9800"),
    list(label = "regular season", layout = "right", color = "#9C27B0")
  ),
  sortOrder = 0,
  dataPoints = matchups_json
)

tmp_file <- tempfile(fileext = ".json")
write_json(output_data, tmp_file, pretty = TRUE, auto_unbox = TRUE, null = "null", na = "null")

cat("Generated stats for", length(matchups_json), "matchup(s)\n")

s3_bucket <- Sys.getenv("AWS_S3_BUCKET")

if (nzchar(s3_bucket)) {
  s3_key <- "dev/cbb__matchup_stats.json"
  s3_path <- paste0("s3://", s3_bucket, "/", s3_key)
  cmd <- paste("aws s3 cp", shQuote(tmp_file), shQuote(s3_path), "--content-type application/json")
  result <- system(cmd)
  if (result != 0) stop("Failed to upload to S3")
  cat("Uploaded to S3:", s3_path, "\n")

  dynamodb_table <- Sys.getenv("AWS_DYNAMODB_TABLE", "fastbreak-file-timestamps")
  utc_timestamp <- format(Sys.time(), "%Y-%m-%dT%H:%M:%SZ", tz = "UTC")
  chart_title <- paste0("CBB Matchups - ", format(today, "%b %d"), " - ", format(end_date, "%b %d"))
  dynamodb_item <- sprintf(
    '{"file_key": {"S": "%s"}, "updatedAt": {"S": "%s"}, "title": {"S": "%s"}, "interval": {"S": "daily"}}',
    s3_key, utc_timestamp, chart_title
  )
  dynamodb_cmd <- sprintf('aws dynamodb put-item --table-name %s --item %s', shQuote(dynamodb_table), shQuote(dynamodb_item))
  dynamodb_result <- system(dynamodb_cmd)
  if (dynamodb_result != 0) cat("Warning: Failed to update DynamoDB\n")
  else cat("Updated DynamoDB:", dynamodb_table, "key:", s3_key, "\n")
} else {
  dev_output <- "/tmp/cbb_matchup_test.json"
  file.copy(tmp_file, dev_output, overwrite = TRUE)
  cat("Development mode - output written to:", dev_output, "\n")
}

cat("\n=== CBB Matchup Stats generation complete ===\n")
