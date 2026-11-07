# Application Observability Specification

## Purpose

Provide an immediately usable operational view of finanzBuddy by collecting application metrics and presenting service and document-processing signals in Grafana dashboards.

## Requirements

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

## Related implementation and history

- Documentation: [monitoring map](../../../docs/README.md#subsystem-map), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`docker-compose.yml`](../../../docker-compose.yml); Prometheus and Grafana configuration live in the root homeBuddy `monitoring/` folder
- History: [Grafana and Prometheus monitoring](../../changes/archive/2026-10-10-grafana-prometheus-monitoring/), [move monitoring to the central homeBuddy stack](../../changes/archive/2026-11-07-move-monitoring-to-homebuddy/)
