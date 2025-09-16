#!/usr/bin/env Rscript

# Load required libraries
suppressPackageStartupMessages({
  library(rvest)
  library(httr)
  library(dplyr)
  library(stringr)
  library(jsonlite)
})

# Get command line arguments
args <- commandArgs(trailingOnly = TRUE)

# Set year (default to current year if not provided)
if (length(args) > 0) {
  year <- as.integer(args[1])
} else {
  year <- 2024
}

# Global rate limiting tracker
# We'll limit to 10 requests per minute to be safe (Sports Reference allows 20)
rate_limiter <- new.env()
rate_limiter$last_request_time <- NULL
rate_limiter$request_count <- 0
rate_limiter$max_requests_per_minute <- 10
rate_limiter$base_delay <- 60 / 10  # 6 seconds base delay

# Function to check and enforce rate limits
check_rate_limit <- function() {
  current_time <- Sys.time()
  
  if (!is.null(rate_limiter$last_request_time)) {
    # Calculate time since last request
    time_since_last <- as.numeric(difftime(current_time, rate_limiter$last_request_time, units = "secs"))
    
    # Add random 2-3 seconds to base delay (6 + 2-3 = 8-9 seconds total)
    random_extra <- runif(1, min = 2, max = 3)
    required_delay <- rate_limiter$base_delay + random_extra
    
    # If less than required delay, wait
    if (time_since_last < required_delay) {
      wait_time <- required_delay - time_since_last
      cat(sprintf("Rate limiting: waiting %.1f seconds before next request...\n", wait_time))
      Sys.sleep(wait_time)
    }
  }
  
  # Update last request time and increment counter
  rate_limiter$last_request_time <- Sys.time()
  rate_limiter$request_count <- rate_limiter$request_count + 1
}

# Rate limiting function with exponential backoff
rate_limit_delay <- function(min_seconds = 2, max_seconds = 4, attempt = 1) {
  # Exponential backoff for retries
  base_delay <- runif(1, min_seconds, max_seconds)
  backoff_delay <- base_delay * (2 ^ (attempt - 1))
  
  # Cap maximum delay at 30 seconds
  final_delay <- min(backoff_delay, 30)
  
  cat(sprintf("Rate limiting: waiting %.1f seconds (attempt %d)...\n", final_delay, attempt))
  Sys.sleep(final_delay)
}

# Function to clean player names for URL
clean_name_for_url <- function(name) {
  # Remove Jr., Sr., III, etc.
  name <- gsub(" Jr\\.?| Sr\\.?| III$| II$| IV$| V$", "", name)
  # Handle special characters
  name <- gsub("'", "", name)
  name <- gsub("-", " ", name)
  return(trimws(name))
}

# Function to parse Sports Reference player page
parse_sports_ref_page <- function(url) {
  tryCatch({
    page <- read_html(url)
    
    # Try to find games played in the statistics table
    # Look for career totals row - try passing_standard first
    stats_table <- page %>%
      html_node("#passing_standard") %>%
      html_table(fill = TRUE)
    
    if (!is.null(stats_table)) {
      # Look for Career row specifically
      career_rows <- which(grepl("^Career", stats_table$Season, ignore.case = TRUE))
      
      if (length(career_rows) > 0) {
        career_row <- stats_table[career_rows[1], ]
        # Try to find Games column (G)
        if ("G" %in% names(career_row)) {
          games <- as.numeric(career_row$G)
          if (!is.na(games) && games > 0) {
            return(games)
          }
        }
      }
      
      # Alternative: sum up all individual season rows (exclude Career and school total rows)
      if ("G" %in% names(stats_table)) {
        # Filter for rows with actual season data (usually start with year)
        season_rows <- grepl("^[0-9]{4}", stats_table$Season)
        if (any(season_rows)) {
          game_col <- suppressWarnings(as.numeric(stats_table$G[season_rows]))
          total_games <- sum(game_col, na.rm = TRUE)
          if (total_games > 0) {
            return(total_games)
          }
        }
      }
    }
    
    return(NA)
  }, error = function(e) {
    return(NA)
  })
}

