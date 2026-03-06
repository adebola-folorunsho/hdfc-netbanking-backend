package com.hdfc.userservice.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound DTO returned to the client after successful authentication.
 *
 * <p>Returned in the {@code data} field of an
 * {@link com.hdfc.userservice.common.response.ApiResponse} on a
 * successful {@code POST /api/v1/auth/login} or
 * {@code POST /api/v1/auth/refresh} request.
 *
 * <p>Contains both the access token and refresh token.
 * The client stores the refresh token securely and uses it to
 * obtain new access tokens when the current one expires (15 minutes).
 * The refresh token itself expires after 7 days.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Short-lived JWT access token — expires in 15 minutes.
     * Sent in the Authorization header as: {@code Bearer <accessToken>}
     * on every subsequent authenticated request.
     */
    private String accessToken;

    /**
     * Long-lived JWT refresh token — expires in 7 days.
     * Stored in Redis under key {@code user:refresh:{userId}}.
     * Single-use — invalidated immediately when used to obtain
     * a new access token (refresh token rotation).
     */
    private String refreshToken;

    /**
     * The type of token — always "Bearer" for JWT.
     * Included so clients can construct the Authorization header correctly
     * without hardcoding the token type on the client side.
     */
    @Builder.Default
    private String tokenType = "Bearer";
}