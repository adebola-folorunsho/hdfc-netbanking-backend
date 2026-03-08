package com.hdfc.admingateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "dGVzdFNlY3JldEtleUZvckFkbWluR2F0ZXdheVRlc3RpbmdLZXkxMjM0NTY=";
    private static final String ROLE_CLAIM = "role";

    private SecretKey secretKey;

    // Manually construct the filter with a real JwtProperties
    private AdminJwtAuthenticationFilter filter;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        filter = new AdminJwtAuthenticationFilter(jwtProperties);
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
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/admin/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject request with malformed Authorization header — 401")
    void shouldRejectRequestWithMalformedAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "InvalidHeader")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject request with expired token — 401")
    void shouldRejectRequestWithExpiredToken() {
        Date expiredDate = new Date(System.currentTimeMillis() - 3600000);
        String expiredToken = generateToken("ROLE_ADMIN", expiredDate);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject request with ROLE_CUSTOMER token — 403")
    void shouldRejectRequestWithCustomerRole() {
        Date futureDate = new Date(System.currentTimeMillis() + 900000);
        String customerToken = generateToken("ROLE_CUSTOMER", futureDate);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should reject request with ROLE_TELLER token — 403")
    void shouldRejectRequestWithTellerRole() {
        Date futureDate = new Date(System.currentTimeMillis() + 900000);
        String tellerToken = generateToken("ROLE_TELLER", futureDate);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tellerToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should allow request with valid ROLE_ADMIN token — chain proceeds")
    void shouldAllowRequestWithAdminRole() {
        Date futureDate = new Date(System.currentTimeMillis() + 900000);
        String adminToken = generateToken("ROLE_ADMIN", futureDate);

        // Stub chain only here — this is the only test that reaches chain.filter()
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}