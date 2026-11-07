# Tasks

## 1. Compose

- [x] 1.1 In `docker-compose.yml`, replace `homebuddy` with the external `finanzbuddy-identity` network for `api-gateway`, declare the network, and keep the UI on `homebuddy`. Verify with `docker compose config` that `api-gateway` lists `default` and `finanzbuddy-identity` only, and `ui` still lists `homebuddy`.
- [x] 1.2 Verify that the Compose smoke test workflow still passes, and add the network creation to it if a started service now needs `finanzbuddy-identity`.

## 2. Documentation

- [x] 2.1 Document `docker network create --internal finanzbuddy-identity` in `README.md` and `docs/HOMEBUDDY_IDENTITY_MIGRATION.md`, and update network mentions in `docs/ARCHITECTURE.md`.

## 3. Verification

- [x] 3.1 After authBuddy is deployed on `finanzbuddy-identity`, verify that `http://localhost:3000/api/documents` accepts a fresh homeBuddy token.
- [x] 3.2 Verify that `api-gateway` and `finanzbuddy-api-gateway` do not resolve from a throwaway container on `homebuddy`.
- [x] 3.3 Verify that starting the stack without `finanzbuddy-identity` fails with a message naming it.
