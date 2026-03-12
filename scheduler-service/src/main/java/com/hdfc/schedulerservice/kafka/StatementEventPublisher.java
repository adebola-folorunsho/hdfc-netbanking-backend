package com.hdfc.schedulerservice.kafka;

import com.hdfc.schedulerservice.statement.dto.StatementResponse;

/**
 * Contract for publishing statement-ready events to Kafka.
 *
 * <p>DIP (Dependency Inversion Principle) — ScheduledJobs depends on
 * this interface, never on KafkaTemplate directly. This allows the
 * messaging backend to be swapped without touching the cron job.</p>
 *
 * <p>OCP (Open/Closed Principle) — new event publishing strategies
 * are added by creating new implementations, never by modifying
 * this interface.</p>
 */
public interface StatementEventPublisher {

    /**
     * Publishes a statement-ready event to the statement-events Kafka topic.
     * Notification Service consumes this event and emails the statement
     * summary to the customer.
     *
     * @param statement the generated statement to publish as an event
     */
    void publishStatementReady(StatementResponse statement);
}