package com.finanzbuddy.api.auth.adapter.out.security;

import com.finanzbuddy.api.auth.domain.exception.InvalidCredentialsException;
import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.domain.model.Credentials;
import com.finanzbuddy.api.auth.port.out.CredentialAuthenticatorPort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SpringCredentialAuthenticatorAdapter implements CredentialAuthenticatorPort {

    private final AuthenticationManager authenticationManager;

    public SpringCredentialAuthenticatorAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthenticatedUser authenticate(Credentials credentials) {
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    credentials.email(), credentials.password()));
            return new AuthenticatedUser(
                    ((UserDetails) authentication.getPrincipal()).getUsername());
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException();
        }
    }
}
