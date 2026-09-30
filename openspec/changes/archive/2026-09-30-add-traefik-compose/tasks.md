# Tasks

## 1. Configure the production reverse proxy

- [x] 1.1 Add the production-profile Traefik service with HTTP/HTTPS entry points, HTTP-to-HTTPS redirection, ACME HTTP-01 configuration, environment-based domain/email values, read-only Docker socket access, and persistent ACME storage; verify `docker compose -f docker-compose.yml --profile production config` succeeds with the deployment variables set.
- [x] 1.2 Add the UI router/service labels for the configured hostname and remove UI/API Gateway host port mappings from the production base; verify the rendered production Compose configuration exposes ports 80/443 through Traefik and no direct UI/API Gateway host ports.
- [x] 1.3 Remove the host port mappings for the internal infrastructure services (PostgreSQL, Kafka, MinIO) from the production base so only Traefik is published; verify the rendered production Compose configuration publishes only ports 80/443.

## 2. Preserve and document local development

- [x] 2.1 Add `docker-compose.override.yml` with the localhost UI/API Gateway and infrastructure (PostgreSQL, Kafka, MinIO) port mappings; verify the default `docker compose config` includes ports 3000, 8080, 5432, 29092, 9000, 9001 and the local startup does not activate the production Traefik profile.
- [x] 2.2 Add the Traefik domain and ACME email to `.env.example` and document separate local and production commands, DNS and firewall prerequisites, and the production domain requirements in the root README; verify the documented commands and required variables match the Compose configuration.

## 3. Verify deployment behavior

- [ ] 3.1 Start the local stack with `docker compose up --build` and verify the UI responds at `http://localhost:3000` and the API Gateway remains reachable at `http://localhost:8080`.
- [ ] 3.2 With a real configured domain resolving to the deployment host and ports 80/443 reachable, start the production profile and verify ACME issues a valid certificate, HTTP redirects to HTTPS, and the HTTPS UI and `/api` paths reach their intended services.
