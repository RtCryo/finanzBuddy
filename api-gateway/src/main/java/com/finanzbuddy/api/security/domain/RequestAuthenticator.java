package com.finanzbuddy.api.security.domain;

import com.finanzbuddy.api.security.domain.exception.ExpiredTokenException;
import com.finanzbuddy.api.security.domain.exception.InvalidTokenException;
import com.finanzbuddy.api.security.domain.model.Principal;
import com.finanzbuddy.api.security.domain.model.TokenValidationResult;
import com.finanzbuddy.api.security.port.in.AuthenticateRequestUseCase;
import com.finanzbuddy.api.security.port.out.AccessTokenPort;
import com.finanzbuddy.api.security.port.out.PrincipalLookupPort;
import java.util.Optional;

public class RequestAuthenticator implements AuthenticateRequestUseCase {

    private final AccessTokenPort accessTokenPort;
    private final PrincipalLookupPort principalLookupPort;

    public RequestAuthenticator(
            AccessTokenPort accessTokenPort, PrincipalLookupPort principalLookupPort) {
        this.accessTokenPort = accessTokenPort;
        this.principalLookupPort = principalLookupPort;
    }

    @Override
    public Optional<Principal> authenticate(String token) {
        TokenValidationResult result = accessTokenPort.validate(token);
        switch (result.status()) {
            case EXPIRED -> throw new ExpiredTokenException(result.subject());
            case INVALID -> throw new InvalidTokenException();
            case VALID -> {
                // fall through to principal resolution below
            }
        }

        String subject = result.subject();
        if (subject == null) {
            return Optional.empty();
        }
        // Guard against a subject that resolves to a differently-keyed account: only authenticate
        // when the looked-up principal's username matches the token subject exactly.
        return principalLookupPort.findBySubject(subject).filter(p -> subject.equals(p.username()));
    }
}
