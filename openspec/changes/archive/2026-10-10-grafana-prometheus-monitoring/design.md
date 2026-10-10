# Design

## Context

See proposal.md for motivation and the spec deltas for required behavior. Both Spring Boot services already include Actuator and a Prometheus Micrometer registry, and expose `/actuator/prometheus`. The parser already records document processed/failed/duplicate counters and processing duration. The API Gateway already records outbox pending count, oldest pending age, and dead-lettered events. Docker Compose runs a single-broker Kafka cluster on the shared service network; the local override publishes host ports, while production omits that override and routes public traffic through Traefik.

## Goals / Non-Goals

**Goals:**
- Make the full monitoring path reproducible from Compose: scrape targets, datasource, dashboard provisioning, and metric panels for overall health and individual services.
- Reuse existing metrics, identify missing signals during implementation, and add only bounded-cardinality application metrics needed for the specified views.
- Preserve the existing production boundary: monitoring UI and scrape endpoints remain inside the Compose network and are not exposed through Traefik.

**Non-Goals:**
- Add alert delivery, long-term/remote metrics storage, or infrastructure exporters for PostgreSQL or SeaweedFS.
- Change application document-processing or deployment behavior beyond observability and local monitoring access.

## Decisions

1. **Run Prometheus and Grafana as base Compose services.** Prometheus must be present alongside the applications to make the existing scrape endpoints useful in a clean checkout. Both applications listen on container port 8080, so scrape jobs can use Compose DNS (`api-gateway:8080` and `parser-service:8080`) and `/actuator/prometheus`. An externally managed Prometheus or Grafana Cloud was considered, but would not meet the request for a self-contained stack.

2. **Keep host port mappings in the local override only.** Publish Prometheus on host port 9090 and Grafana on 3001 (the UI already uses 3000) for local development. Production uses the base Compose file without the override; monitoring stays on the internal network and is not added to Traefik. Publishing monitoring through Traefik was rejected because it would make operational interfaces public and weaken the existing single-entry-point boundary.

3. **Provision Grafana from repository-managed configuration.** Store the Prometheus datasource and separate overview, API Gateway, parser-service, and Kafka dashboard definitions as mounted provisioning files so a fresh stack has the dashboards without manual setup. Use a stable datasource identity. The overview emphasizes service health and uptime; service dashboards show the related HTTP, document, outbox, or Kafka signals. Represent absent samples explicitly rather than disguising them as zero.

4. **Extend Micrometer only for gaps in the specified application views.** Preserve existing parser and outbox instruments. Tag upload outcomes, parser outcomes, parsed-transaction volume, and processing duration with a normalized, bounded file type (`CSV`, `PDF`, `XML`, or `OTHER`). Never tag metrics with user/document identifiers, filenames, or financial values. Prefer standard Spring HTTP/JVM measurements where available over duplicate custom instruments.

5. **Pin monitoring images to explicit supported versions.** Follow the repository's Compose image policy: choose current upstream-supported explicit tags during implementation and validate the resolved Compose configuration and stack. Do not use floating `latest` tags.

6. **Use Kafka Exporter for Kafka operational metrics.** Run a version-pinned `danielqsj/kafka-exporter` on the internal Compose network, configure it for the bundled Kafka broker, and scrape its broker/topic/consumer-group metrics from Prometheus. Do not publish the exporter port to the host or Traefik.

## Risks / Trade-offs

- **Monitoring adds container and storage overhead** → Keep Prometheus retention bounded and use named volumes for monitoring state where needed; document how to inspect and clear local state.
- **Prometheus scrape data may be absent during startup or outages** → Use target health signals, and ensure dashboards gracefully display no-data states.
- **Application labels can create high cardinality or expose private data** → Restrict custom labels to bounded operational categories and verify metrics do not contain identifiers or transaction values.
- **Prometheus is unauthenticated by default** → Keep its host port local-development-only and do not publish it in production or route it through Traefik.

## Migration Plan

No application data migration is required. Start or update the Compose stack, verify both scrape targets become available, and confirm the provisioned Grafana dashboards render. For rollback, stop/remove the monitoring services and their Compose configuration; application Actuator endpoints and existing metrics can remain available without affecting application behavior. Retain or remove named monitoring volumes according to whether historical local samples and Grafana state are wanted.
