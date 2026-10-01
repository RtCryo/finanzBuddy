## Why

The `security` slice mixes token/authentication business logic with Spring Security and servlet infrastructure. `JwtService` couples token generation/validation to `io.jsonwebtoken` and Spring (`@Value`, `UserDetails`, `@Service`); `JwtAuthenticationFilter` and `InternalApiKeyAuthFilter` embed the api-key matching and token-extraction logic inside `jakarta.servlet` + Spring Security filter plumbing. Swapping the JWT library or the web framework forces edits to the logic that decides whether a request is authenticated.

This is the third slice of the api-gateway hexagonal refactor, applying the pattern from [refactor-document-hexagonal]. The auth slice ([refactor-auth-hexagonal]) depends on the token port introduced here, so this slice should land first or alongside it.

## What Changes

- Introduce a framework-free **security domain** for the decisions: issue an access token, validate a token and resolve its subject, and verify an internal api-key. Depends only on `java.*`, domain types, and (pragmatically) `jakarta.inject`/`jakarta.validation`.
- Define **ports**:
  - inbound (called by filters): `AuthenticateRequestUseCase` (resolve principal from a bearer token), `AuthenticateInternalUseCase` (verify api-key).
  - outbound: `AccessTokenPort` (issue/parse/validate tokens — the JWT concern), `ApiKeyPort` (constant-time key comparison), `PrincipalLookupPort` (load user by subject).
- Move framework code into **adapters**:
  - `JwtAccessTokenAdapter` implements `AccessTokenPort` and holds the only `io.jsonwebtoken` imports (replaces `JwtService`).
  - `JwtAuthenticationFilter` / `InternalApiKeyAuthFilter` become thin inbound servlet adapters that call the inbound ports and populate the `SecurityContext`.
  - `SecurityConfig` remains Spring wiring (explicitly a configuration adapter, not domain).
- Replace scattered token-parsing exception handling with domain results/exceptions (`InvalidTokenException`, `ExpiredTokenException`), mapped to 401 at the filter edge.
- Add architecture (ArchUnit) tests enforcing security domain-isolation and port rules.
- **No external behavior change**: bearer-token auth, api-key auth, 401 responses, and token format are identical.

## Capabilities

### New Capabilities
- `security-architecture`: Structural requirements for the security slice — domain isolation of token/api-key decisions, mandatory ports, filter/config adapter responsibilities, and automated enforcement. Verifiable constraints, not functional behavior.

### Modified Capabilities
<!-- None. Observable authentication behavior is unchanged. -->

## Impact

- **Code (api-gateway only)**: `security/jwt/JwtService.java`, `security/jwt/JwtAuthenticationFilter.java`, `security/apikey/InternalApiKeyAuthFilter.java`, `security/SecurityConfig.java` reorganized into `security/domain`, `security/port/{in,out}`, `security/adapter/{in/web,out/token}` plus the retained `SecurityConfig` wiring.
- **Cross-slice**: exposes `AccessTokenPort` consumed by [refactor-auth-hexagonal]; `PrincipalLookupPort` is backed by the user repository (shared with auth).
- **APIs**: unchanged. **Config/env**: unchanged (`JWT_SECRET`, `INTERNAL_API_KEY`).
- **Out of scope**: auth registration/login logic, document/cleanup slices.
- **Tests**: existing security/filter tests re-pointed; new domain unit tests (no servlet/Spring) and ArchUnit tests added.
