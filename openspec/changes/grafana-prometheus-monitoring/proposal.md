# Proposal

## Why

The application exposes Prometheus scrape endpoints and some domain metrics, but the repository does not start or configure a Prometheus server or Grafana, so the Compose stack provides no ready-to-use dashboards. Adding a bundled monitoring stack will make service health and document-processing behavior visible during local development and deployment.

## What Changes

- Add Prometheus to Docker Compose with scrape targets for the API Gateway and parser service.
- Add Grafana with a provisioned Prometheus datasource and useful, version-controlled dashboards.
- Provide a main service-health dashboard and separate API Gateway, parser-service, and Kafka dashboards.
- Ensure dashboards surface service availability and uptime, HTTP behavior, document upload/processing outcomes and duration by bounded file type, parsed transaction volume, Kafka broker/topic/consumer lag signals, and the existing outbox backlog, age, and dead-letter metrics.
- Add bounded-cardinality application metrics where these views lack data and a pinned Kafka exporter for broker metrics.
- Keep monitoring services on the internal Compose network in production; publish local-development ports through the Compose override without routing them through Traefik.
- Document how to access the local dashboards and what operational signals they contain.

## Capabilities

### New Capabilities

- `application-observability`: Prometheus collection, Grafana dashboards, and application-level operational metrics.

### Modified Capabilities

- `compose-deployment`: Add monitoring services while preserving the production deployment's private-service boundary and local-development access conventions.

## Impact

- Docker Compose configuration and local port overrides.
- New Prometheus scrape and Grafana provisioning/dashboard files.
- A pinned Kafka exporter service on the internal Compose network.
- Spring Boot Actuator/Micrometer metrics in the API Gateway and parser service.
- Root and service monitoring documentation.
