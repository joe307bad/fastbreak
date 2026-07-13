FROM --platform=linux/amd64 rocker/tidyverse:latest

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
# hockeyR is GitHub-only (default branch: master). Download the tarball with curl
# so we avoid both GitHub API rate limits and R download.file redirect failures.
RUN curl -fsSL -o /tmp/hockeyR.tar.gz \
      https://codeload.github.com/danmorse314/hockeyR/tar.gz/refs/heads/master && \
    R -e "options(repos = c(CRAN = 'https://packagemanager.posit.co/cran/__linux__/jammy/latest')); \
    install.packages(c('rlang', 'vctrs', 'lifecycle')); \
    install.packages(c('nflreadr', 'hoopR', 'baseballr', 'remotes')); \
    remotes::install_local('/tmp/hockeyR.tar.gz', dependencies = TRUE); \
    if (!require('nflreadr')) stop('nflreadr installation failed'); \
    if (!require('hoopR')) stop('hoopR installation failed'); \
    if (!require('baseballr')) stop('baseballr installation failed'); \
    if (!require('hockeyR')) stop('hockeyR installation failed')" && \
    rm -f /tmp/hockeyR.tar.gz

# Create app directory
WORKDIR /app

# Copy R scripts and data
COPY startup /app/startup
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
