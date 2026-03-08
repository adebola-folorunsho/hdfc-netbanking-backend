package com.hdfc.userservice.user;

import com.hdfc.userservice.common.exception.DuplicateUserException;
import com.hdfc.userservice.common.exception.KycVerificationException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.user.dto.ChangePasswordRequest;
import com.hdfc.userservice.user.dto.UpdateProfileRequest;
import com.hdfc.userservice.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link IUserService}.
 *
 * <p>Handles user profile retrieval, profile updates, password changes,
 * admin profile lookup, and KYC verification.
 *
 * <p>Redis key patterns used (per architecture decision):
 * <ul>
 *   <li>{@code user:refresh:{userId}} — invalidated on password change</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "user:refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public UserProfileResponse getOwnProfile(Long userId) {
        User user = findUserById(userId);
        log.info("Profile retrieved for user id: {}", userId);
        return mapToProfileResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public UserProfileResponse updateOwnProfile(Long userId,
                                                UpdateProfileRequest request) {
        User user = findUserById(userId);

        // Check for duplicate phone number only if the user is
        // changing their phone number — not if they are keeping the same one.
        // Without this check, the user would be blocked from updating
        // their own profile because their current phone number already
        // "exists" in the database.
        if (!user.getPhoneNumber().equals(request.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                throw new DuplicateUserException(
                        "phoneNumber", request.getPhoneNumber());
            }
        }

        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAddress(request.getAddress());

        User savedUser = userRepository.save(user);
        log.info("Profile updated for user id: {}", userId);
        return mapToProfileResponse(savedUser);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Password change flow:
     * <ol>
     *   <li>Verify current password against BCrypt hash</li>
     *   <li>Hash the new password</li>
     *   <li>Save the new hash to MySQL</li>
     *   <li>Invalidate the refresh token in Redis — forcing fresh login</li>
     * </ol>
     *
     * <p>Step 4 is critical — without it, a compromised session could
     * continue to refresh tokens indefinitely after a password change.
     * The access token expires naturally in 15 minutes.
     */
    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserById(userId);

        // Verify current password — reject immediately if wrong.
        // Never give a hint about which field is wrong — BadCredentialsException
        // is intentionally vague to prevent enumeration attacks.
        if (!passwordEncoder.matches(request.getCurrentPassword(),
                user.getPassword())) {
            log.warn("Password change failed — wrong current password " +
                    "for user id: {}", userId);
            throw new BadCredentialsException("Current password is incorrect");
        }

        // Hash and save the new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate refresh token in Redis — forces fresh login.
        // The access token remains valid for up to 15 more minutes —
        // acceptable given the short TTL.
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);

        log.info("Password changed successfully for user id: {}. " +
                "Refresh token invalidated.", userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserProfileResponse getUserProfile(Long targetUserId) {
        User user = findUserById(targetUserId);
        log.info("Admin/Teller profile lookup for user id: {}", targetUserId);
        return mapToProfileResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void verifyKyc(Long targetUserId) {
        User user = findUserById(targetUserId);

        // Guard — idempotent KYC is confusing from a compliance perspective.
        // If a user is already KYC verified, re-verifying them could mask
        // a data integrity issue. Throw to force the Admin to investigate.
        if (user.isKycVerified()) {
            throw new KycVerificationException(
                    "User id: " + targetUserId +
                            " is already KYC verified");
        }

        user.setKycVerified(true);
        userRepository.save(user);

        log.info("KYC verified for user id: {}", targetUserId);
    }

    /**
     * Maps a {@link User} entity to a {@link UserProfileResponse} DTO.
     *
     * <p>Sensitive fields are deliberately excluded — password,
     * twoFactorSecret, and governmentId are never returned in any
     * API response. This is a security invariant enforced here.
     *
     * @param user the user entity to map
     * @return the profile response DTO
     */
    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .roles(user.getRoles())
                .isEnabled(user.isEnabled())
                .isKycVerified(user.isKycVerified())
                .isTwoFactorEnabled(user.isTwoFactorEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Loads a user by ID — throws if not found.
     *
     * <p>Extracted as a private method to satisfy DRY — every public
     * method in this service needs to load the user first.
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