package com.hdfc.userservice.twofa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound DTO returned after a successful 2FA setup initiation.
 *
 * <p>Returned in the {@code data} field of an
 * {@link com.hdfc.userservice.common.response.ApiResponse} on a
 * successful {@code POST /api/v1/2fa/setup} request.
 *
 * <p>The client uses the {@code qrCodeUri} to display a scannable
 * QR code in the UI. The user scans it with Google Authenticator,
 * Authy, or any RFC 6238 TOTP-compatible app. The {@code secret}
 * is included for users who prefer manual entry over QR scanning.
 *
 * <p>The setup secret is stored temporarily in Redis under
 * {@code user:2fa-setup:{userId}} with a 10-minute TTL. It is only
 * written to MySQL after the user successfully verifies the first
 * TOTP code via {@code POST /api/v1/2fa/verify}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorSetupResponse {

    /**
     * The Base32-encoded TOTP secret key generated for this user.
     * Used by authenticator apps to generate time-based 6-digit codes.
     * Displayed to the user for manual entry if QR scanning is not possible.
     */
    private String secret;

    /**
     * The otpauth:// URI encoding the secret in QR-scannable format.
     *
     * <p>Format: {@code otpauth://totp/{issuer}:{email}?secret={secret}&issuer={issuer}}
     * Compatible with Google Authenticator, Authy, Microsoft Authenticator,
     * and all RFC 6238 TOTP-compliant apps.
     */
    private String qrCodeUri;
}