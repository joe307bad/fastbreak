#!/usr/bin/env Rscript

# Batch runner for second_year_sleepers.R across multiple weeks
# Usage: Rscript run_sleeper_analysis_batch.R [year] [start_week] [end_week] [--combine]
# Example: Rscript run_sleeper_analysis_batch.R 2024 3 17
# Example: Rscript run_sleeper_analysis_batch.R 2024 3 17 --combine

suppressPackageStartupMessages({
  library(dplyr)
  library(readr)
  library(purrr)
})

# Parse command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Check for combine flag
combine_outputs <- FALSE
if ("--combine" %in% args) {
  combine_outputs <- TRUE
  args <- args[args != "--combine"]
}

# Validate arguments
if (length(args) < 1 || length(args) > 3) {
  cat("Usage: Rscript run_sleeper_analysis_batch.R [year] [start_week] [end_week]\n")
  cat("  year: The season year (default: current year)\n")
  cat("  start_week: Starting week (default: 3)\n")
  cat("  end_week: Ending week (default: 17)\n")
  cat("  --combine: Combine all raw outputs into a single file\n\n")
  cat("Examples:\n")
  cat("  Rscript run_sleeper_analysis_batch.R 2024        # Run weeks 3-17 for 2024\n")
  cat("  Rscript run_sleeper_analysis_batch.R 2024 5 10   # Run weeks 5-10 for 2024\n")
  cat("  Rscript run_sleeper_analysis_batch.R 2024 3 17 --combine  # Run and combine\n")
  quit(status = 1)
}

# Set defaults
current_year <- as.integer(format(Sys.Date(), "%Y"))
year <- if (length(args) >= 1) as.integer(args[1]) else current_year
start_week <- if (length(args) >= 2) as.integer(args[2]) else 3
end_week <- if (length(args) >= 3) as.integer(args[3]) else 17

# Validate inputs
if (year < 2020 || year > current_year) {
  cat("Error: Year must be between 2020 and", current_year, "\n")
  quit(status = 1)
}

if (start_week < 1 || start_week > 18) {
  cat("Error: Start week must be between 1 and 18\n")
  quit(status = 1)
}

if (end_week < 1 || end_week > 18) {
  cat("Error: End week must be between 1 and 18\n")
  quit(status = 1)
}

if (start_week > end_week) {
  cat("Error: Start week must be less than or equal to end week\n")
  quit(status = 1)
}

# Create output directory if it doesn't exist
output_dir <- paste0("sleeper_analysis_", year)
if (!dir.exists(output_dir)) {
  dir.create(output_dir)
  cat("Created output directory:", output_dir, "\n")
}

# Initialize tracking
total_weeks <- end_week - start_week + 1
successful_runs <- 0
failed_runs <- 0
all_raw_files <- c()

cat("\n============================================\n")
cat("Starting batch analysis for", year, "season\n")
cat("Processing weeks", start_week, "to", end_week, "\n")
cat("Output directory:", output_dir, "\n")
cat("============================================\n\n")

