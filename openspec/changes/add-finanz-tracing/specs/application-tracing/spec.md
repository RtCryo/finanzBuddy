# Spec Delta

## Purpose

Defines how finanzBuddy's `api-gateway` and `parser-service` take part in homeBuddy tracing. The trace pipeline, naming and propagation convention, retention, and privacy baseline are the root capability `homebuddy-tracing`; this capability adds only finanzBuddy specifics.

## ADDED Requirements

### Requirement: finanzBuddy components export traces
`api-gateway` and `parser-service` SHALL record spans for incoming HTTP requests other than `/actuator/**`, for Kafka records they publish and consume, and for HTTP calls they make to other homeBuddy components, and SHALL export them to the homeBuddy telemetry collector as defined by the root capability `homebuddy-tracing`. Their resource attributes SHALL be `service.name = finanzbuddy-api-gateway` or `finanzbuddy-parser-service` and `service.namespace = finanzbuddy`. The export endpoint SHALL be configured by the deployment only, so that a component exports nothing when it is not set.

#### Scenario: Upload request
- **WHEN** a signed-in user uploads a statement
- **THEN** a trace with a server span of `finanzbuddy-api-gateway` named after the upload route template is stored in the central trace store

#### Scenario: JWKS fetch
- **WHEN** `api-gateway` fetches authBuddy's JWKS while validating a token in a request
- **THEN** the fetch is a client span in that request's trace

#### Scenario: Health checks, scrapes, and scheduled polls
- **WHEN** the health check or the central Prometheus calls `/actuator/**`, or a scheduled job polls without publishing an event
- **THEN** no trace is exported for it

#### Scenario: Collector unavailable
- **WHEN** the collector cannot be reached
- **THEN** uploads, parsing, and status updates succeed with unchanged latency, and the components only log that spans could not be exported

### Requirement: An upload forms one trace through the outbox and Kafka
`api-gateway` SHALL store the W3C trace context of the upload request with its outbox event and SHALL publish the event within a span that continues that trace. Kafka records between finanzBuddy components SHALL carry W3C trace context in their headers, `parser-service` SHALL continue the trace when it consumes a record, and its status callbacks to `api-gateway` SHALL propagate it. An outbox event without stored context SHALL be published in a new trace.

#### Scenario: Statement upload end to end
- **WHEN** a user uploads a statement and the parser processes it
- **THEN** one trace contains the `api-gateway` upload request, the outbox publish with its Kafka producer span, the `parser-service` consumer span, and the status callback client span with `api-gateway`'s server span for it

#### Scenario: Event written before the migration
- **WHEN** `OutboxRelay` publishes an event whose stored trace context is empty
- **THEN** the publish starts a new trace and the event is delivered unchanged

### Requirement: finanzBuddy samples every request by default
Each finanzBuddy component SHALL sample every trace it starts by default, SHALL let operators lower the probability with `API_GATEWAY_TRACING_SAMPLING_PROBABILITY` or `PARSER_SERVICE_TRACING_SAMPLING_PROBABILITY` without rebuilding, and SHALL follow the sampling decision carried by an incoming HTTP request or Kafka record.

#### Scenario: Parser follows the gateway
- **WHEN** `api-gateway` does not sample an upload and `parser-service` consumes its event
- **THEN** `parser-service` records no trace for that event

### Requirement: finanzBuddy spans hold no credentials or financial data
Spans of finanzBuddy components SHALL NOT contain tokens, internal API keys, `Authorization` or cookie headers, request, response, or record bodies, e-mail addresses, file names, file contents, transaction fields, or amounts.

#### Scenario: Search traces after an upload
- **WHEN** an operator uploads a statement with a known file name and amount and searches the stored spans for them, the token, an `eyJ` prefix, and the internal API key
- **THEN** none is found

### Requirement: finanzBuddy log lines carry the trace id
Every log line a finanzBuddy component writes while handling a traced request or record SHALL contain `[<traceId>-<spanId>]` with the 32- and 16-character lowercase hexadecimal ids of the current span.

#### Scenario: Parser log line
- **WHEN** `parser-service` logs "Received CSV document event" for a traced upload
- **THEN** the log line contains the trace id of the upload's trace
