# ADR 0009: Observability with Prometheus, Loki, Alloy and Grafana

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

The deployed system must provide structured logs, metrics, error reporting and correlation IDs (NFR-010), health checks (NFR-011) and a dashboard with service-level indicators such as notification latency, sync lag and failed jobs (NFR-012). Everything runs on one small instance.

## Options considered

1. **Prometheus + Grafana only**: metrics, but no central log search.
2. **Prometheus + Loki + Alloy + Grafana**: metrics and logs in one Grafana; Alloy also replaces the separate host, Postgres and Redis exporters.
3. **Add Tempo for distributed tracing**: full traces, but one more service and more memory.
4. **ELK stack (Elasticsearch, Logstash, Kibana)**: powerful log search, but far too heavy for one small instance.
5. **Hosted service (e.g. Grafana Cloud free tier)**: nothing to run, but data leaves our infrastructure and is subject to free-tier limits.

## Decision

Option 2. Prometheus scrapes the services and RabbitMQ; Alloy ships container logs to Loki and pushes host, Postgres and Redis metrics to Prometheus; Grafana shows both behind NGINX. Alloy is used instead of Promtail, which reached end of life in March 2026. Team has experience with these tools.

## Consequences

- One request can be followed across services by searching its correlation ID in Loki.
- Four extra containers on a small instance;
