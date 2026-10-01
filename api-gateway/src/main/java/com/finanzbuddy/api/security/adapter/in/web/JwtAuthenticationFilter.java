package com.finanzbuddy.api.security.adapter.in.web;

import com.finanzbuddy.api.security.domain.exception.ExpiredTokenException;
import com.finanzbuddy.api.security.domain.exception.InvalidTokenException;
import com.finanzbuddy.api.security.port.in.AuthenticateRequestUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Thin servlet adapter: delegates the decision to the inbound port, then populates the context. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticateRequestUseCase authenticateRequest;

    public JwtAuthenticationFilter(AuthenticateRequestUseCase authenticateRequest) {
        this.authenticateRequest = authenticateRequest;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        try {
            authenticateRequest
                    .authenticate(jwt)
                    .ifPresent(principal -> authenticate(principal, request));
        } catch (ExpiredTokenException e) {
            // Expired tokens are common and useful for audit/ops; log them at a higher level than
            // malformed ones while still leaving the request unauthenticated so permitAll endpoints
            // keep working.
            logger.info("Ignoring expired JWT bearer token for subject " + e.subject());
        } catch (InvalidTokenException e) {
            // Leave the request unauthenticated and let the authorization rules decide: a stale
            // token must not block permitAll endpoints such as /auth/** or /actuator/health.
            logger.debug("Ignoring invalid JWT bearer token");
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(
            com.finanzbuddy.api.security.domain.model.Principal principal,
            HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UserDetails userDetails =
                new User(
                        principal.username(),
                        "",
                        principal.authorities().stream().map(SimpleGrantedAuthority::new).toList());
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
