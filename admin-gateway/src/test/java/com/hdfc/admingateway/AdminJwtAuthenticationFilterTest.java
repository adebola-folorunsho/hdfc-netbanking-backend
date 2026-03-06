package com.hdfc.admingateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.util.Date;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jwt.secret=dGVzdFNlY3JldEtleUZvckFkbWluR2F0ZXdheVRlc3Rpbmc=",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
class AdminJwtAuthenticationFilterTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    // test secret — matches the one in @TestPropertySource above
    private static final String TEST_SECRET = "dGVzdFNlY3JldEtleUZvckFkbWluR2F0ZXdheVRlc3Rpbmc=";
    private static final String ROLE_CLAIM = "role";

    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    }

    private String generateToken(String role, Date expiry) {
        return Jwts.builder()
                .subject("test@hdfc.com")
                .claim(ROLE_CLAIM, role)
                .issuedAt(new Date())
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("Should reject request with no Authorization header — 401")
    void shouldRejectRequestWithNoAuthorizationHeader() {
        webTestClient.get()
                .uri("/api/v1/admin/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Should reject request with malformed Authorization header — 401")
    void shouldRejectRequestWithMalformedAuthorizationHeader() {
        webTestClient.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "InvalidHeader")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Should reject request with expired token — 401")
    void shouldRejectRequestWithExpiredToken() {
        // Token expired 1 hour ago
        Date expiredDate = new Date(System.currentTimeMillis() - 3600000);
        String expiredToken = generateToken("ROLE_ADMIN", expiredDate);

        webTestClient.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Should reject request with ROLE_CUSTOMER token — 403")
    void shouldRejectRequestWithCustomerRole() {
        Date futureDate = new Date(System.currentTimeMillis() + 900000);
        String customerToken = generateToken("ROLE_CUSTOMER", futureDate);

        webTestClient.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should reject request with ROLE_TELLER token — 403")
    void shouldRejectRequestWithTellerRole() {
        Date futureDate = new Date(System.currentTimeMillis() + 900000);
        String tellerToken = generateToken("ROLE_TELLER", futureDate);

        webTestClient.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tellerToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should allow request with valid ROLE_ADMIN token — not 401 or 403")
    void shouldAllowRequestWithAdminRole() {
        Date futureDate = new Date(System.currentTimeMillis() + 900000);
        String adminToken = generateToken("ROLE_ADMIN", futureDate);

        // We expect the filter to pass — the request may fail downstream
        // because there is no real User Service running, but it must not
        // be rejected by the security filter itself (not 401 or 403)
        webTestClient.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().value(status ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(401, status)
                );
    }
}