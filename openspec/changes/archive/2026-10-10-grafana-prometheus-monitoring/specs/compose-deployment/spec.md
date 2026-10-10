# Spec Delta

## ADDED Requirements

### Requirement: Run monitoring services within the Compose deployment
The Docker Compose deployment SHALL include Prometheus and Grafana configured to monitor the API Gateway, parser service, and Kafka through a Kafka exporter on the internal Compose network. In local development, Grafana SHALL be reachable at `http://localhost:3001` and Prometheus SHALL be reachable at `http://localhost:9090`. In production, Prometheus, Grafana, and the Kafka exporter SHALL NOT publish host ports or be routed through Traefik.

#### Scenario: Developer starts the local Compose stack
- **WHEN** a developer starts the default local Compose workflow
- **THEN** Grafana is reachable at `http://localhost:3001`, Prometheus is reachable at `http://localhost:9090`, and both services can scrape the application services over the Compose network

#### Scenario: Kafka is monitored locally
- **WHEN** the local monitoring stack and Kafka broker are running
- **THEN** Prometheus scrapes the internal Kafka exporter and the Kafka dashboard shows broker, topic, partition, and consumer-group metrics

#### Scenario: Production Compose stack is running
- **WHEN** the production Compose workflow is running
- **THEN** Prometheus, Grafana, and the Kafka exporter are reachable to other services only over the internal Compose network and are not exposed through host ports or the public Traefik entry point
