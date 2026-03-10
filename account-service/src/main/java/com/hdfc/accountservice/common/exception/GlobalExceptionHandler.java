package com.hdfc.accountservice.common.exception;

import com.hdfc.accountservice.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler for all Account Service controllers.
 *
 * <p>DESIGN PATTERN — Decorator:
 * @RestControllerAdvice wraps every controller with cross-cutting
 * exception handling behaviour without modifying the controllers
 * themselves. Controllers stay clean — they never catch exceptions.
 *
 * <p>Every custom exception maps to a specific HTTP status code and
 * a structured ApiResponse error body. No raw exception messages
 * ever reach the client — all errors are normalised through this handler.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles account not found errors.
     * Triggered when an account lookup by ID or account number fails.
     *
     * @param ex the exception
     * @return 404 Not Found with error message
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFoundException(
            AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles duplicate account creation attempts.
     * Triggered when a user tries to open an account type they already hold.
     *
     * @param ex the exception
     * @return 409 Conflict with error message
     */
    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateAccountException(
            DuplicateAccountException ex) {
        log.warn("Duplicate account attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles insufficient balance errors.
     * Triggered when a debit or transfer cannot proceed due to low funds.
     *
     * @param ex the exception
     * @return 422 Unprocessable Entity with error message
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientBalanceException(
            InsufficientBalanceException ex) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles operations on non-active accounts.
     * Triggered when a debit/credit is attempted on a FROZEN,
     * INACTIVE, or CLOSED account.
     *
     * @param ex the exception
     * @return 422 Unprocessable Entity with error message
     */
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotActiveException(
            AccountNotActiveException ex) {
        log.warn("Account not active: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles logically invalid account operations.
     * Triggered for type-specific rule violations such as
     * early FD withdrawal or closing an account with non-zero balance.
     *
     * @param ex the exception
     * @return 400 Bad Request with error message
     */
    @ExceptionHandler(InvalidAccountOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAccountOperationException(
            InvalidAccountOperationException ex) {
        log.warn("Invalid account operation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles account ownership violations.
     * Triggered when a CUSTOMER attempts to access an account
     * that does not belong to them.
     *
     * @param ex the exception
     * @return 403 Forbidden with error message
     */
    @ExceptionHandler(AccountOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountOwnershipException(
            AccountOwnershipException ex) {
        log.warn("Account ownership violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles Spring Security authentication failures.
     * Triggered when a request arrives with a missing, expired,
     * or malformed JWT token.
     *
     * @param ex the exception
     * @return 401 Unauthorized with error message
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required: " + ex.getMessage()));
    }

    /**
     * Handles Spring Security authorisation failures.
     * Triggered when a valid JWT is present but the user's role
     * does not permit the requested operation.
     *
     * @param ex the exception
     * @return 403 Forbidden with error message
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied: insufficient permissions"));
    }

    /**
     * Handles Bean Validation failures on request DTOs.
     * Triggered when @Valid on a @RequestBody fails — e.g. a required
     * field is missing or a value fails a @DecimalMin constraint.
     *
     * <p>Collects all field-level validation errors into a single
     * comma-separated message so the client receives all errors
     * in one response rather than one error at a time.
     *
     * @param ex the exception containing all field errors
     * @return 400 Bad Request with all validation error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failure: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errors));
    }

    /**
     * Catch-all handler for any unexpected exception not covered above.
     *
     * <p>Logs the full stack trace at ERROR level for debugging but
     * returns a generic message to the client — never leak internal
     * exception details or stack traces in API responses.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error in Account Service: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Please try again later."));
    }
}