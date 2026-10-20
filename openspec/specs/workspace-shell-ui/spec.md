# Workspace Shell UI Specification

## Purpose

Defines the shared authenticated workspace navigation and reassurance content so common actions stay discoverable without repeating the same controls or messages.

## Requirements

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

### Requirement: Privacy reassurance appears once in the workspace
The authenticated workspace SHALL display the financial-data privacy reassurance in one shared location, without repeating the same statement in another workspace region.

#### Scenario: View an authenticated workspace page
- **WHEN** an authenticated user opens a page using the shared workspace shell
- **THEN** the privacy reassurance is visible once

## Related implementation and history

- Documentation: [UI map](../../../docs/README.md#subsystem-map), [UI README](../../../ui/README.md), [UI design system](../../../ui/DESIGN_SYSTEM.md)
- Implementation: [`AppShell.tsx`](../../../ui/src/components/AppShell.tsx), [`DashboardPage.tsx`](../../../ui/src/pages/DashboardPage.tsx)
- History: [dashboard and workspace](../../changes/archive/2026-10-10-add-dashboard-page/), [document and transaction UI polish](../../changes/archive/2026-10-10-polish-document-transaction-ui/)
