package com.hdfc.notificationservice.notification.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * DTO representing a notification to be sent to a recipient.
 *
 * <p>Immutable by design — all fields set at construction via Builder.
 * Passed to every NotificationStrategy implementation unchanged.</p>
 *
 * <p>Used as the single contract between the Kafka consumer,
 * NotificationService, and all NotificationStrategy implementations.
 * Data coupling — modules share only this simple DTO, never
 * internal state or JPA entities.</p>
 */
@Getter
@Builder
public class NotificationRequest {

    /**
     * The notification recipient.
     * For email: the recipient email address.
     * For SMS: the recipient phone number.
     */
    private final String recipient;

    /**
     * The notification subject line.
     * Used as email subject. For SMS, included in the log message.
     */
    private final String subject;

    /**
     * The notification message body.
     * Full text of the alert or notification.
     */
    private final String message;
}