# Function to run analysis for a single week
run_week_analysis <- function(week, year, output_dir) {
  cat("\n----------------------------------------\n")
  cat("Processing Week", week, "of", year, "season\n")
  cat("----------------------------------------\n")

  # Build the command
  script_path <- "scripts/second_year_sleepers.R"
  if (!file.exists(script_path)) {
    script_path <- "second_year_sleepers.R"
    if (!file.exists(script_path)) {
      cat("ERROR: Cannot find second_year_sleepers.R script\n")
      return(list(success = FALSE, files = NULL))
    }
  }

  # Set output file prefix to include directory
  output_prefix <- file.path(output_dir, paste0("sleepers_", year, "_w", week))

  # Build command with output prefix
  cmd <- paste(
    "Rscript",
    script_path,
    year,
    week,
    output_prefix
  )

  # Log the command
  cat("Executing:", cmd, "\n\n")

  # Run the command and capture output
  start_time <- Sys.time()
  result <- tryCatch({
    system(cmd, intern = FALSE)
    0  # Return 0 for success
  }, error = function(e) {
    cat("ERROR running week", week, ":", e$message, "\n")
    1  # Return 1 for failure
  })

  end_time <- Sys.time()
  elapsed <- round(difftime(end_time, start_time, units = "secs"), 1)

  if (result == 0) {
    cat("\n✓ Week", week, "completed successfully in", elapsed, "seconds\n")

    # List generated files
    expected_files <- c(
      paste0(output_prefix, ".csv"),
      paste0(output_prefix, "_raw.csv"),
      paste0(output_prefix, ".png")
    )

    existing_files <- expected_files[file.exists(expected_files)]

    if (length(existing_files) > 0) {
      cat("Generated files:\n")
      for (f in existing_files) {
        cat("  -", basename(f), "\n")
      }
    }

    # Return raw file path if it exists
    raw_file <- paste0(output_prefix, "_raw.csv")
    return(list(
      success = TRUE,
      files = if (file.exists(raw_file)) raw_file else NULL
    ))
  } else {
    cat("\n✗ Week", week, "failed after", elapsed, "seconds\n")
    return(list(success = FALSE, files = NULL))
  }
}

# Main processing loop
for (week in start_week:end_week) {
  result <- run_week_analysis(week, year, output_dir)

  if (result$success) {
    successful_runs <- successful_runs + 1
    if (!is.null(result$files)) {
      all_raw_files <- c(all_raw_files, result$files)
    }
  } else {
    failed_runs <- failed_runs + 1
  }

  # Progress update
  completed <- week - start_week + 1
  cat("\nProgress:", completed, "/", total_weeks, "weeks completed\n")
  cat("Success rate:", round(successful_runs / completed * 100, 1), "%\n")

  # Add delay between requests to be polite to data sources
  if (week < end_week) {
    cat("\nWaiting 5 seconds before next week...\n")
    Sys.sleep(5)
  }
}

# Combine raw outputs if requested
if (combine_outputs && length(all_raw_files) > 0) {
  cat("\n============================================\n")
  cat("Combining raw data files...\n")
  cat("============================================\n")

  combined_data <- map_dfr(all_raw_files, function(file) {
    cat("Reading:", basename(file), "\n")
    read_csv(file, show_col_types = FALSE)
  })

  # Sort by analysis week and player
  combined_data <- combined_data %>%
    arrange(analysis_week, player)

  # Save combined file
  combined_file <- file.path(output_dir, paste0("combined_raw_", year, "_w",
                                                start_week, "-", end_week, ".csv"))
  write_csv(combined_data, combined_file)

  cat("\nCombined data saved to:", combined_file, "\n")
  cat("Total records:", nrow(combined_data), "\n")
  cat("Total features:", ncol(combined_data), "\n")

  # Show week breakdown
  week_counts <- combined_data %>%
    group_by(analysis_week) %>%
    summarise(
      players = n(),
      positions = paste(unique(position), collapse = "/"),
      .groups = "drop"
    )

  cat("\nWeek breakdown:\n")
  print(week_counts)
}

# Final summary
cat("\n============================================\n")
cat("BATCH ANALYSIS COMPLETE\n")
cat("============================================\n")
cat("Year:", year, "\n")
cat("Weeks processed:", start_week, "-", end_week, "\n")
cat("Successful runs:", successful_runs, "/", total_weeks, "\n")
cat("Failed runs:", failed_runs, "/", total_weeks, "\n")
cat("Output directory:", output_dir, "\n")

if (successful_runs < total_weeks) {
  cat("\nWARNING: Some weeks failed. Check the output above for details.\n")
}

if (combine_outputs && length(all_raw_files) > 0) {
  cat("\nCombined raw data file created with", length(all_raw_files), "weeks of data.\n")
}

cat("\n✓ Batch processing complete!\n\n")