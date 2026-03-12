package com.hdfc.notificationservice.notification.strategy;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;

/**
 * Contract for notification delivery strategies.
 *
 * <p>Design Pattern: Strategy
 * Chosen because notification delivery mechanisms (email, SMS, push)
 * are interchangeable algorithms that can vary independently of the
 * NotificationService that uses them. Adding a new channel (e.g. push
 * notifications, WhatsApp) requires only a new implementation of this
 * interface — no existing code is modified.</p>
 *
 * <p>OCP (Open/Closed Principle) — NotificationService is closed for
 * modification. It depends on this interface, not on any concrete
 * channel implementation. New channels are added by extension only.</p>
 *
 * <p>ISP (Interface Segregation Principle) — this interface has a
 * single method. No implementation is forced to implement methods
 * it does not need.</p>
 */
public interface NotificationStrategy {

    /**
     * Sends a notification to the recipient defined in the request.
     *
     * <p>Implementations must handle their own internal failures
     * gracefully — they should log errors but never throw unchecked
     * exceptions that would prevent other strategies from running.</p>
     *
     * @param request the notification request containing recipient,
     *                subject, and message
     */
    void send(NotificationRequest request);
}