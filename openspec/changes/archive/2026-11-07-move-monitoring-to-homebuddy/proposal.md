# Proposal

## Why

finanzBuddy runs its own Prometheus and Grafana, so only finanzBuddy is monitored and each service would need its own portal. The root change [`add-homebuddy-monitoring`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-monitoring/proposal.md) provides one central Prometheus and Grafana for every homeBuddy service, with one folder per service. finanzBuddy must hand its monitoring over to it.

## What Changes

- **BREAKING (deployment)**: Remove the `prometheus` and `grafana` services, the `prometheus-data` and `grafana-data` volumes, the local ports 3001 and 9090, and the `monitoring/` folder from finanzBuddy. The five dashboards already live in the root `monitoring/grafana/dashboards/finanzbuddy/`, switched to the new job names `finanzbuddy-api-gateway`, `finanzbuddy-parser-service`, and `finanzbuddy-kafka-exporter`. Existing Prometheus samples and Grafana user state are not migrated.
- `api-gateway`, `parser-service`, and `kafka-exporter` join the external, internal-only network `finanzbuddy-metrics`, so the central Prometheus can scrape them over container names (`finanzbuddy-api-gateway:8080`, `finanzbuddy-parser-service:8080`, `finanzbuddy-kafka-exporter:9308`).
- The `application-observability` requirements about the monitoring stack and dashboards move to the root capability `homebuddy-monitoring`; the requirement for privacy-preserving application metrics stays.
- Documentation (README, `docs/`) points to the root monitoring stack and lists the `docker network create --internal finanzbuddy-metrics` command.

The monitoring contract (networks, scrape and label conventions, Grafana portal) is the root capability `homebuddy-monitoring`; this change does not restate it.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `compose-deployment`: Monitoring services leave the stack; the scrape targets join `finanzbuddy-metrics`.
- `application-observability`: The collection and dashboard requirements move to the root capability.

## Non-goals

- Changing any metric name or label that the application exposes.
- Migrating old Prometheus samples or Grafana state. The old volumes stay until an operator removes them after verification.

## Impact

- `docker-compose.yml`, `docker-compose.override.yml`, removal of `monitoring/`, README, `docs/ARCHITECTURE.md`, `docs/README.md`, `api-gateway/README.md`, `parser-service/README.md`, and the two specs above.
- Roll out after the root monitoring stack exists and after the metrics networks are created. On a development machine this change frees ports 3001 and 9090 for the root stack.
- Operators remove `finanzbuddy_prometheus-data` and `finanzbuddy_grafana-data` after verification.
