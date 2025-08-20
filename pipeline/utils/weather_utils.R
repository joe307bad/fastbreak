# Weather data utility functions

#' Get weather data for a team's stadium on a specific date
#' @param team_abbrev Team abbreviation
#' @param game_date Date of the game
get_weather_data <- function(team_abbrev, game_date) {
  tryCatch({
    # Get stadium coordinates
    coords <- STADIUM_COORDS[[team_abbrev]]
    
    if (is.null(coords)) {
      return(list(
        temperature = NA,
        wind_speed = NA, 
        wind_direction = NA,
        precipitation = NA
      ))
    }
    
    # For now, return mock weather data
    # In production, you'd call a weather API here
    get_mock_weather_data(coords$lat, coords$lon, game_date)
    
  }, error = function(e) {
    list(
      temperature = NA,
      wind_speed = NA,
      wind_direction = NA, 
      precipitation = NA
    )
  })
}

#' Get mock weather data (replace with real weather API call)
#' @param lat Latitude
#' @param lon Longitude  
#' @param date Date
get_mock_weather_data <- function(lat, lon, date) {
  # Generate realistic but random weather data
  # In production, replace with actual weather API call
  
  set.seed(as.numeric(as.Date(date)) + round(lat * 1000) + round(lon * 1000))
  
  # Temperature varies by season
  month <- month(as.Date(date))
  base_temp <- case_when(
    month %in% 3:5 ~ 65,   # Spring
    month %in% 6:8 ~ 78,   # Summer  
    month %in% 9:11 ~ 68,  # Fall
    TRUE ~ 50              # Winter
  )
  
  temperature <- round(base_temp + rnorm(1, 0, 10), 1)
  temperature <- pmax(35, pmin(95, temperature))  # Realistic range
  
  wind_speed <- round(rgamma(1, 2, 0.3), 1)
  wind_speed <- pmax(0, pmin(25, wind_speed))
  
  wind_directions <- c("N", "NE", "E", "SE", "S", "SW", "W", "NW")
  wind_direction <- sample(wind_directions, 1)
  
  precipitation <- round(rexp(1, 10), 1)  # Most days have no precip
  precipitation <- pmin(2.0, precipitation)  # Cap at 2 inches
  
  list(
    temperature = temperature,
    wind_speed = wind_speed,
    wind_direction = wind_direction,
    precipitation = precipitation
  )
}

#' Call actual weather API (template function)
#' @param lat Latitude
#' @param lon Longitude
#' @param date Date (YYYY-MM-DD)
call_weather_api <- function(lat, lon, date) {
  # Template for calling a real weather API
  # Example using OpenWeatherMap historical data API
  
  if (is.null(WEATHER_API_KEY) || WEATHER_API_KEY == "") {
    warning("WEATHER_API_KEY not set, using mock data")
    return(get_mock_weather_data(lat, lon, date))
  }
  
  # Convert date to Unix timestamp
  date_timestamp <- as.numeric(as.POSIXct(paste(date, "12:00:00")))
  
  # Build API URL
  url <- glue("{WEATHER_API_URL}?lat={lat}&lon={lon}&dt={date_timestamp}&appid={WEATHER_API_KEY}&units=imperial")
  
  # Make API call
  response <- httr::GET(url)
  
  if (httr::status_code(response) == 200) {
    data <- jsonlite::fromJSON(httr::content(response, "text"))
    
    # Parse weather data from API response
    # (Structure depends on specific weather API used)
    list(
      temperature = data$current$temp %||% NA,
      wind_speed = data$current$wind_speed %||% NA,
      wind_direction = convert_wind_degrees(data$current$wind_deg %||% NA),
      precipitation = data$current$rain$`1h` %||% 0
    )
  } else {
    warning("Weather API call failed, using mock data")
    get_mock_weather_data(lat, lon, date)
  }
}

#' Convert wind degrees to direction
#' @param degrees Wind direction in degrees
convert_wind_degrees <- function(degrees) {
  if (is.na(degrees)) return(NA)
  
  directions <- c("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                  "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
  
  # Convert to 0-360 range
  degrees <- degrees %% 360
  
  # Calculate index (16 directions, so 360/16 = 22.5 degrees per direction)
  index <- round(degrees / 22.5) + 1
  index <- ((index - 1) %% 16) + 1
  
  # Return simplified direction
  simple_directions <- c("N", "NE", "NE", "NE", "E", "SE", "SE", "SE",
                        "S", "SW", "SW", "SW", "W", "NW", "NW", "NW")
  
  simple_directions[index]
}