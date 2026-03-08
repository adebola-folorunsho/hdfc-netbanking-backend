package com.hdfc.userservice.role;

import com.hdfc.userservice.common.exception.UnauthorizedRoleAssignmentException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.role.dto.RoleAssignmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RoleServiceImpl}.
 *
 * <p>Tests role assignment and revocation — covering the full
 * privilege matrix, idempotency, and edge cases — in complete
 * isolation. All dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    private User adminUser;
    private User tellerUser;
    private User customerUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .fullName("Admin User")
                .email("admin@hdfc.com")
                .password("$2a$10$hashedpassword")
                .phoneNumber("08011111111")
                .address("Admin Street, Lagos")
                .governmentId("NGA-ADMIN-001")
                .roles(new HashSet<>(Set.of(Role.ADMIN)))
                .isEnabled(true)
                .isKycVerified(true)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        tellerUser = User.builder()
                .id(2L)
                .fullName("Teller User")
                .email("teller@hdfc.com")
                .password("$2a$10$hashedpassword")
                .phoneNumber("08022222222")
                .address("Teller Street, Lagos")
                .governmentId("NGA-TELLER-001")
                .roles(new HashSet<>(Set.of(Role.TELLER)))
                .isEnabled(true)
                .isKycVerified(true)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        customerUser = User.builder()
                .id(3L)
                .fullName("Customer User")
                .email("customer@hdfc.com")
                .password("$2a$10$hashedpassword")
                .phoneNumber("08033333333")
                .address("Customer Street, Lagos")
                .governmentId("NGA-CUSTOMER-001")
                .roles(new HashSet<>(Set.of(Role.CUSTOMER)))
                .isEnabled(true)
                .isKycVerified(false)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        targetUser = User.builder()
                .id(4L)
                .fullName("Target User")
                .email("target@hdfc.com")
                .password("$2a$10$hashedpassword")
                .phoneNumber("08044444444")
                .address("Target Street, Lagos")
                .governmentId("NGA-TARGET-001")
                .roles(new HashSet<>(Set.of(Role.CUSTOMER)))
                .isEnabled(true)
                .isKycVerified(false)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // ASSIGN ROLE — ADMIN TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin should assign TELLER role to a customer")
    void adminShouldAssignTellerRoleToCustomer() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act
        roleService.assignRole(1L, 4L,
                new RoleAssignmentRequest(Role.TELLER));

        // Assert — target user now has both CUSTOMER and TELLER roles
        verify(userRepository).save(argThat(user ->
                user.getRoles().contains(Role.TELLER) &&
                        user.getRoles().contains(Role.CUSTOMER)
        ));
    }

    @Test
    @DisplayName("Admin should assign ADMIN role to a user")
    void adminShouldAssignAdminRole() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act
        roleService.assignRole(1L, 4L,
                new RoleAssignmentRequest(Role.ADMIN));

        // Assert
        verify(userRepository).save(argThat(user ->
                user.getRoles().contains(Role.ADMIN)
        ));
    }

    // ─────────────────────────────────────────────────────────────
    // ASSIGN ROLE — TELLER TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Teller should assign CUSTOMER role successfully")
    void tellerShouldAssignCustomerRole() {
        // Arrange — target has no roles initially
        targetUser.getRoles().clear();
        when(userRepository.findById(2L)).thenReturn(Optional.of(tellerUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act
        roleService.assignRole(2L, 4L,
                new RoleAssignmentRequest(Role.CUSTOMER));

        // Assert
        verify(userRepository).save(argThat(user ->
                user.getRoles().contains(Role.CUSTOMER)
        ));
    }

    @Test
    @DisplayName("Teller should throw UnauthorizedRoleAssignmentException when assigning TELLER role")
    void tellerShouldThrowWhenAssigningTellerRole() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(tellerUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act & Assert
        assertThatThrownBy(() -> roleService.assignRole(2L, 4L,
                new RoleAssignmentRequest(Role.TELLER)))
                .isInstanceOf(UnauthorizedRoleAssignmentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Teller should throw UnauthorizedRoleAssignmentException when assigning ADMIN role")
    void tellerShouldThrowWhenAssigningAdminRole() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(tellerUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act & Assert
        assertThatThrownBy(() -> roleService.assignRole(2L, 4L,
                new RoleAssignmentRequest(Role.ADMIN)))
                .isInstanceOf(UnauthorizedRoleAssignmentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Assigning a role the user already holds should be a no-op")
    void assigningExistingRoleShouldBeNoOp() {
        // Arrange — target already has CUSTOMER
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act
        roleService.assignRole(1L, 4L,
                new RoleAssignmentRequest(Role.CUSTOMER));

        // Assert — save still called but roles unchanged
        verify(userRepository).save(argThat(user ->
                user.getRoles().size() == 1 &&
                        user.getRoles().contains(Role.CUSTOMER)
        ));
    }

    // ─────────────────────────────────────────────────────────────
    // REVOKE ROLE TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin should revoke TELLER role successfully")
    void adminShouldRevokeTellerRole() {
        // Arrange — target has both CUSTOMER and TELLER
        targetUser.getRoles().add(Role.TELLER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act
        roleService.revokeRole(1L, 4L,
                new RoleAssignmentRequest(Role.TELLER));

        // Assert — TELLER removed, CUSTOMER remains
        verify(userRepository).save(argThat(user ->
                !user.getRoles().contains(Role.TELLER) &&
                        user.getRoles().contains(Role.CUSTOMER)
        ));
    }

    @Test
    @DisplayName("Teller should throw UnauthorizedRoleAssignmentException when revoking TELLER role")
    void tellerShouldThrowWhenRevokingTellerRole() {
        // Arrange — target has TELLER role
        targetUser.getRoles().add(Role.TELLER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(tellerUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act & Assert
        assertThatThrownBy(() -> roleService.revokeRole(2L, 4L,
                new RoleAssignmentRequest(Role.TELLER)))
                .isInstanceOf(UnauthorizedRoleAssignmentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when revoking last role")
    void shouldThrowWhenRevokingLastRole() {
        // Arrange — target has only CUSTOMER role
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act & Assert — cannot leave user with no roles
        assertThatThrownBy(() -> roleService.revokeRole(1L, 4L,
                new RoleAssignmentRequest(Role.CUSTOMER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last role");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Revoking a role the user does not hold should be a no-op")
    void revokingNonExistentRoleShouldBeNoOp() {
        // Arrange — target does not have TELLER role
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

        // Act
        roleService.revokeRole(1L, 4L,
                new RoleAssignmentRequest(Role.TELLER));

        // Assert — save still called, roles unchanged
        verify(userRepository).save(argThat(user ->
                user.getRoles().size() == 1 &&
                        user.getRoles().contains(Role.CUSTOMER)
        ));
    }

    // ─────────────────────────────────────────────────────────────
    // USER NOT FOUND TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should throw UserNotFoundException when acting user not found")
    void shouldThrowWhenActingUserNotFound() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> roleService.assignRole(99L, 4L,
                new RoleAssignmentRequest(Role.CUSTOMER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when target user not found")
    void shouldThrowWhenTargetUserNotFound() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> roleService.assignRole(1L, 99L,
                new RoleAssignmentRequest(Role.CUSTOMER)))
                .isInstanceOf(UserNotFoundException.class);
    }
}