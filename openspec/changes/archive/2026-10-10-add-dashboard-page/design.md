# Design

## Context

The authenticated UI currently routes `/` to `/documents`; its shared shell links to Documents and Transaction history. The documents response already includes per-user total, processed, and in-progress document counts, but transaction history is paginated. The API Gateway reads the parser-service-owned `transactions` table through a read-only JDBC repository and already scopes history by both document owner and transaction user. See `proposal.md` and the dashboard spec delta for the motivation and behavior contract.

## Goals / Non-Goals

**Goals:**
- Return a single complete set of owner-scoped aggregate metrics for the dashboard.
- Reuse the existing authentication, ownership, and transaction read-side boundaries.
- Keep dashboard metrics current while documents are processed asynchronously.

**Non-Goals:**
- Date-range filtering, charts, exports, or transaction/document lists on the dashboard.
- Changes to transaction parsing, document lifecycle, or the parser-service-owned transaction schema.

## Decisions

- **Add one authenticated `GET /dashboard` aggregate endpoint.** Resolve the current user from the authenticated principal and return total, processed, and in-progress document counts alongside saved-transaction, category, and counterparty counts. A dedicated aggregate response avoids making the UI combine paginated endpoints or changing their existing contracts. Alternatives considered: reusing `GET /documents` and `GET /transactions` would either return incomplete totals or require loading arbitrary pages in the browser.
- **Aggregate transaction metrics in the API Gateway read side.** Use the existing read-only transaction repository boundary to query transaction counts and distinct non-blank category and counterparty values, joining processed documents and constraining both transaction and document ownership to the authenticated user. Keep document lifecycle counts on the existing document repository. This respects the parser service's ownership of transaction writes and keeps aggregation out of the UI. Alternatives considered: adding JPA ownership of the transactions table would couple gateway startup/schema validation to a table created by another service; client-side aggregation would be incomplete and expose excess row data.
- **Add a dedicated authenticated `/dashboard` UI route and make it the root destination.** Add Dashboard to the shared workspace navigation while retaining Documents and Transaction history. This makes the overview discoverable and preserves existing destinations.
- **Use Dashboard as the post-login fallback while preserving safe deep links.** The protected-route redirect already stores the requested location, including its query and hash, but the current return-path validator accepts only `/documents` routes. Extend its allow-list to valid protected workspace routes (Dashboard, document pages, and transaction history), reject external or unsafe destinations, and fall back to `/dashboard` when no safe destination exists. This preserves the established direct-link behavior without allowing an arbitrary redirect. Alternatives considered: always navigating to Dashboard after login would discard a destination the user explicitly requested; accepting any return URL could redirect outside the application.
- **Poll aggregate data every five seconds while the dashboard is mounted.** Match the current documents page refresh cadence so document processing changes appear without manual refresh. Cancel scheduled work on unmount and ignore stale responses after a newer request or navigation.
- **No database migration or external dependency is needed.** The metrics can be derived from existing document and transaction fields; deploy the API and UI changes together. Rolling back the release restores the current root redirect and removes the dashboard endpoint and route without data migration.

## Risks / Trade-offs

- [Repeated aggregation adds read load] → Compute counts in SQL rather than transferring transaction rows, and use the existing five-second cadence; adjust cadence if real usage shows material database load.
- [The transactions table may not yet exist or may be temporarily unavailable when the API Gateway starts] → Keep the endpoint read-only, return a normal API error, show a recoverable dashboard error state, and let the next scheduled refresh retry.
- [Independent document and transaction aggregates can observe slightly different database moments] → Accept eventual consistency for overview counts; each displayed metric remains complete for its own query and user scope.
