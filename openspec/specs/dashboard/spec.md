# Dashboard Specification

## Purpose

Provides each authenticated user with an at-a-glance summary of their uploaded documents and the transactions extracted from those documents.

## Requirements

### Requirement: User can view an aggregate dashboard
The system SHALL provide an authenticated dashboard page and aggregate dashboard data for the current user. The summary SHALL include total uploaded documents, processed documents, documents in progress, saved transactions, unique categories, and unique counterparties. Counts SHALL cover all available history and SHALL NOT be derived only from a paginated result.

#### Scenario: View dashboard summary
- **WHEN** an authenticated user opens the dashboard
- **THEN** the dashboard displays the six aggregate counts for that user's data

#### Scenario: Open dashboard without data
- **WHEN** an authenticated user has no documents or transactions
- **THEN** the dashboard displays zero for each count

### Requirement: Dashboard document counts reflect lifecycle status
The system SHALL count all of the current user's documents as uploaded, count documents with status `PROCESSED` as processed, and count documents with status `PENDING` or `PROCESSING` as in progress. A document SHALL contribute to the uploaded total regardless of its current status.

#### Scenario: Count documents across lifecycle states
- **WHEN** the user's documents include uploaded, processing, processed, and failed documents
- **THEN** the dashboard's uploaded count includes all of them, the processed count includes only `PROCESSED`, and the in-progress count includes only `PENDING` and `PROCESSING`

### Requirement: Dashboard transaction counts reflect processed transactions
The system SHALL count saved transaction records belonging to the current user and linked to processed documents. Unique category and counterparty counts SHALL count distinct non-empty values among those transactions; null and blank values SHALL NOT count as categories or counterparties.

#### Scenario: Count saved transactions and distinct values
- **WHEN** the user's processed transactions include repeated, blank, and non-empty category and counterparty values
- **THEN** the saved-transaction count includes each transaction record and the category and counterparty counts include each distinct non-empty value once

### Requirement: Dashboard data is private to its owner
The system SHALL return dashboard metrics only for the authenticated user and SHALL reject requests that have no valid authentication without disclosing dashboard data.

#### Scenario: Request own dashboard data
- **WHEN** an authenticated user requests dashboard data
- **THEN** the response contains aggregate data only for that user

#### Scenario: Request dashboard data while unauthenticated
- **WHEN** a request for dashboard data has no valid authentication
- **THEN** the system rejects the request without disclosing aggregate data

### Requirement: Dashboard communicates loading and retrieval errors
The dashboard SHALL communicate while aggregate data is loading and SHALL show a recoverable error state if the data cannot be retrieved.

#### Scenario: Load dashboard data
- **WHEN** the dashboard requests its aggregate data
- **THEN** the page communicates that the summary is loading until the response is received

#### Scenario: Dashboard data request fails
- **WHEN** the dashboard aggregate-data request fails
- **THEN** the page displays an error state instead of presenting the failure as valid zero counts

### Requirement: Dashboard metrics update automatically
The dashboard SHALL periodically refresh its aggregate data while the page is open and SHALL NOT require a manual refresh control for updated counts.

#### Scenario: Dashboard reflects document processing updates
- **WHEN** a document changes lifecycle status while the dashboard remains open
- **THEN** the dashboard refreshes its counts automatically and reflects the updated status count

## Related implementation and history

- Documentation: [subsystem map](../../../docs/README.md#subsystem-map), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`DashboardPage.tsx`](../../../ui/src/pages/DashboardPage.tsx), [`DashboardController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/dashboard/controller/DashboardController.java), [`DashboardService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/dashboard/service/DashboardService.java)
- History: [dashboard page](../../changes/archive/2026-10-10-add-dashboard-page/)
