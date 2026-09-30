## 1. Branch setup

- [x] 1.1 Create and check out a feature branch (e.g. `update-compose-image-versions`) from `main`

## 2. Audit current versions

- [x] 2.1 Look up the current upstream-supported tag for `traefik:v3.3`
- [x] 2.2 Look up the current upstream-supported tag for `postgres:18-alpine`
- [x] 2.3 Look up the current upstream-supported tag for `apache/kafka:3.9.1`
- [x] 2.4 Look up the current upstream-supported `RELEASE.*` tag for `minio/minio`
- [x] 2.5 Look up current tags for Dockerfile bases: `maven:3.9-eclipse-temurin-25`, `eclipse-temurin:25-jre-alpine`, `eclipse-temurin:25-jdk-alpine`, `node:24-alpine`, `nginx:alpine`
- [x] 2.6 Record findings (current pin vs latest supported) and flag any bump that would require a breaking major upgrade as an open question for the user

### Audit findings (looked up live 2026-09-30)

| Image | Current pin | Latest supported | Action |
| --- | --- | --- | --- |
| `traefik` | `v3.3` | `v3.7.13` (v3 line) | Bump → `v3.7.13` (verified: pulls) |
| `apache/kafka` | `3.9.1` | `3.9.2` (3.9 line); `4.x` exists | Bump → `3.9.2` (verified: pulls). **4.x is a major bump → open question** |
| `postgres` | `18-alpine` | 18 line = `18.6`; `18-alpine` rolls automatically | No edit — rolling major-line tag already current (18 is latest GA; 19 is beta) |
| `minio/minio` | `RELEASE.2024-03-03T17-50-39Z` | **No public image** | **BLOCKED — see open question below** |
| `maven` (api-gateway build) | `3.9-eclipse-temurin-25` | 3.9 line = `3.9.16`; rolls automatically | No edit — rolling tag current (Maven 4 is RC → open question) |
| `eclipse-temurin` (jre/jdk) | `25-jre-alpine` / `25-jdk-alpine` | `25.0.4.1_1`; `25-*` rolls automatically | No edit — rolling tag current (26 GA exists → major, open question) |
| `node` (ui build) | `24-alpine` | `24-alpine` rolls automatically | No edit — rolling tag current (26 is current, 22 is other LTS → major, open question) |
| `nginx` (ui runtime) | `alpine` | rolling | No edit — pre-existing rolling tag |

**Open questions for the user:**
- **MinIO community edition is now source-only.** As of 2026-09-30, `minio/minio` no longer pulls from Docker Hub (both the old pinned `RELEASE.2024-03-03T17-50-39Z` and the newest GitHub release `RELEASE.2025-10-15T17-29-55Z` are access-denied), and quay.io/ghcr.io are also unauthorized. MinIO's README states the community edition is distributed as source code only, with no pre-compiled binary/image releases. The existing compose pin is therefore already broken for fresh pulls. A replacement decision is required (pin a mirrored/last-public image + digest, switch to an S3-compatible alternative, or build MinIO from source) — cannot be chosen silently.
- Major-version bumps intentionally NOT applied (would need app/data work): Kafka 4.x, Maven 4.x, Temurin/JDK 26, Node 26. Left on current supported major lines.

## 3. Apply version updates

- [x] 3.1 Update stale image tags in `docker-compose.yml` (prioritize MinIO), staying within compatible version lines — traefik `v3.3`→`v3.7.13`, kafka `3.9.1`→`3.9.2`, minio → `bitnamilegacy/minio:2025.7.23-debian-12-r5` (user-chosen bitnami replacement; minio/minio community is now source-only with no public image). Also fixed the postgres 18 data-dir mount (`/var/lib/postgresql/data` → `/var/lib/postgresql`), required by the postgres 18 image and previously latent-broken.
- [x] 3.2 Update stale base image tags in `api-gateway/Dockerfile`, `parser-service/Dockerfile`, `ui/Dockerfile` — no edits needed; all bases use rolling within-major tags that already track the latest patch
- [x] 3.3 Confirm MinIO `server /data --console-address ":9001"` command and `/minio/health/live` endpoint are still valid for the chosen release; adjust command only if required — switched to bitnami: removed the `command:` override (bitnami's entrypoint runs the server; console defaults to :9001), moved data volume to `/bitnami/minio/data`; `/minio/health/live` on :9000 still valid and minio reports healthy.
- [x] 3.4 Keep all tags explicit — no `latest`

## 4. Verify the stack

- [x] 4.1 Run `docker compose config` and confirm it is valid
- [x] 4.2 Build/pull images and start the local stack (`docker compose up`, default local profile with override) — all 6 images build/pull and containers start
- [x] 4.3 Wait for postgres, kafka, minio, and ui healthchecks to pass; confirm api-gateway and parser-service start — **all six services up**: postgres, kafka, minio, ui HEALTHY; api-gateway healthy; parser-service "Started ParserServiceApplication" and stable. (Required fixing several pre-existing app-startup bugs — see below.)
- [x] 4.4 Verify the UI is reachable on its localhost port and an `/api` path reaches the API Gateway (end-to-end reachability) — UI serves HTTP 200; `/api/actuator/health` → 200 (proxy reaches the healthy gateway); `/api/auth/login` → 400 (Spring validation, i.e. request processed by the app). Verified on host :3001 because :3000 was occupied by an unrelated `eventim-mcp` container.
- [x] 4.5 If any bump breaks a service, revert that single pin to its prior value and record it as an open question — no image bump broke a service; all infra images verified healthy

### Pre-existing app-startup bugs fixed to complete verification (out of the original image-version scope)
Bringing up a truly fresh stack surfaced a chain of pre-existing bugs, all rooted in an incomplete Spring Boot 4.1 migration (the app declared tech libraries but not Boot 4's modular autoconfiguration artifacts, and had never been booted against an empty DB). These fail on `main` too and are unrelated to the image bumps. User approved fixing them here ("fix it here too"). Fixes applied:
1. `MinioConfig.documentsBucket` was a `@Bean` returning `void` (Spring Boot 4 rejects it) → converted to an `ApplicationRunner` bean. (api-gateway)
2. `spring-boot-liquibase` autoconfig module was missing (only `liquibase-core` present), so Liquibase never ran and schemas weren't created → added to both services' poms.
3. `spring-boot-kafka` autoconfig module was missing (only `spring-kafka` present), so no `KafkaTemplate` bean existed → added to both services' poms.
4. parser-service `Transaction` entity used `GenerationType.SEQUENCE` with a named sequence, but its changelog (and the whole project convention) uses `BIGSERIAL autoIncrement` → switched the entity to `GenerationType.IDENTITY` to match api-gateway's `Document`/`User`.

## 5. Docs and wrap-up

- [x] 5.1 Update `README.md` / any docs that reference specific image versions — no doc pins specific image versions (README/ARCHITECTURE reference MinIO/Postgres/Kafka only conceptually, still accurate). Added a `docker-compose.yml` comment explaining the non-obvious `bitnamilegacy/minio` choice.
- [ ] 5.2 Commit the changes on the feature branch with an English commit message summarizing the bumps
