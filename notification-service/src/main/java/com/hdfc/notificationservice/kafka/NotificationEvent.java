package com.hdfc.notificationservice.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal DTO representing a Kafka event consumed from the
 * transaction-events topic published by Transaction Service.
 *
 * <p>@JsonIgnoreProperties(ignoreUnknown = true) — Transaction Service
 * may publish additional fields in the future. We only deserialise what
 * Notification Service needs — unknown fields are safely ignored.</p>
 *
 * <p>SRP: this class only exists to deserialise the Kafka message JSON.
 * No business logic lives here.</p>
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationEvent {

    /**
     * The type of event — determines the notification subject and tone.
     * e.g. "TRANSACTION_CREATED", "FRAUD_ALERT"
     */
    private String eventType;

    /**
     * The recipient email address extracted from the event.
     * Transaction Service includes the customer email in the event
     * so Notification Service does not need to call User Service.
     */
    private String recipientEmail;

    /**
     * Human-readable description of the event.
     * Used as the notification message body.
     */
    private String description;
}