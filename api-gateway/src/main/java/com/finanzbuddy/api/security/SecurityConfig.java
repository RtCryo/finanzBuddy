package com.finanzbuddy.api.security;

import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.security.apikey.InternalApiKeyAuthFilter;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalApiKeyAuthFilter internalApiKeyAuthFilter,
            Converter<Jwt, AbstractAuthenticationToken> accountAuthenticationConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/error")
                                        .permitAll()
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers("/internal/**")
                                        .hasRole("SERVICE")
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        internalApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        accountAuthenticationConverter)))
                .exceptionHandling(
                        exception ->
                                exception.authenticationEntryPoint(authenticationEntryPoint()));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.auth.jwks-uri}") String jwksUri,
            @Value("${app.auth.issuer}") String issuer) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(jwksUri)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        OAuth2TokenValidator<Jwt> audience =
                jwt ->
                        jwt.getAudience().contains("finanzbuddy")
                                ? OAuth2TokenValidatorResult.success()
                                : OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error("invalid_token", "Wrong audience", null));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(Duration.ofSeconds(60)),
                        new JwtIssuerValidator(issuer),
                        audience));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> accountAuthenticationConverter(
            HomeBuddyAccountResolver resolver) {
        return jwt -> {
            User localUser = resolver.resolve(jwt);
            UserDetails principal =
                    new org.springframework.security.core.userdetails.User(
                            localUser.getEmail(),
                            "",
                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
            return new UsernamePasswordAuthenticationToken(
                    principal, jwt, principal.getAuthorities());
        };
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }
}
