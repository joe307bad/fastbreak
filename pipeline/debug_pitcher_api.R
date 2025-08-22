# Debug script to explore MLB API data structure for finding starting pitchers
source("config/setup.R")

# Test game from the error: GamePK 746817, 2024-04-01
game_pk <- 746817
game_date <- "2024-04-01"

cat("=== Exploring MLB API data structure ===\n")
cat(sprintf("Game PK: %s, Date: %s\n\n", game_pk, game_date))

# 1. Try mlb_game_info
cat("1. Testing mlb_game_info():\n")
tryCatch({
  game_info <- mlb_game_info(game_pk)
  cat("Game info structure:\n")
  cat("Names at top level:", paste(names(game_info), collapse = ", "), "\n\n")
  
  # Check gameData section
  if ("gameData" %in% names(game_info)) {
    cat("gameData section found. Names:\n")
    cat(paste(names(game_info$gameData), collapse = ", "), "\n\n")
    
    # Check probables
    if ("probables" %in% names(game_info$gameData)) {
      cat("probables section found:\n")
      probables <- game_info$gameData$probables
      cat("Probables structure:", str(probables), "\n\n")
    } else {
      cat("No probables section found\n\n")
    }
  }
  
  # Check liveData section
  if ("liveData" %in% names(game_info)) {
    cat("liveData section found. Names:\n")
    cat(paste(names(game_info$liveData), collapse = ", "), "\n\n")
    
    # Check boxscore
    if ("boxscore" %in% names(game_info$liveData)) {
      cat("boxscore section found. Names:\n")
      boxscore <- game_info$liveData$boxscore
      cat(paste(names(boxscore), collapse = ", "), "\n\n")
      
      # Check teams in boxscore
      if ("teams" %in% names(boxscore)) {
        cat("teams section in boxscore found:\n")
        teams <- boxscore$teams
        cat("Team names:", paste(names(teams), collapse = ", "), "\n")
        
        # Check home team
        if ("home" %in% names(teams)) {
          cat("Home team structure:\n")
          cat(paste(names(teams$home), collapse = ", "), "\n")
          
          if ("players" %in% names(teams$home)) {
            cat("Home team has", length(teams$home$players), "players\n")
            # Show first few player IDs
            player_ids <- names(teams$home$players)[1:min(3, length(teams$home$players))]
            cat("Sample player IDs:", paste(player_ids, collapse = ", "), "\n")
          }
        }
      }
    }
  }
  
}, error = function(e) {
  cat("Error with mlb_game_info:", e$message, "\n")
})

# 2. Try mlb_probables
cat("\n2. Testing mlb_probables():\n")
tryCatch({
  probables <- mlb_probables(game_date)
  cat("Probables data retrieved. Rows:", nrow(probables), "\n")
  if (nrow(probables) > 0) {
    cat("Probables columns:", paste(names(probables), collapse = ", "), "\n")
    
    # Filter for our game
    game_probables <- probables[probables$game_pk == game_pk, ]
    cat("Rows for our game:", nrow(game_probables), "\n")
    if (nrow(game_probables) > 0) {
      cat("Game probables data:\n")
      print(game_probables)
    }
  }
}, error = function(e) {
  cat("Error with mlb_probables:", e$message, "\n")
})

# 3. Try mlb_schedule with more details
cat("\n3. Testing mlb_schedule() for detailed game info:\n")
tryCatch({
  schedule <- mlb_schedule(
    season = 2024,
    level_ids = 1,
    start_date = game_date,
    end_date = game_date
  )
  
  cat("Schedule data retrieved. Rows:", nrow(schedule), "\n")
  if (nrow(schedule) > 0) {
    cat("Schedule columns:", paste(names(schedule), collapse = ", "), "\n")
    
    # Filter for our game
    game_schedule <- schedule[schedule$game_pk == game_pk, ]
    cat("Rows for our game:", nrow(game_schedule), "\n")
    if (nrow(game_schedule) > 0) {
      cat("Game schedule data structure:\n")
      cat("Columns with 'pitcher' or 'probable':\n")
      pitcher_cols <- names(game_schedule)[grepl("pitcher|probable", names(game_schedule), ignore.case = TRUE)]
      cat(paste(pitcher_cols, collapse = ", "), "\n")
      
      if (length(pitcher_cols) > 0) {
        for (col in pitcher_cols) {
          cat(sprintf("%s: %s\n", col, game_schedule[[col]]))
        }
      }
    }
  }
}, error = function(e) {
  cat("Error with mlb_schedule:", e$message, "\n")
})

# 4. Try other baseballr functions
cat("\n4. Testing other baseballr functions:\n")

# Try mlb_game_linescore
cat("Testing mlb_game_linescore():\n")
tryCatch({
  linescore <- mlb_game_linescore(game_pk)
  if (!is.null(linescore) && nrow(linescore) > 0) {
    cat("Linescore data found. Columns:\n")
    cat(paste(names(linescore), collapse = ", "), "\n")
    pitcher_cols <- names(linescore)[grepl("pitcher", names(linescore), ignore.case = TRUE)]
    if (length(pitcher_cols) > 0) {
      cat("Pitcher-related columns:", paste(pitcher_cols, collapse = ", "), "\n")
    }
  } else {
    cat("No linescore data found\n")
  }
}, error = function(e) {
  cat("Error with mlb_game_linescore:", e$message, "\n")
})

cat("\n=== End of API exploration ===\n")