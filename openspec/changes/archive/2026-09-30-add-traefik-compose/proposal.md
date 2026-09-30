# Proposal

## Why

The Compose stack currently exposes the UI and API Gateway directly on separate host ports, so it has no single HTTPS entry point suitable for a domain-based deployment. Adding Traefik with automatic ACME certificates provides managed HTTPS while keeping routing configuration alongside the Compose services.

## What Changes

- Add Traefik to the Compose stack as the public HTTP/HTTPS entry point, with HTTP-to-HTTPS redirection and ACME certificate provisioning.
- Route the configured application domain to the UI; retain the existing UI Nginx `/api` proxy to the API Gateway.
- Remove direct host-port publication for the UI and API Gateway so application traffic enters through Traefik.
- Preserve the existing localhost development workflow through a Compose override that is excluded from the production deployment command.
- Configure the domain, ACME contact email, and persistent certificate storage without hardcoding deployment-specific values.
- Document the DNS, port, environment, and startup requirements for the HTTPS Compose deployment.

## Capabilities

### New Capabilities

- `compose-deployment`: Public HTTPS access and reverse-proxy routing for the Docker Compose application stack.

### Modified Capabilities

None.

## Impact

- Affected systems: root `docker-compose.yml`, a local-development Compose override, `.env.example`, and deployment guidance in `README.md`.
- Adds the Traefik container and a persistent volume for ACME certificate data.
- In production, the UI and API Gateway will no longer be directly published on host ports; application access will use the configured domain through ports 80 and 443. The local-development workflow retains its existing localhost ports.
- ACME HTTP-01 validation requires the configured DNS name to resolve to the host and inbound port 80 to be reachable; HTTPS traffic uses port 443.
