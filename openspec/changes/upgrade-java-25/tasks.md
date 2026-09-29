# Tasks

## 1. Prepare the Java 25 update branch

- [ ] 1.1 Create and switch to `chore/upgrade-java-25`; verify it is the current branch with `git branch --show-current`.

## 2. Align Maven and CI toolchains

- [ ] 2.1 Set Java 25 as the compiler target in both Maven projects and set every API Gateway and Parser Service CI job to Temurin 25 with matching labels; run `mvn -B compile`, `mvn -B spotless:check`, and `mvn -B test` in each service, resolve migration-related failures, and rerun failed checks until they pass.

## 3. Align and verify container images

- [ ] 3.1 Use Java 25 JDK builder and JRE runtime images in both service Dockerfiles; verify `docker compose build api-gateway parser-service` succeeds and each built service reports Java 25 with `java -version`.
