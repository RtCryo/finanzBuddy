# finanzBuddy Architecture

## Overview

finanzBuddy is a financial document processing platform. Authenticated users upload financial documents (CSV, PDF, XML) through a web UI. Each document is tagged by type, stored in S3-compatible object storage, and announced via Apache Kafka. A downstream parser service consumes the event, parses the file, normalizes the extracted data into transactions, and persists the results in PostgreSQL.

The project is organized as a **monorepo** containing independent microservices:

- `api-gateway` — user authentication, document upload, status tracking, Kafka event publishing
- `parser-service` — Kafka consumer, document parser, transaction normalization
- `ui` — React web frontend

## High-Level Data Flow

```
+--------+      +-------------+      +---------------------+
|   UI   |----->| API Gateway |----->|   Document Upload   |
+--------+      |  (Spring)   |      | SeaweedFS+PostgreSQL|
                +-------------+      +----------+----------+
                            |
                            | Kafka Producer
                            v
                   +------------------+
                   |  Kafka Topics    |
                   | documents.csv    |
                   | documents.pdf    |
                   | documents.xml    |
                   +---------+--------+
                             |
          +------------------+------------------+
          |                  |                  |
          v                  v                  v
   +-------------+    +-------------+    +-------------+
   | CSV Parser  |    | PDF Parser  |    | XML Parser  |
   |  (enabled   |    |  (enabled   |    |  (enabled   |
   |   by config)|    |   by config)|    |   by config)|
   +------+------+    +------+------+    +------+------+
          |                  |                  |
          +------------------+------------------+
                             |
                             v
                    +------------------+
                    |   PostgreSQL     |
                    |  transactions    |
                    +------------------+
```

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, Vite, Tailwind CSS, React Router, Axios |
| API Gateway | Spring Boot 4.1, Spring Web MVC, Spring Security, JWT, Spring Data JPA, Spring Kafka |
| Parser Service | Spring Boot 4.1, Spring Kafka, Spring Data JPA, OpenCSV, Apache PDFBox, Jackson XML |
| Database | PostgreSQL 16 |
| Object Storage | SeaweedFS (S3-compatible, accessed via the `io.minio` client) |
| Message Bus | Apache Kafka |
| Containerization | Docker, Docker Compose |
| Build | Maven (parent POM + per-service modules) |

## Monorepo Layout

```
finanzBuddy/
├── api-gateway/                  # Spring Boot service
│   └── openspec/changes/api-gateway/  # OpenSpec planning artifacts
├── parser-service/               # Spring Boot service
│   └── openspec/changes/parser-service/
├── ui/                           # React frontend
│   └── openspec/changes/ui/
├── docs/
│   └── ARCHITECTURE.md           # This document
├── docker-compose.yml            # Local infrastructure
└── pom.xml                       # Maven parent POM
```

## Services

### API Gateway

- **Responsibilities**
  - User registration and login with JWT
  - Authenticated multipart document upload
  - Document type validation (`CSV`, `PDF`, `XML`)
  - File storage in object storage (SeaweedFS) under `users/{userId}/{documentId}-{filename}`
  - Document metadata persistence in PostgreSQL with status `PENDING`
  - Kafka event publishing to `documents.{type}`
  - REST endpoints for listing documents and updating status

