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

## Quick start (local development)

Run the full stack from the repository root:

```bash
cp .env.example .env   # fill in JWT_SECRET and INTERNAL_API_KEY
docker compose up --build
```

`docker compose` automatically loads `docker-compose.override.yml`, which publishes the
localhost ports. The UI is served on `http://localhost:3000` and the API Gateway on
`http://localhost:8080`. No domain or TLS certificate is required for local development.

## Production deployment (HTTPS via Traefik)

The production mode puts a [Traefik](https://traefik.io/) reverse proxy in front of the
stack as the single public entry point on ports 80 and 443. It obtains and renews TLS
certificates automatically through Let's Encrypt (ACME HTTP-01) and redirects HTTP to
HTTPS. The UI and API Gateway are not published on host ports; all application traffic
enters through Traefik, and `/api` requests continue through the UI's existing Nginx proxy
to the API Gateway.

Prerequisites:

- A public DNS `A`/`AAAA` record for your domain resolving to the deployment host.
- Inbound TCP ports 80 and 443 reachable from the internet (port 80 is required for ACME
  HTTP-01 validation and the HTTP→HTTPS redirect).
- `.env` populated with `JWT_SECRET`, `INTERNAL_API_KEY`, strong unique values for
  `DB_PASSWORD` and `MINIO_ROOT_PASSWORD` (do not keep the local defaults in production),
  and the production values for `TRAEFIK_DOMAIN` (your real domain) and
  `TRAEFIK_ACME_EMAIL` (a real contact email).

Start the production stack from the repository root:

```bash
docker compose -f docker-compose.yml --profile production up --build -d
```

The explicit `-f docker-compose.yml` omits `docker-compose.override.yml`, so the local
host ports are not published, and `--profile production` enables Traefik. The application
is then served at `https://<TRAEFIK_DOMAIN>`.

Per-module build instructions, environment variables, and API examples:

- [`api-gateway/README.md`](api-gateway/README.md)
- [`parser-service/README.md`](parser-service/README.md)
- [`ui/README.md`](ui/README.md)
