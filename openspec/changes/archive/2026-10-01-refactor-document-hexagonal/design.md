## Context

The api-gateway `document` slice currently uses a layered-but-coupled design. `DocumentService` is the whole business layer and it imports frameworks directly: `io.minio` for storage, Jackson for event payloads, Spring Web (`MultipartFile`, `ResponseStatusException`, `HttpStatus`) for transport, Spring Data (`Page`/`Pageable`) for paging, and `TransactionTemplate`/`PlatformTransactionManager` for transactions. JPA `@Entity` classes (`Document`, `OutboxEvent`) double as the domain model. The result: business rules cannot be unit-tested without Spring, and replacing any framework edits core logic.

This design applies the hexagonal (ports & adapters) pattern to the `document` capability only, as a reusable pilot. Core-purity decision: pragmatic — the domain may use `jakarta.inject` and `jakarta.validation`, but not Spring, minio, JPA, Jackson, or web types.

## Goals / Non-Goals

**Goals:**
- Business logic for documents lives in a framework-free domain layer behind interfaces.
- Every outbound concern (persistence, storage, messaging, transactions, serialization) is a port with a single adapter implementation.
- Every use case is an inbound port the web adapter calls.
- Framework swap (e.g. minio → AWS SDK, Spring MVC → something else) touches only adapters.
- Behavior is preserved; existing tests keep passing; new ArchUnit tests enforce the boundaries.

**Non-Goals:**
- No change to `auth`, `security` filters, `cleanup`, or parser-service (follow-up changes).
- No change to the REST contract, Kafka topics, DB schema, or runtime dependencies (no minio→AWS swap in this change — only the ability to do so later).
- Not introducing a second DI container or a multi-module Maven split; the domain stays in the same module, isolated by package + enforced by tests.

## Decisions

### D1: Package structure under `com.finanzbuddy.api.document`
```
document/
  domain/
    model/        Document, DocumentStatus, Transaction (domain), DocumentPage ...
    DocumentService            (implements inbound ports; the only business class)
    exception/    DocumentNotFoundException, UnauthorizedDocumentAccessException,
                  UploadFailedException, DownloadUnavailableException ...
  port/
    in/           UploadDocumentUseCase, ListDocumentsUseCase, GetDocumentUseCase,
                  ListTransactionsUseCase, DownloadOriginalFileUseCase
    out/          DocumentRepositoryPort, TransactionReadPort, FileStoragePort,
                  DocumentEventPort, TransactionRunnerPort
  adapter/
    in/web/       DocumentController, InternalDocumentController, DTOs, request/response mappers
    out/persistence/  Document/OutboxEvent JPA entities, Spring Data repos,
                      JpaDocumentRepositoryAdapter, JpaTransactionReadAdapter
    out/storage/      MinioFileStorageAdapter, MinioConfig, MinioHealthIndicator
    out/messaging/    OutboxEventAdapter (+ Jackson payload mapping), OutboxRelay stays here
    out/tx/           SpringTransactionRunner (wraps TransactionTemplate)
```
Rationale: a single module keeps the diff manageable; the `domain` vs `adapter` boundary is explicit and machine-checkable. Alternative considered — split into Maven modules (`document-domain`, `document-adapters`) which gives compile-time enforcement for free, but adds build complexity and a bigger blast radius; rejected for a pilot. ArchUnit gives us the same guarantee at test time.

### D2: Domain model separate from JPA entities
`domain/model/Document` is a plain record/class; the JPA `@Entity` lives in `adapter/out/persistence`. The persistence adapter maps between them. Rationale: entities carry `jakarta.persistence` and lifecycle semantics that must not leak into the core. Trade-off: hand-written mapping boilerplate — accepted; it is the price of decoupling and keeps optimistic-locking/`@Version` concerns inside the adapter.

### D3: Upload input and paging as domain types
Replace `MultipartFile` with an `UploadDocumentCommand(byte[] content, String filename, String contentType, DocumentType type, String ownerEmail)` built in the web adapter. Replace `Page`/`Pageable` with domain `PageRequest(page, size, filenameFilter)` and `PageResult<T>(items, totalElements, totalPages, page, size, …)`. Rationale: these Spring types are the most insidious leak because they ride through method signatures. The web adapter adapts `MultipartFile`→bytes and `Pageable`→`PageRequest`.

### D4: Errors as domain exceptions, mapped at the edge
Core throws domain exceptions; `ApiExceptionHandler` (web) maps them to HTTP (404/401/409/500) and problem responses. The current `ResponseStatusException` usages in `DocumentService` move into the handler as a mapping table. Rationale: HTTP status is a transport concern. Note optimistic-lock (`OptimisticLockingFailureException`) is caught in the persistence adapter and re-thrown as a domain `ConcurrentModificationException`-style type.

### D5: Transactions via a port
`TransactionRunnerPort.run(Supplier<T>)` / `runInTransaction(Runnable)` wraps `TransactionTemplate`. The domain declares transactional boundaries by calling the port, not via `@Transactional` on domain methods. Rationale: `@Transactional` is Spring. Alternative — keep `@Transactional` on an application-service that sits between web and domain; rejected because it reintroduces Spring into the orchestration layer we want portable. Trade-off: slightly more explicit transaction scoping in code.

### D6: Enforcement with ArchUnit (test scope)
Add ArchUnit tests encoding the spec rules: domain package may not depend on the forbidden packages; `port.out` interfaces may not reference framework transport types; `adapter.in.web` may not depend on `domain.DocumentService` concretely; concrete clients (`MinioClient`, Spring Data repos, `ObjectMapper`, Kafka template) only in `adapter.out.*`. Rationale: cheap, runs in the normal test phase, prevents regression. Alternative — Maven module boundaries (D1) or manual review; ArchUnit chosen for low cost + living documentation.

## Risks / Trade-offs

- **Large mechanical diff touching many files** → Do it as behavior-preserving moves + mapping; lean on the existing document test suite (9 gateway tests) re-pointed to new packages as the safety net; land in one reviewable PR for the slice.
- **Mapping boilerplate (domain ↔ entity, command ↔ MultipartFile)** → Accepted cost of isolation; keep mappers small and in the adapter; no mapping framework added.
- **Over-abstraction / port explosion** → Scope ports to the five real outbound concerns and five use cases only; do not add ports speculatively.
- **Hidden transactional-boundary bugs when moving off `@Transactional`** → Mirror current boundaries exactly in `TransactionRunnerPort` calls; verify with existing concurrency/outbox tests before merging.
- **Pattern not yet validated for auth/security** → Intentional; this is the pilot. Capture learnings in the change before replicating.

## Migration Plan

1. Introduce domain model, inbound/outbound ports, and domain exceptions (new packages, no deletions yet).
2. Implement adapters wrapping the existing framework code; move minio/JPA/Jackson/Kafka imports into them.
3. Rewrite `DocumentService` to depend only on ports; delete framework imports from it.
4. Re-point controllers to inbound ports; move exception mapping into `ApiExceptionHandler`.
5. Re-point existing tests; add domain unit tests (no Spring) and ArchUnit tests.
6. Delete now-dead coupled code.

Rollback: the change is isolated to the `document` package tree and additive until step 6; revert the PR to restore the prior structure. No data migration, so rollback is code-only.

## Open Questions

- Keep `OutboxRelay`/`OutboxRetentionJob` (scheduled infra) classified purely as outbound-messaging adapter, or treat the outbox as its own capability later? Leaning adapter for this change.
- Is a shared `PageRequest`/`PageResult` worth promoting to a common package now, anticipating the auth slice reuse, or keep it document-local until a second consumer exists? Default: keep local (avoid premature sharing).
