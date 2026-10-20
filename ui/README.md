# finanzBuddy UI

The finanzBuddy web interface. A React 19 + TypeScript single-page application built with Vite and styled with Tailwind CSS.

Documentation map: [UI implementation, capabilities, and history](../docs/README.md#subsystem-map) · [UI design system](DESIGN_SYSTEM.md) · [cross-service architecture](../docs/ARCHITECTURE.md).

## What it does

- User registration and login against the API Gateway.
- Upload financial documents (`CSV`) with a document type tag.
- List uploaded documents and poll for processing status updates.
- View normalized transactions for processed documents.
- Download the original document from the document detail screen.

## Prerequisites

- Node.js 24+ (the production image uses `node:24-alpine`)
- npm
- A running API Gateway at `http://localhost:8080` for local development (see the root [`docker-compose.yml`](../docker-compose.yml))

## Local development

```bash
cd ui
npm install
npm run dev
```

The Vite dev server starts on `http://localhost:5173` and proxies all `/api` requests to the API Gateway.

## Available scripts

| Script | Description |
|--------|-------------|
| `npm run dev` | Start the Vite dev server with HMR and API proxy. |
| `npm run build` | Type-check and build the production bundle to `dist/`. |
| `npm run preview` | Preview the production build locally. |
| `npm run lint` | Run Oxlint. |
| `npm run test` | Run the Vitest suite. |

## Environment variables

All variables are optional for the UI build.

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_API_PROXY_TARGET` | Target URL for the Vite dev proxy on `/api`. | `http://localhost:8080` |

Create `ui/.env` if you need to override the proxy target:

```bash
VITE_API_PROXY_TARGET=http://localhost:8080
```

The backend secrets (`JWT_SECRET`, `INTERNAL_API_KEY`) are configured in the root `.env` file for the Docker Compose stack. See [`.env.example`](../.env.example).

## Docker

Build the production image:

```bash
podman build -t finanzbuddy-ui ./ui
```

Or build it via Docker Compose:

```bash
docker compose build ui
```

Run the full stack (the `ui` service depends on `api-gateway`):

```bash
# From the repository root
cp .env.example .env   # fill in JWT_SECRET and INTERNAL_API_KEY
docker compose up -d
```

The UI is served on port `3000` and proxies `/api` to the API Gateway inside the container network.

## Project structure

```
ui/
├── Dockerfile          # Multi-stage production build
├── nginx.conf          # Nginx runtime config with /api proxy
├── index.html          # Vite entry point
├── package.json        # Scripts and dependencies
├── tsconfig*.json      # TypeScript configuration
├── vite.config.ts      # Vite + Tailwind + API proxy config
└── src/                # Application source
```
