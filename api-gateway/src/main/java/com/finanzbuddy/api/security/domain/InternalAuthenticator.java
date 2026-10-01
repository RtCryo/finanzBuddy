package com.finanzbuddy.api.security.domain;

import com.finanzbuddy.api.security.domain.model.Principal;
import com.finanzbuddy.api.security.port.in.AuthenticateInternalUseCase;
import com.finanzbuddy.api.security.port.out.ApiKeyPort;
import java.util.List;
import java.util.Optional;

public class InternalAuthenticator implements AuthenticateInternalUseCase {

    private static final Principal SERVICE_PRINCIPAL =
            new Principal("internal-service", List.of("ROLE_SERVICE"));

    private final ApiKeyPort apiKeyPort;

    public InternalAuthenticator(ApiKeyPort apiKeyPort) {
        this.apiKeyPort = apiKeyPort;
    }

    @Override
    public Optional<Principal> authenticate(String apiKey) {
        return apiKeyPort.matches(apiKey) ? Optional.of(SERVICE_PRINCIPAL) : Optional.empty();
    }
}
