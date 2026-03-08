package com.hdfc.userservice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO carrying login credentials submitted by the client.
 *
 * <p>Sent in the request body to {@code POST /api/v1/auth/login}.
 * Contains only the two fields needed for authentication —
 * no other data is accepted or processed during login.
 *
 * <p>Immutable after deserialization — no setters.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    /**
     * The user's email address — their primary login identifier.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /**
     * The user's raw password — verified against the BCrypt hash
     * stored in the database. Never logged or stored anywhere.
     */
    @NotBlank(message = "Password is required")
    private String password;
}
