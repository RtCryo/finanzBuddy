# Spec Delta

## MODIFIED Requirements

### Requirement: User can register from the UI
The system SHALL present a registration form with email and password that creates the family's homeBuddy account, shared with other homeBuddy services, and SHALL state this on the form.

#### Scenario: Successful registration
- **WHEN** a user enters a valid email and password and submits the form
- **THEN** the UI calls `POST /auth/register`, which is routed to the identity service, and shows a success message

#### Scenario: Registration error
- **WHEN** registration fails (e.g., duplicate email)
- **THEN** the UI displays the error returned by the identity service

### Requirement: Authentication screens follow the approved English-language design
The system SHALL present login and registration screens in English using the approved split-panel visual design, and SHALL state that the account is the homeBuddy account also used for calendarBuddy.

#### Scenario: User opens an authentication screen
- **WHEN** a user opens `/login` or `/register`
- **THEN** the UI presents the corresponding form and navigation link in English using the shared visual style and names the shared homeBuddy account
