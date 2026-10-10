# Design

## Context

See proposal.md for motivation and specs/ for the user-visible contracts. The Documents page currently polls every five seconds but also exposes a manual refresh action. Individual document details expose a manual refresh action and poll only while processing. The shared shell shows the privacy statement in both the sidebar and footer and links to upload from the sidebar despite also offering upload from Documents. Transaction history uses a paginated API whose database query currently hard-codes booking-date ordering and booking-date filtering; sorting only the visible client page would therefore produce incomplete and misleading results.

## Goals / Non-Goals

**Goals:**
- Keep transaction sorting and date filtering correct across the full paginated result set.
- Make the date shown in transaction history and the date used by its range filter consistent.
- Keep transaction headers and amount-sign styling usable with keyboard and assistive technology.
- Simplify shared navigation and show the privacy reassurance in one location across responsive layouts.

**Non-Goals:**
- Change the normalized transaction model or remove either date from stored/API transaction records.
- Change the transaction columns on an individual document's detail page.
- Remove automatic status polling while document processing is active.
- Change upload validation, upload APIs, transaction pagination size, or document processing cadence.

## Decisions

### Apply transaction sorting in the paginated API

Add optional sort-field and direction parameters to transaction-history requests, with a default of Value date descending. Accept only the supported displayed fields (value date, amount, currency, counterparty, description, category), map these values through a fixed server-side allowlist, and reject unsupported sort values. Build the SQL ordering from the allowlisted mapping, then apply limit and offset. Keep null values last and use transaction ID as a deterministic tie-breaker. This keeps results globally sorted and stable across pages; sorting only the loaded page was rejected because it cannot produce a correct ordered history.

### Align the history date range with Value date

Change the history query's inclusive bounds from `booking_date` to `value_date`; leave the booking/value date fields in the response intact. The document-detail table continues to show both dates. This follows the user's selection and avoids a filter whose meaning differs from the single date shown in history.

### Keep sort state explicit and accessible

Make each sortable history header keyboard-operable and expose its active direction using table sort semantics (`aria-sort`) alongside a visible direction indicator. Selecting a different column starts in ascending order; selecting it again reverses direction. Reset to the first page whenever sort field or direction changes. Use the server's default Value date descending order before the user chooses another column.

### Use responsive layout and semantic amount polarity

Let filter controls distribute across the card width at wide viewports and wrap at narrower breakpoints. Represent positive, negative, and neutral amount styling with distinct semantic classes, using muted green/red values compatible with the existing teal/green palette; keep sign and numeric text available so meaning is not color-only.

### Simplify the shared shell

Remove the standalone Upload a file workspace navigation entry while retaining the Documents page upload actions. Keep the privacy reassurance in the workspace footer and remove the duplicate sidebar copy; the footer remains the single shared location at desktop and mobile sizes.

### Remove manual document refresh actions

Remove the manual refresh controls from both the documents list and individual document details. Preserve the list's five-second polling and detail polling while status is `PENDING` or `PROCESSING`; opening or reopening a detail route triggers its normal initial document and transaction requests. This removes duplicate controls without adding a new refresh interaction or background polling for completed documents.

## Risks / Trade-offs

- [A sort parameter can be used to construct unsafe SQL if values are interpolated unchecked] → Map accepted values to fixed SQL expressions and validate direction before building ordering; never interpolate raw request values.
- [Changing the history date range can change which records match for users with different booking and value dates] → Label the range filter accessibly as Value date and cover inclusive bounds with API/service tests.
- [A completed document no longer has an in-page manual refresh action] → Reopening its detail route performs the current fetch; retain the automatic poll while status is pending or processing.
- [Some transactions may not have a value date] → Keep undated rows available when no date range is set, place nulls last in sorting, and exclude them when a value-date bound is active.
- [Removing sidebar reassurance leaves it in the shared footer] → Keep the footer present in responsive layouts and verify that exactly one reassurance is rendered on authenticated pages.

## Migration Plan

No data migration is needed. Deploy the API changes and UI together so the history client and server agree on the new sort parameters and Value date filter semantics. Rollback is a code rollback: restore the previous UI/API pairing and booking-date history filter; stored transaction records require no changes.
