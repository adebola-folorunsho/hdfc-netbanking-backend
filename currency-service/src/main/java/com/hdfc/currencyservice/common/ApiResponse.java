package com.hdfc.currencyservice.common;

import lombok.Getter;

/**
 * Standard API response wrapper for all Currency Service endpoints.
 *
 * <p>Design Pattern: Builder
 * Chosen because ApiResponse has optional fields (message may be null
 * on success) and constructing it via static factory methods is cleaner
 * than multiple constructors.</p>
 *
 * <p>Consistent with the response wrapper used by all other HDFC
 * NetBanking services — frontend always receives the same envelope:
 * { "success": true/false, "message": "...", "data": {...} }</p>
 *
 * @param <T> the type of the data payload
 */
@Getter
public class ApiResponse<T> {

    /** Whether the request was successful. */
    private final boolean success;

    /** Human-readable message — describes the result or error. */
    private final String message;

    /** The response payload — null on error responses. */
    private final T data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a successful response with data and a default message.
     *
     * @param data    the response payload
     * @param <T>     the type of the data payload
     * @return        a successful ApiResponse wrapping the data
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Request successful", data);
    }

    /**
     * Creates a successful response with data and a custom message.
     *
     * @param data    the response payload
     * @param message the success message
     * @param <T>     the type of the data payload
     * @return        a successful ApiResponse wrapping the data
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * Creates an error response with no data.
     *
     * @param message the error message
     * @param <T>     the type of the data payload
     * @return        an error ApiResponse with null data
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}