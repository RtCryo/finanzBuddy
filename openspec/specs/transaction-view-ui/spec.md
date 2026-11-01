# Transaction View UI Specification

## Purpose

Shows normalized transactions extracted from a processed document.

## Requirements

### Requirement: Transactions are shown for processed documents
The system SHALL display transactions only when the document status is `PROCESSED`.

#### Scenario: View transactions
- **WHEN** a user opens a processed document
- **THEN** the UI fetches transactions from `GET /documents/{id}/transactions` and displays them

### Requirement: Transaction columns match normalized model
The system SHALL display booking date, value date, amount, currency, counterparty, description, and category for transactions on a processed document's detail page. Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column. Both lists SHALL display the latest version of each transaction, including its current category.

#### Scenario: CSV-derived transaction
- **WHEN** a CSV document has been parsed and a user opens its processed-document details
- **THEN** the transaction row shows both booking date and value date with the other normalized fields from the latest transaction version

### Requirement: Non-processed documents hide transactions
The system SHALL not attempt to load transactions for documents that are not `PROCESSED`.

#### Scenario: Pending or processing document
- **WHEN** a user opens a document with status `PENDING` or `PROCESSING`
- **THEN** the UI shows the processing state and does not load transactions

#### Scenario: Failed document
- **WHEN** a user opens a document with status `FAILED`
- **THEN** the UI shows the failure details and does not load transactions

### Requirement: Document details show status and download control
The system SHALL show the status text badge beside the document filename and an icon-only, right-aligned control for downloading the original document.

#### Scenario: Download original document
- **WHEN** an authenticated owner activates the download control
- **THEN** the UI downloads the original document through an authenticated API Gateway endpoint

### Requirement: Users can browse transactions across processed documents
The system SHALL provide an authenticated transaction history page listing normalized transactions from all of the current user's processed documents. Each transaction SHALL retain its normalized booking date, value date, amount, currency, counterparty, description, and category fields.

#### Scenario: Open transaction history
- **WHEN** an authenticated user opens the transaction history page
- **THEN** the system displays that user's transactions from processed documents across their uploaded statements

#### Scenario: No transactions are available
- **WHEN** an authenticated user has no transactions from processed documents
- **THEN** the page displays an empty state indicating that no transactions are available

### Requirement: Transaction history can be filtered
The system SHALL allow the user to filter transaction history by a text query matching description or counterparty, an inclusive value-date range, category, and multiple personal tags with an explicit AND/all or OR/any mode. Filters SHALL be combinable, and changing filters SHALL update the displayed results.

#### Scenario: Search transactions
- **WHEN** the user enters a text query
- **THEN** the displayed transactions include only records whose description or counterparty matches the query without case sensitivity

#### Scenario: Filter by value-date range
- **WHEN** the user sets a start date, end date, or both
- **THEN** the displayed transactions include only records with a value date within the selected inclusive bounds

#### Scenario: Filter by category
- **WHEN** the user selects a category
- **THEN** the displayed transactions include only records assigned to that category

#### Scenario: Combine filters
- **WHEN** the user applies more than one supported filter
- **THEN** the displayed transactions satisfy all active filters

#### Scenario: Clear filters
- **WHEN** the user clears the active filters
- **THEN** the page displays the unfiltered transaction history again

#### Scenario: Filter by multiple tags
- **WHEN** the user selects several personal tags and chooses all or any matching
- **THEN** results satisfy the selected tag condition combined with other filters, reset to page one, and keep tags out of table rows

#### Scenario: Clear tag filters
- **WHEN** the user activates Clear filters
- **THEN** all tag selections and other filters are cleared and tag matching returns to AND

### Requirement: Transaction history is private to its owner
The system SHALL return transaction history only for the authenticated user, regardless of any client-supplied identifiers or filter values.

#### Scenario: Request transaction history while authenticated
- **WHEN** an authenticated user requests transaction history
- **THEN** the response contains transactions belonging only to that user

#### Scenario: Request transaction history while unauthenticated
- **WHEN** a request for transaction history has no valid authentication
- **THEN** the system rejects the request without disclosing transaction data

### Requirement: Transaction history pagination supports direct page navigation
The system SHALL present transaction results with numbered pagination. The current page SHALL be visually distinct, and the first and last page numbers SHALL remain directly selectable. When the page count cannot be shown compactly, the system SHALL use ellipses while retaining the first page, last page, and a small range around the current page. Previous and next controls SHALL be icon-only, remain visible when unavailable, and expose accessible names.

