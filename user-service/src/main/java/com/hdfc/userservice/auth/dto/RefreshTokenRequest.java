package com.hdfc.userservice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO carrying a refresh token submitted by the client.
 *
 * <p>Sent in the request body to {@code POST /api/v1/auth/refresh}.
 * The client submits the refresh token received during login or the
 * last refresh operation. If valid and present in Redis, a new
 * access token and refresh token are returned (token rotation).
 *
 * <p>Immutable after deserialization — no setters.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    /**
     * The refresh token previously issued during login or token refresh.
     * Must be present in Redis — expired or missing tokens are rejected.
     */
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}