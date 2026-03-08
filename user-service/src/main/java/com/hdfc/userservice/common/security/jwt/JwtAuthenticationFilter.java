package com.hdfc.userservice.common.security.jwt;

import com.hdfc.userservice.common.security.userdetails.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter that runs once per HTTP request.
 *
 * <p>Intercepts every incoming request and checks for a valid JWT token
 * in the {@code Authorization} header. If a valid token is found, the
 * user is authenticated and their identity is stored in the
 * {@link SecurityContextHolder} for the duration of the request.
 *
 * <p>Extends {@link OncePerRequestFilter} — a Spring utility base class
 * that guarantees this filter runs exactly once per request, even in
 * complex filter chain scenarios where a filter might otherwise execute
 * multiple times due to request forwarding or includes.
 *
 * <p>This filter does NOT throw exceptions directly. If the token is
 * missing, invalid, or expired, it simply does not set the authentication
 * in the security context. Spring Security's downstream filters then
 * reject the request with a 401 response automatically.
 *
 * <p>Filter execution flow:
 * <pre>
 * Request → Extract token → Validate → Load user → Set auth context → Continue
 *                ↓ (no token or invalid)
 *           Skip auth, continue filter chain → Spring Security rejects with 401
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Core filter logic — runs once per request.
     *
     * <p>Extracts the JWT from the Authorization header, validates it,
     * loads the user, and populates the Spring Security context.
     * If any step fails, the filter chain continues without setting
     * authentication — resulting in a 401 from Spring Security.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain to execute after this filter
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Extract the Authorization header from the request
        final String authHeader = request.getHeader("Authorization");

        // If there is no Authorization header or it does not start with "Bearer ",
        // this request is either unauthenticated (hitting a public endpoint like
        // /register or /login) or malformed. Skip JWT processing entirely and
        // pass the request to the next filter in the chain.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the token by stripping the "Bearer " prefix (7 characters)
        final String jwt = authHeader.substring(7);

        try {
            // Extract the email (subject) from the token.
            // If the token is malformed or has an invalid signature,
            // JJWT throws a JwtException which we catch below.
            final String userEmail = jwtService.extractEmail(jwt);

            // Only proceed if we have an email AND there is no authentication
            // already set in the context. If authentication is already set,
            // this request has already been authenticated — no need to repeat.
            if (userEmail != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load the full UserDetails from the database using the email.
                // This also verifies the user still exists and is still active.
                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(userEmail);

                // Validate the token against the loaded user details.
                // Checks: token subject matches email, token is not expired,
                // token signature is valid.
                if (jwtService.isTokenValid(jwt, userDetails)) {

                    // Create a Spring Security authentication token.
                    // The third argument is the list of granted authorities
                    // (roles) loaded from the database via UserDetails.
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null, // credentials are null — we use JWT, not password here
                                    userDetails.getAuthorities()
                            );

                    // Attach request details (IP address, session ID) to the
                    // authentication token — useful for audit logging.
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    // Store the authentication in the SecurityContext.
                    // From this point forward, the request is considered
                    // authenticated for the remainder of its lifecycle.
                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);

                    log.debug("Authenticated user: {}, URI: {}",
                            userEmail, request.getRequestURI());
                }
            }
        } catch (JwtException ex) {
            // Token is malformed, expired, or has an invalid signature.
            // Log the reason but do not set authentication — Spring Security
            // will reject the request with 401 downstream.
            log.warn("JWT validation failed for request to {}: {}",
                    request.getRequestURI(), ex.getMessage());
        } catch (Exception ex) {
            // Catch any unexpected exception during JWT processing.
            // We must never let an exception escape this filter —
            // it would result in a 500 instead of a clean 401.
            log.error("Unexpected error during JWT processing: {}",
                    ex.getMessage(), ex);
        }

        // Always continue the filter chain — whether authenticated or not.
        // Spring Security's downstream filters handle the authorization decision.
        filterChain.doFilter(request, response);
    }
}