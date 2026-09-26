# finanzBuddy

[![API Gateway CI](https://github.com/RtCryo/finanzBuddy/actions/workflows/api-gateway.yml/badge.svg)](https://github.com/RtCryo/finanzBuddy/actions/workflows/api-gateway.yml)
[![codecov](https://codecov.io/gh/RtCryo/finanzBuddy/branch/main/graph/badge.svg)](https://codecov.io/gh/RtCryo/finanzBuddy)

finanzBuddy is a personal finance document processing platform. It provides an API Gateway that accepts financial documents (CSV, PDF, XML), stores them securely, and publishes events to downstream parsers via Kafka.

## Key capabilities

- **Secure document upload** — authenticated users can upload financial documents to object storage (MinIO).
- **Metadata tracking** — every document is tracked in PostgreSQL with statuses (`PENDING`, `PROCESSED`, `FAILED`).
- **Kafka event bus** — successful uploads emit `documents.{type}` events for parser services to consume.
- **JWT authentication** — stateless auth with registration and login endpoints.
- **Containerized deployment** — Docker Compose stack with Postgres, Kafka, Zookeeper, MinIO, and the API Gateway.

## Repository structure

```
.
├── api-gateway/      # Spring Boot 4.1 API Gateway
├── docker-compose.yml # Local infrastructure stack
└── openspec/         # OpenSpec change tracking
```

## Quick start

See [`api-gateway/README.md`](api-gateway/README.md) for build instructions, environment variables, and API examples.
