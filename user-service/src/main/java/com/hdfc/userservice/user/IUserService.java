package com.hdfc.userservice.user;

import com.hdfc.userservice.user.dto.ChangePasswordRequest;
import com.hdfc.userservice.user.dto.UpdateProfileRequest;
import com.hdfc.userservice.user.dto.UserProfileResponse;

/**
 * Contract for user profile management operations.
 *
 * <p>Defines self-profile access, profile updates, password changes,
 * admin profile lookup, and KYC verification. The controller depends
 * on this interface — never on the concrete implementation.
 * Satisfies DIP and OCP.
 *
 * <p>Follows ISP — this interface covers only profile management.
 * Authentication, 2FA, and role management are in separate interfaces.
 */
public interface IUserService {

    /**
     * Retrieves the authenticated user's own profile.
     *
     * @param userId the ID of the authenticated user
     * @return the user's profile response DTO
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     */
    UserProfileResponse getOwnProfile(Long userId);

    /**
     * Updates the authenticated user's own profile.
     *
     * <p>Only fullName, phoneNumber, and address are updatable.
     * Email, password, roles, and KYC status are not changed here.
     *
     * @param userId  the ID of the authenticated user
     * @param request the profile update request
     * @return the updated profile response DTO
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     * @throws com.hdfc.userservice.common.exception.DuplicateUserException
     *         if the new phone number is already taken by another user
     */
    UserProfileResponse updateOwnProfile(Long userId,
                                         UpdateProfileRequest request);

    /**
     * Changes the authenticated user's password.
     *
     * <p>Verifies the current password before accepting the new one.
     * On success, invalidates the user's refresh token in Redis —
     * forcing a fresh login with the new credentials.
     *
     * @param userId  the ID of the authenticated user
     * @param request the change password request
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         if the current password is incorrect
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * Retrieves any user's profile — Admin or Teller only.
     *
     * @param targetUserId the ID of the user to look up
     * @return the target user's profile response DTO
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     */
    UserProfileResponse getUserProfile(Long targetUserId);

    /**
     * Marks a user as KYC verified — Admin only.
     *
     * <p>Sets {@code isKycVerified = true} on the target user entity.
     * KYC is a compliance and regulatory action — only Admins may
     * perform this operation.
     *
     * @param targetUserId the ID of the user to mark as KYC verified
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     * @throws com.hdfc.userservice.common.exception.KycVerificationException
     *         if the user is already KYC verified
     */
    void verifyKyc(Long targetUserId);
}