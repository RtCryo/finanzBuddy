package com.finanzbuddy.api.security.adapter.in.web;

import com.finanzbuddy.api.security.port.in.AuthenticateInternalUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests to {@code /internal/**} using a shared service API key supplied in the
 * {@code X-Internal-Api-Key} header. A match grants the {@code ROLE_SERVICE} authority; anything
 * else is left unauthenticated and rejected by the authorization rules.
 */
@Component
public class InternalApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";

    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final AuthenticateInternalUseCase authenticateInternal;

    public InternalApiKeyAuthFilter(AuthenticateInternalUseCase authenticateInternal) {
        this.authenticateInternal = authenticateInternal;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateInternal
                    .authenticate(provided)
                    .ifPresent(
                            principal -> {
                                AbstractAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                principal.username(),
                                                null,
                                                principal.authorities().stream()
                                                        .map(SimpleGrantedAuthority::new)
                                                        .toList());
                                SecurityContextHolder.getContext()
                                        .setAuthentication(authentication);
                            });
        }
        filterChain.doFilter(request, response);
    }
}
