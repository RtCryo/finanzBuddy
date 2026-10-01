package com.finanzbuddy.api.security.adapter.out.user;

import com.finanzbuddy.api.auth.port.out.UserRepositoryPort;
import com.finanzbuddy.api.security.domain.model.Principal;
import com.finanzbuddy.api.security.port.out.PrincipalLookupPort;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrincipalLookupAdapter implements PrincipalLookupPort {

    private final UserRepositoryPort userRepository;

    public PrincipalLookupAdapter(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Principal> findBySubject(String subject) {
        // Emails are stored lower-cased at registration; normalize the lookup so a request resolves
        // regardless of the casing the token subject carries.
        String email = subject == null ? null : subject.trim().toLowerCase(Locale.ROOT);
        return userRepository
                .findByEmail(email)
                .map(account -> new Principal(account.email(), List.of("ROLE_USER")));
    }
}
