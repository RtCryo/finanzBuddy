## Context

The Compose stack pins every third-party image and every Dockerfile base image to an explicit tag. Explicit pins are good for reproducibility but rot over time. A quick inventory of the current pins:

| Location | Image | Notes |
| --- | --- | --- |
| compose | `traefik:v3.3` | minor line, check for v3.x patch |
| compose | `postgres:18-alpine` | major line pin, patches roll automatically |
| compose | `apache/kafka:3.9.1` | patch pin |
| compose | `minio/minio:RELEASE.2024-03-03T17-50-39Z` | ~18 months old, clearly stale |
| api-gateway Dockerfile | `maven:3.9-eclipse-temurin-25`, `eclipse-temurin:25-jre-alpine` | build + runtime |
| parser-service Dockerfile | `eclipse-temurin:25-jdk-alpine`, `eclipse-temurin:25-jre-alpine` | build + runtime |
| ui Dockerfile | `node:24-alpine`, `nginx:alpine` | build + runtime |

Constraint: the local UI test toolchain cannot run locally (Node too old on this machine), so UI verification relies on the Docker build (`npm ci && npm run build` inside `node:24-alpine`) plus a running container, not host-side vitest.

## Goals / Non-Goals

**Goals:**
- Establish the current upstream version for each pinned image.
- Bump the pins that are meaningfully behind to a current, supported tag.
- Prove the stack still builds, starts, passes healthchecks, and serves the app after the bumps.

**Non-Goals:**
- No switch to floating tags (`latest`) — pins stay explicit.
- No application code, schema, or config changes beyond image tags and docs.
- No major-version migration that would require app changes (e.g. a Postgres major upgrade with data migration) — flag those as open questions rather than doing them silently.

## Decisions

- **Determine current versions from authoritative sources, not guesses.** Query Docker Hub / registry tags (or the vendor release pages) for each image at implementation time. The knowledge cutoff makes hardcoding "latest" unreliable, so the apply step must look up live data.
- **Stay within the same major line by default.** Patch/minor bumps are low-risk and get applied directly. A major bump (e.g. Traefik v3→v4, Kafka 3→4, Node 24→newer LTS) is only applied if release notes show no breaking change relevant to this stack; otherwise it becomes an open question for the user.
- **MinIO is the priority.** The date-based `RELEASE.*` tag is the most stale and most security-relevant; pick a current stable `RELEASE.*` tag and confirm the `server /data --console-address` command and health endpoint still work.
- **Verify with the real stack.** After edits, run `docker compose config`, then `docker compose up` for the local profile, wait for healthchecks, and hit the UI + an API path. This is the definition of done, per the new spec requirement.
- **Do the work on a dedicated branch.** Create a feature branch before editing so the audit + bumps land as a reviewable unit.

## Risks / Trade-offs

- **A version bump breaks a service at runtime** → Verify against a live stack before finishing; if a bump breaks something and can't be quickly fixed, revert that single pin to its previous value and record it as an open question.
- **MinIO command/health API changed across the large version gap** → Check the chosen release's docs for `server` flags and the `/minio/health/live` endpoint; adjust the compose command only if required.
- **Registry lookups are unavailable in the environment** → If live tag lookups fail, stop and ask the user rather than bumping to a guessed tag.
- **Postgres/Kafka major bump implies data/format migration** → Explicitly out of scope; keep the current major and note the availability of a newer major as an open question.

## Migration Plan

1. Create the feature branch.
2. Look up current versions for every pinned image.
3. Edit the tags that warrant an update.
4. Build/pull and bring up the local stack; verify healthchecks and end-to-end reachability.
5. Update docs referencing versions.
6. Rollback: image tags are the only runtime change, so reverting the tag edits (or the branch) fully restores prior behavior.

## Open Questions

- Should any image take a major-version jump (Traefik, Kafka, Node LTS, Postgres), given those may need app or data-migration work? Default is no unless clearly safe.
