package com.hdfc.userservice.common.exception;

import com.hdfc.userservice.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler for the User Service.
 *
 * <p>Intercepts all exceptions thrown anywhere in the service and maps
 * them to structured {@link ApiResponse} JSON responses with the correct
 * HTTP status code. This is the single place where exceptions become
 * HTTP responses — no controller or service class handles this directly.
 *
 * <p>This satisfies SRP — exception-to-HTTP mapping is this class's
 * sole responsibility. It also satisfies OCP — adding a new exception
 * type requires only adding a new {@code @ExceptionHandler} method here,
 * never modifying existing handlers.
 *
 * <p>Uses {@code @RestControllerAdvice} rather than {@code @ControllerAdvice}
 * because all responses are JSON — there are no view-based responses
 * in this REST service.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles user not found errors.
     * Returns 404 when a requested user does not exist.
     *
     * @param ex the exception carrying the lookup identifier
     * @return 404 Not Found with error message
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(
            UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles duplicate registration attempts.
     * Returns 409 when email, phone number, or government ID already exists.
     *
     * @param ex the exception carrying the conflicting field and value
     * @return 409 Conflict with error message
     */
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateUser(
            DuplicateUserException ex) {
        log.warn("Duplicate user attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles invalid or expired OTP codes during 2FA verification.
     * Returns 401 to indicate the authentication attempt failed.
     *
     * @param ex the exception
     * @return 401 Unauthorized with error message
     */
    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOtp(
            InvalidOtpException ex) {
        log.warn("Invalid OTP attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles unauthorised role assignment attempts.
     * Returns 403 when a non-admin attempts to assign or revoke roles.
     *
     * @param ex the exception carrying the acting user's ID
     * @return 403 Forbidden with error message
     */
    @ExceptionHandler(UnauthorizedRoleAssignmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedRoleAssignment(
            UnauthorizedRoleAssignmentException ex) {
        log.warn("Unauthorized role assignment attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles KYC validation failures during registration.
     * Returns 422 because the request was well-formed but semantically invalid.
     *
     * @param ex the exception carrying the KYC failure reason
     * @return 422 Unprocessable Entity with error message
     */
    @ExceptionHandler(KycVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleKycVerification(
            KycVerificationException ex) {
        log.warn("KYC verification failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles 2FA operation failures.
     * Returns 400 for invalid state transitions (e.g. enabling already-enabled 2FA).
     *
     * @param ex the exception carrying the failure reason
     * @return 400 Bad Request with error message
     */
    @ExceptionHandler(TwoFactorAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleTwoFactorAuth(
            TwoFactorAuthException ex) {
        log.warn("2FA operation failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles invalid, expired, or revoked JWT tokens.
     * Returns 401 to indicate the authentication token is not acceptable.
     *
     * @param ex the exception carrying the rejection reason
     * @return 401 Unauthorized with error message
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(
            InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles Spring Security's AccessDeniedException.
     *
     * <p>Thrown when an authenticated user attempts to access a resource
     * they do not have permission for — for example, a CUSTOMER attempting
     * to access a TELLER or ADMIN endpoint. Returns a structured 403 JSON
     * response rather than Spring Security's default redirect behaviour.
     * This is the custom AccessDeniedHandler requirement from Section 5.2.
     *
     * @param ex the access denied exception
     * @return 403 Forbidden with structured JSON error
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "Access denied. You do not have permission to perform this action."));
    }

    /**
     * Handles Spring Security's BadCredentialsException.
     * Thrown when login fails due to wrong password.
     * Returns 401 — deliberately vague to prevent user enumeration attacks.
     *
     * @param ex the bad credentials exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex) {
        // Deliberately vague — do not reveal whether the email exists or the
        // password is wrong, as this would enable user enumeration attacks
        log.warn("Failed login attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password."));
    }

    /**
     * Handles Spring Security's DisabledException.
     * Thrown when a suspended user attempts to log in.
     *
     * @param ex the disabled exception
     * @return 403 Forbidden
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(
            DisabledException ex) {
        log.warn("Disabled account login attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "Your account has been suspended. Please contact support."));
    }

    /**
     * Handles Bean Validation failures from {@code @Valid} on request bodies.
     *
     * <p>Thrown by Spring when a request body fails validation constraints
     * such as {@code @NotBlank}, {@code @Email}, {@code @Size}. Collects all
     * field-level errors into a single comma-separated message so the client
     * knows exactly which fields are invalid in one response.
     *
     * @param ex the validation exception containing all field errors
     * @return 400 Bad Request with all validation error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errors));
    }

    /**
     * Catch-all handler for any unexpected exception not handled above.
     *
     * <p>Returns 500 Internal Server Error with a generic message —
     * never exposing internal exception details to the client, which
     * would be a security vulnerability. The full stack trace is logged
     * server-side for debugging.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Please try again later."));
    }
}