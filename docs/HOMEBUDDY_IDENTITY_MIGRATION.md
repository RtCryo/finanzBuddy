# Migrating finanzBuddy to homeBuddy identity

This runbook covers the finanzBuddy side of the coordinated [homeBuddy rollout](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/archive/2026-11-05-establish-homebuddy-platform/proposal.md). Follow the [authBuddy legacy import runbook](https://github.com/RtCryo/authBuddy/blob/main/docs/MIGRATION.md) for the export and import commands. The export contains password hashes; keep it private and delete it after verification.

## Order of operations

1. Create the external Docker network `homebuddy` with `docker network create homebuddy`, or start the root homeBuddy Compose stack. Start authBuddy with `AUTH_REGISTRATION_ENABLED=false`.
2. Export every existing finanzBuddy user and import them into authBuddy using its runbook. Check that `created + skipped` equals the export count and `invalid=0`. Sign in with a known old password against authBuddy before switching the gateway.
3. Deploy finanzBuddy migration `012`, the gateway resource-server configuration, the UI auth proxy, and the shared-network Compose changes together. Set `AUTH_JWKS_URI=http://auth:8080/.well-known/jwks.json` and `AUTH_ISSUER=homebuddy-auth` in `.env`. The gateway no longer needs `JWT_SECRET`.
4. Sign in through the finanzBuddy UI with an existing account and the old password. Confirm that its documents and transactions remain visible. Check that the existing `users.id` is unchanged and its `account_id` now matches the token subject. A legacy browser session must sign in again because HS256 gateway tokens are rejected.
5. Enable authBuddy registration only after the import and finanzBuddy switch are verified. A new homeBuddy account gets a local finanzBuddy user on first API request, with `password_hash` null and no documents. Deploy calendarBuddy after this step.

The import-before-registration order protects email linking: an unlinked finanzBuddy row may be claimed by its imported authBuddy account, while an email already linked to another account is rejected.

## Verification

- `POST /api/auth/login` and `POST /api/auth/register` on the UI origin reach authBuddy. The gateway does not serve `/auth/**`.
- A token with issuer `homebuddy-auth`, audience `finanzbuddy`, and a key in authBuddy's JWKS reaches an owner-scoped endpoint. Wrong issuer, wrong audience, expired, or old HS256 tokens receive 401.
- An existing user's old password works at authBuddy and their document ownership remains attached to the same numeric `users.id`.
- `/internal/**` still requires the parser service key.
- In shared hosting, only root homeBuddy Traefik binds ports 80/443. Do not start the finanzBuddy service-local `production` Traefik profile alongside it.

## Rollback

Disable new registration in authBuddy, then redeploy the previous finanzBuddy gateway and UI. Legacy password hashes remain in `users.password_hash`, so imported users can use their old credentials. Migration `012` is additive for the old gateway and may remain in place during this rollback. If it must be rolled back, its rollback gives users created after cutover an unusable local password marker before restoring the NOT NULL constraint; their homeBuddy credentials and data remain in authBuddy and the finanzBuddy tables for a later retry. Keep authBuddy and its imported accounts until the gateway rollback is complete.
