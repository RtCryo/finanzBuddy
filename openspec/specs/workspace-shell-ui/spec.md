# Workspace Shell UI Specification

## Purpose

Defines the shared authenticated workspace navigation and reassurance content so common actions stay discoverable without repeating the same controls or messages.

## Requirements

### Requirement: Workspace navigation avoids a duplicate upload shortcut
The authenticated workspace navigation SHALL provide links to Documents and Transaction history without a separate Upload a file item. Users SHALL be able to start an upload from the Documents page.

#### Scenario: Navigate to document upload
- **WHEN** an authenticated user opens the Documents page
- **THEN** the page provides an Upload document action that opens the upload flow

#### Scenario: View workspace navigation
- **WHEN** an authenticated user views the workspace navigation
- **THEN** it contains Documents and Transaction history links and no separate upload link

### Requirement: Privacy reassurance appears once in the workspace
The authenticated workspace SHALL display the financial-data privacy reassurance in one shared location, without repeating the same statement in another workspace region.

#### Scenario: View an authenticated workspace page
- **WHEN** an authenticated user opens a page using the shared workspace shell
- **THEN** the privacy reassurance is visible once
