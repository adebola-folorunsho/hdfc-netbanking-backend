package com.hdfc.userservice.auth;

import com.hdfc.userservice.auth.dto.AuthRequest;
import com.hdfc.userservice.auth.dto.AuthResponse;
import com.hdfc.userservice.auth.dto.RefreshTokenRequest;
import com.hdfc.userservice.common.exception.InvalidTokenException;
import com.hdfc.userservice.common.exception.UserNotFoundException;
import com.hdfc.userservice.common.security.jwt.JwtService;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link IAuthService}.
 *
 * <p>Handles login, refresh token rotation, and logout.
 * Integrates Spring Security's {@link AuthenticationManager} for
 * credential verification, {@link JwtService} for token operations,
 * and Redis for refresh token storage.
 *
 * <p>Redis key patterns used (per architecture decision):
 * <ul>
 *   <li>{@code user:refresh:{userId}} — refresh token, TTL: 7 days</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    // TTL constants — named constants per the principles.
    // Never use magic numbers for time values.
    private static final long REFRESH_TOKEN_TTL_DAYS = 7L;
    private static final String REFRESH_TOKEN_KEY_PREFIX = "user:refresh:";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     *
     * <p>Delegates credential verification to Spring Security's
     * {@link AuthenticationManager} — which in turn calls
     * {@link com.hdfc.userservice.common.security.userdetails.UserDetailsServiceImpl}
     * and verifies the password with BCrypt. If authentication fails,
     * Spring throws {@link org.springframework.security.authentication.BadCredentialsException}
     * which propagates to the {@link com.hdfc.userservice.common.exception.GlobalExceptionHandler}.
     */
    @Override
    public AuthResponse login(AuthRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Delegate to Spring Security for credential verification.
        // If email or password is wrong, BadCredentialsException is thrown here.
        // If account is disabled, DisabledException is thrown here.
        // Both are caught by GlobalExceptionHandler.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Authentication succeeded — load the full User entity for token generation.
        // We need the entity (not just UserDetails) because JwtService.generateAccessToken
        // accepts a User and reads the roles directly from it.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(request.getEmail()));

        // Generate both tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Store refresh token in Redis — single source of truth for valid tokens.
        // Key: user:refresh:{userId}, TTL: 7 days
        storeRefreshToken(user.getId(), refreshToken);

        log.info("Login successful for user id: {}", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refresh token rotation — every refresh token is single-use only.
     * The submitted token is validated, checked against Redis, deleted,
     * and replaced with a fresh pair of tokens. This prevents replay attacks:
     * if an attacker steals a refresh token and tries to use it after the
     * legitimate user has already refreshed, the token will not be in Redis.
     */
    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String submittedToken = request.getRefreshToken();

        // Step 1 — Check token expiry before any Redis lookup.
        // Fail fast if the token is already expired.
        if (jwtService.isTokenExpired(submittedToken)) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        // Step 2 — Extract the email from the token to identify the user
        String email = jwtService.extractEmail(submittedToken);

        // Step 3 — Load the user from the database
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // Step 4 — Look up the stored refresh token in Redis
        String redisKey = buildRefreshTokenKey(user.getId());
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        // Step 5 — Reject if token not found in Redis.
        // This covers: already used tokens, logged-out users,
        // and tokens that have expired in Redis.
        if (storedToken == null) {
            log.warn("Refresh token not found in Redis for user id: {}",
                    user.getId());
            throw new InvalidTokenException(
                    "Refresh token not found — please login again");
        }

        // Step 6 — Reject if submitted token does not match stored token.
        // Prevents replay attacks where an attacker submits an older token
        // after the user has already refreshed with a newer one.
        if (!storedToken.equals(submittedToken)) {
            log.warn("Refresh token mismatch for user id: {} — possible replay attack",
                    user.getId());
            throw new InvalidTokenException(
                    "Refresh token mismatch — please login again");
        }

        // Step 7 — Delete the old refresh token immediately (rotation).
        // From this moment, the submitted token is invalid.
        // If this request fails after this point, the user must log in again.
        redisTemplate.delete(redisKey);

        // Step 8 — Generate fresh tokens
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Step 9 — Store the new refresh token in Redis
        storeRefreshToken(user.getId(), newRefreshToken);

        log.info("Token refresh successful for user id: {}", user.getId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deletes the refresh token from Redis. The access token remains
     * valid until its natural 15-minute expiry — acceptable given the
     * short TTL. For immediate access token invalidation, a token
     * blocklist would be required — flagged as a future enhancement.
     */
    @Override
    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        String redisKey = buildRefreshTokenKey(user.getId());
        redisTemplate.delete(redisKey);
        log.info("User id: {} logged out — refresh token deleted from Redis",
                user.getId());
    }

    /**
     * Stores a refresh token in Redis under the user's key with a 7-day TTL.
     *
     * <p>Extracted as a private method to satisfy DRY — both login and
     * refresh operations store tokens in exactly the same way.
     *
     * @param userId       the ID of the user who owns this token
     * @param refreshToken the refresh token string to store
     */
    private void storeRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                buildRefreshTokenKey(userId),
                refreshToken,
                REFRESH_TOKEN_TTL_DAYS,
                TimeUnit.DAYS
        );
    }

    /**
     * Builds the Redis key for a user's refresh token.
     *
     * <p>Key pattern: {@code user:refresh:{userId}}
     * Extracted as a private method — the key pattern is defined
     * in one place only. If the pattern ever changes, only this
     * method needs to be updated. DRY principle.
     *
     * @param userId the user's ID
     * @return the Redis key string
     */
    private String buildRefreshTokenKey(Long userId) {
        return REFRESH_TOKEN_KEY_PREFIX + userId;
    }
}