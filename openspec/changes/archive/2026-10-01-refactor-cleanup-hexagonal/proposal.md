## Why

`StalePendingReconciler` is a scheduled job whose reconciliation logic is fused to infrastructure: it imports `io.minio` (orphaned-object deletion), Spring Data `PageRequest`, `@Scheduled`, and `TransactionTemplate`. The business rule — "documents left in PENDING with no `storage_path` past a threshold are deleted (the signature of an upload whose metadata transaction never completed), and their orphaned storage object is removed best-effort" — cannot be tested or re-timed without Spring and MinIO, and the scheduler/storage/clock cannot be swapped independently.

This is the fourth slice of the api-gateway hexagonal refactor, applying the pattern from [refactor-document-hexagonal]. It depends on the document slice because it reuses that slice's `DocumentRepositoryPort` and `FileStoragePort`, so it should land after [refactor-document-hexagonal].

## What Changes

- Introduce a framework-free **cleanup domain**: `StalePendingReconciliation` business logic (select stale PENDING documents with no `storage_path` older than the threshold, conditionally delete each row only while it is still stale, then delete its orphaned storage object best-effort) depending only on `java.*`, domain types, and (pragmatically) `jakarta.inject`/`jakarta.validation`.
- Define **inbound port**: `ReconcileStalePendingUseCase` (one reconciliation pass, invoked by the scheduler).
- Reuse **outbound ports** from the document slice (`DocumentRepositoryPort` — including the conditional `deleteByIdIfStale` and the stale-PENDING finder, `FileStoragePort`, `TransactionRunnerPort`) and add a `ClockPort` so the staleness threshold is testable without wall-clock time; replace Spring Data `PageRequest` at the boundary with the domain paging type.
- Move framework code into **adapters**: a Spring `@Scheduled` **trigger adapter** that only calls `ReconcileStalePendingUseCase`; storage/persistence/transaction concerns delegated to the existing document adapters.
- Add architecture (ArchUnit) tests enforcing the cleanup domain-isolation and port rules.
- **No external behavior change**: the same stale no-storage-path PENDING documents are deleted (conditionally, only while still stale) and the same orphaned objects removed best-effort, on the same schedule.

## Capabilities

### New Capabilities
- `cleanup-architecture`: Structural requirements for the cleanup slice — domain isolation of reconciliation logic, inbound use-case port, reuse of document outbound ports, scheduler-as-adapter and clock-as-port, and automated enforcement. Verifiable constraints, not functional behavior.

### Modified Capabilities
<!-- None. Observable reconciliation behavior and schedule are unchanged. -->

## Impact

- **Code (api-gateway only)**: `document/cleanup/StalePendingReconciler.java` split into `cleanup/domain` (reconciliation logic), `cleanup/port/in` (`ReconcileStalePendingUseCase`), `cleanup/adapter/in/scheduler` (the `@Scheduled` trigger). `ClockPort` + adapter added.
- **Dependency on document slice**: consumes `DocumentRepositoryPort`/`FileStoragePort`/`TransactionRunnerPort` from [refactor-document-hexagonal]; must be sequenced after it.
- **APIs**: none (internal scheduled job). **Config**: unchanged (staleness threshold / schedule properties).
- **Out of scope**: document request-path refactor (its own change), auth, security.
- **Tests**: existing reconciler tests re-pointed; new domain unit tests using a fake clock and fake ports (no Spring/MinIO) and ArchUnit tests added.
