package com.finanzbuddy.api.security.port.out;

import com.finanzbuddy.api.security.domain.model.TokenValidationResult;

public interface AccessTokenPort {

    /** Issues a signed access token for the given subject. */
    String issue(String subject);

    /** Parses and validates a token, translating library failures into a domain result. */
    TokenValidationResult validate(String token);
}
