# Tasks: User Interface

## 1. Project Scaffolding

- [x] 1.1 Initialize `ui/` with Vite React TypeScript template and verify `npm install` and `npm run dev` work
- [x] 1.2 Add Tailwind CSS and verify styles compile
- [x] 1.3 Add React Router, Axios, and verify no dependency conflicts
- [x] 1.4 Configure Vite proxy to route `/api` to the API Gateway and verify the proxy works
- [x] 1.5 Implement the shared English-language application shell and visual styles using the SVG references in `openspec/changes/ui/design/`

## 2. Authentication Screens

- [x] 2.1 Create `RegisterPage` with email and password form and verify it calls `POST /api/auth/register`
- [x] 2.2 Create `LoginPage` with email and password form and verify it calls `POST /api/auth/login` and stores the JWT
- [x] 2.3 Implement protected route wrapper that redirects unauthenticated users to `/login` and verify behavior
- [x] 2.4 Add Axios request interceptor to attach JWT Authorization header and verify headers on API calls
- [x] 2.5 Add global error handling for 401 responses and verify users are redirected to login on token expiration

## 3. Document Upload

- [x] 3.1 Create `UploadPage` with file input and document type dropdown (`CSV`, `PDF`, `XML`) and verify form validation
- [x] 3.2 Implement multipart upload to `POST /api/documents` and verify successful uploads return document id
- [x] 3.3 Show upload progress and success/error messages and verify UX for both outcomes
- [x] 3.4 Restrict file input to supported extensions and verify the browser enforces it
- [x] 3.5 Support drag-and-drop as well as file-picker selection and verify both flows

## 4. Document List

- [x] 4.1 Create `DocumentsPage` that fetches `GET /api/documents` and verify documents render
- [x] 4.2 Display filename, type, status, and upload date with status-specific styling and verify visual distinction
- [x] 4.3 Implement manual refresh button and verify list updates
- [x] 4.4 Add auto-polling every 5 seconds for status updates and verify `PROCESSING` documents update to `PROCESSED`
- [x] 4.5 Make each document row navigate to its detail page and verify no separate action column is needed

## 5. Transaction View

- [x] 5.1 Create `DocumentDetailPage` routed by `id` and verify navigation from document list
- [x] 5.2 Fetch and display transactions from `GET /api/documents/{id}/transactions` and verify columns match normalized model; the API currently returns an empty stub list with a TODO to connect the transaction read model
- [x] 5.3 Show pending/processing placeholder when document is not yet processed and verify correct state
- [x] 5.4 Show error details when document status is `FAILED` and verify error message is visible
- [x] 5.5 Show the status text badge beside the filename and add an accessible icon-only download control aligned to the right, wired to the authenticated API Gateway stub (`501` until MinIO streaming is implemented)

## 6. Docker and Documentation

- [x] 6.1 Add `Dockerfile` for production build and verify the image builds
- [x] 6.2 Add `ui` service definition to root `docker-compose.yml` and verify the container serves the built app
- [x] 6.3 Document local development setup, available scripts, and environment variables in `ui/README.md` and verify the documented steps work
