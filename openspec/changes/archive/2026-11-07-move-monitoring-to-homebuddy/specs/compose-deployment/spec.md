## ADDED Requirements

### Requirement: Metrics targets join the central monitoring network
`api-gateway`, `parser-service`, and `kafka-exporter` SHALL join the external, internal-only network `finanzbuddy-metrics` defined by the root capability `homebuddy-monitoring`, so the central Prometheus can scrape them by container name. The stack SHALL NOT run its own Prometheus or Grafana, and SHALL NOT publish host ports for monitoring. The README SHALL list the `docker network create --internal finanzbuddy-metrics` command.

#### Scenario: Central Prometheus scrapes the stack
- **WHEN** the central Prometheus connects to `finanzbuddy-api-gateway:8080`, `finanzbuddy-parser-service:8080`, or `finanzbuddy-kafka-exporter:9308`
- **THEN** the connection succeeds over `finanzbuddy-metrics`

#### Scenario: No local monitoring services
- **WHEN** the finanzBuddy stack is running
- **THEN** no `finanzbuddy-prometheus` or `finanzbuddy-grafana` container exists

#### Scenario: Missing metrics network
- **WHEN** the stack starts on a host where `finanzbuddy-metrics` does not exist
- **THEN** startup fails with a message naming the network

### Requirement: The UI proxy does not serve metrics endpoints
The UI SHALL NOT serve the gateway's `/actuator` paths through its `/api/` proxy, including variants that normalize to them such as `/api/auth/../actuator/prometheus`, as required by the root capability `homebuddy-monitoring`.

#### Scenario: Metrics through the UI origin
- **WHEN** a browser requests `/api/actuator/prometheus` or `/api/auth/../actuator/prometheus` on the finanzBuddy origin
- **THEN** the response is 404 and no metrics are returned

## REMOVED Requirements

### Requirement: Run monitoring services within the Compose deployment
**Reason**: Prometheus and Grafana are now one central stack owned by the root capability `homebuddy-monitoring`, shared by every homeBuddy service.
**Migration**: Use the root stack: Grafana at `http://localhost:3001` and Prometheus at `http://localhost:9090` locally, and the portal on `MONITORING_DOMAIN` in production. The Kafka exporter stays here and joins `finanzbuddy-metrics`.
