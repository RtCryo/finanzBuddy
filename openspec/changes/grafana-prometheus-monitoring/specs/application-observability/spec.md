# Spec Delta

## Purpose

Provide an immediately usable operational view of finanzBuddy by collecting application metrics and presenting service and document-processing signals in Grafana dashboards.

## ADDED Requirements

### Requirement: Collect metrics from application services
The monitoring stack SHALL collect Prometheus metrics from both the API Gateway and parser service, and SHALL make the availability of each scrape target observable.

#### Scenario: Both application services are running
- **WHEN** Prometheus is running and the API Gateway and parser service expose their metrics endpoints
- **THEN** Prometheus collects metrics from both services and reports both targets as available

#### Scenario: An application service cannot be scraped
- **WHEN** either application service is unavailable or its metrics endpoint cannot be scraped
- **THEN** Prometheus reports that target as unavailable so the outage is visible in monitoring

### Requirement: Provide provisioned Grafana operational dashboards
Grafana SHALL be provisioned with a Prometheus datasource and dashboards that are available without manual dashboard or datasource setup. A main dashboard SHALL show API Gateway, parser, and Kafka availability and application uptime. Separate API Gateway, parser-service, and Kafka dashboards SHALL show service-specific signals. The dashboards SHALL show HTTP request rate, latency and errors; upload and parser outcomes and parser processing duration by file type; parsed transaction volume by file type; outbox pending count, oldest pending age, and dead-lettered events; and Kafka broker count, topic partitions, under-replicated partitions, consumer group members, and consumer lag.

#### Scenario: Operator opens the monitoring dashboard
- **WHEN** the monitoring stack has started and an operator opens Grafana
- **THEN** the provisioned dashboards display the specified service and application signals using Prometheus data

#### Scenario: No application activity has occurred
- **WHEN** Prometheus has no samples yet for a dashboard panel
- **THEN** the panel communicates that data is unavailable or absent rather than presenting a misleading zero or failing to load the dashboard

#### Scenario: Operator opens a service dashboard
- **WHEN** an operator opens the API Gateway, parser-service, or Kafka dashboard
- **THEN** the dashboard shows that service's availability and its relevant HTTP, document, outbox, or Kafka broker and consumer signals

### Requirement: Expose useful, privacy-preserving application metrics
The application SHALL expose operational metrics for document upload outcomes, document processing outcomes and duration, parsed transaction volume, and outbox delivery backlog and failures. Upload and parser document metrics SHALL include a bounded file-type dimension using supported format names and `OTHER` for unknown types. Metrics SHALL NOT include user identifiers, document identifiers, filenames, financial values, or other unbounded or sensitive values as labels.

#### Scenario: Documents and transactions are processed
- **WHEN** a document upload or parser processing attempt completes
- **THEN** the corresponding outcome and processing signals are reflected in metrics that can be aggregated over time

#### Scenario: Metrics are labeled for aggregation
- **WHEN** application metrics include dimensions for grouping results
- **THEN** those dimensions use bounded operational categories, including normalized file type, and exclude user- or document-specific data

#### Scenario: Documents are uploaded or processed in multiple formats
- **WHEN** documents of different supported file types are uploaded or processed
- **THEN** upload outcomes, parser outcomes, parsed transaction volume, and processing duration can be aggregated separately by bounded file type
