package com.hdfc.userservice.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO carrying the data a client submits to register a new user.
 *
 * <p>This object is what the client sends in the request body to
 * {@code POST /api/v1/users/register}. Bean Validation annotations
 * enforce all constraints before the request reaches the service layer —
 * the service never receives invalid data.
 *
 * <p>This is a pure data carrier — no business logic lives here.
 * Validation annotations define WHAT is valid. The service defines
 * WHY something is rejected (e.g. duplicate email).
 *
 * <p>Immutable by design — no setters. Once deserialized from the
 * request body by Jackson, this object is never modified.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {

    /**
     * User's full legal name as required for KYC.
     * Must be between 2 and 100 characters.
     */
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    /**
     * Email address — used as the primary login identifier.
     * Must be a valid email format and is unique across all users.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /**
     * Raw password submitted by the user.
     * Must be at least 8 characters and contain at least one uppercase
     * letter, one lowercase letter, one digit, and one special character.
     * Never stored — always hashed with BCrypt before persistence.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain at least one uppercase letter, " +
                    "one lowercase letter, one digit, and one special character"
    )
    private String password;

    /**
     * Phone number for KYC and account notifications.
     * Must be a valid international format — digits only, 10 to 15 characters.
     * Unique across all users.
     */
    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[0-9]{10,15}$",
            message = "Phone number must contain only digits and be between 10 and 15 characters"
    )
    private String phoneNumber;

    /**
     * Physical address for KYC verification.
     * Must be between 10 and 255 characters.
     */
    @NotBlank(message = "Address is required")
    @Size(min = 10, max = 255, message = "Address must be between 10 and 255 characters")
    private String address;

    /**
     * Government-issued ID number for KYC identity verification.
     * Must be between 5 and 50 characters. Unique across all users —
     * prevents one person from registering multiple accounts.
     */
    @NotBlank(message = "Government ID is required")
    @Size(min = 5, max = 50, message = "Government ID must be between 5 and 50 characters")
    private String governmentId;
}