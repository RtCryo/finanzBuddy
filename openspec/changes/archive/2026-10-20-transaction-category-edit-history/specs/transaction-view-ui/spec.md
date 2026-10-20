# Spec Delta

## MODIFIED Requirements

### Requirement: Transaction columns match normalized model
The system SHALL display booking date, value date, amount, currency, counterparty, description, and category for transactions on a processed document's detail page. Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column. Both lists SHALL display the latest version of each transaction, including its current category.

#### Scenario: CSV-derived transaction
- **WHEN** a CSV document has been parsed and a user opens its processed-document details
- **THEN** the transaction row shows both booking date and value date with the other normalized fields from the latest transaction version

### Requirement: Transaction history displays Value date without a separate Booking date column
Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column. Each row SHALL show the latest version of its transaction.

#### Scenario: View the transaction-history date column
- **WHEN** an authenticated user views transaction history
- **THEN** each transaction row displays Value date and the other history columns, without a separate Booking date column, using the latest version

### Requirement: Users can open transaction details from transaction lists
The system SHALL let users open a right-side panel with the selected transaction's available normalized fields from both the transaction-history list and a processed document's transaction list. The panel SHALL initially be a read-only inspection view showing booking date, value date, amount, currency, counterparty, description, and category, representing unavailable values consistently with the corresponding list. Category SHALL appear as a plain text value, not as a chip or active control. The user SHALL enter edit mode only by activating a generic "Edit transaction" action. In this change, only the category field SHALL become editable; other transaction details SHALL remain read-only. Editing SHALL apply to the latest version only.

The category control in edit mode SHALL offer the user's available categories and an in-control option to create a personal category. Choosing that option SHALL reveal a category-name input within the same editor. The user SHALL commit category selection or creation with one generic "Save changes" action, or discard unsaved changes with "Cancel"; the panel SHALL NOT expose category-specific save or create actions. Creating a personal category and assigning it SHALL be committed atomically with the transaction version.

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
- **THEN** the category field becomes editable, the remaining fields stay read-only, and generic "Save changes" and "Cancel" actions are shown

#### Scenario: Edit a transaction category
- **WHEN** the user selects a different existing category in edit mode and activates "Save changes"
- **THEN** the latest transaction version is updated with that category, a new full transaction version is recorded, and the panel reflects the saved category

#### Scenario: Create and assign a personal category
- **WHEN** the user chooses the create-category option in the category control, enters a name, and activates "Save changes"
- **THEN** the category is saved for that user, assigned to the transaction as a new version, and available in that user's future category selections

#### Scenario: Cancel transaction edits
- **WHEN** the user changes the category or enters a new category name and activates "Cancel"
- **THEN** no category or version is saved and the panel returns to read-only details

#### Scenario: Close transaction details and continue browsing
- **WHEN** the user closes the panel using its close control or the Escape key
- **THEN** the panel closes, focus returns to the transaction that opened it, and the current list state and scroll position are preserved

#### Scenario: Open transaction details using the keyboard
- **WHEN** the user navigates to a transaction in either list using the keyboard and activates it
- **THEN** the same transaction-details panel opens with an accessible name and keyboard-operable close behavior

## ADDED Requirements

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
