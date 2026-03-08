package com.hdfc.userservice.user.dto;

import com.hdfc.userservice.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Outbound DTO representing a user's profile.
 *
 * <p>Returned for both self-profile ({@code GET /api/v1/users/me})
 * and admin/teller profile lookup ({@code GET /api/v1/users/{userId}}).
 *
 * <p>Sensitive fields are deliberately excluded:
 * password, twoFactorSecret, governmentId are never returned in
 * any API response. This is a security invariant.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private Set<Role> roles;
    private boolean isEnabled;
    private boolean isKycVerified;
    private boolean isTwoFactorEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}