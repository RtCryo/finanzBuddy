# Migrating finanzBuddy to homeBuddy identity

This runbook covers the finanzBuddy side of the coordinated [homeBuddy rollout](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/archive/2026-11-05-establish-homebuddy-platform/proposal.md). Follow the [authBuddy legacy import runbook](https://github.com/RtCryo/authBuddy/blob/main/docs/MIGRATION.md) for the export and import commands. The export contains password hashes; keep it private and delete it after verification.

## Order of operations

1. Create the external Docker network `homebuddy` with `docker network create homebuddy`, or start the root homeBuddy Compose stack, and create the internal identity network with `docker network create --internal finanzbuddy-identity` (the gateway reaches authBuddy's JWKS only over it). Start authBuddy with `AUTH_REGISTRATION_ENABLED=false`.
2. Export every existing finanzBuddy user and import them into authBuddy using its runbook. Check that `created + skipped` equals the export count and `invalid=0`. Sign in with a known old password against authBuddy before switching the gateway.
3. Deploy finanzBuddy migrations `012` and `013`, the gateway resource-server configuration, the UI auth proxy, and the shared-network Compose changes together. Set `AUTH_JWKS_URI=http://auth:8080/.well-known/jwks.json` and `AUTH_ISSUER=homebuddy-auth` in `.env`. The gateway no longer needs `JWT_SECRET`. Until step 4 is done, existing users receive 401 because their rows are not linked yet.
4. Link the existing finanzBuddy users to their imported accounts (see [Link legacy users](#link-legacy-users)). The step must report `still_unlinked = 0`.
5. Sign in through the finanzBuddy UI with an existing account and the old password. Confirm that its documents and transactions remain visible. Check that the existing `users.id` is unchanged and its `account_id` matches the token subject. A legacy browser session must sign in again because HS256 gateway tokens are rejected.
6. Enable authBuddy registration only after the import, the link step, and the finanzBuddy switch are verified. A new homeBuddy account gets a local finanzBuddy user on first API request, with `password_hash` null and no documents. Deploy calendarBuddy after this step.

The gateway never links a local user by email at request time, because a matching email does not prove that the account belongs to the person who owned the row. Linking happens only in step 4, while registration is still disabled, so every account in authBuddy came from the import. A token whose email matches a local user that is still unlinked is rejected with 401, and the gateway logs a warning that names the account id.

## Link legacy users

Run both commands from a shell that can reach the two database containers. The account file contains no passwords, but it does contain every email address; keep it private and delete it afterwards.

```bash
docker exec authbuddy-postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\copy (select id, email_normalized from accounts) to stdout with (format csv)"' \
  > accounts.csv
chmod 600 accounts.csv

docker exec -i finanzbuddy-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -1 \
  -c "create temp table homebuddy_accounts (account_id uuid not null, email text not null)" \
  -c "\copy homebuddy_accounts from pstdin with (format csv)" \
  -c "update users u set account_id = a.account_id from homebuddy_accounts a where u.account_id is null and lower(trim(u.email)) = a.email" \
  -c "select count(*) as still_unlinked from users where account_id is null"' \
  < accounts.csv
```

The second command runs as one transaction and is safe to repeat. It prints `UPDATE <n>` and then `still_unlinked`.

- `still_unlinked > 0`: those rows had no imported account, usually because the import reported them as `invalid`. Fix and import them in authBuddy, then run this step again.
- `duplicate key value violates unique constraint "uk_users_account_id"`: two local rows differ only in letter case and match the same account. Nothing was changed. Decide which row keeps the data, merge or remove the other one, and run the step again.

Delete `accounts.csv` when `still_unlinked` is `0`.

## Verification

- `POST /api/auth/login` and `POST /api/auth/register` on the UI origin reach authBuddy. The gateway does not serve `/auth/**`.
- A token with issuer `homebuddy-auth`, audience `finanzbuddy`, and a key in authBuddy's JWKS reaches an owner-scoped endpoint. Wrong issuer, wrong audience, expired, or old HS256 tokens receive 401.
- An existing user's old password works at authBuddy and their document ownership remains attached to the same numeric `users.id`.
- `/internal/**` still requires the parser service key.
- A token without `aud` or without `exp` receives 401.
- In shared hosting, only root homeBuddy Traefik binds ports 80/443. Do not start the finanzBuddy service-local `production` Traefik profile alongside it.

## Rollback

Disable new registration in authBuddy, stop the new gateway, and prepare the database for the previous gateway before you redeploy it:

```bash
docker exec -i finanzbuddy-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -1' <<'SQL'
-- Users created after cutover have no local password. The previous gateway cannot load a
-- null password, so give them an unusable marker; they cannot sign in until the retry.
update users set password_hash = '!' where password_hash is null;
-- The previous gateway looks users up by email and needs it to be unique again (migration 013).
select email, count(*) from users group by email having count(*) > 1;
create unique index if not exists idx_users_email_unique on users (email);
SQL
```

If the duplicate query returns rows, the unique index fails and nothing is changed. Two accounts then store the same email locally, which happens only after an email change. Resolve them before you redeploy. Then redeploy the previous finanzBuddy gateway and UI. Legacy password hashes remain in `users.password_hash`, so imported users can use their old credentials. The `account_id` column and its links can stay in place: the previous gateway ignores them, and a later retry reuses them. Keep authBuddy and its imported accounts until the gateway rollback is complete. Before the next attempt, drop `idx_users_email_unique` again.
