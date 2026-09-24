# Design: API Gateway

## Context

The API Gateway is the user-facing entry point of the finanzBuddy monorepo. It owns user identity, document intake, and event publishing. Downstream parser services consume Kafka events produced by this service. See `proposal.md` for the motivation and scope.

## Goals / Non-Goals

**Goals:**
- Provide secure JWT-based registration and login.
- Accept financial document uploads with a user-selected type tag.
- Store files in MinIO and metadata in PostgreSQL under a single transaction boundary.
- Publish typed `DocumentUploadedEvent` messages to Kafka.
- Expose REST endpoints for users to query their documents and statuses.

**Non-Goals:**
- Parsing documents (handled by `parser-service`).
- Real-time notifications such as WebSockets or SSE.
- Admin or multi-user management features.
- Production-grade identity federation (OIDC/SAML).

## Decisions

### Spring Boot 4.1 Web MVC

**Rationale:** Spring Boot 4.1 is the latest stable release available on start.spring.io. Web MVC provides a straightforward request/response model for file uploads and JWT authentication, and the team is familiar with the Java/Spring ecosystem. WebFlux is unnecessary at this stage.

**Alternatives considered:** Quarkus (faster startup, less ecosystem familiarity), Spring WebFlux (higher concurrency, steeper learning curve for file handling).

### Spring Security + JWT

**Rationale:** Self-contained authentication avoids external identity provider dependencies in local Docker Compose. JWT tokens are stateless and easy for the UI to store and send.

**Alternatives considered:** Keycloak (more features but heavier local setup), session cookies (requires stateful session storage).

### MinIO for Object Storage

**Rationale:** S3-compatible API, lightweight, runs in Docker, and avoids storing binary files in PostgreSQL. Files are organized under `users/{userId}/{documentId}-{originalFilename}`.

**Alternatives considered:** Local filesystem (not scalable across containers), PostgreSQL bytea (impacts DB performance and backups).

### Kafka Topics per Document Type

**Rationale:** One topic per type (`documents.csv`, `documents.pdf`, `documents.xml`) lets parser services subscribe selectively and simplifies scaling per format.

**Alternatives considered:** Single generic topic (all consumers filter messages, less efficient).

### Transactional Upload (DB + MinIO + Kafka)

**Rationale:** Document metadata is inserted into PostgreSQL. If Kafka publishing is configured, the event is sent after a successful DB commit. On failure, the request returns an error and no inconsistent state is left.

**Note:** True distributed transactions across MinIO, PostgreSQL, and Kafka are not used. The design accepts that a file may exist in MinIO while DB insertion fails. A cleanup task or background reconciler is left as future work.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| File stored but DB commit fails | Return error to user; add background cleanup later |
| Kafka unavailable during upload | Upload returns 500; user retries; idempotency via document id |
| Large file uploads block threads | Set reasonable file size limits (e.g., 10 MB) |
| JWT secret rotation | Externalize secret via environment variable; rotation requires re-login |

## Migration Plan

1. Start PostgreSQL, Kafka, Zookeeper, and MinIO via Docker Compose.
2. Run Liquibase migrations to create `users` and `documents` tables.
3. Build and start `api-gateway` container.
4. Verify endpoints with curl/Postman.

## Open Questions

- None at this time.
