# Spec Delta

## ADDED Requirements

### Requirement: finanzBuddy reaches the homeBuddy identity service
The API Gateway and UI containers SHALL join the external `homebuddy` Docker network. The UI SHALL proxy `/api/auth/` to the identity service and all other `/api/` requests to the gateway. The gateway SHALL be configured with the identity service's JWKS URI and issuer through environment variables and SHALL NOT require `JWT_SECRET`.

#### Scenario: Sign in through the UI proxy
- **WHEN** the browser posts credentials to `/api/auth/login` on the finanzBuddy origin
- **THEN** the request reaches the identity service and the issued token is accepted by the gateway

#### Scenario: Missing network
- **WHEN** the `homebuddy` network does not exist
- **THEN** startup fails with a message naming the network, as documented in the README
