# Tasks

## 1. Gateway identity

- [x] 1.1 Add Liquibase changeset `012` for `users.account_id` (unique, nullable) and nullable `password_hash` with rollback; verify migration and entity/schema parity tests.
- [x] 1.2 Replace HS256 issuance/validation with JWKS resource-server validation (issuer, `finanzbuddy` audience) and account-to-user resolution by id, email link, or creation; verify tests for each path, email change, concurrent first requests, wrong issuer/audience, legacy HS256 tokens, and unchanged ownership checks.
- [x] 1.3 Remove `AuthController`, auth DTOs/service, `DatabaseUserDetailsService` password use, and `JWT_SECRET`; verify `/auth/**` is no longer served by the gateway and `/internal/**` still requires the service key.

## 2. UI and deployment

- [x] 2.1 Add the nginx `/api/auth/` proxy to authBuddy and update auth copy to name the shared homeBuddy account; verify UI tests for login/register and the production build.
- [x] 2.2 Join gateway and UI to the external `homebuddy` network, add `AUTH_JWKS_URI`/`AUTH_ISSUER` to compose and `.env.example`, and add `traefik.docker.network=homebuddy` to the UI labels so the shared root Traefik can route it (the local Traefik stays for standalone deployments only); verify the compose workflow.

## 3. Documentation and verification

- [x] 3.1 Update `docs/ARCHITECTURE.md` (auth boundary, routes, `users` ownership), gateway/UI READMEs, the migration runbook, and `docs/README.md`; run `rtk proxy python scripts/check_documentation_map.py`.
- [x] 3.2 Run gateway and UI test suites, build, lint, and `openspec validate adopt-homebuddy-identity --strict`.