# Function to search for player on Sports Reference with retry logic
search_sports_reference <- function(player_name, max_retries = 3) {
  clean_name <- clean_name_for_url(player_name)
  search_url <- sprintf("https://www.sports-reference.com/cfb/search/search.fcgi?search=%s", 
                       URLencode(clean_name))
  
  for (attempt in 1:max_retries) {
    tryCatch({
      # Check rate limit before making request
      check_rate_limit()
      
      # Add user agent to avoid blocking
      response <- GET(
        search_url,
        user_agent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"),
        timeout(10)
      )
      
      # Handle rate limiting responses
      if (status_code(response) == 429) {
        cat(sprintf("Rate limited on attempt %d for %s\n", attempt, player_name))
        cat(sprintf("HTTP Status: %d\n", status_code(response)))
        cat(sprintf("Response Headers:\n"))
        print(headers(response))
        cat(sprintf("Response Body:\n%s\n", content(response, "text")))
        cat(sprintf("Backing off...\n"))
        rate_limit_delay(min_seconds = 5, max_seconds = 10, attempt = attempt)
        next
      }
      
      # Handle server errors with backoff
      if (status_code(response) >= 500) {
        cat(sprintf("Server error %d on attempt %d for %s. Backing off...\n", 
                   status_code(response), attempt, player_name))
        rate_limit_delay(min_seconds = 3, max_seconds = 6, attempt = attempt)
        next
      }
    
    if (status_code(response) == 200) {
      content <- content(response, "text")
      
      # Check if we got redirected directly to a player page
      if (grepl("/cfb/players/", response$url)) {
        # Direct match found
        starts <- parse_sports_ref_page(response$url)
        
        # Extract college from the page
        page <- read_html(content)
        college <- tryCatch({
          # Try to get from the meta info
          meta_text <- page %>%
            html_nodes("div#meta p") %>%
            html_text()
          
          school_line <- meta_text[grepl("School", meta_text)]
          if (length(school_line) > 0) {
            # Extract school name(s) from the line, cleaning up whitespace and newlines
            school_text <- gsub(".*School[s]?:\\s*", "", school_line[1])
            school_text <- gsub("\n|\t", " ", school_text)
            school_text <- gsub("\\s+", " ", school_text)
            school_text <- trimws(school_text)
            if (nchar(school_text) > 0) {
              school_text
            } else {
              NA
            }
          } else {
            NA
          }
          
          # Alternative: get from the team column in stats table
          stats_table <- page %>%
            html_node("#passing_standard") %>%
            html_table(fill = TRUE)
          
          if (!is.null(stats_table) && "Team" %in% names(stats_table)) {
            # Get unique teams, excluding empty and Career rows
            teams <- unique(stats_table$Team[stats_table$Team != "" & 
                                            !grepl("Career", stats_table$Team)])
            if (length(teams) > 0) {
              paste(teams, collapse = ", ")
            } else {
              NA
            }
          } else {
            NA
          }
        }, error = function(e) NA)
        
        return(list(
          found = TRUE,
          starts = starts,
          college = college,
          url = response$url
        ))
      }
      
      # Parse search results
      page <- read_html(content)
      
      # Look for search results
      search_results <- page %>%
        html_nodes("div.search-item")
      
      if (length(search_results) > 0) {
        # Get first result link
        first_link <- search_results[1] %>%
          html_node("a") %>%
          html_attr("href")
        
        if (!is.null(first_link)) {
          player_url <- paste0("https://www.sports-reference.com", first_link)
          
          # Check rate limit before second request
          check_rate_limit()
          
          # Get player page
          player_response <- GET(
            player_url,
            user_agent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"),
            timeout(10)
          )
          
          if (status_code(player_response) == 200) {
            starts <- parse_sports_ref_page(player_url)
            
            # Extract college
            player_page <- read_html(content(player_response, "text"))
            college <- tryCatch({
              # Try to get from the meta info
              meta_text <- player_page %>%
                html_nodes("div#meta p") %>%
                html_text()
              
              school_line <- meta_text[grepl("School", meta_text)]
              if (length(school_line) > 0) {
                # Extract school name(s) from the line, cleaning up whitespace and newlines
                school_text <- gsub(".*School[s]?:\\s*", "", school_line[1])
                school_text <- gsub("\n|\t", " ", school_text)
                school_text <- gsub("\\s+", " ", school_text)
                school_text <- trimws(school_text)
                if (nchar(school_text) > 0) {
                  school_text
                } else {
                  NA
                }
              } else {
                NA
              }
              
              # Alternative: get from the team column in stats table
              stats_table <- player_page %>%
                html_node("#passing_standard") %>%
                html_table(fill = TRUE)
              
              if (!is.null(stats_table) && "Team" %in% names(stats_table)) {
                # Get unique teams, excluding empty and Career rows
                teams <- unique(stats_table$Team[stats_table$Team != "" & 
                                                !grepl("Career", stats_table$Team)])
                if (length(teams) > 0) {
                  paste(teams, collapse = ", ")
                } else {
                  NA
                }
              } else {
                NA
              }
            }, error = function(e) NA)
            
            return(list(
              found = TRUE,
              starts = starts,
              college = college,
              url = player_url
            ))
          }
        }
      }
      
      # If we get here, no results were found
      return(list(found = FALSE, starts = NA, college = NA))
    }
      
    }, error = function(e) {
      cat(sprintf("Error on attempt %d for %s: %s\n", attempt, player_name, e$message))
      if (attempt < max_retries) {
        rate_limit_delay(min_seconds = 2, max_seconds = 4, attempt = attempt)
        next
      }
    })
  }
  
  # All retries failed
  cat(sprintf("All %d attempts failed for %s\n", max_retries, player_name))
  return(list(found = FALSE, starts = NA, college = NA))
}