#### Scenario: Jump to a numbered page
- **WHEN** the user selects an available page number
- **THEN** the page displays that page of transaction results and marks its number as current

#### Scenario: Jump directly to the first or last page
- **WHEN** the page count is greater than one
- **THEN** the user can select page 1 or the last page number directly, including when intermediate numbers are collapsed with ellipses

#### Scenario: Navigate at a page boundary
- **WHEN** the user is on the first or last page
- **THEN** the unavailable previous or next arrow remains visible and is disabled

#### Scenario: Condense a long page list
- **WHEN** the transaction history has many pages
- **THEN** the paginator shows the first and last page numbers, a compact range around the current page, and ellipses for omitted page numbers

### Requirement: Document details update without a manual refresh control
The document detail page SHALL NOT provide a manual Refresh control. It SHALL automatically poll while a document is `PENDING` or `PROCESSING`, and opening or reopening a document detail page SHALL fetch the current document and, when processed, its transactions.

#### Scenario: Processing status updates automatically
- **WHEN** a user views a document whose status is `PENDING` or `PROCESSING`
- **THEN** the detail page updates its status through automatic polling without a manual refresh control

#### Scenario: Reopen document details
- **WHEN** a user opens or reopens a document detail page
- **THEN** the page fetches the latest document state and loads its transactions when the document is processed

### Requirement: Transaction history displays Value date without a separate Booking date column
Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column. Each row SHALL show the latest version of its transaction.

#### Scenario: View the transaction-history date column
- **WHEN** an authenticated user views transaction history
- **THEN** each transaction row displays Value date and the other history columns, without a separate Booking date column, using the latest version

### Requirement: Transaction history can be sorted by each displayed column
The system SHALL allow users to sort transaction history by Value date, amount, currency, counterparty, description, and category. Sorting SHALL apply to the complete filtered result set before pagination, and the active sort column and direction SHALL be conveyed accessibly.

#### Scenario: Sort by a displayed column
- **WHEN** the user activates a sortable transaction-history column
- **THEN** the displayed results are ordered by that column across all matching pages and the active direction is indicated

#### Scenario: Reverse a column's sort direction
- **WHEN** the user activates the currently sorted column again
- **THEN** the results use the opposite sort direction

#### Scenario: Keep pagination consistent with sorting
- **WHEN** the user changes the sort while viewing any page
- **THEN** the results return to the first page of the newly sorted filtered result set

### Requirement: Transaction-history filters use the available card width
The transaction-history filter panel SHALL use the full available width of its card, distributing its controls across that width on wide layouts while keeping them usable on narrow layouts.

#### Scenario: View filters on a wide screen
- **WHEN** a user views transaction history on a wide screen
- **THEN** the filter controls span the available card width instead of clustering at the left

#### Scenario: View filters on a narrow screen
- **WHEN** a user views transaction history on a narrow screen
- **THEN** the filter controls wrap within the card without horizontal page overflow

### Requirement: Transaction amounts communicate their sign accessibly
The transaction-history table SHALL style positive amounts with a soft green from the application palette and negative amounts with a soft red compatible with that palette. Zero, missing, or invalid amounts SHALL retain neutral styling, and the displayed amount value SHALL remain available as text.

#### Scenario: Display a credit
- **WHEN** a transaction has a positive amount
- **THEN** its amount is shown with soft green styling

#### Scenario: Display a debit
- **WHEN** a transaction has a negative amount
- **THEN** its amount is shown with soft red styling

#### Scenario: Display a neutral amount
- **WHEN** a transaction amount is zero or unavailable
- **THEN** its amount uses neutral styling

### Requirement: Users can open transaction details from transaction lists
The system SHALL let users open a right-side panel with the selected transaction's available normalized fields from both the transaction-history list and a processed document's transaction list. The panel SHALL initially be a read-only inspection view showing booking date, value date, amount, currency, counterparty, description, and category, representing unavailable values consistently with the corresponding list. Category SHALL appear as a plain text value, not as a chip or active control. The user SHALL enter edit mode only by activating a generic "Edit transaction" action. The category and current tag set SHALL become editable; other transaction details SHALL remain read-only. Current tags SHALL appear as chips, with an empty chip area when none are assigned. Tag additions, inline creation, and removal SHALL be available only in edit mode and committed through the same Save changes action. Editing SHALL apply to the latest version only.

