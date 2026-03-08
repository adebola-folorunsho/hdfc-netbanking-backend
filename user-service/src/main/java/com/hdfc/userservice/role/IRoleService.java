package com.hdfc.userservice.role;

import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.role.dto.RoleAssignmentRequest;

/**
 * Contract for role management operations in the User Service.
 *
 * <p>Defines role assignment and revocation. The acting user's role
 * is passed explicitly from the controller — the service enforces
 * the role assignment matrix as a business rule.
 *
 * <p>Role assignment matrix:
 * <ul>
 *   <li>{@code ADMIN} — can assign {@code CUSTOMER}, {@code TELLER}, {@code ADMIN}</li>
 *   <li>{@code TELLER} — can only assign {@code CUSTOMER}</li>
 *   <li>{@code CUSTOMER} — blocked at SecurityConfig, never reaches here</li>
 * </ul>
 *
 * <p>Follows ISP — this interface covers only role management concerns.
 * Authentication, registration, and 2FA are in separate interfaces.
 */
public interface IRoleService {

    /**
     * Assigns a role to the target user.
     *
     * <p>The acting user's role determines which roles they are
     * permitted to assign. A Teller attempting to assign TELLER
     * or ADMIN triggers {@link com.hdfc.userservice.common.exception.UnauthorizedRoleAssignmentException}.
     *
     * <p>If the target user already has the role, this operation
     * is idempotent — no exception is thrown, no change is made.
     *
     * @param actingUserId   the ID of the user performing the assignment
     * @param targetUserId   the ID of the user receiving the role
     * @param request        the role assignment request
     * @throws com.hdfc.userservice.common.exception.UnauthorizedRoleAssignmentException
     *         if the acting user does not have permission to assign this role
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if the target user does not exist
     */
    void assignRole(Long actingUserId, Long targetUserId,
                    RoleAssignmentRequest request);

    /**
     * Revokes a role from the target user.
     *
     * <p>Applies the same permission matrix as {@link #assignRole}.
     * A Teller cannot revoke TELLER or ADMIN roles.
     *
     * <p>A user must always retain at least one role — attempting to
     * revoke a user's last role throws
     * {@link com.hdfc.userservice.common.exception.UnauthorizedRoleAssignmentException}.
     *
     * @param actingUserId   the ID of the user performing the revocation
     * @param targetUserId   the ID of the user losing the role
     * @param request        the role assignment request
     * @throws com.hdfc.userservice.common.exception.UnauthorizedRoleAssignmentException
     *         if the acting user does not have permission to revoke this role,
     *         or if revoking would leave the target user with no roles
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if the target user does not exist
     */
    void revokeRole(Long actingUserId, Long targetUserId,
                    RoleAssignmentRequest request);
}