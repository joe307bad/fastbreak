FROM grafana/grafana:11.4.0

USER root

# Override default paths to use persistent volume
ENV GF_PATHS_DATA=/data/grafana
ENV GF_PATHS_LOGS=/data/grafana/logs
ENV GF_PATHS_PLUGINS=/data/grafana/plugins

# Create directories with correct permissions
RUN mkdir -p /data/grafana/logs /data/grafana/plugins && \
    chown -R grafana:root /data/grafana

USER grafana
