package com.finanzbuddy.api.auth.port.out;

import com.finanzbuddy.api.auth.domain.model.AccessToken;
import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;

public interface AccessTokenIssuerPort {

    AccessToken issue(AuthenticatedUser user);
}
