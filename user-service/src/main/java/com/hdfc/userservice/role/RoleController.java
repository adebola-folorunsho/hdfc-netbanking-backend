package com.hdfc.userservice.role;

import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.common.response.ApiResponse;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.role.dto.RoleAssignmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for role assignment and revocation.
 *
 * <p>All endpoints are under {@code /api/v1/teller/**} or
 * {@code /api/v1/admin/**} — enforced by SecurityConfig to require
 * at least TELLER or ADMIN role. Fine-grained privilege checks
 * (e.g. Tellers cannot assign TELLER or ADMIN) are enforced in
 * {@link RoleServiceImpl}, not here.
 *
 * <p>Base path: {@code /api/v1/roles}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final IRoleService roleService;
    private final UserRepository userRepository;

    /**
     * Assigns a role to a target user.
     *
     * <p>The acting user's ID is resolved from the security context —
     * never trusted from the request body. The target user's ID is
     * taken from the path variable.
     *
     * @param targetUserId the ID of the user receiving the role
     * @param userDetails  the authenticated acting user
     * @param request      the role assignment request
     * @return 200 OK confirming the role was assigned
     */
    @PostMapping("/{targetUserId}/assign")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RoleAssignmentRequest request) {

        Long actingUserId = resolveUserId(userDetails.getUsername());
        roleService.assignRole(actingUserId, targetUserId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Role assigned successfully"));
    }

    /**
     * Revokes a role from a target user.
     *
     * <p>The acting user's ID is resolved from the security context —
     * never trusted from the request body. The target user's ID is
     * taken from the path variable.
     *
     * @param targetUserId the ID of the user losing the role
     * @param userDetails  the authenticated acting user
     * @param request      the role assignment request carrying the role to revoke
     * @return 200 OK confirming the role was revoked
     */
    @DeleteMapping("/{targetUserId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeRole(
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RoleAssignmentRequest request) {

        Long actingUserId = resolveUserId(userDetails.getUsername());
        roleService.revokeRole(actingUserId, targetUserId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Role revoked successfully"));
    }

    /**
     * Resolves the authenticated user's ID from their email address.
     *
     * @param email the authenticated user's email from the security context
     * @return the user's database ID
     * @throws UserNotFoundException if the email cannot be resolved
     */
    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email))
                .getId();
    }
}