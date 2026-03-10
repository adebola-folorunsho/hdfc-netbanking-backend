package com.hdfc.accountservice.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for Account Service.
 *
 * <p>Account Service is a stateless JWT-secured microservice.
 * It does not manage sessions, does not have a login form, and
 * does not issue tokens. Every request must carry a valid Bearer
 * token issued by User Service.
 *
 * <p>@EnableMethodSecurity activates @PreAuthorize on service and
 * controller methods so role-based access can be enforced at the
 * method level in addition to the URL-level rules defined here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configures the security filter chain for Account Service.
     *
     * <p>Rules applied in order:
     * <ol>
     *   <li>CSRF disabled — stateless REST APIs do not use CSRF tokens</li>
     *   <li>Session management — STATELESS, no HttpSession ever created</li>
     *   <li>Public endpoints — actuator health check only</li>
     *   <li>All other endpoints — require authentication</li>
     *   <li>JWT filter — runs before UsernamePasswordAuthenticationFilter</li>
     * </ol>
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is irrelevant for stateless REST APIs.
                // CSRF attacks exploit browser cookie behaviour — our API
                // uses Bearer tokens in Authorization headers, not cookies.
                .csrf(AbstractHttpConfigurer::disable)

                // Never create or use an HttpSession.
                // Every request is authenticated independently via its JWT.
                // This ensures horizontal scalability — any instance can
                // handle any request without shared session state.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Actuator health endpoint must be public.
                        // Eureka pings this to determine if the service is UP.
                        // Blocking it would cause Eureka to mark us as DOWN.
                        .requestMatchers("/actuator/**").permitAll()

                        // Admin-only endpoints — enforced at URL level here
                        // and additionally at method level via @PreAuthorize.
                        // Double enforcement ensures security even if a route
                        // is accidentally misconfigured at the gateway level.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // All other endpoints require a valid JWT.
                        // Fine-grained role checks are handled at the method
                        // level via @PreAuthorize in the service/controller layer.
                        .anyRequest().authenticated()
                )

                // Register our JWT filter before Spring Security's default
                // UsernamePasswordAuthenticationFilter. This ensures the
                // SecurityContext is populated before any security decisions
                // are made for the current request.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}