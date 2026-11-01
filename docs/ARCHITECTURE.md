# finanzBuddy Architecture

## Scope and sources of truth

This document describes the current repository architecture and the boundaries between the UI, API Gateway, parser service, and shared infrastructure. It is a contributor map, not a substitute for executable configuration or behavioral specifications: when details conflict, the source code and deployment configuration are authoritative and this document should be corrected in the same change.

- [Documentation map](README.md) — subsystem entry points, implementation paths, current capabilities, and archived changes.

- [Root README](../README.md) — product overview, quick start, and deployment commands.
- [API Gateway README](../api-gateway/README.md) — gateway configuration, endpoint examples, and operational constraints.
- [Parser Service README](../parser-service/README.md) — parser configuration, format support, retries, and metrics.
- [UI README](../ui/README.md) — frontend development and build commands.
- [UI design system](../ui/DESIGN_SYSTEM.md) — visual and interaction standards.
- `openspec/specs/` — durable capability and behavior requirements.

## System overview

finanzBuddy is a monorepo application for uploading financial documents, extracting transactions, and reviewing the resulting data. The browser talks to the API Gateway through the UI's `/api` proxy. The gateway owns user-facing authentication and document APIs; the parser consumes upload events asynchronously and reports processing status back to the gateway.

```text
Browser
  │ HTTPS / same-origin /api
  ▼
UI (React SPA, served by Nginx) ── /api proxy ──► API Gateway (Spring Boot)
                                                       │          │
                                  document metadata ───┘          ├──► SeaweedFS S3 gateway
                                                       │          │    original documents
                                                       │          ▼
                                                       │      PostgreSQL
                                                       │      users, documents, outbox,
                                                       │      transaction versions/categories
                                                       │
                                                       └── outbox relay ──► Kafka
                                                                              │
                                                                              ▼
                                                                    Parser Service
                                                                    │          │
                                          download original ◄───────┘          ├── parse and persist
                                          from SeaweedFS                        │   transactions
                                                                                ▼
                                                                            PostgreSQL
                                                                            transactions,
                                                                            processed_documents

Parser Service ── service-key authenticated status update ──► API Gateway
Prometheus ◄── application metrics and Kafka exporter ── API Gateway / Parser / Kafka
Grafana ── provisioned dashboards over Prometheus
```

The API Gateway and parser service use the same PostgreSQL database and S3 bucket, but manage different tables and lifecycle responsibilities. Kafka carries document-upload events; transaction data is read by the gateway from the parser-owned tables for UI queries.

## Components and responsibilities

| Component | Responsibility | Main implementation areas |
|---|---|---|
| UI | Registration/login, dashboard, document upload/list/detail, transaction history and editing | `ui/src/pages/`, `ui/src/components/`, `ui/src/api/` |
| API Gateway | JWT auth, document upload and ownership, document status, dashboard/history APIs, transaction category/version operations, outbox delivery | `api-gateway/src/main/java/com/finanzbuddy/api/` |
| Parser Service | Kafka consumption, object download, format-specific parsing, deduplication, transaction persistence, status reporting | `parser-service/src/main/java/com/finanzbuddy/parser/` |
| PostgreSQL | Shared relational store; each service applies its own Liquibase changelog | `api-gateway/src/main/resources/db/changelog/`, `parser-service/src/main/resources/db/changelog/` |
| SeaweedFS | S3-compatible storage for original documents; Compose keeps the service name `minio` and `MINIO_*` settings for client compatibility | `docker-compose.yml`, each service's storage configuration |
| Kafka | Asynchronous document-upload event transport | Compose broker; topics `documents.csv`, `documents.pdf`, `documents.xml` |
| Monitoring | Prometheus scraping, Kafka exporter, provisioned Grafana dashboards | `monitoring/`, `docker-compose.yml` |

## Data ownership and service boundaries

Both services connect to the same database/schema. IDs crossing service boundaries are numeric IDs carried in the event and rows; the parser's transaction rows are not JPA relationships to gateway-owned user/document entities.

| Data | Owner/writer | Readers and purpose |
|---|---|---|
| `users` | API Gateway | Authentication and owner lookup |
| `documents` | API Gateway | Upload metadata, ownership, status and document list/detail APIs |
| `outbox_events` | API Gateway | Durable event publication after an upload or reprocess request |
| `transactions`, `processed_documents` | Parser Service | Gateway reads transactions for document details, dashboard, history, and ownership-scoped category edits; the processed marker makes event handling idempotent |
| `transaction_versions`, `personal_transaction_categories` | API Gateway | Full snapshots for category edits/history and each user's reusable category list |

The gateway intentionally accesses parser-owned transactions through JDBC (`TransactionReadRepository` and `TransactionCategoryRepository`) rather than mapping the `transactions` table as a JPA entity. This keeps gateway Hibernate schema validation from requiring a table that the parser service creates and permits the gateway to start before the parser. The gateway migration for category/version tables likewise does not require a foreign-key constraint to a parser-owned table.

History and document transaction reads project the latest saved version when one exists, otherwise they return the parser's original transaction row. Category changes are recorded by the gateway as complete snapshots; the parser remains the writer of imported transaction rows.

## Document processing flow