# Helper function to process search response
process_search_response <- function(response, player_name) {
  content <- content(response, "text")
  
  # Check if we got redirected directly to a player page
  if (grepl("/cfb/players/", response$url)) {
    # Direct match found
    starts <- parse_sports_ref_page(response$url)
    
    # Extract college from the page
    page <- read_html(content)
    college <- tryCatch({
      # Try to get from the meta info
      meta_text <- page %>%
        html_nodes("div#meta p") %>%
        html_text()
      
      school_line <- meta_text[grepl("School", meta_text)]
      if (length(school_line) > 0) {
        # Extract school name(s) from the line
        school_text <- gsub(".*School[s]?:\\s*", "", school_line[1])
        return(trimws(school_text))
      }
      
      # Alternative: get from the team column in stats table
      stats_table <- page %>%
        html_node("#passing_standard") %>%
        html_table(fill = TRUE)
      
      if (!is.null(stats_table) && "Team" %in% names(stats_table)) {
        # Get unique teams, excluding empty and Career rows
        teams <- unique(stats_table$Team[stats_table$Team != "" & 
                                        !grepl("Career", stats_table$Team)])
        if (length(teams) > 0) {
          return(paste(teams, collapse = ", "))
        }
      }
      
      return(NA)
    }, error = function(e) NA)
    
    return(list(
      found = TRUE,
      starts = starts,
      college = college,
      url = response$url
    ))
  }
  
  # Parse search results
  page <- read_html(content)
  
  # Look for search results
  search_results <- page %>%
    html_nodes("div.search-item")
  
  if (length(search_results) > 0) {
    # Get first result link
    first_link <- search_results[1] %>%
      html_node("a") %>%
      html_attr("href")
    
    if (!is.null(first_link)) {
      player_url <- paste0("https://www.sports-reference.com", first_link)
      
      # Check rate limit before second request
      check_rate_limit()
      
      # Get player page
      player_response <- GET(
        player_url,
        user_agent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"),
        timeout(10)
      )
      
      if (status_code(player_response) == 200) {
        starts <- parse_sports_ref_page(player_url)
        
        # Extract college
        player_page <- read_html(content(player_response, "text"))
        college <- tryCatch({
          # Try to get from the meta info
          meta_text <- player_page %>%
            html_nodes("div#meta p") %>%
            html_text()
          
          school_line <- meta_text[grepl("School", meta_text)]
          if (length(school_line) > 0) {
            # Extract school name(s) from the line
            school_text <- gsub(".*School[s]?:\\s*", "", school_line[1])
            return(trimws(school_text))
          }
          
          # Alternative: get from the team column in stats table
          stats_table <- player_page %>%
            html_node("#passing_standard") %>%
            html_table(fill = TRUE)
          
          if (!is.null(stats_table) && "Team" %in% names(stats_table)) {
            # Get unique teams, excluding empty and Career rows
            teams <- unique(stats_table$Team[stats_table$Team != "" & 
                                            !grepl("Career", stats_table$Team)])
            if (length(teams) > 0) {
              return(paste(teams, collapse = ", "))
            }
          }
          
          return(NA)
        }, error = function(e) NA)
        
        return(list(
          found = TRUE,
          starts = starts,
          college = college,
          url = player_url
        ))
      }
    }
  }
  
  return(list(found = FALSE, starts = NA, college = NA))
}

# Function to scrape college starts with safeguards
scrape_college_starts <- function(player_name) {
  cat(sprintf("Searching for %s...\n", player_name))
  
  # Try Sports Reference scraping
  result <- search_sports_reference(player_name)
  
  if (result$found) {
    cat(sprintf("Found data for %s: %s starts at %s\n", 
                player_name, 
                ifelse(is.na(result$starts), "Unknown", result$starts),
                ifelse(is.na(result$college), "Unknown", result$college)))
    return(list(
      player = player_name,
      college = result$college,
      starts = result$starts,
      source = "sports_ref"
    ))
  }
  
  # Return NA if nothing found
  cat(sprintf("No data found for %s\n", player_name))
  return(list(
    player = player_name,
    college = NA,
    starts = NA,
    source = "not_found"
  ))
}

