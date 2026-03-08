package com.hdfc.userservice.user;

import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.common.response.ApiResponse;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.user.dto.ChangePasswordRequest;
import com.hdfc.userservice.user.dto.UpdateProfileRequest;
import com.hdfc.userservice.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user profile management.
 *
 * <p>Exposes endpoints for self-profile access, profile updates,
 * password changes, admin profile lookup, and KYC verification.
 *
 * <p>Coarse-grained access is enforced by SecurityConfig.
 * Method-level fine-grained access uses {@code @PreAuthorize}
 * where endpoint-specific role restrictions apply beyond what
 * SecurityConfig already enforces globally.
 *
 * <p>Base path: {@code /api/v1/users}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final UserRepository userRepository;

    /**
     * Returns the authenticated user's own profile.
     *
     * @param userDetails the authenticated user from the security context
     * @return 200 OK with the user's profile
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getOwnProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails.getUsername());
        UserProfileResponse response = userService.getOwnProfile(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Profile retrieved successfully",
                        response));
    }

    /**
     * Updates the authenticated user's own profile.
     *
     * <p>Only fullName, phoneNumber, and address are updatable.
     *
     * @param userDetails the authenticated user from the security context
     * @param request     the profile update request
     * @return 200 OK with the updated profile
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateOwnProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {

        Long userId = resolveUserId(userDetails.getUsername());
        UserProfileResponse response =
                userService.updateOwnProfile(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Profile updated successfully",
                        response));
    }

    /**
     * Changes the authenticated user's password.
     *
     * <p>Requires current password verification. On success,
     * invalidates the refresh token in Redis.
     *
     * @param userDetails the authenticated user from the security context
     * @param request     the change password request
     * @return 200 OK confirming the password was changed
     */
    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        Long userId = resolveUserId(userDetails.getUsername());
        userService.changePassword(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Password changed successfully. " +
                                "Please login again with your new password."));
    }

    /**
     * Returns any user's profile — Admin or Teller only.
     *
     * <p>{@code @PreAuthorize} enforces TELLER or ADMIN here because
     * this endpoint shares the {@code /api/v1/users} base path with
     * the self-profile endpoints which are open to all authenticated
     * users. SecurityConfig cannot distinguish between them at the
     * path level alone.
     *
     * @param targetUserId the ID of the user to look up
     * @return 200 OK with the target user's profile
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable("userId") Long targetUserId) {

        UserProfileResponse response =
                userService.getUserProfile(targetUserId);
        return ResponseEntity.ok(
                ApiResponse.success("User profile retrieved successfully",
                        response));
    }

    /**
     * Marks a user as KYC verified — Admin only.
     *
     * <p>{@code @PreAuthorize} enforces ADMIN here for the same reason
     * as {@code getUserProfile} — path-level SecurityConfig cannot
     * distinguish this endpoint from the self-profile endpoints.
     *
     * @param targetUserId the ID of the user to mark as KYC verified
     * @return 200 OK confirming KYC verification
     */
    @PutMapping("/{userId}/kyc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> verifyKyc(
            @PathVariable("userId") Long targetUserId) {

        userService.verifyKyc(targetUserId);
        return ResponseEntity.ok(
                ApiResponse.success("KYC verification completed " +
                        "successfully for user id: " + targetUserId));
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