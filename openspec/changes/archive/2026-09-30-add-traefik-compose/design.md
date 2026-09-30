# Design

## Context

The root Compose stack currently publishes the UI on host port 3000 and the API Gateway on 8080. The UI's Nginx configuration serves the SPA and proxies `/api/` to `api-gateway:8080`, so a single host-based route to the UI preserves the existing same-origin API flow. The root README currently describes localhost access as the normal Compose quick start.

See `proposal.md` for motivation and `specs/compose-deployment/spec.md` for the behavior contract.

## Goals / Non-Goals

**Goals:**

- Provide a production Compose mode with one public application entry point on ports 80 and 443, HTTPS, and ACME-managed certificates.
- Keep the existing local-development localhost workflow usable without a real domain or certificate.
- Keep API requests on the existing UI Nginx-to-gateway path.

**Non-Goals:**

- Change application routes, API behavior, or service internals.
- Publish the Traefik dashboard or expose it as a public service.
- Put databases, Kafka, MinIO, or internal services behind Traefik as user-facing routes.

## Decisions

1. **Separate production and local Compose modes.** Keep `docker-compose.yml` as the production-capable base, with Traefik behind a `production` profile and no direct host port mappings for the UI or API Gateway. Use the automatically loaded `docker-compose.override.yml` to preserve the existing localhost port mappings for the default local workflow. Document the production command with an explicit `-f docker-compose.yml` and the `production` profile so the development override is not merged.
   - **Alternative considered:** Keep direct UI/API host ports in the production configuration. This is simpler but leaves a path that bypasses the HTTPS entry point.
   - **Alternative considered:** Require ACME and a public domain for every Compose startup. This breaks the existing localhost quick start and is unnecessary for local development.

2. **Route the production hostname to the UI and retain Nginx API proxying.** Configure a Traefik Docker-provider router for the configured domain targeting the UI container on its internal HTTP port. Keep `/api` handling in the existing Nginx configuration, which forwards those requests to the API Gateway over the Compose network.
   - **Alternative considered:** Route `/api` directly from Traefik to the API Gateway. That duplicates routing and path-prefix behavior already implemented by Nginx.

3. **Use ACME HTTP-01 with environment-configured identity.** Configure Traefik's web and secure web entry points, redirect web traffic to websecure, and use the HTTP challenge for certificate issuance and renewal. Supply the hostname and ACME contact email through documented environment variables rather than hardcoded deployment values. The production instructions must require public DNS to point to the host and inbound ports 80 and 443 to be reachable.
   - **Alternative considered:** Use DNS-01 validation, which requires credentials for a DNS provider that the project has not selected.
   - **Alternative considered:** Mount manually managed certificate files, which would not provide the requested automatic ACME lifecycle.

4. **Persist ACME state and keep Traefik administration private.** Store Traefik's ACME data in a named volume, ensure the ACME file has permissions accepted by Traefik, mount the Docker socket read-only for provider discovery, and leave the dashboard disabled/unpublished.
   - **Alternative considered:** Store certificates in the container filesystem, which loses state on recreation and risks repeated issuance.

## Risks / Trade-offs

- [ACME issuance or renewal fails if public DNS or inbound port 80 is unavailable] → Document DNS and firewall prerequisites and keep the challenge entry point available.
- [An incorrectly configured hostname or email prevents a working production TLS setup] → Add explicit `.env.example` entries and production startup instructions requiring operators to replace the example values.
- [Production and local Compose modes can be confused] → Document separate commands and explain that the production command intentionally omits the local override.
- [Traefik needs access to Docker metadata through the Docker socket] → Mount the socket read-only and do not publish the dashboard.

## Migration Plan

1. Add production Traefik configuration and routing labels, persistent ACME storage, and a local Compose override for the existing localhost ports.
2. Add domain and ACME email variables to `.env.example` and update the root README with separate local and production startup steps and network prerequisites.
3. Validate the local Compose configuration without ACME, then validate the production Compose configuration with sample domain/email values; verify the final deployment on a real DNS name to exercise ACME issuance and HTTPS redirects.
4. Roll back by reverting the Compose and documentation changes; for an immediate operational rollback, stop the production profile and use the local override workflow.
