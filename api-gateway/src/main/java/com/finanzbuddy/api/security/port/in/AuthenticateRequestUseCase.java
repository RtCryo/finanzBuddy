package com.finanzbuddy.api.security.port.in;

import com.finanzbuddy.api.security.domain.model.Principal;
import java.util.Optional;

public interface AuthenticateRequestUseCase {

    /**
     * Resolves the principal behind a bearer token. Returns empty when the token is valid but no
     * matching user exists. Throws {@link
     * com.finanzbuddy.api.security.domain.exception.ExpiredTokenException} / {@link
     * com.finanzbuddy.api.security.domain.exception.InvalidTokenException} for unusable tokens.
     */
    Optional<Principal> authenticate(String token);
}
