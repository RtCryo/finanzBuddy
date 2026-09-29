# Proposal

## Why

The project is moving to Java 25, an LTS release, but the two services currently mix Java 21 and Java 25 across Maven compiler targets, CI, and Docker build images. Aligning the toolchain on Java 25 makes the supported runtime and the code compiled and tested by CI consistent.

## What Changes

- **BREAKING**: Set both Maven projects' Java target to 25 so produced artifacts require Java 25 or newer.
- Align API Gateway and Parser Service CI compile, style, and test jobs on Temurin 25, correcting misleading step labels.
- Align Docker build and runtime stages with Java 25 and verify both container builds.
- Perform the migration on a dedicated branch, run service checks, and resolve regressions before completing the update.

## Capabilities

### New Capabilities

None. This is a build/runtime toolchain update with no new application behavior; specs are intentionally skipped.

### Modified Capabilities

None.

## Impact

- `api-gateway/pom.xml`, `parser-service/pom.xml`
- `.github/workflows/api-gateway.yml`, `.github/workflows/parser-service.yml`
- `api-gateway/Dockerfile`, `parser-service/Dockerfile`
- Java bytecode compatibility and minimum runtime requirement for both services
