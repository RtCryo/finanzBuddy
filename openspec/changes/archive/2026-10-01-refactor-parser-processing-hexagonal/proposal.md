## Why

`DocumentProcessor` is the parser-service's core business flow — consume an upload event, fetch the file, route it to a parser, persist the extracted transactions idempotently, and report the result back to the gateway — yet it is wired to frameworks and concrete collaborators. It imports Micrometer (`Counter`/`Timer`/`MeterRegistry`) and Spring (`@Service`, `@Value`, `DataAccessException`), and depends directly on the concrete classes `MinioStorageService`, `GatewayStatusClient`, `TransactionPersistenceService`, and `DocumentParserRouter`. The Kafka consumer (`DocumentEventListeners`, `@KafkaListener`) and the Jackson/Kafka deserializer bind the entry point to the broker. The orchestration rules cannot be unit-tested without Kafka, MinIO, a REST client, and a metrics registry, and none of those frameworks can be swapped without editing the flow.

This mirrors the api-gateway hexagonal refactor and applies the pattern proven by [refactor-document-hexagonal] (vertical slice, pragmatic framework-free core) to the parser-service's processing slice.

## What Changes

- Introduce a framework-free **processing domain**: `DocumentProcessor` orchestration depending only on `java.*`, domain types, and (pragmatically) `jakarta.inject`/`jakarta.validation`. No Micrometer, Kafka, Spring, or storage/web types.
- Define **inbound port**: `ProcessDocumentUseCase` (handle one `DocumentUploadedEvent`).
- Define **outbound ports**:
  - `FileFetchPort` — fetch the stored object by key (backed by MinIO).
  - `TransactionParserPort` — route by document type and parse to domain transactions (backed by the parsing slice, [refactor-parser-parsing-hexagonal]).
  - `TransactionWriterPort` — persist transactions and record idempotency (backed by JPA + `ProcessedDocument`).
  - `StatusReporterPort` — report `PROCESSED`/`FAILED` to the gateway (backed by the REST client).
  - `ProcessingMetricsPort` — processed/failed/duplicate counters and processing timer (backed by Micrometer).
- Move framework code into **adapters**:
  - inbound: `KafkaDocumentEventAdapter` (the `@KafkaListener`) + the Jackson/Kafka deserializer call `ProcessDocumentUseCase`.
  - outbound: `MinioFileFetchAdapter`, `RestClientStatusReporterAdapter` (keeps `@Retryable`/`RestClient`), `JpaTransactionWriterAdapter` (persistence + `ProcessedDocument` idempotency, `@Transactional`), `MicrometerProcessingMetricsAdapter`.
- Keep domain exceptions in the core (`DuplicateDocumentException`, `StorageException` reframed as a domain fetch failure); framework exceptions (`DataAccessException`, `RestClientException`) are translated inside adapters.
- Add architecture (ArchUnit) tests enforcing processing domain-isolation and port rules.
- **No external behavior change**: Kafka topics/consumption, idempotency, gateway status callbacks, emitted metrics, and persisted transactions are identical.

## Capabilities

### New Capabilities
- `parser-processing-architecture`: Structural requirements for the parser processing slice — domain isolation of the orchestration flow, mandatory inbound/outbound ports, Kafka-consumer-as-adapter and metrics-as-port, and automated enforcement. Verifiable constraints, not functional behavior.

### Modified Capabilities
<!-- None. The document-consumption, status-reporting, and transaction-normalization functional specs and their observable behavior are unchanged. -->

## Impact

- **Code (parser-service only)**: `service/DocumentProcessor.java`, `service/TransactionPersistenceService.java`, `event/DocumentEventListeners.java`, `event/DocumentUploadedEventDeserializer.java`, `client/GatewayStatusClient.java`, `storage/MinioStorageService.java`, `entity/*`, `repository/*` reorganized into `processing/domain`, `processing/port/{in,out}`, `processing/adapter/{in/kafka,out/storage,out/status,out/persistence,out/metrics}`.
- **Dependency on other slices**: `TransactionParserPort` is backed by [refactor-parser-parsing-hexagonal]; sequence parsing first or stub the port.
- **APIs/contracts**: unchanged — same event schema, same gateway `PUT /internal/documents/{id}/status` calls, same DB schema, same metric names.
- **Out of scope**: the format parsers themselves (own change), parser-service Spring/Kafka/REST/MinIO wiring in `config/*` (stays as configuration adapters), api-gateway.
- **Tests**: existing processor/consumer/persistence tests re-pointed; new domain unit tests (no Kafka/MinIO/REST/Micrometer) using fake ports and ArchUnit tests added.
