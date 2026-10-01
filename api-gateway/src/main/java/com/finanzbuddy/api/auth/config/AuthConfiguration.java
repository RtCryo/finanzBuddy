package com.finanzbuddy.api.auth.config;

import com.finanzbuddy.api.auth.domain.AuthService;
import com.finanzbuddy.api.auth.port.out.AccessTokenIssuerPort;
import com.finanzbuddy.api.auth.port.out.CredentialAuthenticatorPort;
import com.finanzbuddy.api.auth.port.out.PasswordHasherPort;
import com.finanzbuddy.api.auth.port.out.TransactionRunnerPort;
import com.finanzbuddy.api.auth.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfiguration {

    @Bean
    AuthService authService(
            UserRepositoryPort userRepository,
            PasswordHasherPort passwordHasher,
            CredentialAuthenticatorPort credentialAuthenticator,
            AccessTokenIssuerPort accessTokenIssuer,
            TransactionRunnerPort transactionRunner) {
        return new AuthService(
                userRepository,
                passwordHasher,
                credentialAuthenticator,
                accessTokenIssuer,
                transactionRunner);
    }
}
