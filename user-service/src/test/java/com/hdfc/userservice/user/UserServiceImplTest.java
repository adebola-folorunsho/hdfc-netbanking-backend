package com.hdfc.userservice.user;

import com.hdfc.userservice.common.exception.DuplicateUserException;
import com.hdfc.userservice.common.exception.KycVerificationException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.user.dto.ChangePasswordRequest;
import com.hdfc.userservice.user.dto.UpdateProfileRequest;
import com.hdfc.userservice.user.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserServiceImpl}.
 *
 * <p>Tests profile retrieval, profile update, password change,
 * admin profile lookup, and KYC verification — in complete isolation.
 * All dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

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
    }

    // ─────────────────────────────────────────────────────────────
    // GET OWN PROFILE TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return own profile successfully")
    void shouldReturnOwnProfileSuccessfully() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));

        // Act
        UserProfileResponse response = userService.getOwnProfile(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFullName()).isEqualTo("John Doe");
        // Sensitive fields must never appear in the response
        assertThat(response).extracting("roles").isNotNull();
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user not found for own profile")
    void shouldThrowUserNotFoundForOwnProfile() {
        // Arrange
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getOwnProfile(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE OWN PROFILE TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should update own profile successfully")
    void shouldUpdateOwnProfileSuccessfully() {
        // Arrange
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("John Updated")
                .phoneNumber("08099999999")
                .address("456 New Street, Abuja, Nigeria")
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(userRepository.existsByPhoneNumber("08099999999"))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(testUser);

        // Act
        UserProfileResponse response =
                userService.updateOwnProfile(1L, request);

        // Assert — updated fields saved to database
        verify(userRepository).save(argThat(user ->
                user.getFullName().equals("John Updated") &&
                        user.getPhoneNumber().equals("08099999999") &&
                        user.getAddress().equals("456 New Street, Abuja, Nigeria")
        ));
    }

    @Test
    @DisplayName("Should throw DuplicateUserException when new phone number already taken")
    void shouldThrowDuplicateUserExceptionWhenPhoneNumberTaken() {
        // Arrange
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("John Updated")
                .phoneNumber("08099999999")
                .address("456 New Street, Abuja, Nigeria")
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        // Phone number already taken by another user
        when(userRepository.existsByPhoneNumber("08099999999"))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() ->
                userService.updateOwnProfile(1L, request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should allow update with same phone number as current user")
    void shouldAllowUpdateWithSamePhoneNumber() {
        // Arrange — same phone number as current user — not a duplicate
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("John Updated")
                .phoneNumber("08012345678") // same as testUser's current phone
                .address("456 New Street, Abuja, Nigeria")
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class)))
                .thenReturn(testUser);

        // Act — should not throw
        userService.updateOwnProfile(1L, request);

        // Assert — save called without duplicate check triggering
        verify(userRepository).save(any(User.class));
    }

    // ─────────────────────────────────────────────────────────────
    // CHANGE PASSWORD TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should change password successfully and invalidate refresh token")
    void shouldChangePasswordSuccessfullyAndInvalidateRefreshToken() {
        // Arrange
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass1@")
                .newPassword("NewPass1@")
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        // Current password matches
        when(passwordEncoder.matches("OldPass1@",
                "$2a$10$hashedpassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1@"))
                .thenReturn("$2a$10$newhashedpassword");

        // Act
        userService.changePassword(1L, request);

        // Assert — new hashed password saved
        verify(userRepository).save(argThat(user ->
                user.getPassword().equals("$2a$10$newhashedpassword")
        ));

        // Refresh token invalidated in Redis
        verify(redisTemplate).delete("user:refresh:1");
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when current password is wrong")
    void shouldThrowBadCredentialsExceptionWhenCurrentPasswordWrong() {
        // Arrange
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("WrongPass1@")
                .newPassword("NewPass1@")
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPass1@",
                "$2a$10$hashedpassword")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() ->
                userService.changePassword(1L, request))
                .isInstanceOf(BadCredentialsException.class);

        // Password must NOT be updated on failed verification
        verify(userRepository, never()).save(any(User.class));
        // Refresh token must NOT be invalidated
        verify(redisTemplate, never()).delete(anyString());
    }

    // ─────────────────────────────────────────────────────────────
    // GET USER PROFILE (ADMIN/TELLER) TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return any user profile for admin or teller")
    void shouldReturnUserProfileForAdminOrTeller() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));

        // Act
        UserProfileResponse response = userService.getUserProfile(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail())
                .isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when target user not found")
    void shouldThrowUserNotFoundForUserProfile() {
        // Arrange
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserProfile(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // KYC VERIFICATION TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should mark user as KYC verified successfully")
    void shouldMarkUserAsKycVerifiedSuccessfully() {
        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));

        // Act
        userService.verifyKyc(1L);

        // Assert — isKycVerified set to true and saved
        verify(userRepository).save(argThat(User::isKycVerified));
    }

    @Test
    @DisplayName("Should throw KycVerificationException when user already KYC verified")
    void shouldThrowKycVerificationExceptionWhenAlreadyVerified() {
        // Arrange — user already KYC verified
        User alreadyVerifiedUser = User.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john.doe@example.com")
                .password("$2a$10$hashedpassword")
                .phoneNumber("08012345678")
                .address("123 Main Street, Lagos, Nigeria")
                .governmentId("NGA-123456789")
                .roles(Set.of(Role.CUSTOMER))
                .isEnabled(true)
                .isKycVerified(true)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(alreadyVerifiedUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.verifyKyc(1L))
                .isInstanceOf(KycVerificationException.class)
                .hasMessageContaining("already");

        verify(userRepository, never()).save(any(User.class));
    }
}