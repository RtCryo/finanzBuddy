# Spec Delta

## MODIFIED Requirements

### Requirement: Workspace navigation avoids a duplicate upload shortcut
The authenticated workspace navigation SHALL provide links to Dashboard, Documents, and Transaction history without a separate Upload a file item. The workspace root SHALL open the Dashboard. Users SHALL be able to start an upload from the Documents page.

#### Scenario: Navigate to document upload
- **WHEN** an authenticated user opens the Documents page
- **THEN** the page provides an Upload document action that opens the upload flow

#### Scenario: View workspace navigation
- **WHEN** an authenticated user views the workspace navigation
- **THEN** it contains Dashboard, Documents, and Transaction history links and no separate upload link

#### Scenario: Open the workspace root
- **WHEN** a user opens the workspace root while authenticated
- **THEN** the user is taken to the Dashboard

#### Scenario: Sign in without a requested destination
- **WHEN** a user signs in without first requesting another protected page
- **THEN** the user is taken to the Dashboard
