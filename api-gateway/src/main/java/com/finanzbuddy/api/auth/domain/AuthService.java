package com.finanzbuddy.api.auth.domain;

import com.finanzbuddy.api.auth.domain.exception.EmailAlreadyRegisteredException;
import com.finanzbuddy.api.auth.domain.model.AccessToken;
import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.domain.model.Credentials;
import com.finanzbuddy.api.auth.domain.model.Registration;
import com.finanzbuddy.api.auth.port.in.LoginUseCase;
import com.finanzbuddy.api.auth.port.in.RegisterUserUseCase;
import com.finanzbuddy.api.auth.port.out.AccessTokenIssuerPort;
import com.finanzbuddy.api.auth.port.out.CredentialAuthenticatorPort;
import com.finanzbuddy.api.auth.port.out.PasswordHasherPort;
import com.finanzbuddy.api.auth.port.out.TransactionRunnerPort;
import com.finanzbuddy.api.auth.port.out.UserRepositoryPort;
import java.util.Locale;

public class AuthService implements RegisterUserUseCase, LoginUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final CredentialAuthenticatorPort credentialAuthenticator;
    private final AccessTokenIssuerPort accessTokenIssuer;
    private final TransactionRunnerPort transactionRunner;

    public AuthService(
            UserRepositoryPort userRepository,
            PasswordHasherPort passwordHasher,
            CredentialAuthenticatorPort credentialAuthenticator,
            AccessTokenIssuerPort accessTokenIssuer,
            TransactionRunnerPort transactionRunner) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.credentialAuthenticator = credentialAuthenticator;
        this.accessTokenIssuer = accessTokenIssuer;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public AccessToken register(Registration registration) {
        String email = normalizeEmail(registration.email());
        return transactionRunner.inTransaction(
                () -> {
                    // existsByEmail catches the common case cheaply, but it is not a uniqueness
                    // guarantee: two concurrent requests can both pass the check and then race on
                    // the INSERT. The unique database index is the authoritative guard, enforced by
                    // the persistence adapter translating the violation into the same exception.
                    if (userRepository.existsByEmail(email)) {
                        throw new EmailAlreadyRegisteredException();
                    }
                    String passwordHash = passwordHasher.hash(registration.password());
                    AuthenticatedUser user = userRepository.create(email, passwordHash);
                    return accessTokenIssuer.issue(user);
                });
    }

    @Override
    public AccessToken login(Credentials credentials) {
        AuthenticatedUser user = credentialAuthenticator.authenticate(credentials);
        return accessTokenIssuer.issue(user);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
