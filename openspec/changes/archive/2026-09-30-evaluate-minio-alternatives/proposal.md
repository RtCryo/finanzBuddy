## Why

The Compose stack currently runs its object storage on `bitnamilegacy/minio` — a deliberate stopgap. MinIO community edition became source-only (no official `minio/minio` images), and the Bitnami free catalog was frozen under the `bitnamilegacy` namespace in 2025, so the image we depend on receives no further updates or security patches. This is not a safe long-term foundation for the store that holds every uploaded financial document, so we need to evaluate the modern S3-compatible options and decide whether to replace MinIO — and if so, replace it and prove the stack still works.

## What Changes

- Produce a structured evaluation of current S3-compatible object-storage options against the project's real needs (self-hosted Compose stack, S3 API used via `io.minio`, local dev + single-host production behind Traefik), covering at minimum:
  - Staying on MinIO via a self-built-from-source image
  - Self-hosted alternatives: Garage, SeaweedFS (and note heavier options like Ceph/Ozone as out-of-scope)
  - Managed S3 services: AWS S3, Cloudflare R2, Backblaze B2, Hetzner Object Storage
  - Keeping `bitnamilegacy/minio` as-is (do-nothing baseline)
- Reach and record an explicit decision: **replace MinIO or keep it**, with a pros/cons comparison and the rationale.
- **Conditional on a "replace" decision**: repoint the `minio` service (image/command/volume/health, or removal in favor of a managed endpoint), update the `MINIO_*`/endpoint configuration and env docs, and keep application code unchanged where the S3 client already suffices.
- **Conditional on a "replace" decision**: verify the full stack after the switch — `docker compose config` valid, all services healthy, and an end-to-end document upload → parse → download round-trip against the new backend.
- Update `docker-compose.yml` comments and README/docs to reflect the decision (either the chosen replacement, or why MinIO/bitnamilegacy is retained for now).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `compose-deployment`: Add a requirement that the stack's object storage use a **maintained, supported, reproducibly-obtainable S3-compatible backend** (not a frozen or unmaintained stopgap image), and that any change of the object-storage backend preserve document upload/download behavior and be verified against a running stack. Existing MinIO-by-name references become satisfiable by whichever S3-compatible backend is chosen.

## Impact

- `docker-compose.yml` and `docker-compose.override.yml` (the `minio` service: image/command/volume/health, or its removal for a managed endpoint; local host-port publishing)
- `.env` / `.env.example` and README/`docs/ARCHITECTURE.md` (object-storage endpoint, credentials, and the rationale for the chosen backend)
- `api-gateway` / `parser-service` runtime configuration only via `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET`; no application source changes expected since both already speak the S3 protocol through `io.minio`
- No change to document-upload / document-consumption behavior — only the storage backend and how it is deployed
- A new Git branch is created for the analysis and any resulting replacement work
