## 1. Workflows and release configuration

- [x] 1.1 Run `npm test` in `ui/` and the Maven builds with `./mvnw verify` in `api-gateway/` and `parser-service/` locally and fix or report failures, because the new `ci.yml` adds `npm test` and uses the wrapper. Verify that all three pass.
- [ ] 1.2 Replace `api-gateway.yml`, `parser-service.yml`, `ui.yml`, `docker-compose.yml`, and `documentation.yml` under `.github/workflows/` with `ci.yml` (jobs `changes`, `api-gateway`, `parser-service`, `ui`, `image`, `compose`, `compose-smoke-test`, `documentation`, `openspec`, `ci`), and add `pr-title.yml`. Verify that no workflow triggers on `01-api-gateway`, `02-parser-service`, or `03-ui`, and that the documentation and SeaweedFS jobs run when their files change.
- [ ] 1.3 Add `release.yml`, `release-please-config.json` (`bootstrap-sha`, `release-as: 1.0.0`, `extra-files` for `.env.example`), `.release-please-manifest.json`, and `CHANGELOG.md`; add `":semanticCommits"` to `renovate.json`. Verify with the first release pull request that it proposes `v1.0.0`.

## 2. Images and Compose

- [x] 2.1 Add the `VERSION` and `REVISION` build arguments and OCI labels to the Dockerfiles of `api-gateway`, `parser-service`, and `ui`. Verify with `docker build --build-arg VERSION=0.0.0` and `docker inspect` for each.
- [x] 2.2 Switch the three components in `docker-compose.yml` to the `finanzbuddy-<component>` images pinned by `FINANZBUDDY_VERSION`, move `build:` to `docker-compose.override.yml`, and add the variable to `.env.example`. Verify that `docker compose -f docker-compose.yml config` has no `build:` section, fails without the variable naming it, and that `docker compose up --build` tags `finanzbuddy-<component>:dev` for all three.

## 3. Documentation and specifications

- [ ] 3.1 Update `README.md`, `docs/README.md`, `docs/ARCHITECTURE.md`, and the deployment notes (delivery flow, `FINANZBUDDY_VERSION`, rollback across migrations); add the new `delivery` capability to the documentation map. Verify with `python scripts/check_documentation_map.py` and against the root `docs/ARCHITECTURE.md` registry.
- [ ] 3.2 Run `openspec validate adopt-homebuddy-delivery --strict` and archive the change after the root umbrella tasks for this repository are done.
