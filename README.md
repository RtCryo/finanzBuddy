# finanzBuddy

[![API Gateway CI](https://github.com/RtCryo/finanzBuddy/actions/workflows/api-gateway.yml/badge.svg)](https://github.com/RtCryo/finanzBuddy/actions/workflows/api-gateway.yml)
[![codecov](https://codecov.io/gh/RtCryo/finanzBuddy/branch/main/graph/badge.svg)](https://codecov.io/gh/RtCryo/finanzBuddy)

finanzBuddy is a personal finance document processing platform. Users upload bank statements through a web UI; the API Gateway stores them securely and publishes events over Kafka, and a parser service extracts and normalizes the transactions.

## Architecture

```
UI (React) → API Gateway (Spring Boot) → Kafka → Parser Service (Spring Boot)
                     │                                      │
                     ├── PostgreSQL (users, documents, transactions, outbox)
                     └── MinIO (original files)
```

The gateway and parser service share one PostgreSQL database and one MinIO bucket. The gateway owns
users/documents/outbox and reads the parser-owned `transactions` table to serve it back to the UI.

## Key capabilities

- **Secure document upload** — authenticated users upload financial documents to object storage (MinIO). CSV is accepted in the MVP (`PDF`/`XML` are recognised but not yet parsed).
- **Transaction extraction** — the parser service consumes upload events, parses the file, and persists normalized transactions.
- **Document tracking** — every document is tracked in PostgreSQL with statuses (`PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`); the UI polls for status.
- **Transaction viewing & file download** — processed documents expose their transactions and let the owner download the original file.
- **Kafka event bus** — successful uploads emit `documents.{type}` events via a transactional outbox.
- **JWT authentication** — stateless auth with registration and login endpoints.
- **Containerized deployment** — Docker Compose stack with Postgres, Kafka, Zookeeper, MinIO, the API Gateway, the parser service, and the UI.

## Repository structure

```
.
├── api-gateway/       # Spring Boot API Gateway (auth, upload, documents, outbox)
├── parser-service/    # Spring Boot parser (Kafka consumer, CSV parsing, transactions)
├── ui/                # React + TypeScript single-page app
├── docker-compose.yml # Local infrastructure + services stack
└── openspec/          # OpenSpec change tracking
```

## Quick start

Run the full stack from the repository root:

```bash
cp .env.example .env   # fill in JWT_SECRET and INTERNAL_API_KEY
docker compose up --build
```

The UI is served on `http://localhost:3000` and the API Gateway on `http://localhost:8080`.

Per-module build instructions, environment variables, and API examples:

- [`api-gateway/README.md`](api-gateway/README.md)
- [`parser-service/README.md`](parser-service/README.md)
- [`ui/README.md`](ui/README.md)
