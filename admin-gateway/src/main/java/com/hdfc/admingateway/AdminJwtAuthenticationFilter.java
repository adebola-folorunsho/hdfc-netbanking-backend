package com.hdfc.admingateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

/**
 * Global filter for the Admin Gateway.
 * Intercepts every incoming request and enforces two rules:
 * 1. A valid JWT must be present in the Authorization header.
 * 2. The JWT must carry the ROLE_ADMIN claim.
 *
 * This is the first line of defence — requests that fail here
 * never reach the downstream microservices.
 */
@Component
public class AdminJwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_CLAIM = "role";
    private static final String REQUIRED_ROLE = "ROLE_ADMIN";

    private final JwtProperties jwtProperties;

    public AdminJwtAuthenticationFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorizationHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        // Reject immediately if no Authorization header is present
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = extractClaims(token);
            String role = claims.get(ROLE_CLAIM, String.class);

            // Reject if role claim is missing or is not ROLE_ADMIN
            if (!REQUIRED_ROLE.equals(role)) {
                return rejectRequest(exchange, HttpStatus.FORBIDDEN);
            }

            // Token is valid and role is ROLE_ADMIN — allow request through
            return chain.filter(exchange);

        } catch (Exception e) {
            // Token is malformed, expired, or signature is invalid
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Parses and validates the JWT token using the shared secret.
     * JJWT automatically verifies the signature and expiry claim.
     */
    private Claims extractClaims(String token) {
        SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Rejects the request with the given HTTP status and an empty body.
     * No information is leaked about why the request was rejected
     * beyond the status code — intentional for security.
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    /**
     * Ensures this filter runs before all other filters in the chain.
     * Security checks must always be the first thing that runs.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}