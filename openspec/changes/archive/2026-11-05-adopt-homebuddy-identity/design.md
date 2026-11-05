# Design

## Context

See [proposal](proposal.md). The gateway currently signs HS256 JWTs with the email as subject (`JwtService`), reloads the user by email in `JwtAuthenticationFilter`, and exposes `/auth/register` and `/auth/login`. `documents`, transaction category history, and personal tags reference `users(id)`. authBuddy issues RS256 tokens with `sub` = account UUID, `email`, and `aud` including `finanzbuddy`, and publishes a JWKS.

## Decisions

### Keep local users as a projection linked by account id

Add `users.account_id uuid null unique` and make `password_hash` nullable (changeset `012`, with rollback). A request's principal is resolved in one transaction:

1. `account_id = sub` -> that user (update `email` if the claim changed);
2. otherwise an unlinked user whose email matches the claim ignoring case -> set its `account_id`;
3. otherwise insert a new user with `account_id` and email.

A unique constraint plus retry on conflict handles concurrent first requests. All existing foreign keys stay valid, so no data migration is needed beyond the import. Step 2 is safe only because authBuddy registration stays disabled until the import of every existing finanzBuddy email has completed; that ordering is part of the runbook.

Alternative: rewrite every owner column to the account UUID. Larger, riskier migration with no user-visible benefit.

### Resource-server validation replaces local issuance

Use Spring Security's JWT resource server with `AUTH_JWKS_URI`, required issuer `AUTH_ISSUER`, and audience `finanzbuddy`. Replace `JwtService`/`JwtAuthenticationFilter` with a converter that resolves the local user as above. `/internal/**` keeps the service-key filter unchanged. `/auth/**` is removed from the gateway; nginx in the UI routes `/api/auth/` to `http://auth:8080/auth/` before the generic `/api/` gateway rule.

### Legacy export

A documented SQL export (`select email, password_hash from users where password_hash is not null`) is run by the operator into the authBuddy import. After the cut-over and a verification period, a later change may null out local hashes; this change only stops writing them.

## Risks / Trade-offs

- All users are signed out once -> announce; credentials are unchanged.
- authBuddy outage blocks new sign-ins -> cached JWKS keeps existing tokens valid.
- Email linking could attach a stranger to existing data if registration opened before import -> enforced ordering and a test that a registered-but-not-imported email still links only when no other user owns it; runbook check.

## Migration Plan

1. Deploy authBuddy with registration disabled; export users and import them.
2. Deploy gateway changeset `012` and the new security configuration plus the UI proxy rule.
3. Verify sign-in for an existing user and data ownership; enable authBuddy registration.
4. Rollback: redeploy the previous gateway/UI; local hashes are still present, and `account_id` is ignored by old code.
