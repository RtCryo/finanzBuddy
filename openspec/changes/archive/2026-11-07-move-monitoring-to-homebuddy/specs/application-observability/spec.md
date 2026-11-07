## REMOVED Requirements

### Requirement: Collect metrics from application services
**Reason**: Scraping and target availability are the responsibility of the central Prometheus defined by the root capability `homebuddy-monitoring`, which scrapes `finanzbuddy-api-gateway`, `finanzbuddy-parser-service`, and `finanzbuddy-kafka-exporter`.
**Migration**: None for the application. The metrics endpoints are unchanged; targets are reported through `up{service="finanzbuddy"}` in the central Prometheus.

### Requirement: Provide provisioned Grafana operational dashboards
**Reason**: Grafana and its provisioned dashboards, including the five finanzBuddy dashboards, are provided by the root capability `homebuddy-monitoring` in the `finanzBuddy` folder of the central Grafana.
**Migration**: Open the finanzBuddy folder in the central Grafana. The dashboards keep their uids and signals; their job selectors use the `finanzbuddy-` job names.
