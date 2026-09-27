# Design: User Interface

## Context

The UI is a single-page React application that serves as the client for the API Gateway. It handles authentication, document upload, status tracking, and transaction viewing. See `proposal.md` for motivation and scope.

## Goals / Non-Goals

**Goals:**
- Provide a fast, minimal interface for registering and logging in.
- Allow users to upload financial documents and select their type.
- Show document processing status clearly.
- Display normalized transactions after successful parsing.
- Use the approved English-language visual design consistently across all pages.

**Non-Goals:**
- Mobile-native experience (responsive web is sufficient).
- Advanced dashboards, charts, or analytics.
- Offline support or PWA features.

## Visual Design

### Direction and assets

Use the calm fintech direction shown in the SVG mockups under [`design/`](design/README.md). The UI copy is English. The visual language uses a light gray application background (`#F6F8FB`), white surfaces, dark navy text (`#172B4D`), and a restrained green primary accent (`#167D68`). Amber communicates processing and red communicates failure; status meaning is always written as text as well as conveyed by color.

The mockups cover sign in, registration, upload, the document list, processed transactions, and the processing and failed detail states. They are the visual reference for implementation, while the requirements below remain authoritative for behavior.

### Shared application shell and interactions

- Authenticated pages use a left workspace navigation, a top breadcrumb/profile area, and a consistent content width and spacing. The profile appears only in the top-right header.
- Authentication pages use a split-panel layout with the form and product introduction; authenticated page copy and authentication copy are in English.
- The documents table has no separate action column. Clicking a document row opens that document's detail route.
- Document statuses are text badges with status-specific color, not icon-only indicators. On detail pages, the text badge sits beside the filename.
- Document detail pages expose an icon-only download button aligned to the far right of the filename header. The button has an accessible name/tooltip and downloads the original document.
- Upload supports choosing a file through a drop zone or the native file picker, followed by document type selection and upload progress/result feedback.

### Screen references

| Screen | Reference |
|--------|-----------|
| Documents list | `design/design-option-1.svg` |
| Sign in | `design/login-page.svg` |
| Registration | `design/register-page.svg` |
| Upload | `design/upload-page.svg` |
| Processed document and transactions | `design/transactions-page.svg` |
| Processing state | `design/document-processing-state.svg` |
| Failed state | `design/document-failed-state.svg` |

## Decisions

### React 18 + Vite

**Rationale:** Vite offers fast HMR and simple configuration. React is the most common choice for SPAs and integrates well with the team’s Java backend via REST.

**Alternatives considered:** Next.js (SSR not needed), Vue (less ecosystem alignment if team knows React).

### Tailwind CSS

**Rationale:** Utility-first CSS speeds up UI prototyping without needing a custom design system.

**Alternatives considered:** Material UI (heavier), plain CSS (slower to iterate).

### React Router for Navigation

**Rationale:** Simple client-side routing for `/login`, `/register`, `/documents`, `/documents/upload`, and `/documents/:id`.

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
| Original-file download is not exposed by the current API Gateway contract | Define an authenticated download endpoint and response contract before wiring the detail-page download button |

## Migration Plan

1. Run `npm install` in `ui/`.
2. Start Vite dev server with proxy to API Gateway.
3. Verify registration, login, upload, and transaction views end-to-end.

## Open Questions

- None at this time.
