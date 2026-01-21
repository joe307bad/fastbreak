# NBA Utilities - Shared constants and helper functions for NBA data processing

# Get current NBA season (NBA season spans two years, e.g., 2024-25)
get_current_nba_season <- function() {
  current_year <- as.numeric(format(Sys.Date(), "%Y"))
  current_month <- as.numeric(format(Sys.Date(), "%m"))

  # NBA season starts in October, so if we're in Jan-Sep, use previous year as season start
  if (current_month >= 10) current_year + 1 else current_year
}

# NBA division mapping by team abbreviation
nba_team_divisions <- c(
  "ATL" = "Southeast", "BOS" = "Atlantic", "BKN" = "Atlantic",
  "CHA" = "Southeast", "CHI" = "Central", "CLE" = "Central",
  "DAL" = "Southwest", "DEN" = "Northwest", "DET" = "Central",
  "GSW" = "Pacific", "HOU" = "Southwest", "IND" = "Central",
  "LAC" = "Pacific", "LAL" = "Pacific", "MEM" = "Southwest",
  "MIA" = "Southeast", "MIL" = "Central", "MIN" = "Northwest",
  "NOP" = "Southwest", "NYK" = "Atlantic", "OKC" = "Northwest",
  "ORL" = "Southeast", "PHI" = "Atlantic", "PHX" = "Pacific",
  "POR" = "Northwest", "SAC" = "Pacific", "SAS" = "Southwest",
  "TOR" = "Atlantic", "UTA" = "Northwest", "WAS" = "Southeast"
)

# NBA conference mapping by team abbreviation
nba_team_conferences <- c(
  "ATL" = "Eastern", "BOS" = "Eastern", "BKN" = "Eastern",
  "CHA" = "Eastern", "CHI" = "Eastern", "CLE" = "Eastern",
  "DAL" = "Western", "DEN" = "Western", "DET" = "Eastern",
  "GSW" = "Western", "HOU" = "Western", "IND" = "Eastern",
  "LAC" = "Western", "LAL" = "Western", "MEM" = "Western",
  "MIA" = "Eastern", "MIL" = "Eastern", "MIN" = "Western",
  "NOP" = "Western", "NYK" = "Eastern", "OKC" = "Western",
  "ORL" = "Eastern", "PHI" = "Eastern", "PHX" = "Western",
  "POR" = "Western", "SAC" = "Western", "SAS" = "Western",
  "TOR" = "Eastern", "UTA" = "Western", "WAS" = "Eastern"
)

# Format NBA season string for API calls (e.g., "2024-25")
format_nba_season <- function(season_year) {
  paste0(season_year - 1, "-", substr(season_year, 3, 4))
}
