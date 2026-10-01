## ADDED Requirements

### Requirement: Domain layer is free of framework dependencies
The document domain layer SHALL depend only on `java.*`, project domain types, and the dependency-injection/validation API packages `jakarta.inject.*` and `jakarta.validation.*`. It SHALL NOT import `org.springframework.*`, `io.minio.*`, `jakarta.persistence.*`, `com.fasterxml.*`, or any web/servlet type.

#### Scenario: Domain class imports a forbidden framework package
- **WHEN** a class in the document domain package imports `org.springframework.*`, `io.minio.*`, `jakarta.persistence.*`, `com.fasterxml.*`, or a web type
- **THEN** the architecture test suite fails the build and names the offending class and import

#### Scenario: Domain class uses only allowed packages
- **WHEN** a document domain class imports only `java.*`, project domain types, `jakarta.inject.*`, or `jakarta.validation.*`
- **THEN** the architecture test suite passes for that class

### Requirement: Outbound dependencies are reached only through ports
The document domain SHALL access persistence, object storage, event publishing, transaction demarcation, and payload serialization exclusively through outbound port interfaces (`DocumentRepositoryPort`, `TransactionReadPort`, `FileStoragePort`, `DocumentEventPort`, `TransactionRunnerPort`). Concrete framework clients SHALL live only in adapter classes that implement those ports.

#### Scenario: Domain references a concrete framework client
- **WHEN** a domain class references a Spring Data repository, `MinioClient`, a Kafka template, or `ObjectMapper` directly
- **THEN** the architecture test suite fails the build

#### Scenario: Adapter implements an outbound port
- **WHEN** a storage, persistence, or messaging adapter is defined
- **THEN** it implements the corresponding outbound port interface and is the only place the concrete framework client is imported

### Requirement: Inbound use cases are exposed through ports
Each document use case (upload document, list documents, get document, list transactions, download original file) SHALL be defined as an inbound port interface implemented by the domain. The web layer SHALL depend on these inbound ports and never on a concrete domain class.

#### Scenario: Web adapter depends on an inbound port
- **WHEN** `DocumentController` invokes a use case
- **THEN** it does so through an inbound port interface, not a concrete domain service type

### Requirement: Domain signals failures with domain exceptions
The document domain SHALL raise domain-specific exceptions (for example `DocumentNotFoundException`, `UnauthorizedDocumentAccessException`, `UploadFailedException`) rather than framework exceptions such as `ResponseStatusException`. The inbound web adapter SHALL translate domain exceptions into HTTP responses.

#### Scenario: Missing document in the domain
- **WHEN** a use case is asked for a document that does not exist or is not owned by the caller
- **THEN** the domain throws a domain exception and the web adapter maps it to HTTP 404, with no framework exception type present in the domain layer

### Requirement: Framework transport types do not cross the domain boundary
The document domain boundary SHALL use plain domain types for inputs and outputs. Web upload types (`MultipartFile`) and Spring Data paging types (`Page`, `Pageable`) SHALL NOT appear in inbound or outbound port signatures; the domain SHALL use an upload command carrying raw content, filename, and type, and a domain paging request/result instead.

#### Scenario: Port signature uses a framework transport type
- **WHEN** an inbound or outbound port method declares `MultipartFile`, `Page`, or `Pageable` in its signature
- **THEN** the architecture test suite fails the build

### Requirement: Observable behavior is preserved
The refactor SHALL NOT change the externally observable behavior of the document capability. REST paths, HTTP verbs, request/response payloads, HTTP status codes, document statuses, Kafka topic names, event payloads, and persisted schema SHALL remain identical to the pre-refactor behavior.

#### Scenario: Existing document API contract after refactor
- **WHEN** the pre-refactor document integration and controller tests run against the refactored code
- **THEN** they pass without modification to the asserted request/response contracts

### Requirement: Architecture rules are enforced automatically in the build
The build SHALL include automated architecture tests (ArchUnit or equivalent) that verify the domain-isolation, port-usage, inbound-port, domain-exception, and boundary-type rules above. These tests SHALL run as part of the standard test phase.

#### Scenario: Architecture test runs in CI
- **WHEN** the test phase executes locally or in CI
- **THEN** the architecture test suite runs and a violation of any document-architecture rule fails the build
