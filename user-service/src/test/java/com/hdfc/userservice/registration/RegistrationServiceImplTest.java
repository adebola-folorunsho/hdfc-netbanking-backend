package com.hdfc.userservice.registration;

import com.hdfc.userservice.common.exception.DuplicateUserException;
import com.hdfc.userservice.common.exception.KycVerificationException;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.registration.dto.UserRegistrationRequest;
import com.hdfc.userservice.registration.dto.UserRegistrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistrationServiceImpl}.
 *
 * <p>Tests the registration business logic in complete isolation —
 * no Spring context, no database, no Redis. All dependencies are
 * mocked with Mockito. This makes tests fast and focused on
 * business logic only.
 *
 * <p>Each test follows the Arrange-Act-Assert pattern:
 * Arrange — set up the test data and mock behaviour
 * Act     — call the method under test
 * Assert  — verify the outcome
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private UserRegistrationRequest validRequest;
    private User savedUser;

    /**
     * Sets up valid test data before each test.
     * Using @BeforeEach avoids duplicating setup across every test method.
     */
    @BeforeEach
    void setUp() {
        validRequest = UserRegistrationRequest.builder()
                .fullName("John Doe")
                .email("john.doe@example.com")
                .password("SecurePass1@")
                .phoneNumber("08012345678")
                .address("123 Main Street, Lagos, Nigeria")
                .governmentId("NGA-123456789")
                .build();

        savedUser = User.builder()
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

    @Test
    @DisplayName("Should register a new user successfully when all details are valid")
    void shouldRegisterUserSuccessfully() {
        // Arrange
        when(userRepository.existsByEmail(validRequest.getEmail()))
                .thenReturn(false);
        when(userRepository.existsByPhoneNumber(validRequest.getPhoneNumber()))
                .thenReturn(false);
        when(userRepository.existsByGovernmentId(validRequest.getGovernmentId()))
                .thenReturn(false);
        when(passwordEncoder.encode(validRequest.getPassword()))
                .thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        // Act
        UserRegistrationResponse response =
                registrationService.register(validRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFullName()).isEqualTo("John Doe");
        assertThat(response.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.isKycVerified()).isFalse();

        // Verify the password was hashed — never stored raw
        verify(passwordEncoder).encode(validRequest.getPassword());
        // Verify the user was saved exactly once
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateUserException when email already exists")
    void shouldThrowDuplicateUserExceptionWhenEmailExists() {
        // Arrange — email already taken
        when(userRepository.existsByEmail(validRequest.getEmail()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(validRequest))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("email");

        // Verify we never reached the save step
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateUserException when phone number already exists")
    void shouldThrowDuplicateUserExceptionWhenPhoneNumberExists() {
        // Arrange — phone number already taken
        when(userRepository.existsByEmail(validRequest.getEmail()))
                .thenReturn(false);
        when(userRepository.existsByPhoneNumber(validRequest.getPhoneNumber()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(validRequest))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("phoneNumber");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateUserException when government ID already exists")
    void shouldThrowDuplicateUserExceptionWhenGovernmentIdExists() {
        // Arrange — government ID already taken
        when(userRepository.existsByEmail(validRequest.getEmail()))
                .thenReturn(false);
        when(userRepository.existsByPhoneNumber(validRequest.getPhoneNumber()))
                .thenReturn(false);
        when(userRepository.existsByGovernmentId(validRequest.getGovernmentId()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(validRequest))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("governmentId");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw KycVerificationException when full name is blank")
    void shouldThrowKycVerificationExceptionWhenFullNameIsBlank() {
        // Arrange — invalid KYC data
        UserRegistrationRequest invalidRequest = UserRegistrationRequest.builder()
                .fullName("  ")
                .email("john.doe@example.com")
                .password("SecurePass1@")
                .phoneNumber("08012345678")
                .address("123 Main Street, Lagos, Nigeria")
                .governmentId("NGA-123456789")
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByGovernmentId(anyString())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(invalidRequest))
                .isInstanceOf(KycVerificationException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should assign CUSTOMER role by default — never allow client to set role")
    void shouldAssignCustomerRoleByDefault() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByGovernmentId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString()))
                .thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        UserRegistrationResponse response =
                registrationService.register(validRequest);

        // Assert — role must always be CUSTOMER regardless of what client sends
        assertThat(response.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(response.getRoles()).doesNotContain(Role.ADMIN, Role.TELLER);
    }

    @Test
    @DisplayName("Should never store raw password — always encode before saving")
    void shouldNeverStoreRawPassword() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByGovernmentId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(validRequest.getPassword()))
                .thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        registrationService.register(validRequest);

        // Assert — capture the User object passed to save and verify password is hashed
        verify(userRepository).save(argThat(user ->
                !user.getPassword().equals(validRequest.getPassword()) &&
                        user.getPassword().equals("$2a$10$hashedpassword")
        ));
    }
}