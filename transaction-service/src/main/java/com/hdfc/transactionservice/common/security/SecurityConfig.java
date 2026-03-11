package com.hdfc.transactionservice.common.security;

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
 * Spring Security configuration for Transaction Service.
 *
 * <p>STATELESS — no sessions, no cookies. Every request must carry a
 * valid JWT in the Authorization header. This is consistent with all
 * other HDFC NetBanking microservices.
 *
 * <p>Role-based access control overview:
 * <ul>
 *   <li>CUSTOMER — can initiate transfers and view own transactions</li>
 *   <li>TELLER — can initiate transfers, deposits, withdrawals, view all</li>
 *   <li>ADMIN — full access including reversals</li>
 * </ul>
 *
 * <p>Fine-grained method-level security is applied via @PreAuthorize
 * in the service layer for ownership checks and complex role logic.
 * URL-level rules here are coarse-grained guards only.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // Actuator health — required by Eureka for service discovery.
                        // No authentication needed for health probes.
                        .requestMatchers("/actuator/**").permitAll()

                        // Paystack webhook — called by Paystack server, not a user.
                        // Authentication is via HMAC-SHA512 signature verification
                        // inside the webhook handler — not via JWT.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/transactions/webhook/paystack").permitAll()

                        // All other transaction endpoints require authentication.
                        // Fine-grained role checks are in the service layer.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}