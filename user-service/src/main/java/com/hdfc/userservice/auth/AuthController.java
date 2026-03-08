package com.hdfc.userservice.auth;

import com.hdfc.userservice.auth.dto.AuthRequest;
import com.hdfc.userservice.auth.dto.AuthResponse;
import com.hdfc.userservice.auth.dto.RefreshTokenRequest;
import com.hdfc.userservice.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations.
 *
 * <p>Exposes login, token refresh, and logout endpoints.
 * All endpoints under {@code /api/v1/auth/**} are public —
 * permitted without authentication in SecurityConfig.
 *
 * <p>This controller's sole responsibility is to receive HTTP requests,
 * delegate to {@link IAuthService}, and return HTTP responses.
 * No business logic lives here — satisfies SRP.
 *
 * <p>Base path: {@code /api/v1/auth}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    /**
     * Authenticates a user and returns JWT access and refresh tokens.
     *
     * <p>Public endpoint — no authentication required.
     * On success, returns 200 OK with both tokens.
     * On failure, returns 401 (bad credentials) or 403 (disabled account).
     *
     * @param request the login credentials
     * @return 200 OK with access and refresh tokens
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request) {

        log.info("Login request received for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Login successful", response));
    }

    /**
     * Refreshes an expired access token using a valid refresh token.
     *
     * <p>Public endpoint — the refresh token itself is the credential.
     * Implements single-use token rotation — the submitted refresh token
     * is invalidated and replaced with a new one.
     *
     * @param request the refresh token request
     * @return 200 OK with new access and refresh tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", response));
    }

    /**
     * Logs out the currently authenticated user.
     *
     * <p>Requires authentication — the user must send a valid access token.
     * Deletes the refresh token from Redis, preventing further token refreshes.
     * The access token remains valid until its natural 15-minute expiry.
     *
     * <p>{@code @AuthenticationPrincipal} injects the currently authenticated
     * user's {@link UserDetails} directly from the Spring Security context —
     * populated by {@link com.hdfc.userservice.common.security.jwt.JwtAuthenticationFilter}.
     * We never trust a userId sent in the request body — always read it
     * from the security context.
     *
     * @param userDetails the authenticated user injected from security context
     * @return 200 OK confirming logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails) {

        // Load the full user to get the ID — needed for the Redis key.
        // We extract the email from UserDetails (the username in our system)
        // and use it to look up the userId for the Redis key.
        // This is done in the service layer — the controller only passes
        // the email from the security context.
        log.info("Logout request for user: {}", userDetails.getUsername());

        // AuthServiceImpl.logout() accepts userId — we need to resolve it.
        // We delegate email-to-id resolution to the service to keep
        // the controller free of any business logic.
        authService.logout(userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully"));
    }
}