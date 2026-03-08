package com.hdfc.userservice.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO for updating a user's own profile.
 *
 * <p>Sent in the request body to {@code PUT /api/v1/users/me}.
 * Only name, address, and phone number are updatable by the user.
 * Email, password, roles, and KYC status are not updatable here —
 * each has its own dedicated endpoint with appropriate security controls.
 *
 * <p>Immutable after deserialization — no setters.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[0-9]{10,15}$",
            message = "Phone number must be between 10 and 15 digits"
    )
    private String phoneNumber;

    @NotBlank(message = "Address is required")
    @Size(min = 10, max = 255, message = "Address must be between 10 and 255 characters")
    private String address;
}