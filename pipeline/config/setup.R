# Setup script for fastbreak pipeline
# Run this first to install all required packages

# Set CRAN mirror to avoid mirror selection error
options(repos = c(CRAN = "https://cloud.r-project.org/"))

# List of required packages
required_packages <- c(
  "baseballr",      # Baseball data from various APIs
  "tidyverse",      # Data manipulation and visualization
  "dplyr",          # Data manipulation
  "readr",          # Reading/writing CSV files
  "lubridate",      # Date/time handling
  "httr",           # HTTP requests for weather APIs
  "jsonlite",       # JSON parsing
  "glue"            # String interpolation
)

# Function to install packages if they don't exist
install_if_missing <- function(packages) {
  new_packages <- packages[!(packages %in% installed.packages()[, "Package"])]
  if (length(new_packages)) {
    cat("Installing missing packages:", paste(new_packages, collapse = ", "), "\n")
    install.packages(new_packages, dependencies = TRUE)
  } else {
    cat("All required packages are already installed.\n")
  }
}

# Install missing packages
install_if_missing(required_packages)

# Load all required libraries
cat("Loading required libraries...\n")
library(baseballr)
library(tidyverse)
library(dplyr)
library(readr)
library(lubridate)
library(httr)
library(jsonlite)
library(glue)

cat("Setup complete! All packages loaded successfully.\n")