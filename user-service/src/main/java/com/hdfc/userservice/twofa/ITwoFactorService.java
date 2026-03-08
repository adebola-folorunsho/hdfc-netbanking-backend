package com.hdfc.userservice.twofa;

import com.hdfc.userservice.twofa.dto.TwoFactorSetupResponse;
import com.hdfc.userservice.twofa.dto.TwoFactorVerifyRequest;

/**
 * Contract for Two-Factor Authentication (2FA) operations.
 *
 * <p>Defines the full 2FA lifecycle — setup initiation, setup verification,
 * login OTP validation, and disable. The controller depends on this
 * interface, never on the concrete implementation. Satisfies DIP and OCP.
 *
 * <p>Follows ISP — this interface covers only 2FA concerns.
 * Authentication and registration are in separate interfaces.
 *
 * <p>2FA flow:
 * <pre>
 * 1. User calls setup()     → receives secret + QR code URI
 *                             secret stored in Redis (10 min TTL)
 * 2. User scans QR code and calls verifySetup()
 *                           → TOTP code validated
 *                             secret written to MySQL
 *                             isTwoFactorEnabled = true
 * 3. On future logins, after password auth succeeds,
 *    validateOtp() is called → TOTP code validated against stored secret
 * 4. User calls disable()   → secret cleared from MySQL
 *                             isTwoFactorEnabled = false
 * </pre>
 */
public interface ITwoFactorService {

    /**
     * Initiates the 2FA setup flow for the given user.
     *
     * <p>Generates a new TOTP secret, stores it temporarily in Redis
     * under {@code user:2fa-setup:{userId}} with a 10-minute TTL,
     * and returns the secret and QR code URI for the user to scan.
     *
     * <p>The secret is NOT written to MySQL until the user successfully
     * verifies their first TOTP code via {@link #verifySetup}.
     *
     * @param userId the ID of the user initiating 2FA setup
     * @return a response containing the TOTP secret and QR code URI
     * @throws com.hdfc.userservice.common.exception.TwoFactorAuthException
     *         if 2FA is already enabled for this user
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     */
    TwoFactorSetupResponse setup(Long userId);

    /**
     * Completes the 2FA setup by verifying the first TOTP code.
     *
     * <p>Retrieves the setup secret from Redis, validates the submitted
     * TOTP code against it, then writes the secret to MySQL and sets
     * {@code isTwoFactorEnabled = true} on the user entity.
     *
     * @param userId  the ID of the user completing 2FA setup
     * @param request the TOTP verification request containing the 6-digit code
     * @throws com.hdfc.userservice.common.exception.InvalidOtpException
     *         if the TOTP code is invalid or the setup session has expired
     * @throws com.hdfc.userservice.common.exception.TwoFactorAuthException
     *         if 2FA is already enabled or setup was never initiated
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     */
    void verifySetup(Long userId, TwoFactorVerifyRequest request);

    /**
     * Validates a TOTP code during login 2FA verification.
     *
     * <p>Used after password authentication succeeds when the user has
     * 2FA enabled. Validates the submitted code against the TOTP secret
     * stored in MySQL. The OTP pending state is tracked in Redis under
     * {@code user:otp:{userId}} with a 30-second TTL.
     *
     * @param userId  the ID of the user submitting the TOTP code
     * @param request the TOTP verification request containing the 6-digit code
     * @throws com.hdfc.userservice.common.exception.InvalidOtpException
     *         if the TOTP code is invalid or expired
     * @throws com.hdfc.userservice.common.exception.TwoFactorAuthException
     *         if 2FA is not enabled for this user
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     */
    void validateOtp(Long userId, TwoFactorVerifyRequest request);

    /**
     * Disables 2FA for the given user.
     *
     * <p>Clears the TOTP secret from MySQL and sets
     * {@code isTwoFactorEnabled = false} on the user entity.
     *
     * @param userId the ID of the user disabling 2FA
     * @throws com.hdfc.userservice.common.exception.TwoFactorAuthException
     *         if 2FA is not currently enabled for this user
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if no user exists with the given ID
     */
    void disable(Long userId);
}