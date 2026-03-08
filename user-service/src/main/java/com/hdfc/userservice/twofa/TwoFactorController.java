package com.hdfc.userservice.twofa;

import com.hdfc.userservice.common.response.ApiResponse;
import com.hdfc.userservice.twofa.dto.TwoFactorSetupResponse;
import com.hdfc.userservice.twofa.dto.TwoFactorVerifyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Two-Factor Authentication (2FA) operations.
 *
 * <p>All endpoints require authentication — the user must send a valid
 * access token. The authenticated user's ID is resolved from the
 * security context via {@link UserDetails}, never trusted from the
 * request body.
 *
 * <p>Base path: {@code /api/v1/2fa}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final ITwoFactorService twoFactorService;
    private final com.hdfc.userservice.domain.UserRepository userRepository;

    /**
     * Initiates the 2FA setup flow for the authenticated user.
     *
     * <p>Returns a TOTP secret and QR code URI. The user scans the QR
     * code with their authenticator app then calls {@code /verify} to
     * complete setup.
     *
     * @param userDetails the authenticated user from the security context
     * @return 200 OK with the TOTP secret and QR code URI
     */
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails.getUsername());
        TwoFactorSetupResponse response = twoFactorService.setup(userId);
        return ResponseEntity.ok(
                ApiResponse.success("2FA setup initiated. " +
                                "Scan the QR code and verify with your authenticator app.",
                        response));
    }

    /**
     * Completes 2FA setup by verifying the first TOTP code.
     *
     * <p>The user submits the 6-digit code from their authenticator app.
     * If valid, the secret is written to MySQL and 2FA is enabled.
     *
     * @param userDetails the authenticated user from the security context
     * @param request     the TOTP verification request
     * @return 200 OK confirming 2FA is now enabled
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifySetup(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        Long userId = resolveUserId(userDetails.getUsername());
        twoFactorService.verifySetup(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "2FA has been enabled successfully on your account"));
    }

    /**
     * Validates a TOTP code during login 2FA verification.
     *
     * <p>Called after password authentication succeeds when the user
     * has 2FA enabled. If the code is valid, the client proceeds to
     * receive their JWT tokens.
     *
     * @param userDetails the authenticated user from the security context
     * @param request     the TOTP verification request
     * @return 200 OK confirming the OTP is valid
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Void>> validateOtp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        Long userId = resolveUserId(userDetails.getUsername());
        twoFactorService.validateOtp(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("OTP validated successfully"));
    }

    /**
     * Disables 2FA for the authenticated user.
     *
     * <p>Clears the TOTP secret from MySQL and sets
     * {@code isTwoFactorEnabled = false}.
     *
     * @param userDetails the authenticated user from the security context
     * @return 200 OK confirming 2FA is now disabled
     */
    @DeleteMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails.getUsername());
        twoFactorService.disable(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "2FA has been disabled on your account"));
    }

    /**
     * Resolves the authenticated user's ID from their email address.
     *
     * <p>Spring Security's {@link UserDetails#getUsername()} returns
     * the email in our system. We need the userId for the service layer.
     * This lookup is the only logic permitted in the controller —
     * it is pure identity resolution, not business logic.
     *
     * @param email the authenticated user's email from the security context
     * @return the user's database ID
     * @throws com.hdfc.userservice.common.exception.UserNotFoundException
     *         if the email cannot be resolved to a user
     */
    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new com.hdfc.userservice.common.exception
                        .UserNotFoundException(email))
                .getId();
    }
}