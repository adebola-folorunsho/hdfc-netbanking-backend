package com.hdfc.accountservice.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Universal API response wrapper for all Account Service endpoints.
 *
 * <p>Every endpoint — success or error — returns this structure:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Account created successfully",
 *   "data": { ... },
 *   "timestamp": "2026-03-09T15:00:00"
 * }
 * </pre>
 *
 * <p>DESIGN PATTERN — Builder:
 * Lombok @Builder generates a fluent builder so callers construct
 * responses as ApiResponse.builder().success(true)...build()
 * rather than through telescoping constructors.
 *
 * <p>@JsonInclude(NON_NULL) ensures the "data" field is omitted
 * from error responses entirely rather than appearing as null.
 *
 * @param <T> the type of the data payload
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Whether the request was processed successfully.
     * Always present — never null.
     */
    private final boolean success;

    /**
     * Human-readable message describing the outcome.
     * Always present — never null.
     */
    private final String message;

    /**
     * The response payload. Null on error responses —
     * omitted from JSON output by @JsonInclude(NON_NULL).
     */
    private final T data;

    /**
     * Server timestamp when this response was generated.
     * Always present — never null.
     */
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Convenience factory method for successful responses with data.
     *
     * @param message human-readable success message
     * @param data    the response payload
     * @param <T>     the type of the data payload
     * @return a success ApiResponse containing the data
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Convenience factory method for successful responses with no data payload.
     *
     * @param message human-readable success message
     * @param <T>     the type parameter (Void in practice)
     * @return a success ApiResponse with no data field
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * Convenience factory method for error responses.
     * No data field is included — @JsonInclude(NON_NULL) omits it.
     *
     * @param message human-readable error message
     * @param <T>     the type parameter (Void in practice)
     * @return an error ApiResponse with no data field
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}