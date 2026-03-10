package com.hdfc.accountservice.common.security;

import io.jsonwebtoken.Claims;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * JWT authentication filter for Account Service.
 *
 * <p>Intercepts every HTTP request exactly once (OncePerRequestFilter),
 * extracts the Bearer token from the Authorization header, validates it
 * via JwtTokenValidator, and sets the authenticated principal in the
 * Spring SecurityContext if the token is valid.
 *
 * <p>DESIGN PATTERN — Chain of Responsibility:
 * This filter is one link in Spring Security's filter chain.
 * If the token is valid it sets the SecurityContext and passes
 * the request to the next filter. If invalid it passes the request
 * through without setting the context — Spring Security's downstream
 * filters then reject the request with 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;

    /**
     * Intercepts each request, validates the JWT if present,
     * and populates the SecurityContext for downstream filters.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Extract the token from the Authorization header.
        // If no token is present, pass through — SecurityConfig
        // will reject the request if the endpoint requires auth.
        Optional<String> tokenOptional = extractToken(request);

        if (tokenOptional.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOptional.get();

        // Validate the token and extract claims.
        // validateToken returns empty Optional for any invalid token —
        // expired, malformed, wrong signature etc.
        Optional<Claims> claimsOptional = jwtTokenValidator.validateToken(token);

        if (claimsOptional.isEmpty()) {
            // Invalid token — pass through without setting SecurityContext.
            // Spring Security will reject the request with 401 if the
            // endpoint requires authentication.
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = claimsOptional.get();

        // Only set the SecurityContext if it is not already populated.
        // This prevents overwriting an existing authentication — important
        // if multiple filters run in sequence.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtTokenValidator.extractEmail(claims);
            String role = jwtTokenValidator.extractRole(claims);
            Long userId = jwtTokenValidator.extractUserId(claims);

            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority(role));

            // Store userId as the credentials field so controllers can
            // extract it without reparsing the JWT.
            // Principal = email, Credentials = userId, Authorities = role.
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            userId,     // userId stored here for controller access
                            authorities
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {}, userId: {}, role: {}", email, userId, role);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT from the Authorization header.
     *
     * <p>Expects the header format: "Bearer {token}"
     * Returns empty if the header is absent or malformed.
     *
     * @param request the incoming HTTP request
     * @return an Optional containing the raw token, or empty if absent
     */
    private Optional<String> extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // Substring from index 7 strips the "Bearer " prefix.
            return Optional.of(authHeader.substring(7));
        }

        return Optional.empty();
    }
}