1. The user authenticates against the gateway and uploads a file through `POST /documents`. The default accepted upload type is `CSV`; PDF/XML are recognized by the gateway but rejected unless configured and supported by a running parser. The gateway's multipart limit is 10 MB per file.
2. The gateway creates a `PENDING` document row, stores the original under `users/{userId}/{documentId}-{originalFilename}` in the configured S3 bucket, then stores the object path and upload event in one database transaction. Failed finalization cleans up the orphaned object/row where possible.
3. The scheduled outbox relay publishes the persisted JSON event to `documents.{type}` with the document ID as the Kafka key, then marks it delivered. Failed sends are retried with backoff and eventually dead-lettered in the outbox. Delivery is at-least-once; the gateway relay assumes one gateway instance, while the parser deduplicates by document ID.
4. The parser's configured listener downloads the object, routes it to the format-specific parser, and writes a `processed_documents` marker plus extracted rows to `transactions` transactionally. CSV is enabled by default; PDF and XML listeners are disabled and their parsers are stubs.
5. The parser reports `PROCESSING`, `PROCESSED`, or `FAILED` to `PUT /internal/documents/{id}/status` using `X-Internal-Api-Key`. The gateway validates state transitions, retains failure detail only for `FAILED`, and can enqueue another upload event when a failed document is reset to `PENDING`.
6. The UI polls document/dashboard data while relevant screens are open and queries processed transactions through the gateway. User-facing reads are scoped to the authenticated owner.

## HTTP interface

The UI sends API requests to `/api`; Vite rewrites that prefix in development and Nginx proxies it to `api-gateway:8080` in the production UI container.

| Route | Authentication | Purpose |
|---|---|---|
| `POST /auth/register`, `POST /auth/login` | Public | Create an account and issue a JWT |
| `GET /dashboard` | User JWT | Owner-scoped aggregate document/transaction metrics |
| `POST /documents`, `GET /documents`, `GET /documents/{id}` | User JWT | Upload and list/read owned documents |
| `GET /documents/{id}/transactions`, `GET /documents/{id}/download` | User JWT | Read processed transactions and download the original file for an owned document |
| `GET /transactions` | User JWT | Filtered, sorted, paginated owner-scoped history |
| `GET /transactions/{id}/related-expenses` | User JWT | Latest-snapshot negative expenses with exactly the source counterparty across owned processed documents; source and candidate transaction/document ownership checked |
| `GET /transactions/categories`, `POST /transactions/categories` | User JWT | Read categories or atomically create a personal category and assign it |
| `GET /transactions/{id}/versions`, `PUT /transactions/{id}/category` | User JWT | Read snapshots or update the latest category |
| `PUT /internal/documents/{id}/status` | Internal API key | Parser-to-gateway status reporting |
| `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | Service endpoint configuration | Health checks and Prometheus scraping; parser also exposes `/actuator/metrics` |

The gateway uses stateless Spring Security and BCrypt password hashing. `/auth/**` and the configured gateway actuator endpoints are public; user routes require a JWT, and `/internal/**` requires the service-key filter. User document and transaction queries verify ownership before returning data. The internal status endpoint intentionally identifies a document by ID rather than user ownership; its trust boundary is the shared service API key, which must remain server-side and must never be sent to the browser. Original files are accessed through the gateway's owner-checked endpoints, not directly from the browser's S3 credentials.

## Technology stack

| Area | Current implementation |
|---|---|
| Frontend | React 19, TypeScript 7, Vite 8, Tailwind CSS 4, React Router 7, Axios |
| Backend services | Java 25, Spring Boot 4.1.1, Spring Web/Security/Data JPA/Kafka/Actuator, Liquibase |
| Database | PostgreSQL 18 in Docker Compose; H2 is used by the service test suites |
| Object storage | SeaweedFS S3 gateway in Compose, accessed through the MinIO Java client |
| Event transport | Apache Kafka 4.3.2 in single-node KRaft mode in the Compose stack |
| UI runtime | Node 26 build stage and Nginx static runtime |
| Deployment | Docker Compose; optional production Traefik profile for HTTPS termination |

The exact dependency versions and environment-variable defaults live in `ui/package.json`, the module POMs, `docker-compose.yml`, and the application configuration files.

## Local development and deployment

For local development, create `.env` from `.env.example` and run `docker compose up --build` from the repository root. `docker-compose.override.yml` is loaded automatically and publishes local ports for PostgreSQL (`5432`), Kafka (`29092`), SeaweedFS S3 (`9000`), the gateway (`8080`), UI (`3000`), Prometheus (`9090`), and Grafana (`3001`).

The optional production deployment uses `docker compose -f docker-compose.yml --profile production ...`, which omits the local override and enables Traefik on ports 80/443. Traefik terminates HTTPS and routes to the UI; the UI's Nginx proxy forwards `/api` requests to the gateway on the private Compose network. Prometheus, Grafana, the exporter, and application services are not published as host ports in this mode.

## Repository map

```text
.
├── api-gateway/       # Spring Boot gateway, Liquibase schema, and tests
├── parser-service/    # Spring Boot parser, Liquibase schema, and tests
├── ui/                # React SPA, Nginx runtime, tests, and design system
├── monitoring/        # Prometheus configuration and provisioned Grafana dashboards
├── docs/              # Cross-cutting contributor documentation
├── openspec/
│   ├── specs/         # Current capability requirements
│   └── changes/       # Active and archived change plans
├── docker-compose.yml
└── docker-compose.override.yml
```

## Change and documentation guidance

- A new document format requires coordinated gateway accepted-type validation, a Kafka topic/listener, parser implementation/configuration, and tests; keep the default support status explicit.
- Changes to table ownership, event payloads/topics, internal authentication, transaction reads/writes, shared migrations, or deployment routing affect cross-service contracts. Review both module implementations and the relevant OpenSpec capabilities.
- Update this architecture document in the same change when service responsibilities, ownership, data flow, security boundaries, or deployment topology materially change.
- For frontend visual or interaction changes, follow [`ui/AGENTS.md`](../ui/AGENTS.md) and [`ui/DESIGN_SYSTEM.md`](../ui/DESIGN_SYSTEM.md); this document is not a UI styling guide.
