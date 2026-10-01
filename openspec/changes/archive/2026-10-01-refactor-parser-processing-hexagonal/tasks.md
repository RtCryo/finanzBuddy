## 1. Scaffolding & dependencies

- [x] 1.1 Create `processing/` slice packages: `domain/model`, `domain/exception`, `port/in`, `port/out`, `adapter/in/kafka`, `adapter/out/storage`, `adapter/out/status`, `adapter/out/persistence`, `adapter/out/metrics`
- [x] 1.2 Add ArchUnit test-scoped dependency to the parser-service POM
- [x] 1.3 Confirm `jakarta.inject`/`jakarta.validation` API available to the domain

## 2. Domain model & exceptions

- [x] 2.1 Add framework-free domain types: domain `Transaction` (shared `parsing.domain.model.Transaction`), `ProcessingOutcome`, `WriteResult`
- [x] 2.2 Add/relocate domain exceptions: `FileFetchException` (reframed `StorageException`), `TransactionPersistenceException`; parse failures stay as `IllegalArgumentException`/`UnsupportedOperationException` to preserve non-retryable classification; duplicate signalled via `WriteResult.DUPLICATE` instead of an exception (per design D3)

## 3. Ports

- [x] 3.1 Define inbound port `ProcessDocumentUseCase` (handle one `DocumentUploadedEvent`)
- [x] 3.2 Define outbound ports `FileFetchPort`, `TransactionParserPort`, `TransactionWriterPort` (dedupe+insert), `StatusReporterPort`, `ProcessingMetricsPort`

## 4. Outbound adapters

- [x] 4.1 Implement `MinioFileFetchAdapter` (`FileFetchPort`) from `MinioStorageService`; translate `StorageException` to the domain fetch failure
- [x] 4.2 Implement `JpaTransactionWriterAdapter` (`TransactionWriterPort`) keeping dedupe (`ProcessedDocument`) + insert atomic under `@Transactional`; translate `DataAccessException`/`DataIntegrityViolationException`
- [x] 4.3 Implement `RestClientStatusReporterAdapter` (`StatusReporterPort`) from `GatewayStatusClient`, keeping `@Retryable`/`RestClient`
- [x] 4.4 Implement `MicrometerProcessingMetricsAdapter` (`ProcessingMetricsPort`) preserving exact metric names (`parser.documents.processed|failed|duplicate`, `parser.documents.processing.duration`)
- [x] 4.5 Implement `TransactionParserPort` adapter delegating to `DocumentParserRouter` (or the parsing slice)

## 5. Domain orchestrator

- [x] 5.1 Rewrite `DocumentProcessor` to implement `ProcessDocumentUseCase`, depending only on outbound ports + domain types
- [x] 5.2 Record metrics via `ProcessingMetricsPort`; react to the write result for duplicate handling
- [x] 5.3 Replace Spring/Micrometer imports; verify no forbidden imports remain in the processing domain

## 6. Inbound Kafka adapter

- [x] 6.1 Reduce `DocumentEventListeners` to `KafkaDocumentEventAdapter` (`@KafkaListener`) that deserializes and calls the use case, preserving ack/retry behavior
- [x] 6.2 Keep the Jackson/Kafka deserializer in the inbound adapter package

## 7. Tests & enforcement

- [x] 7.1 Re-point existing processor/consumer/persistence tests; confirm behavior and metric names unchanged
- [x] 7.2 Add domain unit tests (success, duplicate, parse-failure → FAILED report) using fake ports (no Kafka/MinIO/REST/Micrometer)
- [x] 7.3 Add ArchUnit tests for domain isolation, Kafka-adapter-holds-no-logic, metrics-via-port, concrete clients only in `adapter/out/*`
- [x] 7.4 Run the full parser-service test suite + architecture suite; fix violations

## 8. Cleanup & docs

- [x] 8.1 Delete dead coupled code
- [x] 8.2 Update `parser-service/README.md` and `docs/ARCHITECTURE.md` for the processing slice structure
