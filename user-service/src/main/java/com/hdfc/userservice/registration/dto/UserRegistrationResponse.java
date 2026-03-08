package com.hdfc.userservice.registration.dto;

import com.hdfc.userservice.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Outbound DTO returned to the client after successful user registration.
 *
 * <p>Returned in the {@code data} field of an {@link com.hdfc.userservice.common.response.ApiResponse}
 * on a successful {@code POST /api/v1/users/register} request.
 *
 * <p>Contains only the data the client needs to confirm registration —
 * never the password, government ID, or 2FA secret. This satisfies the
 * principle of minimal data exposure: only return what the caller needs.
 *
 * <p>The JPA {@link com.hdfc.userservice.domain.User} entity is never
 * returned directly — always mapped to this DTO first. This decouples
 * the API contract from the persistence model. If the entity changes
 * internally, the API response remains stable.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationResponse {

    /**
     * The auto-generated ID of the newly created user.
     * Clients use this to reference the user in subsequent requests.
     */
    private Long id;

    /**
     * The user's full name as stored after registration.
     */
    private String fullName;

    /**
     * The user's email address — their primary login identifier.
     */
    private String email;

    /**
     * The user's phone number as stored after registration.
     */
    private String phoneNumber;

    /**
     * The roles assigned to the new user.
     * Always contains {@link Role#CUSTOMER} for newly registered users —
     * role assignment by clients is not permitted during registration.
     */
    private Set<Role> roles;

    /**
     * Whether the user account is currently active.
     * Always {@code true} for newly registered users.
     */
    private boolean isEnabled;

    /**
     * Whether KYC verification has been completed.
     * Always {@code false} at registration — KYC is a separate step.
     */
    private boolean isKycVerified;

    /**
     * The timestamp when this user account was created.
     * Useful for the client to confirm the registration time.
     */
    private LocalDateTime createdAt;
}