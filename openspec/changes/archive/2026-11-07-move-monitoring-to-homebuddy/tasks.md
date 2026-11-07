# Tasks

## 1. Compose

- [x] 1.1 In `docker-compose.yml`, remove `prometheus`, `grafana`, and the `prometheus-data` and `grafana-data` volumes, and attach `api-gateway`, `parser-service`, and `kafka-exporter` to the external `finanzbuddy-metrics` network next to the default network; update `docker-compose.override.yml` to drop the 3001 and 9090 mappings. Verify with `docker compose config` that no `prometheus` or `grafana` service remains and that the three services list `finanzbuddy-metrics`.
- [x] 1.2 Delete `monitoring/`. Verify that the root `monitoring/grafana/dashboards/finanzbuddy/` still holds the five dashboards.
- [x] 1.3 Make the UI `nginx.conf` return 404 for `/api/actuator` so the gateway's permitted `/actuator/prometheus` is not served to browsers. Verify with requests for `/api/actuator/prometheus` and `/api/auth/../actuator/prometheus` on `localhost:3000`.
- [x] 1.4 Verify that starting the stack without `finanzbuddy-metrics` fails with a message naming the network.

## 2. Documentation

- [x] 2.1 Update `README.md`, `docs/README.md`, `docs/ARCHITECTURE.md`, `api-gateway/README.md`, and `parser-service/README.md`: monitoring runs in the root stack, ports 3001 and 9090 belong to it, and `docker network create --internal finanzbuddy-metrics` is a prerequisite. Verify that no document describes a finanzBuddy Prometheus or Grafana.
- [x] 2.2 Update the CI Compose smoke test if it starts or waits for the removed services. Verify that the workflows contain no reference to them.

## 3. Verification

- [x] 3.1 With the root stack running, verify that `finanzbuddy-prometheus` and `finanzbuddy-grafana` no longer exist, that `finanzbuddy-api-gateway`, `finanzbuddy-parser-service`, and `finanzbuddy-kafka-exporter` are up in the central Prometheus, and that an upload and parse fill the dashboards in the finanzBuddy folder.
- [x] 3.2 After verification, remove the old `finanzbuddy_prometheus-data` and `finanzbuddy_grafana-data` volumes and note the step in the README.
