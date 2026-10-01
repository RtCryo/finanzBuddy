## 1. Scaffolding & dependencies

- [x] 1.1 Create top-level `cleanup/` slice: `domain`, `port/in`, `port/out`, `adapter/in/scheduler`, `adapter/out/clock`
- [x] 1.2 Add ArchUnit test-scoped dependency (if not already added by a prior slice)
- [x] 1.3 Confirm `jakarta.inject`/`jakarta.validation` API available to the domain

## 2. Ports

- [x] 2.1 Define inbound port `ReconcileStalePendingUseCase` (one reconciliation pass)
- [x] 2.2 Define `ClockPort`; reuse document slice `DocumentRepositoryPort`, `FileStoragePort`, `TransactionRunnerPort`
- [x] 2.3 Extend `DocumentRepositoryPort` with the stale-PENDING finder (null storage path, created before cutoff, domain-paged) and conditional `deleteByIdIfStale` if not already present

## 3. Outbound adapters

- [x] 3.1 Implement `SystemClockAdapter` (`ClockPort`)
- [x] 3.2 Ensure the document persistence adapter implements the extended `DocumentRepositoryPort` methods; ensure `FileStoragePort` supports best-effort object removal

## 4. Domain

- [x] 4.1 Implement `StalePendingReconciliation` (`ReconcileStalePendingUseCase`): compute cutoff from `ClockPort`, select candidates, conditional delete per row via `TransactionRunnerPort`, best-effort object removal only when a row was deleted
- [x] 4.2 Preserve the two-step ordering exactly (select → conditional delete → outside-transaction object removal); keep batch sizing
- [x] 4.3 Verify no forbidden imports remain in the cleanup domain layer

## 5. Inbound scheduler adapter

- [x] 5.1 Implement `ScheduledReconcilerTrigger` with `@Scheduled` (same interval property) that only calls the use case

## 6. Tests & enforcement

- [x] 6.1 Re-point existing reconciler tests to new packages; confirm behavior unchanged
- [x] 6.2 Add fake-clock domain unit tests: stale-is-deleted, finalized-between-scan-and-delete is NOT deleted, storage-removal-failure is swallowed (no Spring/MinIO)
- [x] 6.3 Add ArchUnit tests for domain isolation, scheduler-holds-no-logic, clock-via-port
- [x] 6.4 Run the full api-gateway test suite + architecture suite; fix violations

## 7. Cleanup & docs

- [x] 7.1 Delete `StalePendingReconciler` and its `io.minio`/Spring Data imports
- [x] 7.2 Update `api-gateway/README.md` and `docs/ARCHITECTURE.md` for the cleanup slice structure
