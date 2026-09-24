package com.finanzbuddy.api.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("ZmluYW56YnVkZHktYXBpLWdhdGV3YXktand0LXNlY3JldC1rZXk=", 86400000);

    @Test
    void generatesTokenForValidUser() {
        UserDetails userDetails =
                new User(
                        "test@example.com",
                        "password",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    void tokenIsValidForMatchingUser() {
        UserDetails userDetails =
                new User(
                        "test@example.com",
                        "password",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void tokenIsInvalidForDifferentUser() {
        UserDetails userDetails =
                new User(
                        "test@example.com",
                        "password",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        UserDetails otherUser =
                new User(
                        "other@example.com",
                        "password",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService shortLivedService =
                new JwtService("ZmluYW56YnVkZHktYXBpLWdhdGV3YXktand0LXNlY3JldC1rZXk=", -1000);
        UserDetails userDetails =
                new User(
                        "test@example.com",
                        "password",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        String token = shortLivedService.generateToken(userDetails);

        assertThat(shortLivedService.isTokenValid(token, userDetails)).isFalse();
    }
}
