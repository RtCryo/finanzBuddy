# finanzBuddy UI

The finanzBuddy web interface. A React 19 + TypeScript single-page application built with Vite and styled with Tailwind CSS.

Documentation map: [UI implementation, capabilities, and history](../docs/README.md#subsystem-map) · [UI design system](DESIGN_SYSTEM.md) · [cross-service architecture](../docs/ARCHITECTURE.md).

## What it does

- User registration and login against the API Gateway.
- Upload financial documents (`CSV`) with a document type tag.
- List uploaded documents and poll for processing status updates.
- View normalized transactions for processed documents.
- Inspect transactions from history or processed documents, browse snapshots, and edit categories.
- Compare other expenses with the same counterparty inside the shared details panel. The section
  shows latest negative expenses across owned processed documents using exact case/whitespace
  matching, independent of list filters and historical snapshot selection. Selectable rows show
  Value date and signed amount/currency, newest dates first (undated last). Load more appends
  pages of 20; loading, empty, missing-counterparty, and retry states do not block category editing.
  Select a related expense to open its latest details in the same panel. Save or cancel category
  edits before navigating. Closing returns focus to the original list row and preserves list state.
- Download the original document from the document detail screen.

## Prerequisites

- Node.js 26+ (the production image uses `node:26-alpine`)
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
