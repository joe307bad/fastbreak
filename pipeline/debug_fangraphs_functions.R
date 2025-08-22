# Debug script to check available FanGraphs functions in baseballr
source("config/setup.R")

cat("=== Checking Available FanGraphs Functions ===\n")

# Get all functions in baseballr package
all_functions <- ls("package:baseballr")

# Look for FanGraphs functions
fg_functions <- all_functions[grepl("^fg_", all_functions)]
cat("FanGraphs functions found:\n")
if (length(fg_functions) > 0) {
  for (i in seq_along(fg_functions)) {
    cat(sprintf("%2d: %s\n", i, fg_functions[i]))
  }
} else {
  cat("No functions starting with 'fg_' found\n")
}

# Look for any functions with 'fangraphs' in name
fangraphs_functions <- all_functions[grepl("fangraphs", all_functions, ignore.case = TRUE)]
cat("\nFunctions with 'fangraphs' in name:\n")
if (length(fangraphs_functions) > 0) {
  for (i in seq_along(fangraphs_functions)) {
    cat(sprintf("%2d: %s\n", i, fangraphs_functions[i]))
  }
} else {
  cat("No functions with 'fangraphs' found\n")
}

# Look for team-related functions
team_functions <- all_functions[grepl("team", all_functions, ignore.case = TRUE)]
cat("\nTeam-related functions:\n")
if (length(team_functions) > 0) {
  for (i in seq_along(team_functions)) {
    cat(sprintf("%2d: %s\n", i, team_functions[i]))
  }
} else {
  cat("No team-related functions found\n")
}

cat("\nbaseballr package version:\n")
cat(packageVersion("baseballr"), "\n")