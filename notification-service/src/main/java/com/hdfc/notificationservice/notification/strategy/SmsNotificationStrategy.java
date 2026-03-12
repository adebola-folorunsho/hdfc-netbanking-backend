package com.hdfc.notificationservice.notification.strategy;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stubbed SMS implementation of NotificationStrategy.
 *
 * <p>Design Pattern: Strategy + Null Object
 * This is a no-op stub that logs the SMS instead of sending it.
 * Chosen over a real Twilio/Termii integration because:
 * (1) Both require paid accounts or complex setup for portfolio use
 * (2) A clean stub with the Strategy interface demonstrates
 *     extensibility more clearly than a broken real integration
 * (3) Swapping this stub for a real provider requires only replacing
 *     this class — NotificationService and its tests are unchanged.</p>
 *
 * <p>To add real SMS: implement this class using Twilio or Termii SDK,
 * inject the client via constructor, and replace the log statement
 * with the real API call. No other class needs to change.</p>
 *
 * <p>SRP: this class is solely responsible for the SMS notification
 * channel. It does not know about email, Kafka, or any other channel.</p>
 */
@Slf4j
@Component
public class SmsNotificationStrategy implements NotificationStrategy {

    /**
     * {@inheritDoc}
     *
     * <p>Stub implementation — logs the SMS content instead of sending.
     * In production, replace this with a real SMS provider call.</p>
     *
     * @param request the notification request containing recipient,
     *                subject, and message
     */
    @Override
    public void send(NotificationRequest request) {
        // Stub — real SMS provider integration deferred (Twilio/Termii)
        log.info("[SMS STUB] To: {} | Subject: {} | Message: {}",
                request.getRecipient(),
                request.getSubject(),
                request.getMessage());
    }
}