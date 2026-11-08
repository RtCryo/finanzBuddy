# Spec Delta

## ADDED Requirements

### Requirement: Production runs the released finanzBuddy images
The production Compose file SHALL run `api-gateway`, `parser-service`, and `ui` from `ghcr.io/rtcryo/finanzbuddy-<component>:${FINANZBUDDY_VERSION}` without `build:` sections, and SHALL fail with a message naming `FINANZBUDDY_VERSION` when the variable is unset, as the root capability `homebuddy-delivery` requires. `docker-compose.override.yml` SHALL build the three components from their directories and tag them `finanzbuddy-<component>:dev`. `.env.example` SHALL define `FINANZBUDDY_VERSION` with the latest released version. Infrastructure services keep their pinned upstream images; networks, routing, ports, and the service-local Traefik are unchanged.

#### Scenario: Deploy a release
- **WHEN** an operator sets `FINANZBUDDY_VERSION=1.0.0` and runs `docker compose -f docker-compose.yml pull` and `up -d`
- **THEN** the finanzBuddy application containers run the `1.0.0` images and keep their networks and routing

#### Scenario: Version variable missing
- **WHEN** `docker compose -f docker-compose.yml up -d` runs without `FINANZBUDDY_VERSION`
- **THEN** Compose fails with a message naming `FINANZBUDDY_VERSION`

#### Scenario: Local development
- **WHEN** a developer runs `docker compose up --build`
- **THEN** the three components are built from the working tree and tagged `finanzbuddy-<component>:dev`
