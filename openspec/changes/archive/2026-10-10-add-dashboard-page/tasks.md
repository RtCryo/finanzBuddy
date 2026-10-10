# Tasks

## 1. Dashboard Aggregate API

- [x] 1.1 Add an authenticated dashboard response and `GET /dashboard` endpoint resolving the current user from the security principal; verify authenticated responses include all six metrics and unauthenticated requests disclose no data.
- [x] 1.2 Implement owner-scoped document and processed-transaction aggregates using the existing repository boundaries; verify repository/service tests cover lifecycle counts, transaction totals, distinct non-empty categories and counterparties, and isolation between users.

## 2. Dashboard UI and Workspace Navigation

- [x] 2.1 Add the dashboard API client and page with all six metrics, loading and recoverable error states, and five-second polling; verify UI tests cover metric rendering, zero values, request failures, polling cleanup, and stale responses.
- [x] 2.2 Register the protected `/dashboard` route, send the workspace root to Dashboard, and add Dashboard to shared navigation; verify routing and shell tests cover authenticated navigation and preserve Documents and Transaction history links.
- [x] 2.3 Update safe post-login destination handling to accept valid protected workspace routes and fall back to Dashboard; verify login and return-path tests cover default login, direct document and transaction links, query/hash preservation, and rejection of external or unsafe paths.

## 3. Integration Verification

- [x] 3.1 Run the API Gateway and UI test suites and production builds; verify the new endpoint, dashboard page, polling behavior, and root navigation work together without regressions.
