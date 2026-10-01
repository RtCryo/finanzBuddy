package com.finanzbuddy.api.auth.port.out;

import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.domain.model.Credentials;

public interface CredentialAuthenticatorPort {

    /**
     * Verifies the credentials, returning the authenticated user. Throws {@link
     * com.finanzbuddy.api.auth.domain.exception.InvalidCredentialsException} when they are invalid.
     */
    AuthenticatedUser authenticate(Credentials credentials);
}
