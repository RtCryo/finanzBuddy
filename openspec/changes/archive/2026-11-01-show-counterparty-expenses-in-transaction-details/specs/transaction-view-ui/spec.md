# Transaction View UI Delta

## ADDED Requirements

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
