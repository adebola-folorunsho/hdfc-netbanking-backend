package com.hdfc.userservice.auth;

import com.hdfc.userservice.auth.dto.AuthRequest;
import com.hdfc.userservice.auth.dto.AuthResponse;
import com.hdfc.userservice.auth.dto.RefreshTokenRequest;
import com.hdfc.userservice.common.exception.InvalidTokenException;
import com.hdfc.userservice.common.security.jwt.JwtService;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthServiceImpl}.
 *
 * <p>Tests login, token refresh, and logout in complete isolation.
 * All dependencies — AuthenticationManager, JwtService, Redis,
 * UserRepository — are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private AuthRequest validAuthRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john.doe@example.com")
                .password("$2a$10$hashedpassword")
                .phoneNumber("08012345678")
                .address("123 Main Street, Lagos, Nigeria")
                .governmentId("NGA-123456789")
                .roles(Set.of(Role.CUSTOMER))
                .isEnabled(true)
                .isKycVerified(false)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        validAuthRequest = AuthRequest.builder()
                .email("john.doe@example.com")
                .password("SecurePass1@")
                .build();
    }

    @Test
    @DisplayName("Should login successfully and return access and refresh tokens")
    void shouldLoginSuccessfullyAndReturnTokens() {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail(validAuthRequest.getEmail()))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUser))
                .thenReturn("access.token.here");
        when(jwtService.generateRefreshToken(testUser))
                .thenReturn("refresh.token.here");
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        // Act
        AuthResponse response = authService.login(validAuthRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access.token.here");
        assertThat(response.getRefreshToken()).isEqualTo("refresh.token.here");
        assertThat(response.getTokenType()).isEqualTo("Bearer");

        // Verify refresh token stored in Redis with correct key and TTL
        verify(valueOperations).set(
                eq("user:refresh:1"),
                eq("refresh.token.here"),
                eq(7L),
                eq(TimeUnit.DAYS)
        );
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when password is wrong")
    void shouldThrowBadCredentialsExceptionWhenPasswordIsWrong() {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(validAuthRequest))
                .isInstanceOf(BadCredentialsException.class);

        // Verify we never reached token generation
        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Should refresh tokens successfully and rotate refresh token")
    void shouldRefreshTokensSuccessfullyAndRotate() {
        // Arrange
        String oldRefreshToken = "old.refresh.token";
        String newAccessToken = "new.access.token";
        String newRefreshToken = "new.refresh.token";

        when(jwtService.isTokenExpired(oldRefreshToken)).thenReturn(false);
        when(jwtService.extractEmail(oldRefreshToken))
                .thenReturn("john.doe@example.com");
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(testUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:refresh:1"))
                .thenReturn(oldRefreshToken);
        when(jwtService.generateAccessToken(testUser))
                .thenReturn(newAccessToken);
        when(jwtService.generateRefreshToken(testUser))
                .thenReturn(newRefreshToken);

        // Act
        AuthResponse response = authService.refresh(
                new RefreshTokenRequest(oldRefreshToken));

        // Assert
        assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(response.getRefreshToken()).isEqualTo(newRefreshToken);

        // Verify old token deleted — rotation enforced
        verify(redisTemplate).delete("user:refresh:1");

        // Verify new refresh token stored in Redis
        verify(valueOperations).set(
                eq("user:refresh:1"),
                eq(newRefreshToken),
                eq(7L),
                eq(TimeUnit.DAYS)
        );
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when refresh token is expired")
    void shouldThrowInvalidTokenExceptionWhenRefreshTokenExpired() {
        // Arrange
        String expiredToken = "expired.refresh.token";
        when(jwtService.isTokenExpired(expiredToken)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh(
                new RefreshTokenRequest(expiredToken)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when refresh token not found in Redis")
    void shouldThrowInvalidTokenExceptionWhenTokenNotInRedis() {
        // Arrange — token valid but not in Redis (already used or logged out)
        String usedToken = "already.used.token";
        when(jwtService.isTokenExpired(usedToken)).thenReturn(false);
        when(jwtService.extractEmail(usedToken))
                .thenReturn("john.doe@example.com");
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(testUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Redis returns null — token not found
        when(valueOperations.get("user:refresh:1")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh(
                new RefreshTokenRequest(usedToken)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("not found");

        // Verify no new tokens generated
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when submitted token does not match Redis")
    void shouldThrowInvalidTokenExceptionWhenTokenMismatch() {
        // Arrange — replay attack: submitting an old token when a newer one exists
        String oldToken = "old.token";
        String currentTokenInRedis = "newer.token";

        when(jwtService.isTokenExpired(oldToken)).thenReturn(false);
        when(jwtService.extractEmail(oldToken))
                .thenReturn("john.doe@example.com");
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(testUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:refresh:1"))
                .thenReturn(currentTokenInRedis);

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh(
                new RefreshTokenRequest(oldToken)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("mismatch");

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Should logout successfully by deleting refresh token from Redis")
    void shouldLogoutSuccessfullyByDeletingRefreshTokenFromRedis() {
        // Arrange
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(testUser));

        // Act
        authService.logout("john.doe@example.com");

        // Assert — refresh token deleted from Redis under correct key
        verify(redisTemplate).delete("user:refresh:1");
    }
}