# Observability

Configuration of the observability stack ([ADR 0009](../../docs/deliverables/adr/0009-observability-stack.md)): Prometheus, Loki, Alloy and Grafana. It is empty until [#70](https://github.com/andrereis26/crowded-labdsof/issues/70) (structured logs, health checks, metrics and trace IDs) adds the services to [`docker-compose.swarm.yml`](../docker-compose.swarm.yml).

Planned layout, one folder per tool, each mounted read-only into its container as a Swarm config:

| Folder | Contents |
|---|---|
| `prometheus/` | `prometheus.yml` (scrape targets: the services' `/actuator/prometheus`, RabbitMQ) and alert rules |
| `loki/` | `loki.yml` |
| `alloy/` | `config.alloy`: container logs to Loki; host, PostgreSQL and Redis metrics to Prometheus |
| `grafana/` | data source and dashboard provisioning, and the dashboards as JSON |

Grafana is served by the router at `/grafana`, not on a port of its own.
