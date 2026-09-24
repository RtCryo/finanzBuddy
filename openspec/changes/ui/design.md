# Design: User Interface

## Context

The UI is a single-page React application that serves as the client for the API Gateway. It handles authentication, document upload, status tracking, and transaction viewing. See `proposal.md` for motivation and scope.

## Goals / Non-Goals

**Goals:**
- Provide a fast, minimal interface for registering and logging in.
- Allow users to upload financial documents and select their type.
- Show document processing status clearly.
- Display normalized transactions after successful parsing.

**Non-Goals:**
- Mobile-native experience (responsive web is sufficient).
- Advanced dashboards, charts, or analytics.
- Offline support or PWA features.

## Decisions

### React 18 + Vite

**Rationale:** Vite offers fast HMR and simple configuration. React is the most common choice for SPAs and integrates well with the team’s Java backend via REST.

**Alternatives considered:** Next.js (SSR not needed), Vue (less ecosystem alignment if team knows React).

### Tailwind CSS

**Rationale:** Utility-first CSS speeds up UI prototyping without needing a custom design system.

**Alternatives considered:** Material UI (heavier), plain CSS (slower to iterate).

### React Router for Navigation

**Rationale:** Simple client-side routing for `/login`, `/register`, `/documents`, and `/documents/:id`.

### Axios + Request Interceptor

**Rationale:** Axios automatically attaches the JWT access token to every request via an interceptor and handles common error codes globally.

### Token Storage

**Rationale:** Store JWT in `localStorage` for MVP simplicity. Future iterations should move to httpOnly cookies or a refresh-token rotation.

**Alternatives considered:** httpOnly cookie (requires backend cookie support and CSRF handling).

### Vite Proxy for Local Development

**Rationale:** Proxy `/api` to the API Gateway during development to avoid CORS issues.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| JWT in localStorage vulnerable to XSS | Keep token short-lived; move to httpOnly cookies in v2 |
| CORS in production | Configure API Gateway CORS origins explicitly |
| Large file upload UX | Show progress bar and handle timeout errors |

## Migration Plan

1. Run `npm install` in `ui/`.
2. Start Vite dev server with proxy to API Gateway.
3. Verify registration, login, upload, and transaction views end-to-end.

## Open Questions

- None at this time.
