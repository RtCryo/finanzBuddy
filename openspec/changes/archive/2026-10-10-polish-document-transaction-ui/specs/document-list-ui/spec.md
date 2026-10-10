# Spec Delta

## MODIFIED Requirements

### Requirement: User can refresh the list
The system SHALL automatically poll the document list for status updates and SHALL NOT provide a separate manual Refresh control on the documents list page.

#### Scenario: Status update
- **WHEN** a document transitions from `PROCESSING` to `PROCESSED` while the documents page is open
- **THEN** the UI reflects the new status through automatic polling without requiring a manual refresh control
