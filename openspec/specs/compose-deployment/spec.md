# Compose Deployment Specification

## Purpose

This capability defines how the Docker Compose application is exposed to users through a configured public domain, including HTTPS, request routing, and the distinction between production access and local development.

## Requirements

### Requirement: Serve the production application over HTTPS
The production Compose deployment SHALL expose the application through the configured domain over HTTPS using certificates automatically obtained and renewed through ACME. The deployment SHALL redirect ordinary HTTP application requests to HTTPS.

#### Scenario: Domain is configured and reachable
- **WHEN** the configured domain resolves to the deployment host and ACME HTTP-01 validation can reach port 80
- **THEN** users can access the application over HTTPS with a valid certificate for that domain

#### Scenario: User opens the application over HTTP
- **WHEN** a user requests the application over HTTP
- **THEN** the request is redirected to the corresponding HTTPS URL

### Requirement: Route application requests through one public entry point
The production Compose deployment SHALL route requests for the configured application domain to the UI, and requests under `/api` SHALL continue to reach the API Gateway through the UI's existing proxy. The UI and API Gateway SHALL NOT publish their own host ports in the production deployment.

#### Scenario: User opens the application domain
- **WHEN** a user requests the application root over HTTPS
- **THEN** the UI is served through the reverse proxy

#### Scenario: UI sends an API request
- **WHEN** a client requests an application API path under `/api`
- **THEN** the request reaches the API Gateway and its existing API path behavior is preserved

#### Scenario: Production services are accessed
- **WHEN** the production Compose deployment is running
- **THEN** public application traffic enters through the reverse proxy rather than directly through published UI or API Gateway ports

### Requirement: Keep infrastructure services private in production
The production Compose deployment SHALL NOT publish host ports for internal infrastructure services (PostgreSQL, Kafka, and MinIO). These services SHALL be reachable only over the internal Compose network so that the reverse proxy on ports 80 and 443 is the only externally published entry point.

#### Scenario: Production stack is running
- **WHEN** the production Compose deployment is running
- **THEN** the PostgreSQL, Kafka, and MinIO services are not published on host ports and are reachable only by other containers on the internal network

#### Scenario: Application service reaches infrastructure
- **WHEN** the API Gateway or parser service connects to PostgreSQL, Kafka, or MinIO in production
- **THEN** the connection uses the internal Compose network hostnames without any published host port

### Requirement: Preserve local Compose development access
The local-development Compose workflow SHALL retain localhost access to the UI, API Gateway, and infrastructure services (PostgreSQL, Kafka, MinIO) without requiring a public DNS name or ACME certificate.

#### Scenario: Developer starts the local stack
- **WHEN** a developer starts the local-development Compose workflow
- **THEN** the UI, API Gateway, and infrastructure services remain reachable on their documented localhost ports without ACME configuration

### Requirement: Persist ACME certificate state
The production Compose deployment SHALL persist ACME certificate state across container recreation so that valid certificates are retained and renewal state is not discarded during routine restarts.

#### Scenario: Reverse proxy is recreated
- **WHEN** the Traefik container is recreated while its persistent storage remains intact
- **THEN** previously obtained certificate and ACME account state remain available to Traefik

### Requirement: Pin images to current supported versions
The Compose deployment SHALL pin its third-party service images and its Dockerfile base images to explicit tags that correspond to current, upstream-supported releases. Image version bumps SHALL be verified against a running stack before the change is considered complete, and SHALL NOT change application behavior other than picking up upstream fixes.

#### Scenario: Pinned image has a newer supported release
- **WHEN** a pinned image tag is behind the current upstream-supported release within a compatible version line
- **THEN** the pin is updated to the current supported tag rather than left on the stale tag

#### Scenario: Bump would require a breaking major upgrade
- **WHEN** the only newer release for an image is a major version that would require application or data-migration changes
- **THEN** the image stays on its current supported major line and the newer major is recorded as an open question rather than applied silently

#### Scenario: Stack is verified after version bumps
- **WHEN** image pins have been updated
- **THEN** the local Compose stack builds and pulls the updated images, `docker compose config` is valid, all services reach a healthy state, and the application is reachable end-to-end before the change is considered done

#### Scenario: Tags remain explicit
- **WHEN** an image pin is updated
- **THEN** the tag remains an explicit version and is not replaced with a floating tag such as `latest`

### Requirement: Object storage uses a maintained, supported backend
The Compose deployment's object storage SHALL be provided by a maintained, upstream-supported, S3-compatible backend whose image is publicly obtainable or reproducibly built from source. It SHALL NOT depend long-term on a frozen or unmaintained image that no longer receives updates or security patches. The choice of backend SHALL be recorded with its rationale.

#### Scenario: Object-storage backend is selected
- **WHEN** the object-storage backend for the stack is chosen or reaffirmed
- **THEN** it is an S3-compatible backend that is either published as a currently-maintained image or reproducibly built from source, and the decision and its rationale are recorded in the repository

#### Scenario: Backend depends on an unmaintained image
- **WHEN** the object-storage service would otherwise rely on a frozen or end-of-life image that no longer receives updates
- **THEN** that reliance is treated as a defect to resolve, not an acceptable long-term state, and is either replaced or explicitly recorded as a time-bound stopgap with a follow-up

### Requirement: Object-storage backend changes preserve document behavior and are verified
When the object-storage backend is changed (replaced, upgraded, or moved to a managed endpoint), the change SHALL preserve document upload and download behavior through the existing S3 client, and SHALL be verified against a running stack before being considered complete.

#### Scenario: Backend is replaced
- **WHEN** the `minio` object-storage service is repointed to a different S3-compatible backend or a managed endpoint
- **THEN** the API Gateway and parser service reach it through their existing `MINIO_*` endpoint/credentials configuration without application source changes, and the previously required bucket is available

#### Scenario: Stack is verified after a backend change
- **WHEN** the object-storage backend has been changed
- **THEN** `docker compose config` is valid, all services reach a healthy state, and an end-to-end document upload, parse, and download round-trip succeeds against the new backend before the change is considered done

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
