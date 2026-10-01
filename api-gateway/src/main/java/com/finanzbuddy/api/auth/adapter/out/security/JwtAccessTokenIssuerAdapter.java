package com.finanzbuddy.api.auth.adapter.out.security;

import com.finanzbuddy.api.auth.domain.model.AccessToken;
import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.port.out.AccessTokenIssuerPort;
import com.finanzbuddy.api.security.port.out.AccessTokenPort;
import org.springframework.stereotype.Component;

/** Issues login/registration tokens through the security slice's {@link AccessTokenPort}. */
@Component
public class JwtAccessTokenIssuerAdapter implements AccessTokenIssuerPort {

    private final AccessTokenPort accessTokenPort;

    public JwtAccessTokenIssuerAdapter(AccessTokenPort accessTokenPort) {
        this.accessTokenPort = accessTokenPort;
    }

    @Override
    public AccessToken issue(AuthenticatedUser user) {
        return new AccessToken(accessTokenPort.issue(user.email()));
    }
}
