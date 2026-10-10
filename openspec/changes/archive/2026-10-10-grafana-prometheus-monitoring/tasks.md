# Tasks

## 1. Compose and Prometheus collection

- [x] 1.1 Add explicitly version-pinned Prometheus and Grafana services to the base Compose file, with bounded Prometheus retention and named storage volumes; verify `docker compose -f docker-compose.yml config` resolves both services and their mounts.
- [x] 1.2 Add repository-managed Prometheus scrape configuration for `api-gateway:8080` and `parser-service:8080` at `/actuator/prometheus`; verify the config loads and both targets report healthy when the application services are running.
- [x] 1.3 Publish Prometheus on host port 9090 and Grafana on 3001 only in the local Compose override; verify both local URLs respond and the production Compose configuration publishes neither monitoring service nor adds either to Traefik.

## 2. Application metrics

- [x] 2.1 Add API Gateway document-upload outcome metrics for signals not already available; verify tests cover successful and unsuccessful outcomes and metric labels contain no user/document-specific values.
- [x] 2.2 Add a parser metric for parsed transaction volume if no equivalent exists; verify parser tests assert the measurement for successful processing and that it contains no transaction or document data.
- [x] 2.3 Verify the existing parser document outcome/duration metrics, API Gateway outbox backlog/age/dead-letter metrics, and HTTP request metrics are exposed at both Prometheus endpoints; run the relevant Maven test suites and inspect sample exposition for private identifiers or financial values.
- [x] 2.4 Document the application metric names, meanings, and aggregation caveats in the API Gateway and parser service READMEs; verify each documented custom metric matches the implementation and is available from its service endpoint.
- [x] 2.5 Add bounded file-type labels to upload outcomes and parser outcomes, processing duration, and transaction volume; verify supported types are distinguished and unknown types map to `OTHER` without sensitive labels.
- [x] 2.6 Document per-file-type upload, processing, transaction-volume, and duration metrics in the API Gateway and parser service READMEs.

## 3. Grafana provisioning and dashboards

- [x] 3.1 Provision a Prometheus datasource and version-controlled Grafana dashboard definitions; verify a fresh Grafana start loads them without manual setup and panels cover target/service availability, HTTP rate/latency/errors, parser outcomes/duration/transaction volume, and outbox backlog/age/dead letters.
- [x] 3.2 Configure dashboard panels to present missing samples as no data rather than misleading zeros; verify panels render successfully before application activity and populate after metrics are scraped.
- [x] 3.3 Document local Grafana and Prometheus URLs and dashboard contents in the root README; verify the documented startup and access instructions match the Compose configuration.
- [x] 3.4 Provision separate main health, API Gateway, and parser-service dashboards; verify the overview includes service availability and uptime and service dashboards break down upload/processing metrics by file type.
- [x] 3.5 Add a pinned internal Kafka exporter and provision a Kafka dashboard for broker, topic/partition, under-replicated partition, consumer group, and lag metrics; verify exporter scraping and dashboard queries.

## 4. End-to-end verification

- [x] 4.1 Start the complete local stack and verify `docker compose config`, both Prometheus targets, the Grafana datasource and provisioned dashboards, and the required panels work together with real application metrics.
- [x] 4.2 Verify the complete dashboard set and per-type application metrics against live API Gateway, parser, and Kafka exporter samples in the local stack.
