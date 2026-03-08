package com.hdfc.userservice.role.dto;

import com.hdfc.userservice.domain.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO carrying the role to assign or revoke for a target user.
 *
 * <p>Sent in the request body to:
 * <ul>
 *   <li>{@code POST /api/v1/roles/{targetUserId}/assign}</li>
 *   <li>{@code DELETE /api/v1/roles/{targetUserId}/revoke}</li>
 * </ul>
 *
 * <p>The target user's ID is taken from the path variable — not from
 * this DTO. This DTO carries only the role to act upon.
 *
 * <p>Immutable after deserialization — no setters.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignmentRequest {

    /**
     * The role to assign or revoke.
     * Must be a valid {@link Role} enum value: CUSTOMER, TELLER, or ADMIN.
     * Cannot be null — enforced by Bean Validation before reaching the service.
     */
    @NotNull(message = "Role must not be null")
    private Role role;
}