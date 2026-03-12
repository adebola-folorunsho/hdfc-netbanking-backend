package com.hdfc.auditservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.auditservice.auditlog.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the transaction-events topic.
 *
 * <p>Design Pattern: Observer (via Kafka)
 * Chosen because Audit Service must react to domain events published
 * by Transaction Service without being directly coupled to it.
 * Transaction Service publishes events and moves on — it has no
 * knowledge of who consumes them. This is loose coupling by design.</p>
 *
 * <p>SRP: this class is solely responsible for consuming Kafka messages,
 * deserialising the JSON payload, and delegating to AuditLogService.
 * No business logic lives here — the consumer is a thin adapter
 * between Kafka and the service layer.</p>
 *
 * <p>Fault tolerance: malformed or null payloads are logged and
 * discarded — the Kafka offset is still committed so the consumer
 * does not get stuck. A GitHub issue is raised for DLT as a future
 * improvement per architectural decision in Chat 4.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Consumes events from the transaction-events topic.
     *
     * <p>Handles both TRANSACTION_CREATED and FRAUD_ALERT event types.
     * Both are persisted as AuditLog entries — the eventType field
     * distinguishes them in the audit table.</p>
     *
     * <p>groupId = "audit-service" — each service that consumes from
     * transaction-events has its own consumer group so each gets its
     * own independent copy of every message.</p>
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
            TransactionEvent event = objectMapper.readValue(payload, TransactionEvent.class);
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
     * Delegates the deserialized event to the audit log service.
     * Validates that required fields are present before delegating.
     */
    private void processEvent(TransactionEvent event) {
        if (isInvalidEvent(event)) {
            log.warn("Received incomplete event — missing required fields. Discarding: {}", event);
            return;
        }

        log.info("Processing audit event — type: {}, actor: {}", event.getEventType(), event.getActor());

        auditLogService.recordAuditLog(
                event.getEventType(),
                event.getActor(),
                event.getDescription()
        );
    }

    /**
     * Returns true if the event is missing any required fields.
     * Guards against partially-formed events that would cause
     * IllegalArgumentException in the service layer.
     */
    private boolean isInvalidEvent(TransactionEvent event) {
        return event.getEventType() == null || event.getEventType().isBlank()
                || event.getActor() == null || event.getActor().isBlank()
                || event.getDescription() == null || event.getDescription().isBlank();
    }
}