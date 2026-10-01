## 1. Scaffolding & dependencies

- [x] 1.1 Create package tree under `document/`: `domain/model`, `domain/exception`, `port/in`, `port/out`, `adapter/in/web`, `adapter/out/persistence`, `adapter/out/storage`, `adapter/out/messaging`, `adapter/out/tx`
- [x] 1.2 Add ArchUnit as a test-scoped dependency to the api-gateway POM
- [x] 1.3 Ensure `jakarta.inject` / `jakarta.validation` API is available to the domain (confirm already on classpath or add)

## 2. Domain model & exceptions

- [x] 2.1 Add framework-free `domain/model` types: `Document`, `DocumentStatus`, domain `Transaction`, and `UploadDocumentCommand(content, filename, contentType, type, ownerEmail)`
- [x] 2.2 Add domain paging types `PageRequest(page, size, filenameFilter)` and `PageResult<T>`
- [x] 2.3 Add domain exceptions: `DocumentNotFoundException`, `UnauthorizedDocumentAccessException`, `UploadFailedException`, `DownloadUnavailableException`, concurrent-modification type

## 3. Ports

- [x] 3.1 Define inbound ports: `UploadDocumentUseCase`, `ListDocumentsUseCase`, `GetDocumentUseCase`, `ListTransactionsUseCase`, `DownloadOriginalFileUseCase` (signatures use domain types only)
- [x] 3.2 Define outbound ports: `DocumentRepositoryPort`, `TransactionReadPort`, `FileStoragePort`, `DocumentEventPort`, `TransactionRunnerPort` (no `MultipartFile`/`Page`/`Pageable` in signatures)

## 4. Outbound adapters (wrap existing framework code)

- [x] 4.1 Move `Document` and `OutboxEvent` JPA entities + Spring Data repositories into `adapter/out/persistence`
- [x] 4.2 Implement `JpaDocumentRepositoryAdapter` and `JpaTransactionReadAdapter` with domain↔entity mapping; translate `OptimisticLockingFailureException` to the domain concurrent-modification exception
- [x] 4.3 Move `MinioConfig`/`MinioHealthIndicator` and implement `MinioFileStorageAdapter` (`FileStoragePort`) holding the only `io.minio` imports
- [x] 4.4 Implement `OutboxEventAdapter` (`DocumentEventPort`) owning Jackson payload serialization; relocate `OutboxRelay`/`OutboxRetentionJob`/`OutboxMetrics` under `adapter/out/messaging`
- [x] 4.5 Implement `SpringTransactionRunner` (`TransactionRunnerPort`) wrapping `TransactionTemplate`

## 5. Domain service

- [x] 5.1 Rewrite `DocumentService` to implement all inbound ports and depend only on outbound ports + domain types
- [x] 5.2 Replace every `ResponseStatusException` with the matching domain exception
- [x] 5.3 Replace `@Transactional`/`TransactionTemplate` usage with `TransactionRunnerPort` calls, mirroring current transaction boundaries exactly
- [x] 5.4 Verify no forbidden imports remain in the domain layer (`org.springframework.*`, `io.minio.*`, `jakarta.persistence.*`, `com.fasterxml.*`, web types)

## 6. Inbound web adapter

- [x] 6.1 Re-point `DocumentController` and `InternalDocumentController` to inbound ports; build `UploadDocumentCommand` from `MultipartFile` and `PageRequest` from `Pageable` in the controller
- [x] 6.2 Keep request/response DTOs in `adapter/in/web`; map domain results ↔ DTOs
- [x] 6.3 Extend `ApiExceptionHandler` to map document domain exceptions → HTTP status (404/401/409/500), preserving current response bodies

## 7. Tests & enforcement

- [x] 7.1 Re-point existing document controller/integration tests to new packages; confirm they pass unchanged on the contract assertions
- [x] 7.2 Add domain unit tests for `DocumentService` using fake port implementations (no Spring context)
- [x] 7.3 Add ArchUnit tests for: domain forbidden-import rule, port signatures free of framework transport types, web depends on inbound ports (not concrete service), concrete clients only in `adapter/out/*`
- [x] 7.4 Run the full api-gateway test suite and the architecture suite; fix violations

## 8. Cleanup & docs

- [x] 8.1 Delete dead coupled code left behind by the move
- [x] 8.2 Update `api-gateway/README.md` and `docs/ARCHITECTURE.md` to describe the ports & adapters structure for the document slice
- [x] 8.3 Note pattern learnings in the change for the follow-up auth/security refactors
