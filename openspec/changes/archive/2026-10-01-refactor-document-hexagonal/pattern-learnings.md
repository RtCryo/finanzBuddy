# Pattern learnings (document pilot)

Captured for the follow-up auth/security/cleanup hexagonal refactors.

## Tooling
- **ArchUnit must be ≥ 1.4.x on Java 25.** 1.3.0's bundled ASM cannot read class-file major
  version 69 and silently imports **zero** classes, so every `failOnEmptyShould` rule fails with
  "failed to check any classes" rather than a real violation. Use `archunit-junit5` 1.4.1.
- **Scope the ArchUnit import to the slice and exclude out-of-scope subpackages.** Import
  `..<slice>.domain`, `..port`, `..adapter` only. The `cleanup` subpackage still uses `io.minio`
  directly (it is a separate, later slice), so importing it would trip the "clients confined to
  adapters" rule.

## Wiring
- **Framework-free domain services are wired with an explicit `@Configuration` `@Bean`**, not a
  stereotype annotation — `jakarta.inject` is not on the classpath, and this keeps the domain pure
  `java.*` (+ `org.slf4j`, which is not a forbidden package).
- **Per-slice `TransactionRunnerPort`/`SpringTransactionRunner` collide on the default bean name.**
  Two slices each contributing a `@Component SpringTransactionRunner` both resolve to the bean name
  `springTransactionRunner` → `ConflictingBeanDefinitionException` at context load. Give each an
  explicit bean name (e.g. `@Component("documentSpringTransactionRunner")`) or a unique class name.

## Behavior preservation
- **Translate `OptimisticLockingFailureException` inside the persistence adapter using
  `saveAndFlush`.** The flush (and thus the version check) then happens *inside* the port call, so
  the adapter can map it to a domain `ConcurrentDocumentModificationException` reliably — the
  original method-level try/catch around a commit-time flush was fragile.
- **Add an inbound port for every use case, including ones the enumerated task list omits.** The
  internal status-update endpoint needed `UpdateDocumentStatusUseCase`; without it the controller
  would depend on the concrete domain service and fail the "web depends on inbound ports" rule.
- **Convert framework transport types at the web edge.** `MultipartFile`→bytes and
  `Pageable`→domain `PageRequest` in the controller; request `type` string parsing
  (required/unsupported → 400) stays in the web adapter, while the business "type not accepted"
  check lives in the domain.
- **The storage path is part of the external contract** (persisted in `documents.storage_path` and
  carried in the Kafka event payload), so the storage adapter computes and returns it rather than
  the domain.
