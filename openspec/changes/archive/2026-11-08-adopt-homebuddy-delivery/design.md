# Design

## Context

finanzBuddy has three deployable components: `api-gateway` and `parser-service` (Spring Boot, Java 25, Maven wrapper, Spotless, JaCoCo) and `ui` (React, `tsc -b`, `oxlint`, Vite). It also has non-deployable infrastructure in `docker-compose.yml` (Traefik, PostgreSQL, Kafka, kafka-exporter, SeaweedFS) and a documentation map checked by `scripts/check_documentation_map.py`. Today's workflows are `api-gateway.yml` and `parser-service.yml` (compile, Spotless, test, JaCoCo, Codecov), `ui.yml` (type check, lint, build), `docker-compose.yml` (development `config`, SeaweedFS smoke test), and `documentation.yml`. The shared decisions are made in the root change `add-homebuddy-ci-and-releases` and its capability `homebuddy-delivery`; this design records only what is specific to finanzBuddy.

## Decisions

### 1. Jobs

`ci.yml` has these jobs: `changes` (filters `api-gateway/**`, `parser-service/**`, `ui/**`, `compose`, `openspec`, `documentation`); `api-gateway` and `parser-service` (each: `./mvnw -B spotless:check`, then `./mvnw -B verify`, which compiles, tests, and builds the JaCoCo report, followed by a Codecov upload with `fail_ci_if_error: false`); `ui` (`npm ci`, `npx tsc -b`, `npm run lint`, `npm test`, `npm run build`); `image` (matrix `api-gateway`, `parser-service`, `ui`; build without push); `compose` (both configurations with CI-only values for every required variable and `FINANZBUDDY_VERSION=0.0.0-ci`); `compose-smoke-test` (the existing SeaweedFS check, selected by `compose`); `documentation` (documentation integrity, selected by `docs/**`, `openspec/**`, `scripts/**`, and the module READMEs that the map links); `openspec`; and `ci`. The stale branch triggers disappear because `ci.yml` triggers only on pull requests to `main` and pushes to `main`. The `ui` job adds `npm test`, which no current workflow runs.

### 2. Images and Dockerfiles

Three images named `ghcr.io/rtcryo/finanzbuddy-<component>`. Each Dockerfile gains `ARG VERSION=dev` and `ARG REVISION=unknown` in its final stage with the `org.opencontainers.image.version` and `org.opencontainers.image.revision` labels.

### 3. Compose

The three components get `image: ghcr.io/rtcryo/finanzbuddy-<component>:${FINANZBUDDY_VERSION:?Set FINANZBUDDY_VERSION to a released version (see .env.example)}` and lose `build:`. `docker-compose.override.yml` adds `build:` (context and `dockerfile: Dockerfile`) and `image: finanzbuddy-<component>:dev` for each, next to the host ports it already publishes. Infrastructure services keep their pinned upstream images.

### 4. Release bootstrap

`bootstrap-sha` is the `main` commit that merges this change; `release-as: 1.0.0` yields `v1.0.0` and is removed afterwards in a `chore` pull request. `.env.example` carries `FINANZBUDDY_VERSION=1.0.0 # x-release-please-version` and is listed under `extra-files`.

## Risks / Trade-offs

- [The monitoring change edits the same Compose files] → Whichever change lands second rebases; the `compose` job then adds the monitoring variables.
- [`ui` job runs tests that were never run in CI and may fail] → Run `npm test` locally first; fix or report failures before enabling the job (see tasks).
- [The first pull from GHCR fails while packages are private] → Make the three packages public and linked right after the first release, before switching production.
