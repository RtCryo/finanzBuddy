# API Gateway

Spring Boot 4.1 API Gateway for the finanzBuddy platform. Validates homeBuddy account tokens, links them to local owners, and provides document upload to S3-compatible object storage (SeaweedFS), Kafka event publishing, and document query endpoints.

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

This starts PostgreSQL, Kafka, SeaweedFS (S3-compatible object storage), and the API Gateway. Start authBuddy and create the external `homebuddy` network first; see the [identity migration runbook](../docs/HOMEBUDDY_IDENTITY_MIGRATION.md).

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
| `AUTH_JWKS_URI` | authBuddy JWKS endpoint on the shared network | `http://auth:8080/.well-known/jwks.json` |
| `AUTH_ISSUER` | Required token issuer | `homebuddy-auth` |
| `INTERNAL_API_KEY` | Shared key for internal service endpoints (`/internal/**`) | **required, no default** — `/internal/**` is locked until set |
| `OUTBOX_POLL_INTERVAL_MS` | Interval the outbox relay polls for undelivered events | `5000` |
| `UPLOAD_ACCEPTED_TYPES` | Comma-separated document types accepted for upload | `CSV` |

## API Endpoints

### Authentication

Registration and login are served by authBuddy at `/api/auth/register` and `/api/auth/login` on the UI origin. The gateway has no `/auth/**` handlers and stores no new passwords. It validates RS256 tokens against authBuddy's JWKS, requires issuer `homebuddy-auth`, audience `finanzbuddy`, and the `exp`, `sub`, and `email` claims, and resolves `sub` to the local `users.account_id`. Controllers receive the local user id in the principal and never look users up by email. Existing users are linked to their imported accounts by the operator link step of the runbook, without changing their numeric owner ID; the gateway never links by email at request time and rejects a token whose email still belongs to an unlinked local user. An account new to finanzBuddy receives a local owner row with no password hash. The stored email is a projection that follows the token and is not unique. See the [migration runbook](../docs/HOMEBUDDY_IDENTITY_MIGRATION.md).

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

### Transaction history and personal tags

All routes below require `Authorization: Bearer <token>`. The Gateway owns reusable personal tag names and current assignments. Parser-owned imported transaction rows and Gateway-owned version snapshots do not contain tag data. Migration 010 creates the Gateway tag tables without requiring parser tables at Gateway startup. Deploy the additive migration and API before the UI; rolling the migration back drops tag data.

```bash
curl 'http://localhost:8080/transactions?tagIds=4&tagIds=8&tagMode=OR&page=0&size=20' \
  -H 'Authorization: Bearer <token>'
curl http://localhost:8080/transactions/tags -H 'Authorization: Bearer <token>'
curl http://localhost:8080/transactions/14/tags -H 'Authorization: Bearer <token>'
```

`GET /transactions` accepts repeated bare `tagIds` parameters. `tagMode=AND` is the default and requires every distinct selected tag; `OR` requires at least one. No selected IDs means no tag restriction. Tag matching combines with `query`, inclusive `from`/`to`, and `category` filters using AND before sorting, counting, and pagination. Invalid, unknown, or foreign tag IDs return the same generic `400` response. Dictionary reads expose only the authenticated owner's tags; assignment reads also verify transaction ownership.

The shared editor uses one request for category and tags:

```bash
curl -X PUT http://localhost:8080/transactions/14 \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"category":"Travel","tags":[{"id":4},{"name":"Work"}]}'
```

`tags` is the required **complete desired set**; `[]` clears assignments but keeps dictionary entries for reuse. Each item contains either an owned `id` or a `name`, never both. Names are trimmed, nonblank, at most 255 characters, and reused case-insensitively per owner. Omit both `category` and `newCategoryName` to preserve the category, including an uncategorized transaction; to create a personal category, send `newCategoryName` instead of `category`. The response is `{ "transaction": { ...existing transaction fields... }, "tags": [{ "id": 4, "name": "Travel" }] }`. A category change creates the usual version; a tag-only or unchanged save creates none. The entire edit commits or rolls back together. Existing `PUT /transactions/{id}/category` and `POST /transactions/categories` remain available, and `GET /transactions/{id}/versions` stays tag-free. Migration 011 adds `manual_assignment` to existing assignments (old rows become manual) and Gateway-owned rule, condition, and contribution tables. These tables have owner-consistent Gateway foreign keys and no foreign key to parser-owned `transactions`.

