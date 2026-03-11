package com.hdfc.transactionservice.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter for Transaction Service.
 *
 * <p>Executes once per request. Extracts and validates the JWT from the
 * Authorization header. On success, populates the SecurityContext with
 * the authenticated user's email, role, and userId so controllers and
 * service methods can access them without re-parsing the token.
 *
 * <p>userId is stored in the {@code credentials} field of
 * UsernamePasswordAuthenticationToken — the same pattern used in
 * Account Service for consistency across the platform.
 *
 * <p>On any JWT failure the filter clears the SecurityContext and
 * passes the request downstream — Spring Security's access control
 * rules then return 401 or 403 as appropriate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No Authorization header or not a Bearer token — skip filter.
        // SecurityContext remains empty; access control decides outcome.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            Claims claims = jwtTokenValidator.validateAndGetClaims(jwt);

            // Only set authentication if not already set and token is valid.
            if (!jwtTokenValidator.isTokenExpired(claims) &&
                    SecurityContextHolder.getContext()
                            .getAuthentication() == null) {

                String email = jwtTokenValidator.extractEmail(claims);
                String role  = jwtTokenValidator.extractRole(claims);
                Long userId  = jwtTokenValidator.extractUserId(claims);

                // Build the authentication token:
                // principal   = email (standard — used by Spring Security)
                // credentials = userId (custom — used by controllers)
                // authorities = [role] (ROLE_CUSTOMER / TELLER / ADMIN)
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                userId,
                                List.of(new SimpleGrantedAuthority(role))
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request));

                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);

                log.debug("JWT authenticated: userId={}, role={}", userId, role);
            }

        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception ex) {
            log.error("Unexpected error in JWT filter: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}