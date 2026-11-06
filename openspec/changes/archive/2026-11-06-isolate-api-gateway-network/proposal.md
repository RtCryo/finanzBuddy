# Proposal

## Why

The root capability `homebuddy-deployment` says a product API SHALL NOT be reachable from another stack, and the root change [`isolate-product-apis`](../../../../../openspec/changes/archive/2026-11-06-isolate-product-apis/proposal.md) closes the gap. The API Gateway still joins the shared `homebuddy` network only to fetch authBuddy's JWKS, so every container of every stack can call it, including its `/internal/**` endpoints that only the service API key protects.

## What Changes

- **BREAKING (deployment)**: `api-gateway` leaves `homebuddy` and joins the external, internal-only network `finanzbuddy-identity` instead, where authBuddy is reachable as `auth`. `AUTH_JWKS_URI` and all other configuration stay unchanged.
- The UI keeps its `homebuddy` membership and reaches the gateway over the stack's default network.
- The stack no longer starts until `finanzbuddy-identity` exists. README and the identity migration runbook document `docker network create --internal finanzbuddy-identity`.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `compose-deployment`: the requirement "finanzBuddy reaches the homeBuddy identity service" separates the UI's `homebuddy` membership from the gateway's identity network.

## Non-goals

- Changing token validation, the JWKS URI, or the service API key on `/internal/**`.
- Isolating the UI on `homebuddy`.

## Impact

- `docker-compose.yml`, `README.md`, `docs/HOMEBUDDY_IDENTITY_MIGRATION.md`, `docs/ARCHITECTURE.md` where it names networks, and the `compose-deployment` spec.
- Rollout: apply only after authBuddy's [`join-identity-networks`](../../../../../authBuddy/openspec/changes/archive/2026-11-06-join-identity-networks/proposal.md) is deployed; otherwise every request returns 401.
