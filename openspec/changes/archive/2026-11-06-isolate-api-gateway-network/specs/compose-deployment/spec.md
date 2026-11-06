## MODIFIED Requirements

### Requirement: finanzBuddy reaches the homeBuddy identity service
The UI container SHALL join the external `homebuddy` Docker network. The API Gateway SHALL NOT join `homebuddy`; it SHALL join the external, internal-only network `finanzbuddy-identity` defined by the root capability `homebuddy-deployment`, on which authBuddy is reachable as `auth`. The UI SHALL proxy `/api/auth/` to the identity service and all other `/api/` requests to the gateway over the stack's default network. The gateway SHALL be configured with the identity service's JWKS URI and issuer through environment variables and SHALL NOT require `JWT_SECRET`.

#### Scenario: Sign in through the UI proxy
- **WHEN** the browser posts credentials to `/api/auth/login` on the finanzBuddy origin
- **THEN** the request reaches the identity service and the issued token is accepted by the gateway

#### Scenario: Gateway fetches the signing keys
- **WHEN** the gateway validates a token with an unknown key id
- **THEN** it fetches `http://auth:8080/.well-known/jwks.json` over `finanzbuddy-identity`

#### Scenario: Gateway is not reachable from another stack
- **WHEN** a container of another stack resolves `api-gateway` or `finanzbuddy-api-gateway`
- **THEN** the name does not resolve

#### Scenario: Missing network
- **WHEN** the `homebuddy` or `finanzbuddy-identity` network does not exist
- **THEN** startup fails with a message naming the network, as documented in the README
