## Why

The Docker Compose stack pins several third-party images to explicit versions, but they have drifted from current releases. Most notably `minio/minio:RELEASE.2024-03-03T17-50-39Z` is well over a year old, and the other pins (Traefik, PostgreSQL, Kafka) and Dockerfile base images have not been reviewed for a while. Running outdated images risks missing security fixes and bug fixes. This change audits every pinned image against its current release and updates the ones that need it, then verifies the stack still builds and runs.

## What Changes

- Audit all pinned images in `docker-compose.yml` against their current upstream releases:
  - `traefik:v3.3`, `postgres:18-alpine`, `apache/kafka:3.9.1`, `minio/minio:RELEASE.2024-03-03T17-50-39Z`
- Audit the base images pinned in the service Dockerfiles:
  - `api-gateway`: `maven:3.9-eclipse-temurin-25`, `eclipse-temurin:25-jre-alpine`
  - `parser-service`: `eclipse-temurin:25-jdk-alpine`, `eclipse-temurin:25-jre-alpine`
  - `ui`: `node:24-alpine`, `nginx:alpine`
- Update each image tag to a current, supported version where an update is warranted, staying within the same major line unless a major bump is clearly safe and beneficial.
- Verify the stack after updates: images pull/build, `docker compose config` is valid, the full local stack starts, healthchecks pass, and the application is reachable end-to-end.
- Update any README/docs that reference specific image versions so they stay accurate.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `compose-deployment`: Add a requirement that the Compose stack pin its third-party and base images to current, upstream-supported versions, and that version bumps be verified against a running stack before being considered done.

## Impact

- `docker-compose.yml` (image tags for traefik, postgres, kafka, minio)
- `api-gateway/Dockerfile`, `parser-service/Dockerfile`, `ui/Dockerfile` (base image tags)
- `README.md` / any docs referencing pinned versions
- No application source or API changes; runtime behavior should be unchanged aside from picking up upstream fixes. A new Git branch is created for the work.
