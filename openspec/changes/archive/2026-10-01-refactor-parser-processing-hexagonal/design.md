## Context

`DocumentProcessor` is the parser-service orchestrator: on each `DocumentUploadedEvent` it deduplicates, fetches the stored file, routes it to a parser, persists the extracted transactions idempotently, and reports the outcome to the gateway. It imports Micrometer (`Counter`/`Timer`/`MeterRegistry`) and Spring (`@Service`, `@Value`, `DataAccessException`), and holds concrete references to `MinioStorageService`, `GatewayStatusClient`, `TransactionPersistenceService`, and `DocumentParserRouter`. The entry point is `DocumentEventListeners` (`@KafkaListener`) with a Jackson/Kafka deserializer. The orchestration cannot be tested without Kafka, MinIO, a REST client, and a metrics registry.

This mirrors the api-gateway hexagonal refactor and applies the pattern from [refactor-document-hexagonal] to the parser-service processing slice. Core-purity decision: pragmatic (jakarta.inject/validation allowed; no Spring/Kafka/MinIO/Micrometer).

## Goals / Non-Goals

**Goals:**
- The consume→fetch→parse→persist→report orchestration lives in a framework-free domain behind an inbound port.
- Storage, parsing, persistence+idempotency, status reporting, and metrics are outbound ports with single adapters.
- Kafka, MinIO, the REST client, and Micrometer become replaceable details.
- Behavior preserved (topics, idempotency, callbacks, metric names, persisted data); existing tests pass; ArchUnit enforces boundaries.

**Non-Goals:**
- The format parsers themselves are a separate slice ([refactor-parser-parsing-hexagonal]).
- `config/*` (Kafka/MinIO/REST wiring) stays as configuration adapters.
- No change to the event schema, gateway status API, DB schema, or metric names.

## Decisions

### D1: Package structure under `com.finanzbuddy.parser`
```
processing/
  domain/
    DocumentProcessor (implements ProcessDocumentUseCase)
    model/      domain Transaction, ProcessingOutcome ...
    exception/  DuplicateDocumentException, FileFetchException, ...
  port/
    in/     ProcessDocumentUseCase
    out/    FileFetchPort, TransactionParserPort, TransactionWriterPort,
            StatusReporterPort, ProcessingMetricsPort
  adapter/
    in/kafka/        KafkaDocumentEventAdapter (@KafkaListener) + deserializer
    out/storage/     MinioFileFetchAdapter
    out/status/      RestClientStatusReporterAdapter (@Retryable, RestClient)
    out/persistence/ JpaTransactionWriterAdapter (Transaction + ProcessedDocument idempotency)
    out/metrics/     MicrometerProcessingMetricsAdapter
```
Rationale: same single-module, ArchUnit-enforced boundary as the gateway document slice.

### D2: Parsing is an outbound port backed by the parsing slice
`TransactionParserPort.parse(documentType, content, locale) -> domain transactions` is implemented by the parsing slice ([refactor-parser-parsing-hexagonal]) via `DocumentParserRouter`. Rationale: processing should not know OpenCSV/PDFBox exist. If parsing lands later, a thin adapter wraps the current router.

### D3: Idempotency stays inside the persistence port
Duplicate detection (`ProcessedDocument`) and transaction insertion are one outbound concern behind `TransactionWriterPort` (e.g. `writeIfNotProcessed(docId, transactions) -> WriteResult{DUPLICATE|WRITTEN}`). Rationale: the dedupe+insert must stay atomic/consistent with the current `@Transactional` behavior; exposing it as one port keeps that guarantee in the adapter. The domain reacts to the result (records the duplicate metric, reports status).

### D4: Metrics as a port
`ProcessingMetricsPort` exposes `recordProcessed()/recordFailed()/recordDuplicate()` and a `timeProcessing(Runnable/Supplier)` or start/stop. The Micrometer counters/timer move into the adapter, preserving the exact metric names (`parser.documents.processed|failed|duplicate`, `parser.documents.processing.duration`). Rationale: Micrometer is an infrastructure detail; metric names are a contract and must not change.

### D5: Errors as domain exceptions, translated in adapters
`DataAccessException`/`RestClientException`/`StorageException` are caught in their adapters and surfaced as domain exceptions; the domain decides FAILED vs retry. Rationale: keeps framework exceptions out of the orchestration logic.

### D6: Enforcement with ArchUnit (test scope)
Encode: processing domain may not import Micrometer/Spring/MinIO/Kafka/Jackson; the Kafka adapter holds no orchestration; concrete clients only in `adapter/out/*`.

## Risks / Trade-offs

- **Idempotency + transaction semantics are easy to break if split across ports** → Keep dedupe+insert as a single `TransactionWriterPort` operation inside one transaction in the adapter; verify with existing duplicate-event and concurrency tests before merging.
- **Metric-name regressions would silently break dashboards** → Pin names in the adapter; assert them in a test; the Grafana dashboards depend on these exact names.
- **At-least-once redelivery behavior must be preserved** → Mirror the current consumer ack/retry behavior in the Kafka adapter; the domain stays idempotent via the write result.
- **Cross-slice dependency on parsing** → Sequence parsing first or wrap the existing router behind `TransactionParserPort` temporarily.

## Migration Plan

1. Add domain orchestrator skeleton, inbound/outbound ports, domain exceptions (additive).
2. Implement outbound adapters wrapping existing `MinioStorageService`, `GatewayStatusClient`, `TransactionPersistenceService`, and Micrometer code.
3. Rewrite `DocumentProcessor` to depend only on ports; delete Micrometer/Spring imports.
4. Reduce `DocumentEventListeners` to a Kafka adapter calling `ProcessDocumentUseCase`.
5. Re-point tests; add domain unit tests (no Kafka/MinIO/REST/Micrometer) and ArchUnit tests.
6. Delete dead coupled code.

Rollback: additive until step 6, isolated to the processing package tree; revert the PR. No data migration.

## Open Questions

- Should the domain own the retry/timeout policy, or stay in the status-reporter adapter (`@Retryable`)? Default: keep retry in the adapter (it is a transport concern) and surface a terminal failure to the domain.
- Share the domain `Transaction` type with the parsing slice directly, or map at the `TransactionParserPort` boundary? Default: share one domain `Transaction` type across both parser-service slices.
