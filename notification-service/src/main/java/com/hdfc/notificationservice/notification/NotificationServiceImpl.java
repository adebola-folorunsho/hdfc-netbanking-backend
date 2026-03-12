package com.hdfc.notificationservice.notification;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import com.hdfc.notificationservice.notification.strategy.NotificationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of NotificationService.
 *
 * <p>Design Pattern: Strategy
 * Holds a list of all registered NotificationStrategy implementations.
 * Iterates over every strategy and delegates the send operation.
 * New channels (push, WhatsApp) are added by implementing
 * NotificationStrategy and registering as a Spring bean —
 * this class never needs to change.</p>
 *
 * <p>Design Pattern: Iterator (implicit via enhanced for loop)
 * Iterates over the strategy list ensuring every channel is attempted
 * regardless of failures in preceding channels.</p>
 *
 * <p>SRP: this class is solely responsible for coordinating notification
 * delivery across all registered strategies. It does not know about
 * SMTP, SMS providers, or Kafka — those belong to their own classes.</p>
 *
 * <p>Fault tolerance: if one strategy fails, the exception is caught,
 * logged, and the next strategy is still attempted. A failed email
 * must never block an SMS notification from being sent.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /**
     * All registered NotificationStrategy implementations.
     * Spring auto-injects every bean implementing NotificationStrategy
     * into this list — no manual wiring needed when adding new channels.
     */
    private final List<NotificationStrategy> strategies;

    /**
     * {@inheritDoc}
     *
     * <p>Validates the request at the entry point — fail fast before
     * any strategy is invoked. Then iterates over all strategies,
     * catching and logging any per-strategy failures without stopping
     * the iteration.</p>
     */
    @Override
    public void sendNotification(NotificationRequest request) {
        validateRequest(request);

        for (NotificationStrategy strategy : strategies) {
            attemptSend(strategy, request);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers — each does exactly one thing (SRP at method level)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Validates the notification request.
     * Fails fast — never propagates invalid state to strategies.
     */
    private void validateRequest(NotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NotificationRequest must not be null");
        }
        if (request.getRecipient() == null || request.getRecipient().isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Attempts to send via a single strategy.
     * Catches all exceptions — one strategy failure must never
     * prevent other strategies from executing.
     */
    private void attemptSend(NotificationStrategy strategy, NotificationRequest request) {
        try {
            strategy.send(request);
            log.info("Notification sent via {}", strategy.getClass().getSimpleName());
        } catch (Exception exception) {
            // Log and continue — failure is intentionally swallowed here
            // per architectural decision: log-and-ack, no retry at this stage
            log.error("Notification failed via {} — reason: {}",
                    strategy.getClass().getSimpleName(), exception.getMessage());
        }
    }
}