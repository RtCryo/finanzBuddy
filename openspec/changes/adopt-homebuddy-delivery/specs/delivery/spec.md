# Spec Delta

## Purpose

Defines how finanzBuddy instantiates the root capability `homebuddy-delivery`: its deployable components, its images, its checks, and the checks it keeps beyond the baseline.

## ADDED Requirements

### Requirement: finanzBuddy follows the homeBuddy delivery contract
finanzBuddy SHALL satisfy the root capability `homebuddy-delivery` (CI baseline, `ci` gate, Conventional Commits, release-please releases, image publication, production on released images) and SHALL NOT restate it. Its deployable components are `api-gateway`, `parser-service`, and `ui`; their images are `ghcr.io/rtcryo/finanzbuddy-api-gateway`, `ghcr.io/rtcryo/finanzbuddy-parser-service`, and `ghcr.io/rtcryo/finanzbuddy-ui`. Its CI SHALL, for `api-gateway` and `parser-service`, check formatting with Spotless and run `./mvnw verify`, and for `ui`, run the type check, `npm run lint`, `npm test`, and `npm run build`. It SHALL build the image of every deployable component without pushing it. Its workflows SHALL NOT trigger on any branch other than `main` and pull requests to `main`.

#### Scenario: A pull request changes only the UI
- **WHEN** a pull request changes only files under `ui/`
- **THEN** the Maven jobs do not run, the `ui` and image jobs run, and `ci` reflects their result

#### Scenario: A feature branch is pushed
- **WHEN** a contributor pushes to a branch such as `01-api-gateway` without opening a pull request
- **THEN** no workflow runs

### Requirement: finanzBuddy keeps its repository-specific checks
`ci.yml` SHALL keep the documentation integrity check (`scripts/check_documentation_map.py`), the SeaweedFS Compose smoke test, and the Codecov coverage upload of the Java components as part of the same workflow, and the `ci` gate SHALL include the first two. A failed Codecov upload SHALL NOT fail `ci`.

#### Scenario: The documentation map is stale
- **WHEN** a pull request adds a capability spec that `docs/README.md` does not list
- **THEN** the documentation job fails and `ci` fails

#### Scenario: Object storage no longer starts
- **WHEN** a pull request changes `docker-compose.yml` so that the SeaweedFS service does not become healthy
- **THEN** the smoke test fails and `ci` fails

### Requirement: A release publishes the finanzBuddy images
Each finanzBuddy release SHALL publish the three images named above with the tags `X.Y.Z` and `X.Y` as specified by the root capability `homebuddy-delivery`, each built from its component's `Dockerfile` with the build arguments `VERSION` and `REVISION`.

#### Scenario: finanzBuddy releases v1.0.0
- **WHEN** the release pull request for `v1.0.0` is merged
- **THEN** `finanzbuddy-api-gateway:1.0.0`, `finanzbuddy-parser-service:1.0.0`, and `finanzbuddy-ui:1.0.0` exist under `ghcr.io/rtcryo/` with attestations, and the GitHub Release lists the three images with their digests
