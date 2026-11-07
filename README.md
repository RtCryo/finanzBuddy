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
- **Shared homeBuddy account** — authBuddy handles registration and login; the gateway validates its tokens and keeps local document ownership.
- **Containerized deployment** — Docker Compose stack with Postgres, Kafka, SeaweedFS (S3-compatible object storage), a Kafka Prometheus exporter, the API Gateway, the parser service, and the UI.
- **Central monitoring** — the homeBuddy root stack runs the one Prometheus and Grafana that scrape these services and show the finanzBuddy dashboards.

## Repository structure

```
.
├── api-gateway/       # Spring Boot API Gateway (auth, upload, documents, outbox)
├── parser-service/    # Spring Boot parser (Kafka consumer, CSV parsing, transactions)
├── ui/                # React + TypeScript single-page app
├── docker-compose.yml # Local infrastructure + services stack
└── openspec/          # OpenSpec change tracking
```

For a navigable map of module documentation, implementation areas, current capabilities, and archived changes, see the [documentation map](docs/README.md). Cross-service ownership and data-flow details are in the [architecture guide](docs/ARCHITECTURE.md).

## Quick start (local development)

Run the full stack from the repository root:

```bash
docker network create homebuddy
docker network create --internal finanzbuddy-identity
cp .env.example .env   # fill in INTERNAL_API_KEY
docker compose up --build
```

Start authBuddy before the finanzBuddy UI; see the [identity migration runbook](docs/HOMEBUDDY_IDENTITY_MIGRATION.md). `docker compose` automatically loads `docker-compose.override.yml`, which publishes the
localhost ports. The UI is served on `http://localhost:3000` and the API Gateway on
`http://localhost:8080`. No domain or TLS certificate is required for local development.

### Monitoring

finanzBuddy has no Prometheus or Grafana of its own. The root homeBuddy stack (`docker compose up -d`
in the workspace root, see its README) runs the central Prometheus and Grafana, which scrape the
API Gateway, the parser service, and the Kafka exporter over the internal `finanzbuddy-metrics`
network. Create that network once per host before starting this stack:

```bash
docker network create --internal finanzbuddy-metrics
```

Open Grafana at <http://localhost:3001> and use the **finanzBuddy** folder. Its dashboards (Health
Overview, Application Operations, API Gateway, Parser Service, and Kafka) are provisioned from the
root repository's `monitoring/grafana/dashboards/finanzbuddy/`. Prometheus is at
<http://localhost:9090>; the finanzBuddy targets are the jobs `finanzbuddy-api-gateway`,
`finanzbuddy-parser-service`, and `finanzbuddy-kafka-exporter`.

Application file-type labels use the bounded values `CSV`, `PDF`, `XML`, or `OTHER`; no user or
document identifiers, filenames, or financial values are included. The Kafka exporter is reachable
only inside the Compose networks. The UI proxy returns 404 for `/api/actuator`, so metrics are not
served to browsers.

If an earlier checkout ran its own Prometheus and Grafana, remove their old volumes once the central
stack works: `docker volume rm finanzbuddy_prometheus-data finanzbuddy_grafana-data`.

## Production deployment (HTTPS via Traefik)

The root homeBuddy stack provides the shared [Traefik](https://traefik.io/) proxy for multi-product hosting on ports 80 and 443. The finanzBuddy UI joins `homebuddy` as `finanzbuddy-ui`; its labels route `TRAEFIK_DOMAIN` through the shared proxy. The service-local Traefik profile below is for a standalone finanzBuddy host only. The UI and API Gateway are not published on host ports; `/api/auth/` goes to authBuddy and other `/api/` traffic goes to the gateway.

Prerequisites:

- A public DNS `A`/`AAAA` record for your domain resolving to the deployment host.
- Inbound TCP ports 80 and 443 reachable from the internet (port 80 is required for ACME
  HTTP-01 validation and the HTTP→HTTPS redirect).
- `.env` populated with `INTERNAL_API_KEY`, `AUTH_JWKS_URI`, `AUTH_ISSUER`, strong unique values for
  `DB_PASSWORD` and `MINIO_ROOT_PASSWORD` (do not keep the local defaults in production),
  and the production values for `TRAEFIK_DOMAIN` (your real domain) and
  `TRAEFIK_ACME_EMAIL` (a real contact email).

For a standalone host, start the production stack from the repository root:

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
