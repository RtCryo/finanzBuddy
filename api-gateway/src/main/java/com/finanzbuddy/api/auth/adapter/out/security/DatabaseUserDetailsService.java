package com.finanzbuddy.api.auth.adapter.out.security;

import com.finanzbuddy.api.auth.domain.model.UserAccount;
import com.finanzbuddy.api.auth.port.out.UserRepositoryPort;
import java.util.Collections;
import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepositoryPort userRepository;

    public DatabaseUserDetailsService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Emails are stored lower-cased at registration; normalize the lookup so login and
        // subsequent requests resolve regardless of the casing the client sends.
        String email = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        UserAccount account =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () -> new UsernameNotFoundException("User not found: " + username));

        return new User(
                account.email(),
                account.passwordHash(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
