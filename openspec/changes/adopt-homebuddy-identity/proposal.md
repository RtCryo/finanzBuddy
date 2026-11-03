# Proposal

## Why

homeBuddy families should use one account for finanzBuddy and the new calendarBuddy. The new authBuddy identity service ([`add-identity-service`](../../../../authBuddy/openspec/changes/add-identity-service/proposal.md)) owns those accounts. finanzBuddy must accept its tokens and stop issuing its own, while keeping every existing user's data and password.

## What Changes

- **BREAKING (sessions only):** the API Gateway no longer issues HS256 tokens; existing browser sessions must sign in once more. Credentials are unchanged because users are imported into authBuddy with their BCrypt hashes.
- The UI's `/api/auth/register` and `/api/auth/login` calls are proxied by nginx to authBuddy; the request/response contract stays `{ token, email }`, so UI pages keep their behavior.
- The gateway validates RS256 tokens via authBuddy's JWKS (issuer `homebuddy-auth`, audience `finanzbuddy`) and maps the token subject to the local `users` row through a new `account_id` column: by account id, else by linking the unlinked user with the same email, else by creating a local user. Existing foreign keys to `users(id)` remain unchanged.
- Remove the gateway's `AuthController`, password checking, and `JWT_SECRET`; make `users.password_hash` nullable and stop writing it.
- Join the external `homebuddy` network so the gateway and UI reach authBuddy.
- Auth screens mention that the account is the family's homeBuddy account, also used for calendarBuddy.

## Dependencies

- Coordinated by the root umbrella change [`establish-homebuddy-platform`](../../../../openspec/changes/establish-homebuddy-platform/proposal.md) (rollout step 2, after authBuddy and the legacy import). Token validation follows the root `homebuddy-identity-contract`; network, `/api/auth` proxy, and shared Traefik follow `homebuddy-deployment`.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `user-authentication`: Registration and sign-in move to authBuddy; protected endpoints accept homeBuddy tokens.
- `user-auth-ui`: Auth calls go through the identity proxy; copy names the shared homeBuddy account.
- `compose-deployment`: Gateway and UI join the `homebuddy` network.

## Impact

- API Gateway: security config, JWT filter replaced by resource-server validation, user mapping, Liquibase changeset `012`, removal of auth endpoints and DTOs, tests.
- UI: nginx proxy rule and copy only.
- Compose and `.env.example`: `AUTH_JWKS_URI`, `AUTH_ISSUER`; remove `JWT_SECRET` after cut-over.
- `docs/ARCHITECTURE.md`, gateway/UI READMEs, and `docs/README.md` updated with the new trust boundary.