### Personal tag rules

See the [documentation map](../docs/README.md#subsystem-map), [current rule requirements](../openspec/specs/transaction-tag-rules/spec.md), and [archived change](../openspec/changes/archive/2026-11-03-add-transaction-tag-rules/) for implementation and behavior history.

Create and update accept exactly one of an existing owned `tagId` or a `tagName` to create or reuse a personal tag atomically with the rule. Tag names are trimmed, nonblank, at most 255 characters, and reused case-insensitively per owner. A failed rule mutation leaves no new tag. For example, a create request may use `{"tagName":"Groceries","mode":"AND","conditions":[{"field":"CATEGORY","operator":"CONTAINS","value":"grocer"}],"applyExisting":false}`.

All rule routes require the user JWT. `GET /transactions/rules` returns only the owner's rules with `id`, `tagId`, `mode`, and ordered `conditions`. Conditions use `field` `COUNTERPARTY`, `DESCRIPTION`, or `CATEGORY`, `operator` `EXACT` or `CONTAINS`, and a nonblank `value` of at most 255 characters. `mode` is `AND` or `OR` across one or more flat conditions. Both matching modes trim the rule value and latest transaction field and ignore case; a missing or blank field never matches. Historical rule application reads the latest saved category; new imports use their imported category. Rules never change a category or create a category version. Nested groups and unrecognized fields are rejected.

For a Category condition, create and update may include `newCategoryNames` containing names entered through the rule editor's create-category option. Each trimmed, nonblank name (at most 255 characters) must match a Category condition value. The Gateway creates or reuses those personal categories for the owner in the same transaction as the rule; failure rolls back both. This adds category choices for later editing without assigning a category to any transaction. For example, `{"tagId":4,"mode":"AND","conditions":[{"field":"CATEGORY","operator":"EXACT","value":"Utilities"}],"newCategoryNames":["Utilities"],"applyExisting":false}`.

```bash
curl -X POST http://localhost:8080/transactions/rules \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"tagId":4,"mode":"AND","conditions":[{"field":"COUNTERPARTY","operator":"EXACT","value":"X"},{"field":"DESCRIPTION","operator":"CONTAINS","value":"YUPI"}],"applyExisting":true}'
curl -X PUT http://localhost:8080/transactions/rules/7 \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"tagId":4,"mode":"OR","conditions":[{"field":"DESCRIPTION","operator":"CONTAINS","value":"YUPI"}],"applyExisting":false}'
curl -X DELETE http://localhost:8080/transactions/rules/7 \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"removeExisting":true}'
```

Create and update accept `applyExisting` (default `false`). `true` applies a new rule to all processed owner transactions or reconciles an edited rule's old contributions against its new conditions and target; `false` changes future imports only. Delete requires `removeExisting`: `true` removes that rule's contributions, while `false` preserves existing effective assignments as manual. Each mutation and its optional historical work is one database transaction. The effective tag set is the union of manual assignments and rule contributions. An unchanged editor save preserves existing sources; removing a tag in the complete-set editor removes its manual and current rule sources for that transaction. A later explicit rule reconciliation can restore a matching tag. Rule operations never create transaction category versions.

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
On the first `PROCESSING→PROCESSED` transition, the Gateway applies current owner rules to the
committed imported rows and commits assignments with the status. An application failure rolls back
the status and tags so a parser retry can complete the same report. Duplicate `PROCESSED` reports
do not reevaluate rules. The parser remains the sole writer of imported transaction rows.

### Health & Metrics

```bash
curl http://localhost:8080/actuator/health
```

Prometheus metrics are exposed at `/actuator/prometheus` on the gateway port, for the central homeBuddy Prometheus (over `finanzbuddy-metrics`). The UI proxy does not serve them:

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

- **Registration and login responses.** authBuddy owns these endpoints and their error behavior;
  the UI forwards requests through `/api/auth/` on its own origin.

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
