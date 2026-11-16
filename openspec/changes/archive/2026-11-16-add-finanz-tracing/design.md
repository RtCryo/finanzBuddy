# Design

## Context

`api-gateway` and `parser-service` are Spring Boot 4.1.2 on Java 25 with Actuator and the Prometheus registry; neither has a tracing dependency or a custom log pattern. Relevant code:

- `SecurityConfig` (api-gateway) builds a `NimbusJwtDecoder` with `withJwkSetUri(...)`, which uses its own unobserved `RestTemplate`.
- `DocumentService.enqueueUploadedEvent` writes an `OutboxEvent` in the upload transaction. `OutboxRelay.publishPending` is a `@Scheduled` job (every 5 s) that sends pending events with the auto-configured `KafkaTemplate`, outside any request.
- `parser-service` builds its own `ConsumerFactory`, `ConcurrentKafkaListenerContainerFactory`, `ProducerFactory`, and `KafkaTemplate` in `KafkaConfig`, so `spring.kafka.*.observation-enabled` properties do not reach them.
- `RestClientConfig` (parser-service) defines a `RestClient.Builder` bean from `RestClient.builder()` without an observation registry; `GatewayStatusClient` builds its client from the injected builder. Spring Boot 4 auto-configures an observed `RestClient.Builder` only with its separate REST client module, which neither component uses.

Both components join `finanzbuddy-metrics`, on which the root collector `homebuddy-alloy` receives OTLP on port 4318 (root capability `homebuddy-tracing`).

## Decisions

### Spring Boot's OpenTelemetry starter in both components

Same configuration as every homeBuddy component: `spring-boot-starter-opentelemetry`; `management.tracing.sampling.probability: 1.0`; `management.opentelemetry.resource-attributes` with `service.name` (`finanzbuddy-api-gateway`, `finanzbuddy-parser-service`) and `service.namespace: finanzbuddy`; `management.otlp.metrics.export.enabled: false` and `management.logging.export.otlp.enabled: false`. No export endpoint in `application.yml`; `docker-compose.yml` sets `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://homebuddy-alloy:4318/v1/traces` and `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` from `API_GATEWAY_TRACING_SAMPLING_PROBABILITY` / `PARSER_SERVICE_TRACING_SAMPLING_PROBABILITY` (default `1.0`).

### Observed JWKS client

`SecurityConfig.jwtDecoder` creates a `RestTemplate`, sets the application's `ObservationRegistry` on it, and passes it to `NimbusJwtDecoder.withJwkSetUri(...).restOperations(...)`, so the JWKS fetch is a client span that sends `traceparent` to authBuddy. Validation rules are unchanged.

- *Alternative: a `RestTemplate` from Boot's `RestTemplateBuilder`.* It needs Boot 4's REST client module only for this one client.

### Trace context through the outbox

- Liquibase changeset `015-add-outbox-trace-context.xml` adds `outbox_events.trace_parent VARCHAR(55) NULL` (the length of a W3C `traceparent` value), with a rollback that drops it.
- `OutboxTraceContext`, a small component used by `DocumentService` and `OutboxRelay`, holds the propagation logic.
- `DocumentService.enqueueUploadedEvent` injects the current trace context with Micrometer's `Propagator` into a map and stores its `traceparent` entry in the new column, or `null` outside a trace.
- `OutboxRelay.publishOne` extracts that value with the `Propagator` into a span builder and starts an `outbox publish` span (an internal span with the attribute `messaging.destination.name` = topic), or a new root span when the column is `null`. The `KafkaTemplate.send` runs with that span in scope, so the observed Kafka producer span is its child and injects `traceparent` into the record headers. Send failures mark the span as error.
- `spring.kafka.template.observation-enabled: true` in api-gateway turns on the auto-configured template's observation.

Stored context is the trace and span id plus the sampled flag only; no baggage is propagated.

- *Alternative: store the context in the event payload.* It would change the event contract read by `parser-service` and the deserializer; a column keeps the payload unchanged.

### Kafka and status callback in parser-service

- `KafkaConfig` sets `factory.getContainerProperties().setObservationEnabled(true)` on the listener container factory and `setObservationEnabled(true)` on its `KafkaTemplate` (used by the dead-letter recoverer). The listener span continues the trace from the record header, and the parent-based sampler follows its sampled flag.
- `RestClientConfig` keeps its `RestClient.Builder` bean, with the same request factory and timeouts, and sets the application's `ObservationRegistry` on it. `GatewayStatusClient` keeps taking the injected builder, now observed, so each status callback is a client span inside the listener span and sends `traceparent` to `api-gateway`, whose server span joins the same trace. The `X-Internal-Api-Key` header is not recorded.

- *Alternative: a `RestClientCustomizer` on Boot's builder.* Boot 4 provides that builder only with its REST client module; the existing bean with an observation registry has the same effect.

### Spans that are not exported

A `SpanExportingPredicate` bean in each component drops:

- server spans whose `uri` tag starts with `/actuator` (health checks every 10 s, scrapes every 15 s);
- spans of `@Scheduled` executions in api-gateway (root spans named `task <bean>.<method>` with `code.function` and `code.namespace` tags), so the outbox poll, reconciler, retention job, and metrics refresh do not create a trace every few seconds.

In api-gateway an `ObservationPredicate` also skips `spring.security.*` observations whose parent chain leads to a server request for `/actuator/**`; their spans would otherwise be exported without their dropped parent. parser-service has no Spring Security. Observations still run, so existing HTTP and task metrics are unchanged. The outbox publish span does not have the scheduled span as parent (it has the stored context or none), so it is exported.

### Logs and privacy

Spring Boot's default console pattern includes `[<traceId>-<spanId>]`; both components keep it. Server spans use route templates; Spring records no headers, query parameters, or bodies; Kafka spans record topic, partition, and the record key (the document id), not the payload. No custom attribute holds a file name, user id, amount, or transaction field.

## Risks / Trade-offs

- [Events written before the migration have no context] → They start a new trace in `OutboxRelay`.
- [A dropped scheduled span with children would leave orphan spans] → No scheduled job other than the outbox makes an observed call; the outbox span sets its parent explicitly.
- [The dead-letter path calls the status client from the error handler] → It runs inside the listener observation and is traced like the normal callback.
