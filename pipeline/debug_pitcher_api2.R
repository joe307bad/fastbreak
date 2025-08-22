# Debug script to find working approaches for getting starting pitchers
source("config/setup.R")

# Test with a more recent date and explore different functions
game_pk <- 746817
game_date <- "2024-04-01"

cat("=== Testing different approaches ===\n")

# 1. Test mlb_schedule with correct parameters
cat("1. Testing mlb_schedule with correct parameters:\n")
tryCatch({
  # Get the schedule for 2024 season and filter by date
  schedule <- mlb_schedule(season = 2024, level_ids = 1)
  
  if (!is.null(schedule) && nrow(schedule) > 0) {
    cat("Total games in 2024 schedule:", nrow(schedule), "\n")
    
    # Filter for our specific date and game
    game_schedule <- schedule %>%
      filter(official_date == game_date, game_pk == !!game_pk)
    
    cat("Games found for our date/pk:", nrow(game_schedule), "\n")
    
    if (nrow(game_schedule) > 0) {
      cat("All columns in schedule data:\n")
      col_names <- names(game_schedule)
      cat(paste(col_names, collapse = ", "), "\n\n")
      
      # Look for pitcher-related columns
      pitcher_cols <- col_names[grepl("pitcher|probable", col_names, ignore.case = TRUE)]
      cat("Pitcher-related columns found:", length(pitcher_cols), "\n")
      if (length(pitcher_cols) > 0) {
        cat("Pitcher columns:", paste(pitcher_cols, collapse = ", "), "\n")
        for (col in pitcher_cols) {
          val <- game_schedule[[col]][1]
          cat(sprintf("  %s: %s\n", col, ifelse(is.na(val), "NA", as.character(val))))
        }
      }
    }
  }
}, error = function(e) {
  cat("Error with mlb_schedule:", e$message, "\n")
})

# 2. Try the probables function with correct date format
cat("\n2. Testing mlb_probables with date:\n")
tryCatch({
  # Try different date formats
  for (date_fmt in c("2024-04-01", "04/01/2024", "2024/04/01")) {
    cat(sprintf("Trying date format: %s\n", date_fmt))
    probables <- mlb_probables(date_fmt)
    
    if (!is.null(probables) && nrow(probables) > 0) {
      cat("Probables found! Rows:", nrow(probables), "\n")
      cat("Columns:", paste(names(probables), collapse = ", "), "\n")
      
      # Look for our game
      if ("game_pk" %in% names(probables)) {
        game_prob <- probables[probables$game_pk == game_pk, ]
        if (nrow(game_prob) > 0) {
          cat("Found probables for our game!\n")
          print(game_prob)
        } else {
          cat("Game not found in probables\n")
          cat("Available game_pks:", paste(head(probables$game_pk, 5), collapse = ", "), "\n")
        }
      }
      break
    } else {
      cat("No probables data found\n")
    }
  }
}, error = function(e) {
  cat("Error with mlb_probables:", e$message, "\n")
})

# 3. Try using play-by-play data which might have starter info
cat("\n3. Testing mlb_pbp (play-by-play) for pitcher info:\n")
tryCatch({
  pbp <- mlb_pbp(game_pk)
  if (!is.null(pbp) && nrow(pbp) > 0) {
    cat("Play-by-play data found. Rows:", nrow(pbp), "\n")
    cat("Columns:", paste(names(pbp), collapse = ", "), "\n")
    
    # Look for pitcher info in first few plays
    pitcher_cols <- names(pbp)[grepl("pitcher", names(pbp), ignore.case = TRUE)]
    if (length(pitcher_cols) > 0) {
      cat("Pitcher columns in pbp:", paste(pitcher_cols, collapse = ", "), "\n")
      
      # Get unique pitchers from start of game
      first_plays <- pbp[1:min(10, nrow(pbp)), pitcher_cols, drop = FALSE]
      cat("Pitcher info from first plays:\n")
      print(first_plays)
    }
  }
}, error = function(e) {
  cat("Error with mlb_pbp:", e$message, "\n")
})

# 4. Check available functions in baseballr package
cat("\n4. Available baseballr functions with 'mlb' prefix:\n")
mlb_functions <- ls("package:baseballr")[grepl("^mlb_", ls("package:baseballr"))]
cat("Total MLB functions:", length(mlb_functions), "\n")
cat("Functions:", paste(mlb_functions, collapse = ", "), "\n")

# Look specifically for game-related functions
game_functions <- mlb_functions[grepl("game|pitcher|start", mlb_functions)]
cat("\nGame/pitcher related functions:\n")
cat(paste(game_functions, collapse = ", "), "\n")

cat("\n=== End exploration ===\n")