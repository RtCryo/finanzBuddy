# Design

## Context

See proposal.md - Why. API Gateway currently compiles with JDK 21 and runs on a Java 25 JRE image. Parser Service uses Java 25 in CI and both Docker stages, but both Maven projects still set `java.version` to 21. CI step labels in Parser Service also incorrectly say JDK 21.

## Goals / Non-Goals

**Goals:**
- Make the JDK used for compilation, CI verification, and container runtime consistently Java 25 for both services.
- Keep the existing service-specific build tools and container structure where possible.

**Non-Goals:**
- Upgrade Spring Boot, Maven, or unrelated Java dependencies unless a compatibility failure requires a narrowly scoped prerequisite.
- Change service behavior or APIs.

## Decisions

- Set `java.version` to 25 in both Maven projects. This changes the produced bytecode baseline as well as the JDK running the build, so Java 25 is consistently required rather than merely being a test runtime.
- Use Temurin 25 for all Java setup steps in both GitHub Actions workflows, and correct step labels to match the configured version. This makes compile, formatting, and test jobs exercise the same toolchain.
- Use Java 25 JDK build images and Java 25 JRE runtime images in both Dockerfiles. For API Gateway, select a Maven builder image that provides Maven with Temurin 25; for Parser Service, preserve the existing Maven Wrapper-based build.
- Keep updates isolated on `chore/upgrade-java-25`. Prefer resolving incompatibilities by updating only the affected build plugin or dependency after confirming the failure is caused by the Java 25 transition.

## Risks / Trade-offs

- [A Maven plugin, annotation processor, or dependency may not support JDK 25 or Java 25 bytecode] → Run compile, formatting, and full test checks for both services; update only incompatible tooling/dependencies and rerun the failing checks.
- [A Java 25 Docker builder tag may not exist or may differ from the current image's Maven setup] → Verify the exact image by building both service images before merging; use a valid Maven/Temurin 25 builder combination while preserving the current build process.
- [Java 25 bytecode cannot run on Java 21] → Treat the minimum runtime increase as intentional and verify both final images use Java 25 JRE.

## Migration Plan

1. Create the dedicated branch and update Maven compiler targets, CI JDK versions/labels, and Docker builder/runtime images.
2. Run Maven compile, formatting checks, and tests for both services on JDK 25, then build both Docker images.
3. Resolve any failures attributable to the migration and repeat the affected checks; merge only after all checks pass. Roll back by reverting the branch if Java 25 compatibility cannot be established.