- **Key Endpoints**
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /documents` (multipart, requires `file` and `type`)
  - `GET /documents`
  - `GET /documents/{id}`
  - `PUT /internal/documents/{id}/status` (service-to-service)

- **Internal structure (hexagonal)**
  The gateway is being migrated slice-by-slice to a ports-and-adapters (hexagonal) layout. The
  `document` slice was the pilot; `auth`, `cleanup`, and `security` followed; all share the shape. A
  framework-free `domain` holds the business rules behind inbound use-case ports and reaches
  infrastructure only through outbound ports; the concrete frameworks (Spring Security/Data, MinIO,
  Jackson, Kafka, web transport, `@Scheduled`) live only in `adapter/in/*` and `adapter/out/*`,
  never in the domain.
  - `document`: inbound ports for upload/list/get/transactions/download/status; outbound ports
    `DocumentRepositoryPort`, `TransactionReadPort`, `FileStoragePort`, `DocumentEventPort`,
    `TransactionRunnerPort`. `MultipartFile`/`Page`/`Pageable` are converted to domain types at the
    web edge; the transactional outbox lives in the messaging adapter.
  - `auth`: inbound ports `RegisterUserUseCase`/`LoginUseCase`; outbound ports `UserRepositoryPort`,
    `PasswordHasherPort`, `CredentialAuthenticatorPort`, `AccessTokenIssuerPort`,
    `TransactionRunnerPort`.
  - `cleanup`: inbound port `ReconcileStalePendingUseCase` (one stale-PENDING reconciliation pass),
    driven by a thin `@Scheduled` trigger adapter. Reuses the `document` outbound ports
    (`DocumentRepositoryPort` with the stale finder + conditional delete, `FileStoragePort`,
    `TransactionRunnerPort`) and adds `ClockPort` so the staleness window is testable without
    wall-clock time.
  - `security`: inbound ports `AuthenticateRequestUseCase` (bearer token → principal) and
    `AuthenticateInternalUseCase` (api-key → service principal); outbound ports `AccessTokenPort`
    (issue/validate), `ApiKeyPort` (constant-time compare), `PrincipalLookupPort` (backed by the
    auth slice's `UserRepositoryPort`). The servlet filters are thin inbound adapters; the JWT
    library is confined to the token adapter and `AccessTokenPort` is reused by the `auth` slice to
    issue tokens. Token failures are domain results/exceptions mapped to 401 at the filter edge.

  Each slice ships an ArchUnit test (`DocumentArchitectureTest`, `AuthArchitectureTest`,
  `CleanupArchitectureTest`, `SecurityArchitectureTest`) that fails the build if the domain imports
  a forbidden framework package or bypasses a port. The parser-service applies the same pattern in
  its own `processing` and `parsing` slices (see below).

### Parser Service

- **Responsibilities**
  - Consume `DocumentUploadedEvent` messages from configured Kafka topics
  - Download the referenced file from object storage (SeaweedFS)
  - Parse the file using a format-specific strategy
  - Normalize extracted data into a unified `Transaction` model
  - Persist transactions in PostgreSQL
  - Report processing status back to the API Gateway

- **Configuration-Driven Consumption**
  The same service image can run as a CSV-only, PDF-only, XML-only, or all-format consumer by setting:

  ```yaml
  parser:
    formats:
      csv: true
      pdf: false
      xml: false
  ```

  Spring creates Kafka listeners only for enabled formats using `@ConditionalOnProperty`.

- **Parser Strategy**
  ```
  DocumentParser
    ├── CsvTransactionParser
    ├── PdfTransactionParser (MVP stub)
    └── XmlTransactionParser (MVP stub)
  ```

- **Internal structure (hexagonal)**
  The parser-service follows the same ports-and-adapters shape as the gateway, split into two
  framework-free domain slices under `com.finanzbuddy.parser`:
  - `processing`: inbound port `ProcessDocumentUseCase` (handle one `DocumentUploadedEvent`),
    implemented by the `DocumentProcessor` orchestrator; outbound ports `FileFetchPort`,
    `TransactionParserPort`, `TransactionWriterPort` (idempotent dedupe+insert), `StatusReporterPort`,
    `ProcessingMetricsPort`. The Kafka consumer is a thin `@KafkaListener` adapter; MinIO, the
    `RestClient`, Spring Data + the JPA entities, and Micrometer live only in `adapter/out/*`;
    framework exceptions are translated to domain exceptions at the adapter edge.
  - `parsing`: port `DocumentParser` with a framework-free `DocumentParserRouter`; OpenCSV/PDFBox/
    Jackson format libraries are confined to the per-format adapters. Both slices share one
    framework-free domain `Transaction`.

  Each slice ships an ArchUnit test (`ProcessingArchitectureTest`, `ParsingArchitectureTest`) that
  fails the build if the domain imports a forbidden framework package or bypasses a port. Kafka
  topics, idempotency, gateway status callbacks, and emitted metric names are unchanged by the
  refactor.

### User Interface

- **Responsibilities**
  - Registration and login screens
  - Document upload with type selection
  - Document list with status polling
  - Transaction view for processed documents

## Data Model

### `users`

| Column | Type |
|--------|------|
| id | UUID PK |
| email | TEXT UNIQUE |
| password_hash | TEXT |
| created_at | TIMESTAMP |

### `documents`

| Column | Type |
|--------|------|
| id | UUID PK |
| user_id | UUID FK |
| original_filename | TEXT |
| storage_path | TEXT |
| type | VARCHAR(10) |
| status | VARCHAR(20) |
| error_message | TEXT |
| created_at | TIMESTAMP |
| updated_at | TIMESTAMP |

### `transactions`

| Column | Type |
|--------|------|
| id | UUID PK |
| document_id | UUID FK |
| user_id | UUID FK |
| booking_date | DATE |
| value_date | DATE |
| amount | DECIMAL(19,4) |
| currency | VARCHAR(3) |
| counterparty | TEXT |
| description | TEXT |
| category | TEXT |
| raw_data | JSONB |
| created_at | TIMESTAMP |

## Kafka Topics

| Topic | Purpose |
|-------|---------|
| `documents.csv` | Events for CSV documents |
| `documents.pdf` | Events for PDF documents |
| `documents.xml` | Events for XML documents |

### Event Schema: `DocumentUploadedEvent`

```json
{
  "documentId": "uuid",
  "userId": "uuid",
  "type": "CSV",
  "storagePath": "users/{userId}/{documentId}-{filename}"
}
```

## Security

- Passwords are hashed with BCrypt.
- JWT access tokens protect all document endpoints.
- Service-to-service status updates use an internal API key.
- Object-storage files are stored under user-scoped paths.
- Users can only access documents they own.

## Local Development

All infrastructure runs locally with Docker Compose:

- PostgreSQL
- Kafka
- SeaweedFS (S3-compatible object storage)
- API Gateway
- Parser Service
- UI

Build and start the stack:

```bash
docker compose up --build
```

## Extensibility

- New document formats are added by implementing the `DocumentParser` interface and enabling the corresponding Kafka topic.
- New parser service instances can scale consumption per format without changing the API Gateway.
- The normalized `Transaction` model allows future features such as analytics, exports, and categorization.
