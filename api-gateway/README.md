# API Gateway

Spring Boot 4.1 API Gateway for the finanzBuddy platform. Provides JWT authentication, document upload to MinIO, Kafka event publishing, and document query endpoints.

## Requirements

- Java 21
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

This starts PostgreSQL, Kafka (with Zookeeper), MinIO, and the API Gateway.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `local` |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `finanzbuddy` |
| `DB_USER` | PostgreSQL user | `finanzbuddy` |
| `DB_PASSWORD` | PostgreSQL password | `finanzbuddy` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:9092` |
| `MINIO_ENDPOINT` | MinIO endpoint | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO access key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret key | `minioadmin` |
| `MINIO_BUCKET` | MinIO bucket for documents | `finanzbuddy-documents` |
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

### Health

```bash
curl http://localhost:8080/actuator/health
```

## Kafka Topics

Documents are published to topics named `documents.<type>` (e.g., `documents.csv`, `documents.pdf`, `documents.xml`).

Events are written to a transactional outbox (`outbox_events`) in the same database transaction
that finalizes the upload, then delivered to Kafka by a background relay. This guarantees an event
is never lost if the broker is unavailable at upload time — it is retried until delivery succeeds.
A separate reconciler removes documents left in `PENDING` with no stored file (uploads interrupted
before completion).

## MinIO Storage

Uploaded files are stored under `users/{userId}/{documentId}-{originalFilename}`.

## Design decisions & accepted limitations

These are intentional and have come up in review before — documented here so they are not
re-raised as defects.

- **At-least-once Kafka delivery.** The relay treats a send timeout as a failure and retries, but
  does not cancel the in-flight send — with `acks=all` and producer retries the broker may still
  commit the original, so a `DocumentUploadedEvent` can be published more than once. This is an
  accepted property of the outbox pattern; the parser-service consumer is idempotent (dedupes on
  `documentId`), so duplicates are harmless. Events for one document are still delivered in order
  (the relay only publishes an aggregate's oldest undelivered event, keyed on `aggregateId`).

- **Single-instance outbox relay (no row locking / leader election).** The relay reads pending
  events with a plain query — there is no `FOR UPDATE SKIP LOCKED` or leader election. This is
  safe as deployed: the gateway runs as a **single instance**, so `@Scheduled(fixedDelay)` already
  prevents overlapping polls. It is also safe if scaled later, because delivery is at-least-once
  and the downstream consumer (parser service) is idempotent, so duplicate publishes are harmless.
  A claim step (or `SKIP LOCKED`) would only be needed for a multi-instance deployment with a
  non-idempotent consumer — and a naive lock would conflict with the design goal of not holding a
  DB connection during the Kafka round-trip, so it must be a deliberate change, not a quick patch.

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

- **Two Jackson mappers coexist by design.** Spring Boot 4 auto-configures a Jackson 3
  (`tools.jackson`) `ObjectMapper` for HTTP message conversion. `JacksonConfig` additionally
  provides a Jackson 2 (`com.fasterxml`) `ObjectMapper` bean used only for outbox payload
  (de)serialization; Boot does not expose a Jackson 2 bean, so this is required. It intentionally
  does not participate in web (de)serialization — do not remove it.
