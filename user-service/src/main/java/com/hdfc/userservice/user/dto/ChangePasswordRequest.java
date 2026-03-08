package com.hdfc.userservice.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO for changing a user's password.
 *
 * <p>Sent in the request body to {@code POST /api/v1/users/me/change-password}.
 * Requires the current password for verification before the new password
 * is accepted — prevents unauthorized password changes if a session
 * is compromised.
 *
 * <p>On success, the user's refresh token is invalidated in Redis —
 * forcing a fresh login with the new password.
 *
 * <p>Immutable after deserialization — no setters.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    /**
     * The user's current password — verified against the BCrypt hash
     * in the database before the new password is accepted.
     */
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    /**
     * The new password to set. Must meet the same strength requirements
     * as registration — min 8 chars, uppercase, lowercase, digit, special char.
     */
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "New password must contain at least one uppercase letter, " +
                    "one lowercase letter, one digit, and one special character"
    )
    private String newPassword;
}