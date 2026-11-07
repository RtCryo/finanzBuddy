# Design

## Context

See proposal.md. finanzBuddy's `docker-compose.yml` runs `prometheus` and `grafana` on its default network and publishes them locally on 9090 and 3001. Prometheus scrapes `api-gateway:8080`, `parser-service:8080`, and `kafka-exporter:9308` by service name. The root design, including the scrape convention and the metrics networks, is in [`add-homebuddy-monitoring`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-monitoring/design.md).

## Decisions

- **Delete rather than disable.** The two services, their volumes, `monitoring/`, and the local port mappings are removed. A second Prometheus or Grafana in a service stack is forbidden by the root capability.
- **Three targets join `finanzbuddy-metrics`.** `api-gateway`, `parser-service`, and `kafka-exporter` are attached next to the default network, declared `external: true` with an explicit name like the identity network. The central Prometheus reaches them by container name, which already carries the `finanzbuddy-` prefix.
- **Dashboards move unchanged in content.** They are already in the root repository with only the job selectors renamed; this change deletes finanzBuddy's copy so there is one source.
- **Close the UI proxy path to the gateway's metrics.** The gateway permits `/actuator/prometheus` without a token, and the UI's `/api/` proxy strips the prefix, so `/api/actuator/prometheus` reached it from browsers. The root contract forbids this, so nginx returns 404 for `/api/actuator`.
- **Specs.** The monitoring-stack requirement of `compose-deployment` and the two stack requirements of `application-observability` are removed with a pointer to the root capability. A new `compose-deployment` requirement covers `finanzbuddy-metrics`. The application-metrics requirement stays because the application still owns what it exposes.

## Risks / Trade-offs

- A standalone finanzBuddy deployment without the root stack has no monitoring. Documented; standalone deployments already depend on root contracts.
- 15 days of metric history and Grafana preferences are lost. Accepted; dashboards are provisioned.
- Anything that relied on reading metrics through the UI origin stops working; the central Prometheus uses `finanzbuddy-metrics` instead.
- Renaming a metric now requires an umbrella change that also updates the root dashboard.

## Migration Plan

1. Create `finanzbuddy-metrics` on the host and make sure the root monitoring stack is available.
2. Deploy this change: the old Prometheus and Grafana stop, the three targets join `finanzbuddy-metrics`, and the central Prometheus reports them up.
3. Start the root stack locally if it was waiting for ports 3001 and 9090.
4. Remove the old `finanzbuddy_prometheus-data` and `finanzbuddy_grafana-data` volumes after verification.

Rollback: redeploy the previous compose file to restore the own monitoring and stop the root Prometheus and Grafana.
