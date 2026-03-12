package com.hdfc.notificationservice.notification;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;

/**
 * Contract for notification operations.
 *
 * <p>DIP (Dependency Inversion Principle) — the Kafka consumer depends
 * on this interface, never on the concrete implementation. This allows
 * the notification delivery mechanism to be swapped or extended without
 * touching the consumer.</p>
 *
 * <p>OCP (Open/Closed Principle) — new notification behaviours are
 * added by creating new implementations or new strategies, never by
 * modifying this interface or its existing implementation.</p>
 */
public interface NotificationService {

    /**
     * Sends a notification via all registered NotificationStrategy
     * implementations.
     *
     * <p>If one strategy fails, the remaining strategies still execute.
     * Failure in one channel must never block other channels.</p>
     *
     * @param request the notification request containing recipient,
     *                subject, and message
     * @throws IllegalArgumentException if request is null or contains
     *                                  blank required fields
     */
    void sendNotification(NotificationRequest request);
}