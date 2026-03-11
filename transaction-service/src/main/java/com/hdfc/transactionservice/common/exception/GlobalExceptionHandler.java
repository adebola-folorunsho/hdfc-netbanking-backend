package com.hdfc.transactionservice.common.exception;

import com.hdfc.transactionservice.common.dto.ApiResponse;
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
 * Global exception handler for all Transaction Service controllers.
 *
 * <p>DESIGN PATTERN — Decorator:
 * @RestControllerAdvice wraps every controller with cross-cutting
 * exception handling without modifying the controllers themselves.
 * Controllers stay clean — they never catch exceptions.
 *
 * <p>Every custom exception maps to a specific HTTP status code
 * and a structured ApiResponse error body. No raw exception
 * messages or stack traces ever reach the client.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles transaction not found errors.
     *
     * @param ex the exception
     * @return 404 Not Found
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionNotFoundException(
            TransactionNotFoundException ex) {
        log.warn("Transaction not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles insufficient balance errors from Account Service.
     *
     * @param ex the exception
     * @return 422 Unprocessable Entity
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
     * Handles logically invalid transaction requests.
     *
     * @param ex the exception
     * @return 400 Bad Request
     */
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTransactionException(
            InvalidTransactionException ex) {
        log.warn("Invalid transaction: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles duplicate transaction reference — idempotency guard.
     *
     * @param ex the exception
     * @return 409 Conflict
     */
    @ExceptionHandler(TransactionAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionAlreadyExistsException(
            TransactionAlreadyExistsException ex) {
        log.warn("Duplicate transaction reference: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles Account Service communication failures.
     * The error originates upstream — Transaction Service is healthy.
     *
     * @param ex the exception
     * @return 502 Bad Gateway
     */
    @ExceptionHandler(AccountServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountServiceException(
            AccountServiceException ex) {
        log.error("Account Service call failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(
                        "Account Service is currently unavailable. " +
                                "Please try again later."));
    }

    /**
     * Handles Paystack API failures.
     * The error originates from the external payment gateway.
     *
     * @param ex the exception
     * @return 502 Bad Gateway
     */
    @ExceptionHandler(PaystackException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaystackException(
            PaystackException ex) {
        log.error("Paystack API call failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(
                        "Payment gateway is currently unavailable. " +
                                "Please try again later."));
    }

    /**
     * Handles transaction ownership violations.
     * Returns 403 regardless of whether the transaction exists —
     * prevents transaction ID enumeration attacks.
     *
     * @param ex the exception
     * @return 403 Forbidden
     */
    @ExceptionHandler(TransactionOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionOwnershipException(
            TransactionOwnershipException ex) {
        log.warn("Transaction ownership violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles Spring Security authentication failures.
     * Missing, expired, or malformed JWT token.
     *
     * @param ex the exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        "Authentication required: " + ex.getMessage()));
    }

    /**
     * Handles Spring Security authorisation failures.
     * Valid JWT but insufficient role for the requested operation.
     *
     * @param ex the exception
     * @return 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "Access denied: insufficient permissions"));
    }

    /**
     * Handles Bean Validation failures on request DTOs.
     * Collects all field errors into a single response so the
     * client receives all validation errors at once.
     *
     * @param ex the exception
     * @return 400 Bad Request with all validation errors
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
     * Catch-all for any unexpected exception.
     * Logs full stack trace at ERROR level.
     * Returns generic message — never leaks internals to client.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex) {
        log.error("Unexpected error in Transaction Service: {}",
                ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Please try again later."));
    }
}