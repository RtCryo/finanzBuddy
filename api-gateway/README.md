# API Gateway

Spring Boot 4.1 API Gateway for the finanzBuddy platform. Provides JWT authentication, document upload to S3-compatible object storage (SeaweedFS), Kafka event publishing, and document query endpoints.

Documentation map: [API Gateway implementation, capabilities, and history](../docs/README.md#subsystem-map) · [cross-service architecture](../docs/ARCHITECTURE.md).

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

### Related transaction expenses

`GET /transactions/{id}/related-expenses?page=0&size=20` requires a user JWT.
For example: `curl "http://localhost:8080/transactions/7/related-expenses?page=0&size=20" -H "Authorization: Bearer <token>"`.

The gateway authorizes both the source transaction and its processed document against the
authenticated owner, returning `404` for missing, foreign, or unprocessed sources. It derives
the counterparty from the latest source snapshot; historical selection and history filters do
not affect matching. A null, empty, or whitespace-only source counterparty returns an empty page.
Candidates use their latest snapshots across the owner's processed documents, match the stored
counterparty exactly (case and whitespace sensitive), have negative amounts, and exclude the
source ID. Both candidate transaction and document ownership must match.

The response contains `items` (`id`, `valueDate`, `amount`, `currency`), `page`, `size`,
`totalElements`, and `totalPages`. Rows sort by value date descending, null dates last, then ID
ascending. Pages start at zero; negative pages and nonpositive sizes return `400`; size is capped
at 100. Clients can request successive pages until `page + 1 >= totalPages`.

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

Custom document and outbox metrics:

| Metric | Type | Description | Aggregation notes |
|--------|------|-------------|-------------------|
| `api.documents.uploads` | Counter (`type`, `outcome`) | Upload attempts by file type (`CSV`, `PDF`, `XML`, `OTHER`) and outcome (`success`, `failure`) | Use `sum by (type, outcome) (rate(api_documents_uploads_total[5m]))` to compare format volume. Both labels are bounded. |
| `outbox.events.pending` | Gauge | Undelivered, non-dead-lettered outbox events | Current backlog size; may briefly lag by the 15-second refresh interval. |
| `outbox.events.oldest_pending_age` | Gauge (seconds) | Age of the oldest undelivered event | Resets to zero when there are no pending events; use alongside the pending count. |
| `outbox.events.dead_lettered` | Counter | Events moved to the dead-letter state after retries | Process-local since startup; use `rate(outbox_events_dead_lettered_total[5m])` for recent failures. |

Spring Boot also exposes `http.server.requests` for request rate, duration, and response status.
Counters should be queried with `rate()`/`increase()`; gauges are point-in-time values. No custom
metric labels contain user IDs, document IDs, filenames, or financial values.

## Kafka Topics

Documents are published to topics named `documents.<type>` (e.g., `documents.csv`, `documents.pdf`, `documents.xml`).

Events are written to a transactional outbox (`outbox_events`) in the same database transaction
that finalizes the upload, then delivered to Kafka by a background relay. This guarantees an event
is never lost if the broker is unavailable at upload time — it is retried until delivery succeeds.
A separate reconciler removes documents left in `PENDING` with no stored file (uploads interrupted
before completion).

## Object Storage

Uploaded files are stored in S3-compatible object storage (SeaweedFS) under
`users/{userId}/{documentId}-{originalFilename}`. Access is via the `io.minio` S3 client, so the
`MINIO_*` variable names are retained even though the backend is SeaweedFS. The bucket is created at
startup if it does not exist (`app.minio.create-bucket`, default `true`).

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
