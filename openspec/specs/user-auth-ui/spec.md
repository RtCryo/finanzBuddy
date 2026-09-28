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
The system SHALL present a login form and store the issued token for subsequent requests.

#### Scenario: Successful login
- **WHEN** a user enters correct credentials
- **THEN** the UI stores the token and navigates to the documents page

#### Scenario: Invalid credentials
- **WHEN** a user enters incorrect credentials
- **THEN** the UI displays an authentication error without storing a token

### Requirement: Protected routes require authentication
The system SHALL redirect unauthenticated users to the login page when they access protected routes.

#### Scenario: Deep link without token
- **WHEN** an unauthenticated user visits `/documents`
- **THEN** the UI redirects to `/login`

### Requirement: Authentication screens follow the approved English-language design
The system SHALL present login and registration screens in English using the approved split-panel visual design.

#### Scenario: User opens an authentication screen
- **WHEN** a user opens `/login` or `/register`
- **THEN** the UI presents the corresponding form and navigation link in English using the shared visual style
