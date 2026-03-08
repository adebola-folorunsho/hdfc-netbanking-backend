package com.hdfc.userservice.twofa;

import com.hdfc.userservice.common.exception.InvalidOtpException;
import com.hdfc.userservice.common.exception.TwoFactorAuthException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.twofa.dto.TwoFactorSetupResponse;
import com.hdfc.userservice.twofa.dto.TwoFactorVerifyRequest;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link ITwoFactorService}.
 *
 * <p>Handles the full 2FA lifecycle using the Google Authenticator
 * library (RFC 6238 TOTP). Setup secrets are stored temporarily in
 * Redis before being committed to MySQL after successful verification.
 *
 * <p>Redis key patterns used (per architecture decision):
 * <ul>
 *   <li>{@code user:2fa-setup:{userId}} — setup secret, TTL: 10 minutes</li>
 *   <li>{@code user:otp:{userId}} — login OTP pending, TTL: 30 seconds</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorServiceImpl implements ITwoFactorService {

    // Named constants — never magic numbers or strings per the principles
    private static final long SETUP_SECRET_TTL_MINUTES = 10L;
    private static final long OTP_TTL_SECONDS = 30L;
    private static final String SETUP_KEY_PREFIX = "user:2fa-setup:";
    private static final String OTP_KEY_PREFIX = "user:otp:";

    // Issuer name shown in authenticator apps alongside the account
    private static final String TOTP_ISSUER = "HDFC NetBanking";

    private final UserRepository userRepository;
    private final GoogleAuthenticator googleAuthenticator;
    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public TwoFactorSetupResponse setup(Long userId) {
        User user = findUserById(userId);

        // Guard — cannot initiate setup if 2FA is already enabled
        if (user.isTwoFactorEnabled()) {
            throw new TwoFactorAuthException(
                    "2FA is already enabled for this account");
        }

        // Generate a new TOTP secret key using the Google Authenticator library
        GoogleAuthenticatorKey credentials =
                googleAuthenticator.createCredentials();
        String secret = credentials.getKey();

        // Store the secret temporarily in Redis — NOT in MySQL yet.
        // Only written to MySQL after the user verifies their first code.
        // If the user abandons setup, Redis TTL handles cleanup automatically.
        redisTemplate.opsForValue().set(
                buildSetupKey(userId),
                secret,
                SETUP_SECRET_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        // Build the otpauth:// URI manually — compatible with Google Authenticator,
        // Authy, Microsoft Authenticator, and all RFC 6238 TOTP-compliant apps.
        // Format: otpauth://totp/{issuer}:{email}?secret={secret}&issuer={issuer}
        String qrCodeUri = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                TOTP_ISSUER,
                user.getEmail(),
                secret,
                TOTP_ISSUER
        );

        log.info("2FA setup initiated for user id: {}", userId);

        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(qrCodeUri)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void verifySetup(Long userId, TwoFactorVerifyRequest request) {
        User user = findUserById(userId);

        // Retrieve the setup secret from Redis
        String setupKey = buildSetupKey(userId);
        String secret = redisTemplate.opsForValue().get(setupKey);

        // Reject if secret not found — setup session expired or never initiated
        if (secret == null) {
            throw new InvalidOtpException(
                    "2FA setup session has expired — please restart setup");
        }

        // Validate the submitted TOTP code against the secret
        int code = parseOtpCode(request.getCode());
        boolean isValid = googleAuthenticator.authorize(secret, code);

        if (!isValid) {
            throw new InvalidOtpException();
        }

        // Code is valid — write the secret to MySQL and enable 2FA
        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        // Clean up the setup key from Redis — no longer needed
        redisTemplate.delete(setupKey);

        log.info("2FA setup completed successfully for user id: {}", userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateOtp(Long userId, TwoFactorVerifyRequest request) {
        User user = findUserById(userId);

        // Guard — cannot validate OTP if 2FA is not enabled
        if (!user.isTwoFactorEnabled()) {
            throw new TwoFactorAuthException(
                    "2FA is not enabled for this account");
        }

        // Validate the submitted code against the secret stored in MySQL
        int code = parseOtpCode(request.getCode());
        boolean isValid = googleAuthenticator.authorize(
                user.getTwoFactorSecret(), code);

        if (!isValid) {
            log.warn("Invalid OTP submitted for user id: {}", userId);
            throw new InvalidOtpException();
        }

        log.info("OTP validated successfully for user id: {}", userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void disable(Long userId) {
        User user = findUserById(userId);

        // Guard — cannot disable 2FA if it is not currently enabled
        if (!user.isTwoFactorEnabled()) {
            throw new TwoFactorAuthException(
                    "2FA is not enabled for this account");
        }

        // Clear the secret and disable 2FA
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);

        log.info("2FA disabled for user id: {}", userId);
    }

    /**
     * Loads a user by ID — throws if not found.
     *
     * <p>Extracted as a private method to satisfy DRY — every public
     * method in this service needs to load the user first. One place
     * for the lookup and the not-found exception.
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

    /**
     * Parses a 6-digit OTP string into an integer for the TOTP library.
     *
     * <p>The Google Authenticator library's {@code authorize()} method
     * accepts an {@code int} — we parse here rather than in the caller
     * to keep parsing logic in one place. DRY principle.
     *
     * @param code the 6-digit OTP string
     * @return the parsed integer OTP code
     * @throws InvalidOtpException if the code cannot be parsed as an integer
     */
    private int parseOtpCode(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException ex) {
            throw new InvalidOtpException();
        }
    }

    /**
     * Builds the Redis key for a user's 2FA setup secret.
     * Key pattern: {@code user:2fa-setup:{userId}}
     *
     * @param userId the user's ID
     * @return the Redis key string
     */
    private String buildSetupKey(Long userId) {
        return SETUP_KEY_PREFIX + userId;
    }

    /**
     * Builds the Redis key for a user's pending login OTP.
     * Key pattern: {@code user:otp:{userId}}
     *
     * @param userId the user's ID
     * @return the Redis key string
     */
    private String buildOtpKey(Long userId) {
        return OTP_KEY_PREFIX + userId;
    }
}