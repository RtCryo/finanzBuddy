# Tasks

## 1. Tracing setup in both components

- [x] 1.1 Add `spring-boot-starter-opentelemetry` and the `application.yml` settings from design.md to `api-gateway` and `parser-service` (sampling 1.0, resource attributes, OTLP metrics and log export off, no export endpoint). Verify with tests that no OTLP meter registry and no span exporter is active without the endpoint property, and that `./mvnw verify` passes in both modules.
- [x] 1.2 Add the `SpanExportingPredicate` to both components (actuator server spans; scheduled-task spans in `api-gateway`) and, in `api-gateway`, the `ObservationPredicate` that skips Spring Security observations inside actuator requests. Verify with unit tests for an actuator span, a scheduled span, an outbox publish span, and an API span, and with a test that scheduled polls are not exported.
- [x] 1.3 Verify with a test that a log line written inside a traced request contains `[<traceId>-<spanId>]`.

## 2. api-gateway

- [x] 2.1 Build the `JwtDecoder` with a `RestTemplate` that uses the application's `ObservationRegistry`. Verify that the existing token validation tests pass and that the JWKS request carries `traceparent` inside an observation.
- [x] 2.2 Add Liquibase changeset `015-add-outbox-trace-context.xml` (nullable `trace_parent VARCHAR(55)`, rollback drops it) and the `OutboxEvent` field. Verify with the migration tests and a rollback.
- [x] 2.3 Store the upload's `traceparent` in `DocumentService.enqueueUploadedEvent`, publish each event in `OutboxRelay` inside an `outbox publish` span with the stored parent (or a new root), and enable `spring.kafka.template.observation-enabled`. Verify with tests that a stored context becomes the parent of the publish span and the Kafka record carries a `traceparent` header of the same trace, and that an event without context is still published.

## 3. parser-service

- [x] 3.1 Enable observation on the listener container factory and the `KafkaTemplate` in `KafkaConfig`. Verify with a test that a consumed record with a `traceparent` header is processed in a span of that trace.
- [x] 3.2 Set the application's `ObservationRegistry` on the `RestClient.Builder` bean in `RestClientConfig`, keeping the configured timeouts. Verify with tests that `GatewayStatusClient` sends `traceparent` inside an observation, keeps the `X-Internal-Api-Key` header and timeouts, and that existing tests pass.

## 4. Compose and documentation

- [x] 4.1 Set `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` and `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` (from `API_GATEWAY_TRACING_SAMPLING_PROBABILITY` and `PARSER_SERVICE_TRACING_SAMPLING_PROBABILITY`, default `1.0`) in `docker-compose.yml`, document both in `.env.example`. Verify with `docker compose config`.
- [x] 4.2 Update `docs/ARCHITECTURE.md` (trace flow through outbox and Kafka, link to root `homebuddy-tracing`), the module READMEs' configuration tables, and `docs/README.md` (tracing row; the `application-tracing` spec and archived change are linked when the change is archived), and run `python scripts/check_documentation_map.py`.

## 5. Verification

- [x] 5.1 With the root stack running, upload a statement and verify that Tempo has one trace with the upload request, the outbox publish, the Kafka producer and `parser-service` consumer spans, and the status callback, and that `parser-service`'s log line in Loki carries that trace id.
- [x] 5.2 Search the stored spans for the uploaded file name, an amount from the statement, the token, and the internal API key; none is found.
