# finanzBuddy

[![API Gateway CI](https://github.com/RtCryo/finanzBuddy/actions/workflows/api-gateway.yml/badge.svg)](https://github.com/RtCryo/finanzBuddy/actions/workflows/api-gateway.yml)
[![codecov](https://codecov.io/gh/RtCryo/finanzBuddy/branch/main/graph/badge.svg)](https://codecov.io/gh/RtCryo/finanzBuddy)

finanzBuddy is a personal finance document processing platform. Users upload bank statements through a web UI; the API Gateway stores them securely and publishes events over Kafka, and a parser service extracts and normalizes the transactions.

## Architecture

```
UI (React) → API Gateway (Spring Boot) → Kafka → Parser Service (Spring Boot)
                     │                                      │
                     ├── PostgreSQL (users, documents, transactions, outbox)
                     └── SeaweedFS (S3-compatible object storage, original files)
```

The gateway and parser service share one PostgreSQL database and one object-storage bucket. The gateway owns
users/documents/outbox and reads the parser-owned `transactions` table to serve it back to the UI.

## Key capabilities

- **Secure document upload** — authenticated users upload financial documents to S3-compatible object storage (SeaweedFS). CSV is accepted in the MVP (`PDF`/`XML` are recognised but not yet parsed).
- **Transaction extraction** — the parser service consumes upload events, parses the file, and persists normalized transactions.
- **Document tracking** — every document is tracked in PostgreSQL with statuses (`PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`); the UI polls for status.
- **Transaction viewing & file download** — processed documents expose their transactions and let the owner download the original file.
- **Kafka event bus** — successful uploads emit `documents.{type}` events via a transactional outbox.
- **JWT authentication** — stateless auth with registration and login endpoints.
- **Containerized deployment** — Docker Compose stack with Postgres, Kafka, SeaweedFS (S3-compatible object storage), a Kafka Prometheus exporter, the API Gateway, the parser service, and the UI.
- **Local monitoring** — Prometheus scrapes the application metrics and Grafana provides provisioned operational dashboards.

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

### Monitoring

The local Compose override also publishes Prometheus at <http://localhost:9090> and Grafana at
<http://localhost:3001>. Start them with the same `docker compose up --build` command above. On a
fresh Grafana volume, sign in with the initial `admin` / `admin` credentials and open the
**finanzBuddy Health Overview** dashboard in the **finanzBuddy** folder; Grafana will prompt to
change the initial password. The health overview is Grafana's default home dashboard.

Dashboards are provisioned from `monitoring/grafana/`:

- **finanzBuddy Health Overview** — API Gateway and parser availability/uptime, Kafka broker and
  exporter status, consumer lag, outbox backlog, and HTTP error rate.
- **finanzBuddy Application Operations** — combined detailed view of application HTTP traffic,
  parser outcomes and timing, uploads, transaction volume, and outbox metrics.
- **finanzBuddy API Gateway** — HTTP rate/latency/errors, upload outcomes by file type, and outbox
  health.
- **finanzBuddy Parser Service** — document outcomes, mean processing duration, and parsed
  transaction volume by file type.
- **finanzBuddy Kafka** — broker count, topic partitions, under-replicated partitions, consumer
  group members, and lag. Consumer-group panels populate once a group has committed offsets.

Application file-type labels use the bounded values `CSV`, `PDF`, `XML`, or `OTHER`; no user or
document identifiers, filenames, or financial values are included. Panels show “No samples” until
Prometheus has data. Prometheus retains local samples for 15 days in its named volume. The Kafka
exporter is reachable only inside the Compose network. Monitoring host ports are provided only by
`docker-compose.override.yml`; production starts with `docker compose -f docker-compose.yml
--profile production ...` and does not publish Prometheus, Grafana, or the exporter.

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
