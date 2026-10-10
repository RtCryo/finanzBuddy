# Spec Delta

## MODIFIED Requirements

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
