FROM rocker/tidyverse:latest

# Set timezone to Eastern Time
ENV TZ=America/New_York
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Install cron and AWS CLI
RUN apt-get update && \
    apt-get install -y cron python3-pip tzdata && \
    pip3 install awscli --break-system-packages && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install remaining R packages (tidyverse deps already installed)
# Update rlang first (base image has older version)
# hockeyR must be installed from GitHub for latest NHL API compatibility
RUN R -e "options(repos = c(CRAN = 'https://packagemanager.posit.co/cran/__linux__/jammy/latest')); \
    install.packages(c('rlang', 'vctrs', 'lifecycle')); \
    install.packages(c('nflreadr', 'hoopR', 'devtools')); \
    devtools::install_github('danmorse314/hockeyR'); \
    if (!require('nflreadr')) stop('nflreadr installation failed'); \
    if (!require('hoopR')) stop('hoopR installation failed'); \
    if (!require('hockeyR')) stop('hockeyR installation failed')"

# Create app directory
WORKDIR /app

# Copy R scripts and data
COPY weekly /app/weekly
COPY daily /app/daily
COPY utils /app/utils
COPY data /app/data

# Copy Fastbreak.Daily executable (built by CI)
COPY Fastbreak.Daily /app/Fastbreak.Daily
RUN chmod +x /app/Fastbreak.Daily

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
