#!/usr/bin/env Rscript
# Setup: Install required packages for NBA Elo rankings

cat("=== Installing Required Packages ===\n\n")

# List of required packages
required_packages <- c("hoopR", "tidyverse", "lubridate")

# Function to check and install packages
install_if_missing <- function(package) {
  if (!require(package, character.only = TRUE, quietly = TRUE)) {
    cat("Installing", package, "...\n")
    install.packages(package, repos = "https://cloud.r-project.org/")
    library(package, character.only = TRUE)
    cat("✓", package, "installed successfully\n\n")
  } else {
    cat("✓", package, "already installed\n")
  }
}

# Install all required packages
for (pkg in required_packages) {
  install_if_missing(pkg)
}

cat("\n=== Setup Complete ===\n")
cat("All required packages are installed.\n")
