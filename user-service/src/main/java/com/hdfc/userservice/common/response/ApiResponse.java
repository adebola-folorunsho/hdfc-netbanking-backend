package com.hdfc.userservice.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper returned by every endpoint in the User Service.
 *
 * <p>Every HTTP response — success or error — is wrapped in this structure
 * so clients always receive a consistent, predictable JSON shape. This eliminates
 * the need for clients to handle different response formats per endpoint.
 *
 * <p>Example success response:
 * <pre>
 * {
 *   "success": true,
 *   "message": "User registered successfully",
 *   "data": { ... },
 *   "timestamp": "2026-03-05T19:00:00"
 * }
 * </pre>
 *
 * <p>Example error response:
 * <pre>
 * {
 *   "success": false,
 *   "message": "User not found",
 *   "timestamp": "2026-03-05T19:00:00"
 * }
 * </pre>
 *
 * <p>Design pattern: Builder pattern is used here because ApiResponse has
 * multiple optional fields (data, errors) and forcing callers to use a
 * telescoping constructor would be unreadable and error-prone.
 * {@code ApiResponse.builder().success(true).message("OK").data(dto).build()}
 * is far clearer than {@code new ApiResponse(true, "OK", dto, null, timestamp)}.
 *
 * <p>This class is immutable — all fields are final via {@code @Getter}
 * with no {@code @Setter}. Once built, an ApiResponse cannot be modified.
 * This is intentional — response objects should never be mutated after creation.
 *
 * @param <T> the type of the response payload — allows type-safe data wrapping
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Indicates whether the request was processed successfully.
     * Always present in every response — never null.
     */
    private final boolean success;

    /**
     * Human-readable message describing the outcome.
     * For success responses: a confirmation message.
     * For error responses: a description of what went wrong.
     * Always present in every response — never null.
     */
    private final String message;

    /**
     * The response payload. Present only on success responses.
     * Null on error responses — omitted from JSON via {@code @JsonInclude}.
     *
     * <p>Typed as {@code T} to allow callers to wrap any DTO without casting.
     */
    private final T data;

    /**
     * The timestamp when this response was generated.
     * Always present — allows clients and logs to correlate responses with time.
     */
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Static factory method for success responses with a data payload.
     *
     * <p>Use this when the request succeeded and there is data to return:
     * {@code ApiResponse.success("User registered successfully", userDto)}
     *
     * @param message a confirmation message
     * @param data    the response payload
     * @param <T>     the type of the payload
     * @return a success ApiResponse wrapping the given data
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Static factory method for success responses without a data payload.
     *
     * <p>Use this when the request succeeded but there is nothing to return:
     * {@code ApiResponse.success("2FA disabled successfully")}
     *
     * @param message a confirmation message
     * @param <T>     the type parameter — inferred as Void in practice
     * @return a success ApiResponse with no data payload
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * Static factory method for error responses.
     *
     * <p>Use this when the request failed:
     * {@code ApiResponse.error("User not found")}
     *
     * <p>The global {@code @ControllerAdvice} exception handler uses this
     * exclusively to build all error responses — ensuring every error
     * follows the same JSON structure.
     *
     * @param message a description of what went wrong
     * @param <T>     the type parameter — irrelevant for error responses
     * @return an error ApiResponse with no data payload
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}