FROM rocker/r-base:latest

# Install cron and system dependencies for R packages
RUN apt-get update && \
    apt-get install -y \
    cron \
    libcurl4-openssl-dev \
    libssl-dev \
    libxml2-dev \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install R packages using Posit PPM for faster binary installs
RUN R -e "options(repos = c(CRAN = 'https://packagemanager.posit.co/cran/latest')); \
    install.packages(c('jsonlite', 'dplyr', 'nflfastR')); \
    if (!require('nflfastR')) stop('nflfastR installation failed')"

# Create app directory and output directory
WORKDIR /app
RUN mkdir -p /app/output /app/weekly /app/daily /app/data

# Scripts will be mounted as volumes at runtime

# Copy registry and static files to data directory
COPY registry.json /app/data/registry.json

# Copy cron configuration and startup script
COPY crontab /etc/cron.d/r-cron
COPY start.sh /app/start.sh

# Give execution rights
RUN chmod 0644 /etc/cron.d/r-cron && \
    chmod +x /app/start.sh

# Apply cron job
RUN crontab /etc/cron.d/r-cron

# Create cron log file
RUN touch /var/log/cron.log

CMD ["/app/start.sh"]
