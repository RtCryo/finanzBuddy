# User Authentication Specification

## Purpose

Provides stateless JWT-based registration and authentication so that only identified users can access document endpoints.

## Requirements

### Requirement: User can register
The system SHALL allow a new user to register with a unique email address and a password.

#### Scenario: Successful registration
- **WHEN** a user submits a valid email and password to `POST /auth/register`
- **THEN** the system creates the user and returns a success response

#### Scenario: Duplicate email
- **WHEN** a user registers with an email that already exists
- **THEN** the system returns a 409 Conflict error

### Requirement: User can log in
The system SHALL authenticate a registered user and issue a signed JWT access token.

#### Scenario: Successful login
- **WHEN** a registered user submits correct credentials to `POST /auth/login`
- **THEN** the system returns a JWT access token

#### Scenario: Invalid credentials
- **WHEN** a user submits incorrect credentials
- **THEN** the system returns a 401 Unauthorized error

### Requirement: Authenticated requests are authorized
The system SHALL reject requests to protected endpoints when the JWT is missing, expired, or invalid.

#### Scenario: Missing token
- **WHEN** a request reaches a protected endpoint without an Authorization header
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Expired token
- **WHEN** a request reaches a protected endpoint with an expired JWT
- **THEN** the system returns a 401 Unauthorized error
