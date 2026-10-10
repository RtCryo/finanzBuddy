# Design

## Context

See `proposal.md` for motivation and `specs/transaction-view-ui/spec.md` for the behavior contract. The UI is a React application with protected routes and a shared `AppShell`. It currently loads transactions only from `GET /documents/{id}/transactions`. In the API Gateway, `TransactionReadRepository` reads the parser-owned `transactions` table via JDBC; transaction rows have `user_id`, while document ownership is checked through the authenticated user. The document list already uses server-side pagination and filename filtering.

## Goals / Non-Goals

**Goals:**
- Reuse the current authenticated workspace and normalized transaction representation.
- Query and filter transactions across all processed documents belonging to the authenticated user.
- Keep query cost and response size bounded through server-side pagination.

**Non-Goals:**
- Changing transaction extraction, normalization, or per-document transaction behavior.
- Adding transaction editing, export, or additional filter dimensions beyond those in the spec.

## Decisions

1. **Add a user-scoped aggregate transaction query in the API Gateway.** Add a collection route such as `GET /transactions`, deriving the owner from the authenticated principal rather than accepting a user identifier. The query uses the transaction `user_id` and joins the owning document to include only documents with `PROCESSED` status. This reuses the existing normalized read model and avoids iterating document-by-document from the UI. A client-side aggregation over the document list was considered, but it would require many requests and complicate complete filtering and pagination.

2. **Apply filters and pagination on the server.** Accept optional text search, inclusive booking-date bounds, category, and page parameters. Match text case-insensitively against description or counterparty; combine supplied filters with AND semantics. Return a page-shaped response compatible with the UI's existing document-list conventions, ordered by booking date descending and then transaction ID descending (null booking dates last). Server filtering avoids transferring the complete transaction history to the browser. The category selector should use distinct categories available in the authenticated user's processed history, including a clear representation for uncategorized rows if supported by stored data.

3. **Add a protected `/transactions` UI route and workspace navigation item.** Build the page using the existing app shell and transaction fields, with filter controls, paged results, and loading/error/empty states. Use numbered offset pagination for the transaction table: keep page 1 and the last page visible, show a compact range around the current page with ellipses for omitted pages, and visually highlight the current page. Previous/next controls are icon-only with accessible names; keep them visible but disabled at their respective boundaries. Show the visible result range and total count beside the controls. This is more suitable than keyset or infinite-scroll navigation for a table users may need to scan or jump through. Debounce text search consistently with the document list and reset the current page whenever filters change. Keep `/documents/{id}` as the route for the existing per-document view.

4. **Preserve API ownership boundaries.** Resolve the authenticated principal to the internal user ID in the service layer and pass it as a required query parameter. Do not expose cross-user records through client-supplied owner or document identifiers. Continue protecting both page and API with the existing authentication configuration.

## Risks / Trade-offs

- [Filtering across a large transaction history can be expensive] → Keep result pages bounded, use parameterized JDBC queries, and review query plans/index support for `user_id`, booking date, and category when implementing.
- [A page-shaped response changes the simple list shape used by the document-specific route if shared carelessly] → Keep the existing per-document endpoint and response contract intact; introduce the page response only for the aggregate endpoint.
- [Some transactions have nullable booking dates or categories] → Keep unfiltered history inclusive of those rows, sort undated rows last, and make the category control clearly represent uncategorized records if available.

## Migration Plan

No existing data migration is required. Deploy the aggregate API and UI together; rollback consists of removing the new route/navigation entry and aggregate endpoint while retaining the existing per-document view.
