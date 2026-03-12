package com.hdfc.schedulerservice.common;

import com.hdfc.schedulerservice.statement.exception.StatementNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for Scheduler Service.
 *
 * <p>Maps every custom exception to the correct HTTP status code
 * and a structured JSON error response. No raw exception messages
 * are ever returned directly to callers.</p>
 *
 * <p>SRP: this class is solely responsible for translating exceptions
 * into HTTP responses. No business logic lives here.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles StatementNotFoundException — thrown when a statement
     * record with the requested ID does not exist.
     *
     * @param exception the thrown exception
     * @return 404 NOT FOUND with structured error body
     */
    @ExceptionHandler(StatementNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStatementNotFoundException(
            StatementNotFoundException exception) {

        log.warn("Statement not found: {}", exception.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * Handles IllegalArgumentException — thrown when invalid arguments
     * are passed to service methods.
     *
     * @param exception the thrown exception
     * @return 400 BAD REQUEST with structured error body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException exception) {

        log.warn("Invalid argument: {}", exception.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * Catch-all handler for any unexpected exception not explicitly
     * handled above. Logs the full stack trace for debugging.
     *
     * @param exception the thrown exception
     * @return 500 INTERNAL SERVER ERROR with structured error body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception exception) {

        log.error("Unexpected error in Scheduler Service: {}", exception.getMessage(), exception);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later."
        );
    }

    /**
     * Builds a consistent structured JSON error response body.
     *
     * <p>LinkedHashMap preserves insertion order so JSON fields always
     * appear in the same order: timestamp, status, error, message.</p>
     *
     * @param status  the HTTP status to return
     * @param message the error message to include in the response body
     * @return ResponseEntity with structured error map
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String message) {

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", status.value());
        errorBody.put("error", status.getReasonPhrase());
        errorBody.put("message", message);

        return ResponseEntity.status(status).body(errorBody);
    }
}