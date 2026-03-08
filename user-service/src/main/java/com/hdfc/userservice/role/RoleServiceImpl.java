package com.hdfc.userservice.role;

import com.hdfc.userservice.common.exception.UnauthorizedRoleAssignmentException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.role.dto.RoleAssignmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link IRoleService}.
 *
 * <p>Enforces the role assignment privilege matrix at the service level:
 * <ul>
 *   <li>ADMIN — can assign and revoke CUSTOMER, TELLER, ADMIN</li>
 *   <li>TELLER — can only assign and revoke CUSTOMER</li>
 *   <li>CUSTOMER — never reaches this service (blocked at SecurityConfig)</li>
 * </ul>
 *
 * <p>The controller enforces coarse-grained access (TELLER or ADMIN only).
 * This class enforces fine-grained access — Tellers cannot escalate
 * privileges beyond CUSTOMER. This separation satisfies SRP:
 * the controller handles HTTP concerns, the service handles business rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements IRoleService {

    private final UserRepository userRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void assignRole(Long actingUserId, Long targetUserId,
                           RoleAssignmentRequest request) {
        User actingUser = findUserById(actingUserId);
        User targetUser = findUserById(targetUserId);

        Role roleToAssign = request.getRole();

        // Fine-grained privilege check — enforced at service level.
        // Tellers can only assign CUSTOMER. Attempting to assign
        // TELLER or ADMIN throws UnauthorizedRoleAssignmentException.
        // Admins pass this check unconditionally.
        validatePrivilege(actingUser, roleToAssign);

        // Idempotent — assigning a role the user already holds is a no-op.
        // We still call save() to keep the behaviour consistent and testable.
        targetUser.getRoles().add(roleToAssign);
        userRepository.save(targetUser);

        log.info("User id: {} assigned role {} to user id: {}",
                actingUserId, roleToAssign, targetUserId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void revokeRole(Long actingUserId, Long targetUserId,
                           RoleAssignmentRequest request) {
        User actingUser = findUserById(actingUserId);
        User targetUser = findUserById(targetUserId);

        Role roleToRevoke = request.getRole();

        // Same privilege matrix applies to revocation as assignment.
        // A Teller cannot revoke TELLER or ADMIN roles — doing so
        // would allow a compromised Teller account to demote Admins.
        validatePrivilege(actingUser, roleToRevoke);

        // Guard — every user must hold at least one role at all times.
        // Revoking the last role would leave the user in an invalid state
        // where they are authenticated but have no permissions whatsoever.
        if (targetUser.getRoles().size() == 1 &&
                targetUser.getRoles().contains(roleToRevoke)) {
            throw new IllegalStateException(
                    "Cannot revoke the last role from user id: " +
                            targetUserId + ". A user must hold at least one role.");
        }

        // Idempotent — revoking a role the user does not hold is a no-op.
        // We still call save() to keep the behaviour consistent and testable.
        targetUser.getRoles().remove(roleToRevoke);
        userRepository.save(targetUser);

        log.info("User id: {} revoked role {} from user id: {}",
                actingUserId, roleToRevoke, targetUserId);
    }

    /**
     * Validates that the acting user has sufficient privilege to
     * assign or revoke the requested role.
     *
     * <p>Privilege matrix:
     * <ul>
     *   <li>ADMIN — passes all checks unconditionally</li>
     *   <li>TELLER — only permitted to operate on CUSTOMER role</li>
     * </ul>
     *
     * <p>This check is intentionally at the service level — not the
     * controller level. The controller only knows the caller is at
     * least a TELLER. The service knows the exact role being assigned
     * and can apply the fine-grained matrix. Satisfies SRP.
     *
     * @param actingUser   the user performing the operation
     * @param roleToAssign the role being assigned or revoked
     * @throws UnauthorizedRoleAssignmentException if the acting user
     *         does not have sufficient privilege for this role operation
     */
    private void validatePrivilege(User actingUser, Role roleToAssign) {
        // Admins are unconditionally permitted — short-circuit
        if (actingUser.getRoles().contains(Role.ADMIN)) {
            return;
        }

        // Tellers may only operate on the CUSTOMER role.
        // Any attempt to assign or revoke TELLER or ADMIN is rejected.
        if (!roleToAssign.equals(Role.CUSTOMER)) {
            log.warn("Teller user id: {} attempted unauthorized role " +
                    "assignment of role: {}", actingUser.getId(), roleToAssign);
            throw new UnauthorizedRoleAssignmentException(actingUser.getId());
        }
    }

    /**
     * Loads a user by ID — throws if not found.
     *
     * <p>Extracted as a private method to satisfy DRY — both
     * {@code assignRole} and {@code revokeRole} load two users each.
     * One place for the lookup and the not-found exception.
     *
     * @param userId the ID to look up
     * @return the found User entity
     * @throws UserNotFoundException if no user exists with this ID
     */
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "id: " + userId));
    }
}