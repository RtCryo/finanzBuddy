# Spec Delta

## MODIFIED Requirements

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
