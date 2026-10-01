## Why

The `document` slice of the api-gateway tangles business logic with framework concerns. `DocumentService` directly imports `io.minio.*` (object storage), `com.fasterxml.jackson.*` (serialization), `org.springframework.web.*` (`MultipartFile`, `ResponseStatusException`, `HttpStatus`), Spring Data (`Page`/`Pageable`), and Spring transaction primitives. Swapping any of these frameworks — or testing the logic without them — forces changes inside the core business code. We want the business rules isolated behind interfaces so the surrounding frameworks become replaceable details.

This change refactors the `document` capability to a hexagonal (ports & adapters) structure as a pilot. Auth, security, and the outbox relay follow in later changes using the same proven pattern.

## What Changes

- Introduce a framework-free **domain layer** for documents: `DocumentService` business logic depends only on `java.*`, project domain types, and (pragmatically) `jakarta.inject` / `jakarta.validation`. No `org.springframework.*`, `io.minio.*`, `jakarta.persistence.*`, `com.fasterxml.*`, or web types.
- Define **inbound ports** (use-case interfaces the web adapter calls): upload document, list documents, get document, list transactions, download original file.
- Define **outbound ports** (interfaces the domain depends on): `DocumentRepositoryPort`, `TransactionReadPort`, `FileStoragePort`, `DocumentEventPort`, `TransactionRunnerPort`.
- Move all framework code into **adapters**:
  - inbound web adapter: `DocumentController` + DTOs translate HTTP ↔ domain commands/results and map domain errors to HTTP status.
  - outbound adapters: JPA persistence (`Document`/`OutboxEvent` entities, Spring Data repos), MinIO/S3 storage, Kafka outbox event publishing, Spring transaction runner, Jackson payload serialization.
- Replace framework exceptions in the core (`ResponseStatusException`) with **domain exceptions** (`DocumentNotFoundException`, `UnauthorizedDocumentAccessException`, `UploadFailedException`, …); the web adapter maps them to HTTP responses in `ApiExceptionHandler`.
- Replace `MultipartFile` and `Page`/`Pageable` at the domain boundary with plain domain types (an upload command carrying bytes/filename/type; a domain `PageRequest`/`PageResult`).
- Add **architecture enforcement tests** (ArchUnit or equivalent) that fail the build if the domain layer imports a forbidden framework package or bypasses a port.
- **No external behavior change**: REST endpoints, request/response shapes, statuses, Kafka topics, and persistence remain identical. This is behavior-preserving.

## Capabilities

### New Capabilities
- `document-architecture`: Structural requirements for the document slice — domain isolation rules, mandatory inbound/outbound ports, adapter responsibilities, and the automated checks that enforce them. These are verifiable constraints, not functional behavior.

### Modified Capabilities
<!-- None. This refactor preserves all existing functional requirements. The specs
     document-upload, document-lifecycle, document-consumption, status-reporting and
     their observable behavior are unchanged. -->

## Impact

- **Code (api-gateway only)**: `document/service/DocumentService.java`, `document/controller/*`, `document/dto/*`, `document/DocumentStorage.java`, `storage/Minio*`, `document/outbox/*` reorganized into `document/domain`, `document/port/{in,out}`, `document/adapter/{in/web,out/persistence,out/storage,out/messaging}`. `entity/*` and `repository/*` for documents move under the persistence adapter.
- **APIs**: unchanged — same paths, verbs, payloads, status codes.
- **Dependencies**: add a test-scoped ArchUnit dependency; add `jakarta.inject`/`jakarta.validation` API on the domain if not already present. No production dependency swaps in this change.
- **Out of scope**: `auth`, `security` filters, `cleanup`/`StalePendingReconciler`, parser-service. They keep their current structure and are addressed in follow-up changes.
- **Tests**: existing document tests re-pointed to new packages; new domain unit tests (no Spring context) and architecture fitness tests added.
