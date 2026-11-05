# finanzBuddy UI

The finanzBuddy web interface. A React 19 + TypeScript single-page application built with Vite and styled with Tailwind CSS.

Documentation map: [UI implementation, capabilities, and history](../docs/README.md#subsystem-map) · [UI design system](DESIGN_SYSTEM.md) · [cross-service architecture](../docs/ARCHITECTURE.md).

## What it does

- User registration and login through the UI's authBuddy proxy, using a shared homeBuddy account.
- Upload financial documents (`CSV`) with a document type tag.
- List uploaded documents and poll for processing status updates.
- View normalized transactions for processed documents.
- Review transaction history with combined text, date, category, and reusable personal-tag filters; tag matching supports all selected tags (AND) or any selected tag (OR).
- Edit a transaction's category and current tags together in its detail panel. Personal tags remain available for reuse after their last assignment is removed.
- Manage personal tag rules at `/rules`: choose an existing tag or create one while saving a rule, combine Counterparty, Description, or Category conditions with AND/OR, choose an available category or create a personal category in a Category condition, and decide whether create, edit, or delete should affect existing transactions. Rules apply automatically to future imports.
- Inspect transactions from history or processed documents and browse snapshots.
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
- A running API Gateway at `http://localhost:8080` and authBuddy at `http://localhost:8090` for local development (see the root [`docker-compose.yml`](../docker-compose.yml))

## Local development

```bash
cd ui
npm install
npm run dev
```

The Vite dev server starts on `http://localhost:5173`. It proxies `/api/auth/` to authBuddy and other `/api/` requests to the API Gateway.

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
| `VITE_API_PROXY_TARGET` | Target URL for other `/api/` requests in Vite. | `http://localhost:8080` |
| `VITE_AUTH_PROXY_TARGET` | Target URL for `/api/auth/` in Vite. | `http://localhost:8090` |

Create `ui/.env` if you need to override the proxy target:

```bash
VITE_API_PROXY_TARGET=http://localhost:8080
```

The backend service key and authBuddy JWKS settings are configured in the root `.env` file for the Docker Compose stack. See [`.env.example`](../.env.example).

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
cp .env.example .env   # fill in INTERNAL_API_KEY
docker compose up -d
```

The UI is served on port `3000`, proxies `/api/auth/` to `auth:8080` on `homebuddy`, and sends other `/api/` requests to the API Gateway on the private Compose network. Start the [homeBuddy network and authBuddy](../docs/HOMEBUDDY_IDENTITY_MIGRATION.md) first.

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

The [documentation map](../docs/README.md#subsystem-map), [current rule requirements](../openspec/specs/transaction-tag-rules/spec.md), and [archived change](../openspec/changes/archive/2026-11-03-add-transaction-tag-rules/) connect this page to its implementation and behavior history.

The authenticated `/rules` page is implemented in `src/pages/RulesPage.tsx`; its API calls live in
`src/api/rules.ts`. The rule list has loading, empty, and recoverable error states. The editor uses
repeatable flat condition rows and shows AND/OR only after a second condition is added. Before
submitting a mutation, a separate choice defaults to leaving existing transaction tags unchanged.
Category rows load choices from `/transactions/categories`, retain older condition values not in
that list, and offer creation of a personal category within the rule mutation. The choice travels
in the mutation request, and the rule, tag, and category lists refresh after a successful save.
