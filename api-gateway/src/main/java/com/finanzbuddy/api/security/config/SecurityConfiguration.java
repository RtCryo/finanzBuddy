package com.finanzbuddy.api.security.config;

import com.finanzbuddy.api.security.domain.InternalAuthenticator;
import com.finanzbuddy.api.security.domain.RequestAuthenticator;
import com.finanzbuddy.api.security.port.in.AuthenticateInternalUseCase;
import com.finanzbuddy.api.security.port.in.AuthenticateRequestUseCase;
import com.finanzbuddy.api.security.port.out.AccessTokenPort;
import com.finanzbuddy.api.security.port.out.ApiKeyPort;
import com.finanzbuddy.api.security.port.out.PrincipalLookupPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free security domain authenticators from their outbound port beans. */
@Configuration
public class SecurityConfiguration {

    @Bean
    AuthenticateRequestUseCase requestAuthenticator(
            AccessTokenPort accessTokenPort, PrincipalLookupPort principalLookupPort) {
        return new RequestAuthenticator(accessTokenPort, principalLookupPort);
    }

    @Bean
    AuthenticateInternalUseCase internalAuthenticator(ApiKeyPort apiKeyPort) {
        return new InternalAuthenticator(apiKeyPort);
    }
}
