# Parser Processing Architecture Specification

## Purpose

Defines the hexagonal-architecture rules for the document-processing capability so that the processing domain stays free of framework dependencies, exposes event handling through the `ProcessDocumentUseCase` inbound port, reaches infrastructure (file fetch, parsing, transaction persistence with idempotency, status reporting, and metrics) exclusively through outbound ports, uses domain exceptions instead of framework exceptions, preserves the observable processing behavior, and enforces these rules automatically in the build.

## Requirements

### Requirement: Processing domain is free of framework dependencies
The parser processing domain layer SHALL depend only on `java.*`, project domain types, and `jakarta.inject.*`/`jakarta.validation.*`. It SHALL NOT import `io.micrometer.*`, `org.springframework.*` (including Spring Kafka and Spring Data), `io.minio.*`, `org.apache.kafka.*`, or `com.fasterxml.*`.

#### Scenario: Processing domain class imports a forbidden package
- **WHEN** a class in the processing domain package imports `io.micrometer.*`, `org.springframework.*`, `io.minio.*`, `org.apache.kafka.*`, or `com.fasterxml.*`
- **THEN** the architecture test suite fails the build and names the offending class and import

#### Scenario: Processing domain class uses only allowed packages
- **WHEN** a processing domain class imports only `java.*`, project domain types, `jakarta.inject.*`, or `jakarta.validation.*`
- **THEN** the architecture test suite passes for that class

### Requirement: Event processing is exposed through an inbound port
Handling one `DocumentUploadedEvent` SHALL be defined as an inbound port `ProcessDocumentUseCase` implemented by the domain. The Kafka consumer SHALL depend on this port and contain no orchestration logic.

#### Scenario: Kafka adapter triggers the use case
- **WHEN** a document upload event is consumed from Kafka
- **THEN** the consumer adapter deserializes it and invokes `ProcessDocumentUseCase`, performing no fetch/parse/persist/report logic itself

### Requirement: Infrastructure is reached only through outbound ports
The processing domain SHALL perform file fetch, parsing, transaction persistence with idempotency, status reporting, and metrics exclusively through outbound ports (`FileFetchPort`, `TransactionParserPort`, `TransactionWriterPort`, `StatusReporterPort`, `ProcessingMetricsPort`). MinIO, the REST client, Spring Data, and Micrometer SHALL appear only in adapters implementing those ports.

#### Scenario: Domain references a concrete framework client
- **WHEN** a processing domain class references `MinioClient`, `RestClient`, a Spring Data repository, or `MeterRegistry`/`Counter`/`Timer`
- **THEN** the architecture test suite fails the build

#### Scenario: Metrics recorded via a port
- **WHEN** the domain records a processed, failed, or duplicate outcome or a processing duration
- **THEN** it does so through `ProcessingMetricsPort`, and Micrometer appears only in the metrics adapter

### Requirement: Processing failures use domain exceptions
The processing domain SHALL use domain exceptions (for example `DuplicateDocumentException`, a domain fetch failure, and parse/persist failures) rather than framework exceptions such as `DataAccessException` or `RestClientException`, which SHALL be translated inside adapters.

#### Scenario: Duplicate event
- **WHEN** an event for an already-processed document is handled
- **THEN** the domain treats it as a duplicate (recording the duplicate metric) and does not re-persist, with no framework exception type present in the domain layer

### Requirement: Observable processing behavior is preserved
The refactor SHALL NOT change observable behavior. Kafka topic consumption, idempotency via `ProcessedDocument`, the gateway status callback (`PROCESSED`/`FAILED`), emitted metric names, and persisted transactions SHALL remain identical.

#### Scenario: Existing processor/consumer/persistence tests after refactor
- **WHEN** the pre-refactor processing tests run against the refactored code
- **THEN** they pass without modification to the asserted behavior and metric names

### Requirement: Processing architecture rules are enforced automatically
The build SHALL include ArchUnit (or equivalent) tests verifying the processing domain-isolation, inbound-port, outbound-port, and domain-exception rules, running in the standard test phase.

#### Scenario: Processing architecture test runs in CI
- **WHEN** the test phase executes locally or in CI
- **THEN** the processing architecture suite runs and any violation fails the build
