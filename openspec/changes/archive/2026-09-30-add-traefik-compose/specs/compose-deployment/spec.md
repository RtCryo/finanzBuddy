# Spec Delta

## Purpose

This capability defines how the Docker Compose application is exposed to users through a configured public domain, including HTTPS, request routing, and the distinction between production access and local development.

## ADDED Requirements

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
