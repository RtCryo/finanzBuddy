# API Gateway

Spring Boot 4.1 API Gateway for the finanzBuddy platform. Provides JWT authentication, document upload to S3-compatible object storage (SeaweedFS), Kafka event publishing, and document query endpoints.

## Requirements

- Java 25
- Maven 3.9+
- Docker & Docker Compose (for local infrastructure)

## Build

```bash
mvn clean package
```

## Run locally with Docker Compose

From the repository root:

```bash
docker-compose up --build
```

This starts PostgreSQL, Kafka, SeaweedFS (S3-compatible object storage), and the API Gateway.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `local` |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `finanzbuddy` |
| `DB_USER` | PostgreSQL user | `finanzbuddy` |
| `DB_PASSWORD` | PostgreSQL password | `finanzbuddy` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:29092` |
| `MINIO_ENDPOINT` | S3 object-storage endpoint (SeaweedFS) | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | S3 access key | `minioadmin` |
| `MINIO_SECRET_KEY` | S3 secret key | `minioadmin` |
| `MINIO_BUCKET` | Object-storage bucket for documents | `finanzbuddy-documents` |
| `JWT_SECRET` | Base64-encoded JWT signing secret | **required, no default** — set in `.env` (`openssl rand -base64 32`) |
| `JWT_EXPIRATION_MS` | JWT expiration in milliseconds | `86400000` |
| `INTERNAL_API_KEY` | Shared key for internal service endpoints (`/internal/**`) | **required, no default** — `/internal/**` is locked until set |
| `OUTBOX_POLL_INTERVAL_MS` | Interval the outbox relay polls for undelivered events | `5000` |
| `UPLOAD_ACCEPTED_TYPES` | Comma-separated document types accepted for upload | `CSV` |

## API Endpoints

### Authentication

#### Register

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

Returns `201 Created` with a JWT token.

#### Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

Returns `200 OK` with a JWT token.

### Documents

All document endpoints require a valid JWT token in the `Authorization: Bearer <token>` header.

#### Upload document

```bash
curl -X POST http://localhost:8080/documents \
  -H "Authorization: Bearer <token>" \
  -F "file=@report.csv" \
  -F "type=CSV"
```

Recognised types: `CSV`, `PDF`, `XML`. In the MVP only `CSV` is **accepted** (configurable via
`UPLOAD_ACCEPTED_TYPES`); `PDF`/`XML` return `400 Bad Request` until the parser supports them.
Returns `201 Created` on success.

#### List own documents

```bash
curl "http://localhost:8080/documents?page=0&size=20" \
  -H "Authorization: Bearer <token>"
```

#### Get document metadata

```bash
curl http://localhost:8080/documents/<id> \
  -H "Authorization: Bearer <token>"
```

Returns `404 Not Found` if the document does not belong to the authenticated user.

#### List document transactions

```bash
curl http://localhost:8080/documents/<id>/transactions \
  -H "Authorization: Bearer <token>"
```

Returns the normalized transactions parsed from the document (empty until the parser service has
processed it). Returns `404 Not Found` if the document does not belong to the authenticated user.

#### Download original file

```bash
curl -OJ http://localhost:8080/documents/<id>/download \
  -H "Authorization: Bearer <token>"
```

Streams the originally uploaded file from object storage with a `Content-Disposition` filename.
Returns `404 Not Found` if the document does not belong to the authenticated user.

### Internal endpoints

Endpoints under `/internal/**` are for trusted backend services (e.g. the parser service)
and are authenticated with a shared service API key in the `X-Internal-Api-Key` header rather
than a user JWT. Requests without a valid key receive `401 Unauthorized`.

#### Update document status

```bash
curl -X PUT http://localhost:8080/internal/documents/<id>/status \
  -H "X-Internal-Api-Key: <internal-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"status":"PROCESSED"}'
```

Allowed status values: `PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`. Allowed transitions:
`PENDING→PROCESSING`, `PROCESSING→PROCESSED`, `PROCESSING→FAILED`, `PENDING→FAILED`,
`FAILED→PENDING`. When reporting `FAILED`, include an optional `"message"` field to record the
error. Status updates reference documents by id only (they are not scoped to the uploading user).

### Health & Metrics

```bash
curl http://localhost:8080/actuator/health
```

Prometheus metrics are exposed publicly at `/actuator/prometheus`:

```bash
curl http://localhost:8080/actuator/prometheus
```

## Kafka Topics

Documents are published to topics named `documents.<type>` (e.g., `documents.csv`, `documents.pdf`, `documents.xml`).

Events are written to a transactional outbox (`outbox_events`) in the same database transaction
that finalizes the upload, then delivered to Kafka by a background relay. This guarantees an event
is never lost if the broker is unavailable at upload time — it is retried until delivery succeeds.
A separate scheduled reconciler (the `cleanup` slice) removes documents left in `PENDING` with no
stored file (uploads interrupted before completion).

## Object Storage

Uploaded files are stored in S3-compatible object storage (SeaweedFS) under
`users/{userId}/{documentId}-{originalFilename}`. Access is via the `io.minio` S3 client, so the
`MINIO_*` variable names are retained even though the backend is SeaweedFS. The bucket is created at
startup if it does not exist (`app.minio.create-bucket`, default `true`).

## Internal structure (hexagonal)

The `document`, `auth`, `cleanup`, and `security` slices are organized as ports-and-adapters
(hexagonal): business rules live in a framework-free domain and reach infrastructure only through
ports. `document` was the pilot for the pattern; `auth`, `cleanup`, and `security` followed.

The `document` slice:

```
com.finanzbuddy.api.document/
├── domain/            DocumentService (upload/list/get/transactions/download/status), model,
│                      domain exceptions — no Spring, JPA, MinIO, Jackson, or web types
├── port/
│   ├── in/            UploadDocumentUseCase, ListDocumentsUseCase, GetDocumentUseCase,
│   │                  ListTransactionsUseCase, DownloadOriginalFileUseCase,
│   │                  UpdateDocumentStatusUseCase
│   └── out/           DocumentRepositoryPort, TransactionReadPort, FileStoragePort,
│                      DocumentEventPort, TransactionRunnerPort
├── adapter/
│   ├── in/web/        DocumentController, InternalDocumentController, DTOs, DTO↔domain mapper
│   └── out/
│       ├── persistence/  JPA entities + Spring Data repositories + repository adapters
│       ├── storage/      MinIO/S3 file-storage adapter (the only io.minio imports)
│       ├── messaging/    outbox event adapter (Jackson), relay, retention, metrics
│       └── tx/           Spring transaction-runner adapter
└── config/            Spring wiring that constructs the domain DocumentService from port beans
```

`MultipartFile` and Spring Data `Page`/`Pageable` never cross the domain boundary — the web adapter
converts them to an `UploadDocumentCommand` and a domain `PageRequest`/`PageResult`. Domain errors
are domain exceptions mapped to HTTP in `ApiExceptionHandler`. `DocumentArchitectureTest` (ArchUnit)
fails the build on any boundary violation.

The `auth` slice follows the same shape. Business rules for registration and login live in a
framework-free domain and reach infrastructure only through ports:

```
com.finanzbuddy.api.auth/
├── domain/            AuthService (register + login), domain model, domain exceptions — no Spring
├── port/
│   ├── in/            RegisterUserUseCase, LoginUseCase (driven by the web adapter)
│   └── out/           UserRepositoryPort, PasswordHasherPort, CredentialAuthenticatorPort,
│                      AccessTokenIssuerPort, TransactionRunnerPort
├── adapter/
│   ├── in/web/        AuthController + request/response DTOs + ByteLength validation
│   └── out/
│       ├── security/      Spring Security adapters (PasswordEncoder, AuthenticationManager,
│       │                  UserDetailsService) and the JWT token issuer
│       └── persistence/   Spring Data user repository + transaction runner
└── config/            Spring wiring that constructs the domain AuthService from port beans
```

The domain never imports `org.springframework.*`, JPA, Jackson, or web types; Spring Security and
Spring Data appear only inside `adapter/out/*`. These boundaries are enforced at build time by
`AuthArchitectureTest` (ArchUnit), which fails the build on any violation.

The `cleanup` slice holds the stale-`PENDING` reconciliation rule. It is a thin slice that reuses
the `document` slice's outbound ports (`DocumentRepositoryPort` — including the stale-PENDING finder
and the conditional `deleteByIdIfStale` — plus `FileStoragePort` and `TransactionRunnerPort`) and
adds a `ClockPort` so the staleness window is unit-testable without wall-clock time:

```
com.finanzbuddy.api.cleanup/
├── domain/            StalePendingReconciliation — select stale PENDING docs with no storage path,
│                      conditionally delete each row while still stale, then remove the orphaned
│                      object best-effort; reads time via ClockPort — no Spring, JPA, or MinIO
├── port/
│   ├── in/            ReconcileStalePendingUseCase (one reconciliation pass)
│   └── out/           ClockPort (reuses document DocumentRepositoryPort, FileStoragePort,
│                      TransactionRunnerPort)
├── adapter/
│   ├── in/scheduler/  ScheduledReconcilerTrigger (@Scheduled → calls the use case, no logic)
│   └── out/clock/     SystemClockAdapter
└── config/            Spring wiring that constructs the domain reconciliation from port beans
```

`@Scheduled`, Spring Data `PageRequest`, and MinIO never appear in the cleanup domain; the scheduler
is a thin trigger. `CleanupArchitectureTest` (ArchUnit) enforces these boundaries.

The `security` slice holds the authentication *decisions* — validate a bearer token and resolve its
principal, and verify the internal api-key — behind ports, so the JWT library and the servlet stack
become replaceable details. The servlet filters are thin inbound adapters that delegate to the
inbound ports and populate the `SecurityContext`; `SecurityConfig` remains the Spring Security
wiring:

```
com.finanzbuddy.api.security/
├── domain/            RequestAuthenticator (bearer token → principal), InternalAuthenticator
│                      (api-key → service principal), model (Principal, TokenValidationResult),
│                      exceptions (InvalidTokenException, ExpiredTokenException) — no Spring,
│                      servlet, or io.jsonwebtoken types
├── port/
│   ├── in/            AuthenticateRequestUseCase, AuthenticateInternalUseCase (driven by filters)
│   └── out/           AccessTokenPort (issue/validate), ApiKeyPort (constant-time compare),
│                      PrincipalLookupPort (backed by the auth slice's UserRepositoryPort)
├── adapter/
│   ├── in/web/        JwtAuthenticationFilter, InternalApiKeyAuthFilter (thin servlet adapters)
│   └── out/
│       ├── token/     JwtAccessTokenAdapter (the only io.jsonwebtoken imports)
│       ├── apikey/    ApiKeyMatcherAdapter (constant-time comparison of the configured key)
│       └── user/      PrincipalLookupAdapter (loads the principal via UserRepositoryPort)
└── SecurityConfig     Spring Security filter chain + wiring of the domain authenticators
```

`AccessTokenPort` is the reusable token contract: the `auth` slice issues login/registration tokens
through it. Library exceptions never leave the token adapter — token failures are domain results
(`TokenValidationResult`) or domain exceptions, mapped to the existing 401 behavior at the filter
edge. `SecurityArchitectureTest` (ArchUnit) enforces these boundaries.

## Design decisions & accepted limitations

These are intentional and have come up in review before — documented here so they are not
re-raised as defects.

- **At-least-once Kafka delivery.** The relay treats a send timeout as a failure and retries, but
  does not cancel the in-flight send — with `acks=all` and producer retries the broker may still
  commit the original, so a `DocumentUploadedEvent` can be published more than once. This is an
  accepted property of the outbox pattern; the parser-service consumer is idempotent (dedupes on
  `documentId`), so duplicates are harmless. Events for one document are still delivered in order
  (the relay only publishes an aggregate's oldest undelivered event, keyed on `aggregateId`).

- **Single-instance outbox relay (operational contract).** The relay reads pending events with a
  plain query — there is no `FOR UPDATE SKIP LOCKED` or leader election. This is a deliberate
  operational contract: **the gateway must run as a single instance**. `@Scheduled(fixedDelay)`
  prevents overlapping polls on that one instance. Scaling horizontally without adding a claim
  step will not lose events (delivery is at-least-once and the parser service consumer is
  idempotent), but it will produce duplicate publishes. Add `FOR UPDATE SKIP LOCKED` or a
  leader-election mechanism before running multiple gateway instances.

- **Registration returns `409 Conflict` for an existing email (user enumeration).** This is an
  accepted trade-off: hiding it (returning `201` for a duplicate) would be a functional regression
  — the client legitimately needs to know registration did not create an account, and there is no
  out-of-band email flow to compensate. Login is not affected: it returns a generic
  `401 Unauthorized` for both unknown emails and wrong passwords.

- **Integration tests run on H2, not Postgres.** Controller/service tests use H2
  (`create-drop`). Entity↔migration drift is caught by `LiquibaseMigrationTest`, which runs the
  real Liquibase changelog on H2 (PostgreSQL mode) and asserts column presence and nullability
  parity. A full Testcontainers-Postgres suite (production-parity DB in CI) is a deliberate
  deferred follow-up — it requires Docker in CI and is a larger infrastructure change.

- **Transactions are read cross-service via JDBC, not a JPA entity.** The `transactions` table is
  owned and written by the parser-service; the gateway only reads it (for
  `GET /documents/{id}/transactions`). It uses a JDBC query rather than a mapped `@Entity`
  deliberately: the gateway starts before the parser-service (see `docker-compose.yml`), so with
  `ddl-auto: validate` a JPA entity would fail the gateway's startup on a fresh database where the
  table does not yet exist. The JDBC read takes no schema-validation dependency on another
  service's table.

- **Two Jackson mappers coexist by design.** Spring Boot 4 auto-configures a Jackson 3
  (`tools.jackson`) `ObjectMapper` for HTTP message conversion. `JacksonConfig` additionally
  provides a Jackson 2 (`com.fasterxml`) `ObjectMapper` bean used only for outbox payload
  (de)serialization; Boot does not expose a Jackson 2 bean, so this is required. It intentionally
  does not participate in web (de)serialization — do not remove it.
