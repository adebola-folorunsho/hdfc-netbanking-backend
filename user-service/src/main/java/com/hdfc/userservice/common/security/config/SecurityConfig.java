package com.hdfc.userservice.common.security.config;

import com.hdfc.userservice.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hdfc.userservice.common.security.jwt.JwtAuthenticationFilter;
import com.hdfc.userservice.common.security.userdetails.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.PrintWriter;

/**
 * Spring Security configuration for the User Service.
 *
 * <p>Wires together the full security stack:
 * <ul>
 *   <li>Stateless session management — no HTTP sessions, JWT only</li>
 *   <li>URL-level access rules per Section 5.2 of the project spec</li>
 *   <li>JWT filter registered before Spring's username/password filter</li>
 *   <li>Custom 403 AccessDeniedHandler returning structured JSON</li>
 *   <li>BCrypt password encoding</li>
 *   <li>Method-level security enabling {@code @PreAuthorize}</li>
 * </ul>
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} and
 * {@code @PostAuthorize} annotations on service methods. This is the
 * second line of defence — even if a request bypasses the Admin Gateway,
 * the service itself enforces role checks at the method level.
 *
 * <p>{@code @EnableWebSecurity} takes full control of Spring Security
 * configuration — disabling the auto-configured defaults and replacing
 * them with our explicit setup.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Defines the Security Filter Chain — the core of Spring Security.
     *
     * <p>Every incoming HTTP request passes through this chain.
     * Rules are evaluated top to bottom — the first matching rule wins.
     *
     * <p>URL access rules per Section 5.2:
     * <ul>
     *   <li>{@code /api/v1/auth/**} — public, no authentication required</li>
     *   <li>{@code /api/v1/users/register} — public, no authentication required</li>
     *   <li>{@code /actuator/health} — public, required by Eureka health checks</li>
     *   <li>{@code /api/v1/admin/**} — ADMIN role only</li>
     *   <li>{@code /api/v1/teller/**} — TELLER or ADMIN role</li>
     *   <li>All other requests — any authenticated user</li>
     * </ul>
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // Disable CSRF — not needed for stateless JWT APIs.
                // CSRF protection is for session-based apps where cookies
                // carry the session token. We use JWT in the Authorization
                // header — not vulnerable to CSRF attacks.
                .csrf(AbstractHttpConfigurer::disable)

                // URL-level authorization rules per Section 5.2
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints — no token required
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/users/register",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Admin-only endpoints — ADMIN role required.
                        // First line of defence is the Admin Gateway (port 8090).
                        // This is the second line — defence in depth.
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        // Teller endpoints — TELLER or ADMIN role required
                        .requestMatchers("/api/v1/teller/**")
                        .hasAnyRole("TELLER", "ADMIN")

                        // All other endpoints require any authenticated user
                        .anyRequest().authenticated()
                )

                // Stateless session management — no HTTP sessions created or used.
                // Every request must carry a valid JWT token.
                // Without this, Spring Security would create a session after
                // the first authenticated request — defeating the purpose of JWT.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Register our custom AuthenticationProvider — tells Spring Security
                // to use our UserDetailsService and BCrypt password encoder
                // when authenticating login attempts.
                .authenticationProvider(authenticationProvider())

                // Register the JWT filter BEFORE Spring's built-in
                // UsernamePasswordAuthenticationFilter. This ensures the JWT
                // is validated and the security context is populated before
                // Spring's filter runs — preventing double authentication attempts.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                // Custom AccessDeniedHandler — returns structured JSON 403
                // instead of Spring Security's default HTML error page.
                // Required by Section 5.2: "A custom AccessDeniedHandler returns
                // a structured 403 JSON response — not a redirect."
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    /**
     * Configures the authentication provider.
     *
     * <p>DaoAuthenticationProvider is Spring Security's standard provider
     * for database-backed authentication. It uses our UserDetailsService
     * to load the user and our PasswordEncoder to verify the password.
     *
     * <p>Without this bean, Spring Security would not know how to authenticate
     * username/password login requests — it would have no way to load users
     * from our MySQL database.
     *
     * @return a configured DaoAuthenticationProvider
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // Tell the provider to load users from our database via UserDetailsService
        provider.setUserDetailsService(userDetailsService);
        // Tell the provider to verify passwords using BCrypt
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the AuthenticationManager as a Spring bean.
     *
     * <p>The AuthenticationManager is the entry point for all authentication
     * requests. The auth service calls it directly during login to trigger
     * the authentication flow. Without exposing it as a bean, we cannot
     * inject it into the auth service.
     *
     * @param config Spring's AuthenticationConfiguration — auto-configured
     * @return the application's AuthenticationManager
     * @throws Exception if the manager cannot be retrieved
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt password encoder bean.
     *
     * <p>BCrypt is the industry standard for password hashing in Java
     * applications. It automatically salts passwords and is deliberately
     * slow — making brute-force attacks computationally expensive.
     * The default strength factor is 10 (2^10 = 1024 hashing rounds).
     *
     * <p>Never use MD5, SHA-1, or plain SHA-256 for passwords — these
     * are fast hashing algorithms and trivially brute-forceable.
     *
     * @return a BCryptPasswordEncoder with default strength (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Custom AccessDeniedHandler that returns a structured JSON 403 response.
     *
     * <p>Without this, Spring Security returns an HTML error page when an
     * authenticated user tries to access a resource they don't have permission
     * for. In a REST API, HTML responses are unacceptable — clients expect JSON.
     *
     * <p>This satisfies the requirement from Section 5.2:
     * "A custom AccessDeniedHandler returns a structured 403 JSON response
     * — not a redirect — keeping it REST-friendly."
     *
     * <p>Note: This handler covers 403 Forbidden (insufficient permissions).
     * 401 Unauthorized (not authenticated) is handled separately by
     * the JWT filter's behaviour of not setting authentication in the context.
     *
     * @return a custom AccessDeniedHandler returning JSON 403
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access denied for request to {}: {}",
                    request.getRequestURI(), accessDeniedException.getMessage());

            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            // Build a structured ApiResponse error and write it to the response.
            // We manually serialize here because we are outside the Spring MVC
            // context — ResponseEntity and @RestControllerAdvice are not
            // available at the filter/handler level.
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            ApiResponse<Void> errorResponse = ApiResponse.error(
                    "Access denied. You do not have permission to perform this action.");

            PrintWriter writer = response.getWriter();
            writer.print(mapper.writeValueAsString(errorResponse));
            writer.flush();
        };
    }
}