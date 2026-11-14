# Observability

The observability stack ([ADR 0009](../../docs/deliverables/adr/0009-observability-stack.md)): Prometheus, Loki, Alloy and Grafana. [`docker-compose.observability.yml`](docker-compose.observability.yml) adds them to [`docker-compose.swarm.yml`](../docker-compose.swarm.yml): `remote-deploy.sh` deploys both on every release, and `scripts/local/stack-up.sh --with-observability` adds it to the local stack. Each file below is mounted read-only as a Swarm config, named after a hash of this folder.

| Path | Contents |
|---|---|
| `prometheus/prometheus.yml` | Scrape targets: every replica's `/actuator/prometheus` (Core, Jobs, Notifications) and RabbitMQ's per-queue counts |
| `loki/loki.yml` | One Loki process on the local disk; logs are kept 14 days |
| `alloy/config.alloy` | Every container of the stack to Loki, labelled `service` and `stream`, and `level` for the services' JSON lines |
| `grafana/datasources.yml` | Prometheus and Loki; a `traceId` in a log line links to every line of that trace |

Grafana is served by the router at `/grafana/`, with its own sign-in: `admin` and `GRAFANA_ADMIN_PASSWORD` (from Doppler, or `.env` locally). Prometheus keeps 15 days of metrics, at most 4 GB. The dashboards themselves come later, as JSON provisioned from `grafana/`.

## What the services give

- **Health:** `/actuator/health` reports the service and each dependency (`db`, `rabbit`, `redis`) as `UP` or `DOWN`, without details. Liveness and readiness are what the container health check calls (ADR 0011).
- **Logs:** one JSON object per line (Elastic Common Schema), with the `traceId` and `spanId` of the request or message. The trace starts at the first service a request reaches, or continues the `traceparent` header the request carries, and travels with every RabbitMQ message: Core's outbox stores it with the message, and Notifications continues it. Searching `{service=~".+"} |= "<traceId>"` in Loki shows one request across services. Unhandled errors are logged at `ERROR` with it: a request's, by the last-resort handler, and a message's, when it is moved to the dead-letter queue.
- **Metrics:**

  | Metric | Meaning |
  |---|---|
  | `http_server_requests_seconds` | Request latency (histogram), by `uri`, `method`, `status`, `outcome` and `exception`: the error counts are its `outcome="SERVER_ERROR"` and `status="4xx"` series |
  | `logback_events_total{level="error"}` | Errors logged |
  | `crowded_notification_latency_seconds` | Notifications: from the change's commit in Core until its notifications are stored |
  | `crowded_outbox_lag_seconds` | Core: from the change's commit until RabbitMQ confirmed its message |
  | `crowded_outbox_waiting_seconds` | Core: how long the oldest unconfirmed message has waited, such as while RabbitMQ is down |
  | `rabbitmq_detailed_queue_messages` | RabbitMQ: messages per queue, `notifications.dlq` included |

## Trying it locally

```bash
scripts/local/stack-up.sh --with-observability
```

Then open `http://localhost:8080/grafana/`, *Explore*, and pick Loki (`{service="core"}`) or Prometheus (`crowded_notification_latency_seconds_count`). `scripts/local/stack-down.sh` removes it with its data.
