## Context

`StalePendingReconciler` is a single `@Component` scheduled job. Its reconciliation rule — select `PENDING` documents with a null `storage_path` older than a threshold, conditionally delete each row while still stale, then best-effort remove the orphaned MinIO object — is fused to `io.minio`, Spring Data `PageRequest`, `@Scheduled`, and `TransactionTemplate`. It lives in `document/cleanup` but is a distinct responsibility. The staleness window cannot be exercised without real time, Spring, and MinIO.

This is the fourth slice of the api-gateway hexagonal refactor, applying the pattern from [refactor-document-hexagonal]. It reuses that slice's outbound ports, so it must land after it.

## Goals / Non-Goals

**Goals:**
- The reconciliation rule lives in a framework-free domain behind an inbound port.
- Persistence/storage/transactions reuse the document slice's ports; time is injected via `ClockPort`.
- The scheduler becomes a thin trigger adapter.
- Behavior preserved exactly (same selection, conditional delete, best-effort object removal, same schedule/batches); existing tests pass; ArchUnit enforces boundaries.

**Non-Goals:**
- No change to the document request-path slice (its own change) or to auth/security.
- No change to the schedule, threshold, batch-size properties, or DB schema.
- No new DI container; isolation by package + enforced by tests.

## Decisions

### D1: Package structure under `com.finanzbuddy.api.cleanup`
```
cleanup/
  domain/
    StalePendingReconciliation (implements ReconcileStalePendingUseCase)
  port/
    in/     ReconcileStalePendingUseCase
    out/    ClockPort            (reused: DocumentRepositoryPort, FileStoragePort,
                                  TransactionRunnerPort from the document slice)
  adapter/
    in/scheduler/   ScheduledReconcilerTrigger (@Scheduled → calls the use case)
    out/clock/      SystemClockAdapter
```
Rationale: promoting `cleanup` to a top-level slice (out of `document/cleanup`) makes the shared-port reuse explicit. Alternative — keep it nested under document; rejected because it is a separate responsibility with its own schedule and failure semantics.

### D2: Reuse document outbound ports; extend if needed
`DocumentRepositoryPort` must expose the stale-PENDING finder (`findByStatusAndStoragePathIsNullAndCreatedAtBefore` equivalent, domain-paged) and the conditional `deleteByIdIfStale`. If the document slice has not yet added these, this change extends that port. Rationale: a single persistence abstraction; no second repository path. Trade-off: a cross-slice coupling to the document port — acceptable and intentional (documented in the proposal).

### D3: Time via `ClockPort`
Replace `Instant.now()` with `ClockPort.now()`. Rationale: the staleness window is the core rule; injecting the clock makes it unit-testable without sleeping or wall-clock dependence.

### D4: Preserve the exact two-step delete semantics
The domain must keep: (1) select candidates in one transaction, (2) for each, conditional `deleteByIdIfStale` in its own transaction, (3) only if a row was deleted, best-effort remove the object outside any transaction. Transaction boundaries go through `TransactionRunnerPort`; storage removal through `FileStoragePort`. Rationale: this ordering is what prevents wiping a just-finalized upload and prevents slow storage pinning a DB connection — behavior that must not regress.

### D5: Scheduler as a trigger adapter
`@Scheduled` moves to `ScheduledReconcilerTrigger`, which only calls `ReconcileStalePendingUseCase`. Rationale: scheduling is Spring infrastructure; the domain exposes "run one pass" and is agnostic to what triggers it (scheduler, test, future manual endpoint).

### D6: Enforcement with ArchUnit (test scope)
Encode: cleanup domain may not import `io.minio`/`org.springframework`/`jakarta.persistence`; the scheduler holds no reconciliation logic; the domain reads time from `ClockPort`.

## Risks / Trade-offs

- **Subtle two-step delete ordering could regress (wiping finalized uploads / pinning DB connections)** → Move, don't rewrite; keep the conditional-delete + outside-transaction object removal exactly; cover with existing reconciler tests plus new fake-clock domain tests for the finalized-between-scan-and-delete case.
- **Cross-slice dependency on the document port** → Must sequence after the document slice; if a method is missing, extend `DocumentRepositoryPort` within this change.
- **Best-effort swallow of storage errors must stay** → Assert it in a domain test with a failing `FileStoragePort`.
- **Over-abstraction** → Only one inbound use case and one new port (`ClockPort`); reuse everything else.

## Migration Plan

1. Create the `cleanup` top-level slice packages; add `ReconcileStalePendingUseCase` and `ClockPort` (+ `SystemClockAdapter`).
2. Extend `DocumentRepositoryPort` with the stale finder + conditional delete if not present.
3. Move the reconciliation logic into `StalePendingReconciliation`, depending only on ports + `ClockPort`.
4. Replace `@Scheduled` with `ScheduledReconcilerTrigger` calling the use case.
5. Re-point existing tests; add fake-clock domain tests and ArchUnit tests.
6. Delete `StalePendingReconciler` and its `io.minio`/Spring Data imports.

Rollback: additive until step 6, isolated to the new `cleanup` tree + the one port extension; revert the PR. No data migration.

## Open Questions

- Keep `ClockPort` cleanup-local, or promote to a shared package anticipating reuse (e.g. token expiry in security)? Default: cleanup-local until a second consumer exists.
- Expose a manual "run reconciliation now" inbound adapter (endpoint) for ops, or scheduler-only? Default: scheduler-only (no behavior change in this slice).
