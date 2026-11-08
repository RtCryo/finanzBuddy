# Proposal

## Why

The root change [`add-homebuddy-ci-and-releases`](../../../../openspec/changes/add-homebuddy-ci-and-releases/proposal.md) defines the root capability `homebuddy-delivery`: one CI workflow with a required `ci` gate, Conventional Commits, release-please releases, and production Compose files that run released images. finanzBuddy runs five path-filtered workflows (some still triggered by the long-gone branches `01-api-gateway`, `02-parser-service`, and `03-ui`), builds no Docker image in CI, validates only the development Compose file, never validates OpenSpec, and has no release, so production builds three images from whatever source is checked out.

## What Changes

- Replace `api-gateway.yml`, `parser-service.yml`, `ui.yml`, and `docker-compose.yml` under `.github/workflows/` with `ci.yml` following the root capability `homebuddy-delivery`: change detection, per component the checks listed in the `delivery` spec, an image build without push for each deployable component, validation of the production and development Compose configuration, `openspec validate --all --strict` pinned to `@fission-ai/openspec@1.13.2`, and the `ci` gate. Maven runs through each module's `./mvnw` instead of `mvn`.
- Keep three finanzBuddy-specific checks as further jobs of `ci.yml`: documentation integrity (`scripts/check_documentation_map.py`, replacing `documentation.yml`), the SeaweedFS Compose smoke test, and the Codecov upload as a non-failing step of the Java test jobs.
- Add `pr-title.yml`, `release.yml`, `release-please-config.json`, `.release-please-manifest.json`, and `CHANGELOG.md`; add `":semanticCommits"` to `renovate.json`.
- Publish three images per release: `ghcr.io/rtcryo/finanzbuddy-api-gateway`, `ghcr.io/rtcryo/finanzbuddy-parser-service`, and `ghcr.io/rtcryo/finanzbuddy-ui`, each tagged `X.Y.Z` and `X.Y` with OCI labels and provenance. The three Dockerfiles accept `VERSION` and `REVISION` build arguments.
- `docker-compose.yml` runs the three components from those images, pinned by `FINANZBUDDY_VERSION`; `docker-compose.override.yml` builds them from source and tags them `finanzbuddy-<component>:dev`. `.env.example` documents `FINANZBUDDY_VERSION`. The service-local Traefik, networks, and ports are unchanged.
- **BREAKING (deployment)**: `docker compose -f docker-compose.yml up -d` no longer builds; it pulls the version named in `.env` and fails naming `FINANZBUDDY_VERSION` when it is missing.
- Document the delivery flow in `README.md`, `docs/README.md`, and `docs/ARCHITECTURE.md`.

## Capabilities

### New Capabilities

- `delivery`: finanzBuddy's deployable components, image names, checks per component, the checks kept beyond the baseline, and release configuration, as the instance of root `homebuddy-delivery` for this repository.

### Modified Capabilities

- `compose-deployment`: production runs the released `api-gateway`, `parser-service`, and `ui` images pinned by `FINANZBUDDY_VERSION`; local development builds from source.

## Impact

- `.github/workflows/`, `release-please-config.json`, `.release-please-manifest.json`, `CHANGELOG.md`, `renovate.json`, `docker-compose.yml`, `docker-compose.override.yml`, `.env.example`, the Dockerfiles of `api-gateway`, `parser-service`, and `ui`, `README.md`, `docs/`.
- GitHub settings: squash merge only, required checks `ci` and the title check on `main`, secret `RELEASE_PLEASE_TOKEN`, public and linked GHCR packages (configured in the root umbrella change's rollout).
- Rollout order: finanzBuddy is redeployed from its `v1.0.0` release after authBuddy runs its own release in production.
