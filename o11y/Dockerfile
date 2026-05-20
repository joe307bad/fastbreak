# OpenTelemetry Collector for Fastbreak mobile telemetry
# Receives traces/events from mobile apps and forwards to SigNoz Cloud

FROM otel/opentelemetry-collector-contrib:0.96.0

# Copy the collector configuration
COPY otel-collector-config.yaml /etc/otelcol-contrib/config.yaml

# Expose OTLP gRPC and HTTP ports
EXPOSE 4317 4318

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:13133/health || exit 1

CMD ["--config=/etc/otelcol-contrib/config.yaml"]