# Main function to process all QBs
process_qbs_for_year <- function(year) {
  cat(sprintf("\n=== Processing College Starts for %d Starting QBs ===\n\n", year))
  
  # Load the QB data from previous script
  qb_file <- sprintf("starting_qbs_%d.csv", year)
  
  if (!file.exists(qb_file)) {
    cat(sprintf("Error: File %s not found. Run get_starting_qbs.R first.\n", qb_file))
    return(NULL)
  }
  
  # Read QB data
  qbs <- read.csv(qb_file, stringsAsFactors = FALSE)
  
  # Initialize results
  results <- data.frame(
    Team = character(),
    Starting_QB = character(),
    College = character(),
    College_Starts = integer(),
    Source = character(),
    stringsAsFactors = FALSE
  )
  
  # Process each QB with rate limiting
  total_qbs <- nrow(qbs)
  
  for (i in 1:total_qbs) {
    qb_name <- qbs$Starting_QB[i]
    team <- qbs$Team[i]
    
    # Show progress and request count
    cat(sprintf("[%d/%d] Processing %s (%s) - Total requests: %d\n", 
                i, total_qbs, qb_name, team, rate_limiter$request_count))
    
    # Get college starts - no cache/fallback data
    qb_data <- scrape_college_starts(qb_name)
    
    # Add to results
    results <- rbind(results, data.frame(
      Team = team,
      Starting_QB = qb_name,
      College = ifelse(is.na(qb_data$college), NA, qb_data$college),
      College_Starts = ifelse(is.na(qb_data$starts), NA, qb_data$starts),
      Source = qb_data$source,
      stringsAsFactors = FALSE
    ))
    
    # Progress update
    if (i %% 5 == 0) {
      cat(sprintf("Progress: %d/%d QBs processed (%.1f%%)\n", 
                  i, total_qbs, (i/total_qbs)*100))
    }
  }
  
  return(results)
}

# Save results function
save_college_starts <- function(results, year) {
  # Save as CSV
  csv_filename <- sprintf("qb_college_starts_%d.csv", year)
  write.csv(results, csv_filename, row.names = FALSE)
  cat(sprintf("\nResults saved to: %s\n", csv_filename))
  
  # Save as RDS
  rds_filename <- sprintf("qb_college_starts_%d.rds", year)
  saveRDS(results, rds_filename)
  cat(sprintf("R data saved to: %s\n", rds_filename))
  
  # Create summary
  cat("\n=== SUMMARY ===\n")
  cat(sprintf("Total QBs processed: %d\n", nrow(results)))
  cat(sprintf("QBs with college data found: %d\n", 
              sum(results$College_Starts > 0)))
  cat(sprintf("Average college starts: %.1f\n", 
              mean(results$College_Starts[results$College_Starts > 0], na.rm = TRUE)))
  
  # Top 5 by college starts
  cat("\nTop 5 QBs by College Starts:\n")
  top5 <- results %>%
    arrange(desc(College_Starts)) %>%
    head(5) %>%
    select(Starting_QB, College, College_Starts)
  
  for (i in 1:nrow(top5)) {
    cat(sprintf("%d. %s (%s) - %d starts\n", 
                i, top5$Starting_QB[i], top5$College[i], top5$College_Starts[i]))
  }
  
  # Bottom 5 by college starts (excluding 0s)
  cat("\nQBs with Fewest College Starts (excluding unknowns):\n")
  bottom5 <- results %>%
    filter(College_Starts > 0) %>%
    arrange(College_Starts) %>%
    head(5) %>%
    select(Starting_QB, College, College_Starts)
  
  for (i in 1:nrow(bottom5)) {
    cat(sprintf("%d. %s (%s) - %d starts\n", 
                i, bottom5$Starting_QB[i], bottom5$College[i], bottom5$College_Starts[i]))
  }
}

# Main execution
main <- function() {
  # Process QBs
  results <- process_qbs_for_year(year)
  
  if (!is.null(results)) {
    # Save and display results
    save_college_starts(results, year)
    
    # Display full table
    cat("\n=== FULL RESULTS ===\n")
    print(results %>% 
            select(Team, Starting_QB, College, College_Starts) %>%
            arrange(desc(College_Starts)))
  }
}

# Run the main function
main()