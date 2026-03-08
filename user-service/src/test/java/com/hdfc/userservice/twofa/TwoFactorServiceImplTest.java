package com.hdfc.userservice.twofa;

import com.hdfc.userservice.common.exception.InvalidOtpException;
import com.hdfc.userservice.common.exception.TwoFactorAuthException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.twofa.dto.TwoFactorSetupResponse;
import com.hdfc.userservice.twofa.dto.TwoFactorVerifyRequest;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TwoFactorServiceImpl}.
 *
 * <p>Tests the full 2FA lifecycle — setup, verify setup, validate OTP,
 * and disable — in complete isolation. All dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TwoFactorServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleAuthenticator googleAuthenticator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TwoFactorServiceImpl twoFactorService;

    private User userWithout2FA;
    private User userWith2FA;

    @BeforeEach
    void setUp() {
        userWithout2FA = User.builder()
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
                .twoFactorSecret(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userWith2FA = User.builder()
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
                .isTwoFactorEnabled(true)
                .twoFactorSecret("JBSWY3DPEHPK3PXP")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // SETUP TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should initiate 2FA setup and return secret and QR code URI")
    void shouldInitiate2FASetupSuccessfully() {
        // Arrange
        GoogleAuthenticatorKey mockKey = mock(GoogleAuthenticatorKey.class);
        when(mockKey.getKey()).thenReturn("JBSWY3DPEHPK3PXP");
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWithout2FA));
        when(googleAuthenticator.createCredentials())
                .thenReturn(mockKey);
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        // Act
        TwoFactorSetupResponse response = twoFactorService.setup(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(response.getQrCodeUri()).contains("JBSWY3DPEHPK3PXP");
        assertThat(response.getQrCodeUri()).contains("otpauth://totp");

        // Verify secret stored in Redis with correct key and TTL
        verify(valueOperations).set(
                eq("user:2fa-setup:1"),
                eq("JBSWY3DPEHPK3PXP"),
                eq(10L),
                eq(TimeUnit.MINUTES)
        );

        // Verify secret NOT written to MySQL yet — only after verification
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw TwoFactorAuthException when 2FA already enabled during setup")
    void shouldThrowTwoFactorAuthExceptionWhenAlreadyEnabled() {
        // Arrange — user already has 2FA enabled
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWith2FA));

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.setup(1L))
                .isInstanceOf(TwoFactorAuthException.class)
                .hasMessageContaining("already enabled");

        verify(googleAuthenticator, never()).createCredentials();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user not found during setup")
    void shouldThrowUserNotFoundExceptionDuringSetup() {
        // Arrange
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.setup(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // VERIFY SETUP TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should complete 2FA setup when valid TOTP code submitted")
    void shouldComplete2FASetupWithValidCode() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWithout2FA));
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("user:2fa-setup:1"))
                .thenReturn("JBSWY3DPEHPK3PXP");
        when(googleAuthenticator.authorize("JBSWY3DPEHPK3PXP", 123456))
                .thenReturn(true);

        // Act
        twoFactorService.verifySetup(1L,
                new TwoFactorVerifyRequest("123456"));

        // Assert — secret written to MySQL and 2FA enabled
        verify(userRepository).save(argThat(user ->
                user.isTwoFactorEnabled() &&
                        user.getTwoFactorSecret().equals("JBSWY3DPEHPK3PXP")
        ));

        // Setup key deleted from Redis after successful verification
        verify(redisTemplate).delete("user:2fa-setup:1");
    }

    @Test
    @DisplayName("Should throw InvalidOtpException when TOTP code is wrong during setup")
    void shouldThrowInvalidOtpExceptionWhenCodeWrongDuringSetup() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWithout2FA));
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("user:2fa-setup:1"))
                .thenReturn("JBSWY3DPEHPK3PXP");
        when(googleAuthenticator.authorize("JBSWY3DPEHPK3PXP", 999999))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.verifySetup(1L,
                new TwoFactorVerifyRequest("999999")))
                .isInstanceOf(InvalidOtpException.class);

        // Secret must NOT be written to MySQL on failed verification
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw InvalidOtpException when setup session expired in Redis")
    void shouldThrowInvalidOtpExceptionWhenSetupSessionExpired() {
        // Arrange — Redis returns null (TTL expired)
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWithout2FA));
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("user:2fa-setup:1"))
                .thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.verifySetup(1L,
                new TwoFactorVerifyRequest("123456")))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).save(any(User.class));
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATE OTP TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should validate OTP successfully during login 2FA")
    void shouldValidateOtpSuccessfullyDuringLogin() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWith2FA));
        when(googleAuthenticator.authorize("JBSWY3DPEHPK3PXP", 123456))
                .thenReturn(true);

        // Act & Assert — no exception thrown
        twoFactorService.validateOtp(1L,
                new TwoFactorVerifyRequest("123456"));
    }

    @Test
    @DisplayName("Should throw InvalidOtpException when OTP code is wrong during login")
    void shouldThrowInvalidOtpExceptionWhenCodeWrongDuringLogin() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWith2FA));
        when(googleAuthenticator.authorize("JBSWY3DPEHPK3PXP", 999999))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.validateOtp(1L,
                new TwoFactorVerifyRequest("999999")))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    @DisplayName("Should throw TwoFactorAuthException when 2FA not enabled during validateOtp")
    void shouldThrowTwoFactorAuthExceptionWhenNotEnabledDuringValidate() {
        // Arrange — user does not have 2FA enabled
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWithout2FA));

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.validateOtp(1L,
                new TwoFactorVerifyRequest("123456")))
                .isInstanceOf(TwoFactorAuthException.class)
                .hasMessageContaining("not enabled");
    }

    // ─────────────────────────────────────────────────────────────
    // DISABLE TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should disable 2FA successfully")
    void shouldDisable2FASuccessfully() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWith2FA));

        // Act
        twoFactorService.disable(1L);

        // Assert — secret cleared and 2FA disabled in MySQL
        verify(userRepository).save(argThat(user ->
                !user.isTwoFactorEnabled() &&
                        user.getTwoFactorSecret() == null
        ));
    }

    @Test
    @DisplayName("Should throw TwoFactorAuthException when disabling already disabled 2FA")
    void shouldThrowTwoFactorAuthExceptionWhenDisablingAlreadyDisabled() {
        // Arrange — 2FA already disabled
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(userWithout2FA));

        // Act & Assert
        assertThatThrownBy(() -> twoFactorService.disable(1L))
                .isInstanceOf(TwoFactorAuthException.class)
                .hasMessageContaining("not enabled");

        verify(userRepository, never()).save(any(User.class));
    }
}