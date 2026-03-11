package com.hdfc.transactionservice.common.messaging;

import com.hdfc.transactionservice.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes transaction domain events to Kafka topics.
 *
 * <p>Transaction Service is a Kafka producer only. Events are
 * consumed by:
 * <ul>
 *   <li>Audit Service (Phase 4) — persists immutable audit records</li>
 *   <li>Notification Service (Phase 5) — sends SMS/email to user</li>
 * </ul>
 *
 * <p>DESIGN PATTERN — Async Fire-and-Forget:
 * All publish methods are @Async — they run on a separate thread pool
 * and never block the HTTP response. A Kafka unavailability does not
 * cause the transaction to fail — the transaction has already been
 * committed to MySQL before publishing is attempted.
 *
 * <p>Kafka topics:
 * <ul>
 *   <li>transaction-events — all transaction lifecycle events</li>
 * </ul>
 *
 * <p>Message key: transactionReference — ensures all events for
 * the same transaction land on the same Kafka partition, preserving
 * ordering for consumers that need to reconstruct transaction history.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private static final String TRANSACTION_EVENTS_TOPIC = "transaction-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a TRANSACTION_CREATED event after a successful transfer,
     * deposit, or withdrawal.
     *
     * <p>Event payload includes all transaction details needed by
     * Audit Service and Notification Service without requiring them
     * to call Transaction Service back.
     *
     * @param transaction the completed transaction entity
     */
    @Async
    public void publishTransactionCreated(Transaction transaction) {
        Map<String, Object> event = buildEventPayload(
                "TRANSACTION_CREATED", transaction);

        log.debug("Publishing TRANSACTION_CREATED event: ref={}",
                transaction.getTransactionReference());

        sendEvent(transaction.getTransactionReference(), event);
    }

    /**
     * Publishes a TRANSACTION_FAILED event when a Saga step fails
     * and compensation has been applied.
     *
     * <p>Notification Service uses this to alert the user that
     * their transfer failed and their balance has been restored.
     *
     * @param transaction the failed transaction entity
     */
    @Async
    public void publishTransactionFailed(Transaction transaction) {
        Map<String, Object> event = buildEventPayload(
                "TRANSACTION_FAILED", transaction);

        log.debug("Publishing TRANSACTION_FAILED event: ref={}",
                transaction.getTransactionReference());

        sendEvent(transaction.getTransactionReference(), event);
    }

    /**
     * Publishes a TRANSACTION_REVERSED event when an admin reverses
     * a completed transaction.
     *
     * @param transaction the reversal transaction entity
     */
    @Async
    public void publishTransactionReversed(Transaction transaction) {
        Map<String, Object> event = buildEventPayload(
                "TRANSACTION_REVERSED", transaction);

        log.debug("Publishing TRANSACTION_REVERSED event: ref={}",
                transaction.getTransactionReference());

        sendEvent(transaction.getTransactionReference(), event);
    }

    /**
     * Builds the standard event payload map from a transaction entity.
     *
     * <p>The payload is self-contained — consumers do not need to
     * call back to Transaction Service to get transaction details.
     * This is the choreography-ready event design for Phase 4/5.
     *
     * @param eventType   the event type string
     * @param transaction the transaction entity
     * @return the event payload as a Map
     */
    private Map<String, Object> buildEventPayload(
            String eventType, Transaction transaction) {

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("transactionId", transaction.getId());
        event.put("transactionReference", transaction.getTransactionReference());
        event.put("transactionType", transaction.getTransactionType().name());
        event.put("status", transaction.getStatus().name());
        event.put("userId", transaction.getUserId());
        event.put("sourceAccountId", transaction.getSourceAccountId());
        event.put("destinationAccountId", transaction.getDestinationAccountId());
        event.put("amount", transaction.getAmount());
        event.put("currencyCode", transaction.getCurrencyCode());
        event.put("convertedAmount", transaction.getConvertedAmount());
        event.put("convertedCurrencyCode", transaction.getConvertedCurrencyCode());
        event.put("description", transaction.getDescription());
        event.put("failureReason", transaction.getFailureReason());
        event.put("createdAt", transaction.getCreatedAt() != null
                ? transaction.getCreatedAt().toString() : null);
        return event;
    }

    /**
     * Sends the event to Kafka with callback logging.
     *
     * <p>Uses the transactionReference as the message key to ensure
     * all events for the same transaction go to the same partition.
     * This guarantees ordering for consumers processing events
     * sequentially.
     *
     * <p>On send failure, the error is logged at ERROR level.
     * The transaction is already committed — the failure is operational
     * (Kafka unavailable) not a business logic failure.
     *
     * @param key   the message key (transactionReference)
     * @param event the event payload
     */
    private void sendEvent(String key, Map<String, Object> event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TRANSACTION_EVENTS_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish Kafka event: key={}, " +
                        "error={}", key, ex.getMessage(), ex);
            } else {
                log.debug("Kafka event published: key={}, partition={}, " +
                                "offset={}",
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}