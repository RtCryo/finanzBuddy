## Context

finanzBuddy stores every uploaded financial document as an object in an S3-compatible store. The API Gateway writes objects on upload and reads them on download; the parser service reads them to extract transactions. All access goes through the `io.minio` client, which speaks the S3 protocol, so the application is not bound to MinIO specifically — only to an S3-compatible endpoint configured via `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET`.

Current state: the `minio` service runs `bitnamilegacy/minio:2025.7.23-debian-12-r5`. This is a stopgap chosen because MinIO community edition went source-only (no official `minio/minio` images) and Broadcom froze the free Bitnami catalog under the `bitnamilegacy` namespace in 2025. That image works but receives no further updates or security patches — unacceptable long-term for the store holding user financial documents.

Constraints:
- Deployment is Docker Compose: local development (host ports via override) and single-host production behind Traefik (infra services stay private on the internal network).
- The object store must be S3-compatible so the existing `io.minio` client keeps working with only configuration changes.
- The local UI test toolchain cannot run on this machine (Node too old); verification of UI-facing behavior relies on the Docker build and a running container.

## Goals / Non-Goals

**Goals:**
- Evaluate the realistic S3-compatible options against finanzBuddy's actual constraints and produce a clear pros/cons comparison.
- Reach an explicit, recorded decision: replace MinIO or keep it (and if kept, on what basis and for how long).
- If replacing: repoint the object-storage backend, keep application code unchanged, and prove the full stack still works end-to-end (upload → parse → download).

**Non-Goals:**
- No application source changes to the storage access layer — the S3 client stays; only endpoint/credentials/deployment change.
- No migration of existing stored objects between backends (dev/staging can start empty); a data-migration plan for a populated production bucket is out of scope for this change and, if needed, becomes a follow-up.
- No move to a non-S3 storage model (e.g. storing blobs in Postgres).
- Heavyweight distributed stores (Ceph RadosGW, Apache Ozone) are considered only to be ruled out — they are operationally disproportionate to a single-host Compose stack.

## Decisions

- **Decide against explicit, weighted criteria, not vibes.** Score each candidate on: maintenance/patch cadence, S3 compatibility with `io.minio`, operational effort on a single-host Compose stack, licensing/cost, data durability/backup story, and exit/lock-in risk. The comparison table and the resulting recommendation are the primary deliverables of the analysis step.

- **Candidate set (and why each is in scope):**
  - *MinIO from source* — stay on upstream MinIO by building our own image from the AGPL source; keeps behavior identical but we own image maintenance.
  - *Garage* (`dxflrs/garage`) — lightweight Rust S3 server, actively maintained, designed for self-hosting; strong fit for a single-host stack.
  - *SeaweedFS* (`chrislusf/seaweedfs`) — mature Go store with an S3 gateway; more moving parts but scalable.
  - *Managed S3* (Cloudflare R2, AWS S3, Backblaze B2, Hetzner Object Storage) — removes self-hosted storage entirely; changes the trust/ops model and adds an external dependency + network egress considerations.
  - *Keep `bitnamilegacy/minio`* — do-nothing baseline, retained only as the comparison floor.

- **The decision itself is an apply-time output, made with the user.** This change deliberately does not hard-code the winner up front. The analysis is performed during `/opsx:apply`, the recommendation is presented, and the user confirms the direction before any replacement is implemented. This keeps a reversible, human-owned call for infrastructure that holds user data.

- **Verify replacements the same way the image-version change did.** After any backend switch: `docker compose config`, bring up the local stack, wait for healthchecks, then exercise a real upload → parse → download round-trip rather than only checking that containers start.

## Risks / Trade-offs

- **A chosen backend has subtle S3 incompatibilities** (multipart upload, bucket auto-create, presign, path-style addressing) → Verify with the real upload/parse/download round-trip, not just container health; keep the `MINIO_*` config indirection so reverting is a config change.
- **Self-hosting from source (MinIO) shifts maintenance burden to us** → Weigh explicitly in the criteria; if chosen, pin a specific source ref and document the rebuild process.
- **Managed S3 introduces an external dependency and egress cost/latency** → Evaluate for production only; local dev may still want a self-hosted container, so the design must allow different backends per environment via configuration.
- **Different data path/permissions per backend** (e.g. Bitnami's `/bitnami/minio/data`, non-root users, health endpoints) → Adjust the service's command/volume/health when repointing and confirm against the running container.
- **Rollback**: because access is via `MINIO_*` configuration and no source changes are made, reverting to the current `bitnamilegacy` service is a compose/env revert.

## Migration Plan

1. Create the feature branch for the analysis and any replacement work.
2. Perform the evaluation: gather current maintenance status and S3-compatibility facts for each candidate, fill the comparison table, and form a recommendation.
3. Present the recommendation and get the user's decision (replace vs keep; if replace, which backend, and whether it differs between local and production).
4. If keeping MinIO: record the decision and the stopgap follow-up; update comments/docs; done.
5. If replacing: repoint the `minio` service (image/command/volume/health) or remove it in favor of a managed endpoint; update `.env`/`.env.example` and docs.
6. Verify: `docker compose config`, full local stack up, healthchecks, and an end-to-end upload → parse → download round-trip.
7. Rollback: revert the compose/env changes to restore the current backend.

## Open Questions

- Replace MinIO or keep it? (Resolved during apply, with the user.)
- If replacing, is the backend the same for local development and production, or self-hosted locally + managed in production?
- If a managed S3 is chosen, which provider, and who owns the credentials/bucket provisioning?
- Does any existing production bucket hold data that must be migrated, or can the new backend start empty?
