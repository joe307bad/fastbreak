#!/usr/bin/env Rscript

# Install required packages for the defensive stats analysis
cat("Installing required R packages...\n")

# Check if packages are installed, install if not
packages <- c("nflfastR", "dplyr", "tidyr", "readr", "gt", "webshot2", "scales", "ffanalytics")

for (pkg in packages) {
  if (!require(pkg, character.only = TRUE, quietly = TRUE)) {
    cat("Installing", pkg, "...\n")
    install.packages(pkg, repos = "https://cloud.r-project.org/", quiet = TRUE)
  } else {
    cat(pkg, "is already installed\n")
  }
}

cat("\nAll required packages installed successfully!\n")