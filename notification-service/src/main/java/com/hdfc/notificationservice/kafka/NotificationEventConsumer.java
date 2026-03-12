package com.hdfc.notificationservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.notificationservice.notification.NotificationService;
import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the transaction-events topic.
 *
 * <p>Design Pattern: Observer (via Kafka)
 * Notification Service observes domain events published by Transaction
 * Service without being directly coupled to it. Transaction Service
 * publishes and moves on — it has no knowledge of who reacts.</p>
 *
 * <p>SRP: this class is solely responsible for consuming Kafka messages,
 * deserialising the JSON payload, mapping to a NotificationRequest,
 * and delegating to NotificationService. No notification logic here.</p>
 *
 * <p>Fault tolerance: malformed or null payloads are logged and
 * discarded — Kafka offset is still committed so the consumer
 * does not get stuck on bad messages.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Consumes events from the transaction-events topic.
     *
     * <p>groupId = "notification-service" — independent consumer group
     * so Notification Service gets its own copy of every message,
     * separate from the Audit Service consumer group.</p>
     *
     * @param payload the raw JSON string from Kafka
     */
    @KafkaListener(
            topics = "${kafka.topics.transaction-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String payload) {
        if (payload == null) {
            log.warn("Received null payload from transaction-events topic — discarding");
            return;
        }

        try {
            NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);
            processEvent(event);
        } catch (JsonProcessingException exception) {
            // Log and discard — never block the consumer on malformed messages
            // TODO: Route to dead-letter topic (GitHub issue raised)
            log.error("Failed to deserialise Kafka payload — discarding. Payload: {}. Reason: {}",
                    payload, exception.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Maps the Kafka event to a NotificationRequest and delegates
     * to NotificationService. Subject line is determined by event type.
     */
    private void processEvent(NotificationEvent event) {
        if (isInvalidEvent(event)) {
            log.warn("Received incomplete event — missing required fields. Discarding.");
            return;
        }

        log.info("Processing notification event — type: {}, recipient: {}",
                event.getEventType(), event.getRecipientEmail());

        NotificationRequest request = NotificationRequest.builder()
                .recipient(event.getRecipientEmail())
                .subject(resolveSubject(event.getEventType()))
                .message(event.getDescription())
                .build();

        notificationService.sendNotification(request);
    }

    /**
     * Resolves the notification subject line based on event type.
     * Each event type maps to a human-readable subject for the customer.
     */
    private String resolveSubject(String eventType) {
        return switch (eventType) {
            case "TRANSACTION_CREATED" -> "HDFC NetBanking — Transaction Alert";
            case "FRAUD_ALERT"         -> "HDFC NetBanking — Fraud Alert: Urgent Action Required";
            default                    -> "HDFC NetBanking — Account Notification";
        };
    }

    /**
     * Returns true if the event is missing any required fields.
     */
    private boolean isInvalidEvent(NotificationEvent event) {
        return event.getEventType() == null || event.getEventType().isBlank()
                || event.getRecipientEmail() == null || event.getRecipientEmail().isBlank()
                || event.getDescription() == null || event.getDescription().isBlank();
    }
}