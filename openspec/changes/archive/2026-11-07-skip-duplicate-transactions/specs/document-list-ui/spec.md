# Spec Delta

## ADDED Requirements

### Requirement: Deduplication outcome is shown for processed documents
The system SHALL show the number of saved transactions and the number skipped as duplicates for a `PROCESSED` document in the documents list and on the document detail page, in text that does not rely on color alone. Documents without these numbers SHALL show neither number. When a processed document has no saved transactions because every line was a duplicate, the detail page SHALL say so and SHALL NOT show the generic "no transactions available yet" message.

#### Scenario: Partial duplicates in the list
- **WHEN** a processed document has 28 saved and 300 skipped
- **THEN** the documents list shows "28 saved, 300 duplicates skipped" for that row

#### Scenario: All duplicates on the detail page
- **WHEN** a user opens a processed document whose lines were all skipped
- **THEN** the detail page explains that all transactions were already imported and shows the skipped count

#### Scenario: Historic document
- **WHEN** a processed document has no saved and skipped numbers
- **THEN** the list and detail page show no deduplication text for it
