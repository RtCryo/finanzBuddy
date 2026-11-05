# Spec Delta

## MODIFIED Requirements

### Requirement: User can register
The system SHALL allow a new user to register a homeBuddy account with a unique email address and a password through `POST /auth/register` on the UI origin, which is served by the authBuddy identity service. The API Gateway SHALL NOT store new passwords.

#### Scenario: Successful registration
- **WHEN** a user submits a valid email and password to `POST /auth/register`
- **THEN** the identity service creates the account and returns a success response

#### Scenario: Duplicate email
- **WHEN** a user registers with an email that already exists
- **THEN** the system returns a 409 Conflict error

### Requirement: User can log in
The system SHALL authenticate a homeBuddy account through `POST /auth/login` on the UI origin, served by the identity service, and issue a signed JWT access token. Users that existed in finanzBuddy before the migration SHALL sign in with their previous password and SHALL see their previous data.

#### Scenario: Successful login
- **WHEN** a registered user submits correct credentials to `POST /auth/login`
- **THEN** the system returns a JWT access token

#### Scenario: Invalid credentials
- **WHEN** a user submits incorrect credentials
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Existing finanzBuddy user after migration
- **WHEN** a user who registered in finanzBuddy before the migration signs in with their old password
- **THEN** the token is accepted by the gateway and the user sees their existing documents and transactions

### Requirement: Authenticated requests are authorized
The system SHALL reject requests to protected endpoints when the JWT is missing, expired, invalid, not signed by a key from the identity service's JWKS, not issued by `homebuddy-auth`, or not intended for the `finanzbuddy` audience. An accepted token SHALL be mapped to exactly one local user by account id, linking an unlinked local user with the same email on first use or creating a new local user.

#### Scenario: Missing token
- **WHEN** a request reaches a protected endpoint without an Authorization header
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Expired token
- **WHEN** a request reaches a protected endpoint with an expired JWT
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Legacy gateway token
- **WHEN** a request carries an HS256 token issued by the gateway before the migration
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: New homeBuddy account opens finanzBuddy
- **WHEN** an account registered through calendarBuddy makes its first finanzBuddy request
- **THEN** a new local user is created for that account with no documents
