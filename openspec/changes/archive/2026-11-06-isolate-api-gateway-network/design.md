# Design

## Context

See proposal.md for motivation. Docker registers a container's service name and container name on every network it joins, so aliases cannot hide the gateway on `homebuddy`; only network membership can. The gateway reads `AUTH_JWKS_URI` (default `http://auth:8080/.well-known/jwks.json`) and caches the key set. The root [design](../../../../../openspec/changes/archive/2026-11-06-isolate-product-apis/design.md) records the rationale for per-API identity networks.

## Decisions

- **Replace `homebuddy` with `finanzbuddy-identity` for `api-gateway` only.** The gateway keeps its default network, which provides Kafka, MinIO, and PostgreSQL access. The UI is unchanged.
- **Declare `finanzbuddy-identity` as `external: true`.** Compose then fails fast with the network name, matching the existing behavior for `homebuddy`.
- **No application change.** The alias `auth` is the same on the identity network.

## Risks / Trade-offs

- Deploying before authBuddy joins `finanzbuddy-identity` makes the JWKS fetch fail and every request 401. Mitigation: the rollout order and a reachability check before the change.
- The internal network has no outbound route; the gateway needs none through it.

## Migration Plan

1. Confirm authBuddy is deployed on `finanzbuddy-identity`.
2. Create the network on the host if missing, apply the change, and run `docker compose up -d`.

Rollback: put `api-gateway` back on `homebuddy` and recreate it.
