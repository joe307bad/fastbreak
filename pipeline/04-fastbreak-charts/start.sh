#!/bin/bash

echo "Starting R cron scheduler..."
echo "Output directory: /app/output"
echo ""

# Install R packages if not already installed
echo "Checking R packages..."
Rscript -e "if (!require('jsonlite', quietly = TRUE)) install.packages('jsonlite', repos='https://cran.rstudio.com/')"
Rscript -e "if (!require('dplyr', quietly = TRUE)) install.packages('dplyr', repos='https://cran.rstudio.com/')"
Rscript -e "if (!require('nflfastR', quietly = TRUE)) install.packages('nflfastR', repos='https://cran.rstudio.com/')"
echo "R packages ready."
echo ""

# Run all scripts immediately on startup
echo "Running daily scripts on startup..."
find /app/daily -name "*.R" -exec /usr/bin/Rscript {} \;

echo "Running weekly scripts on startup..."
find /app/weekly -name "*.R" -exec /usr/bin/Rscript {} \;

echo ""
echo "Startup scripts completed."
echo "Daily scripts will run every day at midnight"
echo "Weekly scripts will run every Sunday at midnight"
echo ""

# Start cron in the foreground
cron && tail -f /var/log/cron.log