The category control in edit mode SHALL offer the user's available categories and an in-control option to create a personal category. Choosing that option SHALL reveal a category-name input within the same editor. The user SHALL commit category selection or creation and tag edits with one generic "Save changes" action, or discard unsaved changes with "Cancel"; the panel SHALL NOT expose category-specific save or create actions. Creating a personal category and assigning it SHALL be committed atomically with the transaction version and any tag edits. Tag-only changes SHALL NOT create versions. Cancel or closing the editor SHALL discard all unsaved category and tag drafts.

When a transaction has one version, the panel SHALL NOT display a version selector or comparison section. When it has multiple versions, the panel SHALL provide a compact chronological version selector, default to the latest version, and display the selected snapshot in the details view. For every selected version after the original, the panel SHALL show changed fields and their old/new values compared with the immediately older version. The original version SHALL be identified as imported and SHALL have no comparison section. Historical versions SHALL be read-only, and edit mode SHALL be available only for the latest version. Opening and closing the panel SHALL preserve the current list, filters, sorting, pagination, and scroll position.

#### Scenario: Open transaction details from history
- **WHEN** the user activates a transaction in transaction history
- **THEN** a right-side panel opens in read-only mode for the latest version and displays its booking date, value date, amount, currency, counterparty, description, and plain-text category

#### Scenario: Open transaction details from a processed document
- **WHEN** the user activates a transaction in a processed document's transaction list
- **THEN** a right-side panel opens in read-only mode for the latest version and displays its booking date, value date, amount, currency, counterparty, description, and plain-text category

#### Scenario: Hide version controls for an unedited transaction
- **WHEN** the user opens details for a transaction with exactly one version
- **THEN** the transaction details are shown without a version selector or comparison section

#### Scenario: Browse and compare multiple transaction versions
- **WHEN** the user opens details for a transaction with multiple versions
- **THEN** a compact chronological selector is shown with the latest version selected
- **AND** the selected snapshot is displayed read-only with changed fields compared to its immediately older version
- **AND** selecting the original version identifies it as imported and omits the comparison section
- **AND** selecting an earlier version does not offer the edit action

#### Scenario: Enter transaction edit mode
- **WHEN** the user activates "Edit transaction" while viewing the latest version
- **THEN** the category and tag set become editable, the remaining fields stay read-only, and generic "Save changes" and "Cancel" actions are shown

#### Scenario: Edit a transaction category
- **WHEN** the user selects a different existing category in edit mode and activates "Save changes"
- **THEN** the latest transaction version is updated with that category, a new full transaction version is recorded, and the panel reflects the saved category

#### Scenario: Create and assign a personal category
- **WHEN** the user chooses the create-category option in the category control, enters a name, and activates "Save changes"
- **THEN** the category is saved for that user, assigned to the transaction as a new version, and available in that user's future category selections

#### Scenario: Cancel transaction edits
- **WHEN** the user changes the category or enters a new category name and activates "Cancel"
- **THEN** no category, tag, assignment, or version is saved and the panel returns to read-only details

#### Scenario: Close transaction details and continue browsing
- **WHEN** the user closes the panel using its close control or the Escape key
- **THEN** the panel closes, focus returns to the transaction that opened it, and the current list state and scroll position are preserved

#### Scenario: Open transaction details using the keyboard
- **WHEN** the user navigates to a transaction in either list using the keyboard and activates it
- **THEN** the same transaction-details panel opens with an accessible name and keyboard-operable close behavior

#### Scenario: Add and remove tags in edit mode
- **WHEN** the owner enters Edit transaction, selects existing tags or creates a new tag, removes a selected tag, and saves
- **THEN** the saved current tag set is shown as chips and is available when details are reopened from either transaction list

#### Scenario: Save tags for an uncategorized transaction
- **WHEN** the owner changes only tags on an uncategorized transaction and saves
- **THEN** tags persist without requiring a category or creating a version

#### Scenario: Failed editor save
- **WHEN** saving category or tag edits fails
- **THEN** no partial edit is committed, drafts remain available, and an accessible error allows retry

#### Scenario: Show current tags alongside an older snapshot
- **WHEN** the owner selects a historical version
- **THEN** current tags are shown separately as current metadata, remain read-only, and are excluded from snapshot comparisons

#### Scenario: Return from details after a tag-filtered row disappears
- **WHEN** saved tag edits make the open transaction cease matching the active filters and the owner closes details
- **THEN** results and counts refresh with existing filters and sorting preserved, pagination is clamped if necessary, and focus returns to a stable list control when the opener is unavailable

