# User Authentication UI Specification

## Purpose

Provides web-based registration and login screens that obtain and use a JWT access token from the API Gateway.

## Requirements

### Requirement: User can register from the UI
The system SHALL present a registration form with email and password.

#### Scenario: Successful registration
- **WHEN** a user enters a valid email and password and submits the form
- **THEN** the UI calls `POST /auth/register` and shows a success message

#### Scenario: Registration error
- **WHEN** registration fails (e.g., duplicate email)
- **THEN** the UI displays the error returned by the API Gateway

### Requirement: User can log in from the UI
The system SHALL present a login form and store the issued token for subsequent requests. After successful login, the UI SHALL navigate to the safe protected destination the user originally requested, when one exists; otherwise, it SHALL navigate to the Dashboard.

#### Scenario: Successful login
- **WHEN** a user enters correct credentials without a previously requested protected destination
- **THEN** the UI stores the token and navigates to the Dashboard

#### Scenario: Successful login after requesting a protected page
- **WHEN** a user enters correct credentials after being redirected from a protected page
- **THEN** the UI stores the token and returns to that page, preserving its path, query, and hash

#### Scenario: Invalid credentials
- **WHEN** a user enters incorrect credentials
- **THEN** the UI displays an authentication error without storing a token

### Requirement: Protected routes require authentication
The system SHALL redirect unauthenticated users to the login page when they access protected routes and SHALL retain a safe internal destination for navigation after successful login.

#### Scenario: Deep link without token
- **WHEN** an unauthenticated user visits a protected document detail page
- **THEN** the UI redirects to `/login` and retains the requested page as the post-login destination

#### Scenario: Direct link to another protected workspace page
- **WHEN** an unauthenticated user visits a protected workspace page such as `/transactions` with a query or hash
- **THEN** the UI redirects to `/login` and retains the internal path, query, and hash for post-login navigation

### Requirement: Authentication screens follow the approved English-language design
The system SHALL present login and registration screens in English using the approved split-panel visual design.

#### Scenario: User opens an authentication screen
- **WHEN** a user opens `/login` or `/register`
- **THEN** the UI presents the corresponding form and navigation link in English using the shared visual style

## Related implementation and history

- Documentation: [UI map](../../../docs/README.md#subsystem-map), [UI README](../../../ui/README.md), [UI design system](../../../ui/DESIGN_SYSTEM.md)
- Implementation: [`LoginPage.tsx`](../../../ui/src/pages/LoginPage.tsx), [`RegisterPage.tsx`](../../../ui/src/pages/RegisterPage.tsx), [`AuthLayout.tsx`](../../../ui/src/components/AuthLayout.tsx)
- History: [initial UI](../../changes/archive/2026-09-28-ui/), [dashboard and authentication](../../changes/archive/2026-10-10-add-dashboard-page/)
