## ADDED Requirements

### Requirement: Cleanup domain is free of framework dependencies
The cleanup domain layer SHALL depend only on `java.*`, project domain types, and `jakarta.inject.*`/`jakarta.validation.*`. It SHALL NOT import `io.minio.*`, `org.springframework.*` (including Spring Data and `@Scheduled`), or `jakarta.persistence.*`.

#### Scenario: Cleanup domain class imports a forbidden package
- **WHEN** a class in the cleanup domain package imports `io.minio.*`, `org.springframework.*`, or `jakarta.persistence.*`
- **THEN** the architecture test suite fails the build and names the offending class and import

#### Scenario: Cleanup domain class uses only allowed packages
- **WHEN** a cleanup domain class imports only `java.*`, project domain types, `jakarta.inject.*`, or `jakarta.validation.*`
- **THEN** the architecture test suite passes for that class

### Requirement: Reconciliation is exposed through an inbound port
A single reconciliation pass SHALL be defined as an inbound port `ReconcileStalePendingUseCase` implemented by the domain. The scheduler SHALL depend on this port and SHALL NOT contain reconciliation logic.

#### Scenario: Scheduler triggers the use case
- **WHEN** the scheduled trigger fires
- **THEN** it invokes `ReconcileStalePendingUseCase` and performs no selection, deletion, or storage logic itself

### Requirement: Infrastructure is reached only through ports
The cleanup domain SHALL reach persistence, object storage, transactions, and the current time exclusively through ports — reusing the document slice's `DocumentRepositoryPort` (stale-PENDING finder + conditional `deleteByIdIfStale`), `FileStoragePort`, and `TransactionRunnerPort`, plus a `ClockPort`. MinIO, Spring Data, and `TransactionTemplate` SHALL appear only in adapters.

#### Scenario: Domain references MinIO or Spring Data directly
- **WHEN** a cleanup domain class references `MinioClient`, Spring Data `PageRequest`, or `TransactionTemplate`
- **THEN** the architecture test suite fails the build

#### Scenario: Clock is injected via a port
- **WHEN** the domain computes the staleness cutoff
- **THEN** it reads the current instant from `ClockPort`, enabling tests to drive time deterministically

### Requirement: Observable reconciliation behavior is preserved
The refactor SHALL NOT change observable behavior. The domain SHALL select only `PENDING` documents whose `storage_path` is null and whose `createdAt` is older than the configured threshold, delete each row conditionally only while it is still stale, then remove the orphaned storage object best-effort — on the same schedule, in configurable batches.

#### Scenario: Stale no-storage-path PENDING document
- **WHEN** a `PENDING` document with a null storage path is older than the threshold at reconciliation time
- **THEN** its row is deleted conditionally and its orphaned object is removed best-effort, matching the pre-refactor outcome

#### Scenario: Upload finalized between scan and delete
- **WHEN** a document selected as stale has its storage path set (upload finalized) before the conditional delete runs
- **THEN** the row is NOT deleted and no object is removed

#### Scenario: Storage removal fails
- **WHEN** the orphaned-object removal fails or the object does not exist
- **THEN** the failure is swallowed best-effort and the pass continues, as before

### Requirement: Cleanup architecture rules are enforced automatically
The build SHALL include ArchUnit (or equivalent) tests verifying the cleanup domain-isolation, inbound-port, port-usage, and clock-port rules, running in the standard test phase.

#### Scenario: Cleanup architecture test runs in CI
- **WHEN** the test phase executes locally or in CI
- **THEN** the cleanup architecture suite runs and any violation fails the build