### Requirement: Transaction version history preserves the original and every category edit
The system SHALL preserve an initial full snapshot of each transaction and create a new full snapshot after each successful category change. A snapshot SHALL include the transaction's booking date, value date, amount, currency, counterparty, description, and category. Historical snapshots SHALL remain unchanged and read-only, and the current transaction SHALL be the newest version.

#### Scenario: View the initial transaction version
- **WHEN** the version data for a transaction that has never been edited is requested
- **THEN** the original transaction data is returned as its single initial version and its details panel omits the version selector and comparison section

#### Scenario: Preserve a prior version after a category edit
- **WHEN** a user changes a transaction's category
- **THEN** the previous version remains available with all of its prior field values, and the new full snapshot becomes the latest version

#### Scenario: Keep history unchanged when saving fails
- **WHEN** a category change cannot be saved
- **THEN** no new version is created and the current category and version history remain unchanged

### Requirement: Transaction lists and filters use the latest version
Transaction history and a processed document's transaction list SHALL show only the latest version of each transaction. Transaction-history filtering and sorting by category SHALL use that latest version and SHALL NOT show historical snapshots as separate transactions.

#### Scenario: See an edited category in transaction history
- **WHEN** a user changes a transaction's category and views transaction history
- **THEN** that transaction appears once with its latest category

#### Scenario: See an edited category on the source document
- **WHEN** a user changes a transaction's category and opens the processed document containing it
- **THEN** that transaction appears once with its latest category

#### Scenario: Filter after a category change
- **WHEN** a user filters transaction history by the transaction's new category
- **THEN** the transaction is included under the new category and is not included under its former category

### Requirement: Users have a personal reusable transaction category list
The system SHALL offer each user categories assigned to any of their transaction versions and any personal categories they have created. A category created by one user SHALL NOT become available to another user.

#### Scenario: List available categories while editing
- **WHEN** an authenticated user enters transaction edit mode
- **THEN** the choices include categories present in that user's current or historical transaction versions and categories previously created by that user

#### Scenario: Keep a created category private
- **WHEN** one user creates a personal category
- **THEN** it is available to that user and is not exposed in another user's category list

### Requirement: Transaction category editing and version history are owner-scoped
The system SHALL authorize category changes, personal-category operations, and version-history reads against the authenticated transaction owner. Requests without valid authentication SHALL be rejected without disclosing transaction or category data.

#### Scenario: Access an owned transaction
- **WHEN** an authenticated user edits or reads versions of their own transaction
- **THEN** the requested operation is allowed

#### Scenario: Access another user's transaction
- **WHEN** an authenticated user attempts to edit or read versions of a transaction owned by another user
- **THEN** the system rejects the operation without disclosing or changing the other user's transaction data

### Requirement: Details show other expenses with the same counterparty
The transaction details panel SHALL show a navigable section named Other expenses with this counterparty from both history and processed-document lists. It SHALL include other transactions with a negative latest amount and exactly the same nonblank latest Counterparty as the source transaction, across all of the owner's processed documents. Matching SHALL be case-sensitive and preserve whitespace, with no substring or fuzzy matching. The source transaction SHALL be excluded by ID. Each candidate SHALL appear once using its latest version. Originating list filters and pagination and selection of historical snapshots SHALL NOT change the related set.

#### Scenario: Compare across documents
- **WHEN** a user opens transaction details from either list
- **THEN** matching expenses across their processed documents appear, excluding the selected transaction, credits, zero or missing amounts, and historical duplicates

#### Scenario: Exact matching
- **WHEN** candidate counterparties differ by case, whitespace, or only match a substring
- **THEN** those candidates are excluded

#### Scenario: Independent list and version state
- **WHEN** the originating list has filters or the user selects an older snapshot
- **THEN** results still use the source transaction's latest Counterparty across all eligible documents

### Requirement: Related expenses show compact date and amount rows in descending date order
Each related expense SHALL display Value date and signed amount with currency using established formatting. Results SHALL sort by Value date descending, missing dates last, and transaction ID ascending for ties. A bounded initial page and keyboard-accessible Load more action SHALL make all matching expenses reachable. Activating a row by pointer or keyboard SHALL open the selected transaction at its latest version inside the same panel, reload its related expenses, and preserve the originating list state and opener. Navigation SHALL show loading and recoverable errors and ignore stale responses after closing or switching. While category edits are unsaved, row navigation SHALL be disabled with an explanation. Content SHALL scroll inside the details panel without page-level horizontal overflow.

