# Proposal

## Why

The root change [`add-homebuddy-tracing`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-tracing/proposal.md) adds a central Tempo trace store behind the homeBuddy telemetry collector. A statement upload in finanzBuddy passes through `api-gateway`, its outbox, Kafka, `parser-service`, and back through the status callback, but neither component records traces. When an upload is slow or stays pending, an operator cannot tell which step was slow or failed and has to correlate log lines by timestamp.

## What Changes

- `api-gateway` and `parser-service` record traces and export them with OTLP over HTTP to the collector, following the root capability `homebuddy-tracing`. This change does not restate that contract.
- Resource attributes: `service.name = finanzbuddy-api-gateway` and `finanzbuddy-parser-service`, `service.namespace = finanzbuddy`.
- Traced calls:
  - incoming HTTP requests of both components (except `/actuator/**`);
  - `api-gateway`'s JWKS fetch from authBuddy, through a `RestTemplate` from Spring Boot's observed builder;
  - the outbox: the upload request stores its W3C `traceparent` in a new nullable column `trace_parent` of `outbox_events`, and `OutboxRelay` publishes each event inside an `outbox publish` span whose parent is that context, so the publish belongs to the upload's trace;
  - Kafka: `api-gateway`'s producer and `parser-service`'s listener and dead-letter producer are observed and carry trace context in record headers;
  - the status callback: `parser-service`'s `RestClientConfig` stops building its own `RestClient.Builder` from `RestClient.builder()` and only customizes Spring Boot's injected, observed builder, so `GatewayStatusClient` calls are client spans in the trace.
- Scheduled jobs do not create a trace per poll: spans of `@Scheduled` executions are not exported, and only the per-event outbox spans are.
- Every log line written inside a trace keeps Spring Boot's default console pattern and carries `[<traceId>-<spanId>]`; neither component sets its own console pattern.
- Sampling probability 1.0 by default; operators lower it per component with `API_GATEWAY_TRACING_SAMPLING_PROBABILITY` and `PARSER_SERVICE_TRACING_SAMPLING_PROBABILITY`.
- Spans carry no tokens, internal API keys, file names, file contents, transaction data, or amounts. OTLP metrics and log export stay off.

## Capabilities

### New Capabilities

- `application-tracing`: Which requests, messages, and calls finanzBuddy traces, how the trace continues through the outbox and Kafka, resource attributes, sampling, privacy of spans, and the trace id in logs.

### Modified Capabilities

- None. Publishing semantics of `kafka-publishing`, status reporting, and the metrics of `application-observability` are unchanged; both components already join `finanzbuddy-metrics`, which is also the path to the collector.

## Non-goals

- JDBC, MinIO, and Kafka broker spans; browser and nginx tracing.
- Re-linking outbox events written before the migration to their upload's trace.

## Impact

- `api-gateway`: `pom.xml`, `application.yml`, `SecurityConfig` (JWKS client), `OutboxEvent`, `DocumentService`, `OutboxRelay`, Liquibase changeset `015-add-outbox-trace-context.xml` with rollback, span export filter, tests.
- `parser-service`: `pom.xml`, `application.yml`, `RestClientConfig`, `KafkaConfig` (observation on the listener container factory and template), span export filter, tests.
- `docker-compose.yml`, `.env.example`, `docs/ARCHITECTURE.md`, `docs/README.md`, module READMEs where they describe configuration.
- No new network, port, or secret.
