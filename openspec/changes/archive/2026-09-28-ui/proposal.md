# Proposal: User Interface

## Why

Users need a web interface to register, log in, upload financial documents, and view processing results. The UI is the only client-facing component and must integrate securely with the API Gateway.

## What Changes

- Set up a React 18 + Vite frontend in the `ui/` monorepo module.
- Implement authentication pages: registration and login.
- Store JWT access token securely (httpOnly cookie preferred; if not possible, memory + refresh strategy).
- Implement document upload page with file selection and document type tag.
- Implement documents list page showing status and processing results.
- Implement transactions view showing normalized transactions for a processed document.
- Apply the approved English-language fintech visual design across authentication, upload, document list, and document detail screens.
- Provide an icon-only document download action on detail screens, subject to an authenticated API Gateway download endpoint.
- Configure proxy/Vite dev server to route API calls to the API Gateway during local development.
- Provide Docker Compose service definition and development scripts.

## Capabilities

### New Capabilities

- `user-auth-ui`: Users can register and log in through the web interface.
- `document-upload-ui`: Users can upload a file and select its document type.
- `document-list-ui`: Users can view their uploaded documents and current status.
- `transaction-view-ui`: Users can view normalized transactions extracted from processed documents and access the original document from its detail screen.

### Modified Capabilities

- None.

## Impact

- New `ui/` module with React, Vite, Tailwind CSS, and React Router.
- Depends on API Gateway endpoints for auth, documents, and transactions.
- The detail-screen download action depends on an authenticated API Gateway endpoint for retrieving the user's original document; its contract is not currently defined.
- Adds Node.js tooling to the monorepo.
