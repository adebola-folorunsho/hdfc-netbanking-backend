package com.hdfc.transactionservice.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Universal API response wrapper for all Transaction Service endpoints.
 *
 * <p>Every endpoint — success or error — returns this structure:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Transfer completed successfully",
 *   "data": { ... },
 *   "timestamp": "2026-03-10T12:00:00"
 * }
 * </pre>
 *
 * <p>DESIGN PATTERN — Builder:
 * Lombok @Builder generates a fluent builder eliminating
 * telescoping constructors. Static factory methods provide
 * clean call sites throughout the codebase.
 *
 * @param <T> the type of the data payload
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Convenience factory for successful responses with a data payload.
     *
     * @param message human-readable success message
     * @param data    the response payload
     * @param <T>     the payload type
     * @return a success ApiResponse
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Convenience factory for successful responses with no data payload.
     *
     * @param message human-readable success message
     * @param <T>     the payload type
     * @return a success ApiResponse with no data field
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * Convenience factory for error responses.
     *
     * @param message human-readable error message
     * @param <T>     the payload type
     * @return an error ApiResponse with no data field
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}