#### Scenario: Read related expenses
- **WHEN** related expenses are available
- **THEN** compact rows show Value date and amount with currency in the specified order, representing unavailable dates consistently with existing details

#### Scenario: Load additional expenses
- **WHEN** the user activates Load more
- **THEN** the next page is appended in the same ordering without changing the originating list or editing state

### Requirement: Related expenses have independent loading and recovery states
The section SHALL display independent loading, empty, missing-counterparty, and recoverable error states. A null, empty, or whitespace-only Counterparty SHALL produce an explanation rather than grouping unnamed transactions. Failed requests SHALL offer retry, preserve previously loaded rows, and SHALL NOT disable details, version selection, or category editing. Responses for closed or previously selected transactions SHALL NOT populate another panel.

#### Scenario: Empty results
- **WHEN** a successful request has no matches
- **THEN** an explicit empty state appears

#### Scenario: Missing counterparty
- **WHEN** the latest source Counterparty is blank or missing
- **THEN** the section explains why related expenses are unavailable without grouping unnamed transactions

#### Scenario: Recover from failure
- **WHEN** an initial or additional-page request fails
- **THEN** an inline error and retry action appear while loaded rows and other panel functions remain available

#### Scenario: Ignore stale responses
- **WHEN** a request finishes after closing or switching transactions
- **THEN** its results do not update another transaction's panel

### Requirement: Related expense reads authorize source and results
The related-expense API SHALL require authentication and verify that the source transaction and its processed source document belong to the authenticated owner. Missing, inaccessible, or non-processed source transactions SHALL return 404 without disclosing financial data. Candidate transaction and document ownership SHALL both match the authenticated user, and candidate documents SHALL be processed.

#### Scenario: Reject foreign sources
- **WHEN** an authenticated user requests related expenses for another owner's transaction
- **THEN** the response is 404 without source or related data

#### Scenario: Exclude foreign and unprocessed candidates
- **WHEN** matching expenses belong to another owner or a non-processed document
- **THEN** those expenses are excluded

#### Scenario: Require authentication
- **WHEN** a related-expense request has no valid authentication
- **THEN** it is rejected without financial data

#### Scenario: Open a related transaction
- **WHEN** a user activates a related expense
- **THEN** the same panel shows its latest details and related expenses without changing the parent list
- **AND** closing restores focus to the original list opener

## Related implementation and history

- Documentation: [UI and transaction map](../../../docs/README.md#subsystem-map), [UI README](../../../ui/README.md), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`TransactionHistoryPage.tsx`](../../../ui/src/pages/TransactionHistoryPage.tsx), [`HistoryTagFilter.tsx`](../../../ui/src/components/HistoryTagFilter.tsx), [`TransactionDetailsDialog.tsx`](../../../ui/src/components/TransactionDetailsDialog.tsx), [`TagSelection.tsx`](../../../ui/src/components/TagSelection.tsx), [`TransactionController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/controller/TransactionController.java), [`TransactionReadRepository.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/repository/TransactionReadRepository.java), [`TransactionCategoryService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/TransactionCategoryService.java)
- History: [initial UI](../../changes/archive/2026-09-28-ui/), [transaction history](../../changes/archive/2026-10-10-transaction-history-page/), [transaction details panel](../../changes/archive/2026-10-20-transaction-details-popup/), [category edit history](../../changes/archive/2026-10-20-transaction-category-edit-history/), [transaction tags](../../changes/archive/2026-11-01-add-transaction-tags/), [counterparty expenses](../../changes/archive/2026-11-01-show-counterparty-expenses-in-transaction-details/)

## Implementation Notes

- The API Gateway transaction routes are authenticated and owner-scoped. Transaction history is served from the parser-owned transaction table through `TransactionReadRepository`, including filters, sorting, pagination, and the latest saved transaction version.
- `GET /transactions/{id}/related-expenses` uses latest snapshots to find exact-counterparty negative expenses across owned processed documents. The shared details panel loads independent pages of compact date/amount/currency rows with retry and in-panel navigation to the latest selected transaction; historical selection does not change the source. See the [gateway endpoint contract](../../../api-gateway/README.md#related-transaction-expenses).
- The authenticated download route verifies document ownership, reads the original object from S3-compatible storage, and returns it as an attachment with the stored filename.
- `DocumentResponse.errorMessage` is populated only for documents in the `FAILED` state so the detail screen can show parser failure details.
