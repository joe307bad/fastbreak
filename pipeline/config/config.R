# Configuration file for fastbreak pipeline

# Date ranges
DEFAULT_START_DATE <- "2024-03-28"  # Start of 2024 season
DEFAULT_END_DATE <- "2024-10-01"    # End of regular season

# Output settings
OUTPUT_DIR <- "output"
OUTPUT_FILENAME <- "fastbreak_games.csv"

# Weather API settings (example - replace with actual API)
WEATHER_API_KEY <- Sys.getenv("WEATHER_API_KEY")  # Set as environment variable
WEATHER_API_URL <- "https://api.openweathermap.org/data/2.5/historical"

# MLB team abbreviations mapping
MLB_TEAMS <- list(
  "Los Angeles Angels" = "LAA",
  "Houston Astros" = "HOU", 
  "Oakland Athletics" = "OAK",
  "Toronto Blue Jays" = "TOR",
  "Atlanta Braves" = "ATL",
  "Milwaukee Brewers" = "MIL",
  "St. Louis Cardinals" = "STL",
  "Chicago Cubs" = "CHC",
  "Arizona Diamondbacks" = "ARI",
  "Colorado Rockies" = "COL",
  "Los Angeles Dodgers" = "LAD",
  "San Diego Padres" = "SD",
  "San Francisco Giants" = "SF",
  "Cleveland Guardians" = "CLE",
  "Detroit Tigers" = "DET",
  "Kansas City Royals" = "KC",
  "Chicago White Sox" = "CWS",
  "Minnesota Twins" = "MIN",
  "New York Yankees" = "NYY",
  "Boston Red Sox" = "BOS",
  "Baltimore Orioles" = "BAL",
  "Tampa Bay Rays" = "TB",
  "Texas Rangers" = "TEX",
  "Seattle Mariners" = "SEA",
  "New York Mets" = "NYM",
  "Philadelphia Phillies" = "PHI",
  "Miami Marlins" = "MIA",
  "Washington Nationals" = "WSH",
  "Pittsburgh Pirates" = "PIT",
  "Cincinnati Reds" = "CIN"
)

# Stadium coordinates for weather data
STADIUM_COORDS <- list(
  "LAA" = list(lat = 33.8003, lon = -117.8827),
  "HOU" = list(lat = 29.7572, lon = -95.3555),
  "OAK" = list(lat = 37.7516, lon = -122.2005),
  "TOR" = list(lat = 43.6414, lon = -79.3894),
  "ATL" = list(lat = 33.7490, lon = -84.3880),
  "MIL" = list(lat = 43.0280, lon = -87.9711),
  "STL" = list(lat = 38.6226, lon = -90.1928),
  "CHC" = list(lat = 41.9484, lon = -87.6553),
  "ARI" = list(lat = 33.4453, lon = -112.0667),
  "COL" = list(lat = 39.7559, lon = -104.9942),
  "LAD" = list(lat = 34.0739, lon = -118.2400),
  "SD" = list(lat = 32.7073, lon = -117.1566),
  "SF" = list(lat = 37.7786, lon = -122.3893),
  "CLE" = list(lat = 41.4962, lon = -81.6852),
  "DET" = list(lat = 42.3390, lon = -83.0485),
  "KC" = list(lat = 39.0517, lon = -94.4803),
  "CWS" = list(lat = 41.8300, lon = -87.6338),
  "MIN" = list(lat = 44.9817, lon = -93.2775),
  "NYY" = list(lat = 40.8296, lon = -73.9262),
  "BOS" = list(lat = 42.3467, lon = -71.0972),
  "BAL" = list(lat = 39.2840, lon = -76.6217),
  "TB" = list(lat = 27.7682, lon = -82.6534),
  "TEX" = list(lat = 32.7510, lon = -97.0830),
  "SEA" = list(lat = 47.5915, lon = -122.3326),
  "NYM" = list(lat = 40.7571, lon = -73.8458),
  "PHI" = list(lat = 39.9061, lon = -75.1665),
  "MIA" = list(lat = 25.7781, lon = -80.2197),
  "WSH" = list(lat = 38.8730, lon = -77.0074),
  "PIT" = list(lat = 40.4469, lon = -80.0057),
  "CIN" = list(lat = 39.0974, lon = -84.5068)
)