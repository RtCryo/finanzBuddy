# User Authentication Specification

## Purpose

Provides stateless JWT-based registration and authentication so that only identified users can access document endpoints.

## Requirements

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
The system SHALL reject requests to protected endpoints when the JWT is missing, expired, invalid, not signed by a key from the identity service's JWKS, not issued by `homebuddy-auth`, not intended for the `finanzbuddy` audience, or missing the `exp`, `sub`, or `email` claim. An accepted token SHALL be mapped to exactly one local user by account id only, creating a new local user when none exists. The gateway SHALL NOT link an existing local user to an account by email at request time; existing users SHALL be linked by the operator migration step before registration opens, and a token whose email matches a local user that is not linked SHALL be rejected. The stored email SHALL follow the token claim and SHALL NOT be unique.

#### Scenario: Missing token
- **WHEN** a request reaches a protected endpoint without an Authorization header
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Expired token
- **WHEN** a request reaches a protected endpoint with an expired JWT
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Legacy gateway token
- **WHEN** a request carries an HS256 token issued by the gateway before the migration
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Token without expiry or audience
- **WHEN** a request carries a correctly signed token that has no `exp` or no `aud` claim
- **THEN** the system returns a 401 Unauthorized error

#### Scenario: Account whose email matches an unlinked legacy user
- **WHEN** an account that was not linked by the migration presents an email stored on an unlinked local user
- **THEN** the system returns a 401 Unauthorized error and the local user stays unlinked

#### Scenario: Email taken over from another account
- **WHEN** an account's email changes to an address that another local user still stores
- **THEN** the request succeeds and both local users keep their data

#### Scenario: New homeBuddy account opens finanzBuddy
- **WHEN** an account registered through calendarBuddy makes its first finanzBuddy request
- **THEN** a new local user is created for that account with no documents

## Related implementation and history

- Documentation: [API Gateway map](../../../docs/README.md#subsystem-map), [Gateway README](../../../api-gateway/README.md), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`SecurityConfig.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/security/SecurityConfig.java), [`HomeBuddyAccountResolver.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/security/HomeBuddyAccountResolver.java), [`User.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/entity/User.java)
- History: [API Gateway](../../changes/archive/2026-09-26-api-gateway/)
