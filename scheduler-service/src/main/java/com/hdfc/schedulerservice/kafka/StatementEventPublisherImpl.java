package com.hdfc.schedulerservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * KafkaTemplate-based implementation of StatementEventPublisher.
 *
 * <p>Design Pattern: Observer (via Kafka)
 * Scheduler Service publishes statement-ready events and moves on —
 * it has no knowledge of who consumes them. Notification Service
 * observes the topic and reacts by sending emails. This is loose
 * coupling by design — Scheduler Service never calls Notification
 * Service directly.</p>
 *
 * <p>SRP: this class is solely responsible for serialising the
 * StatementResponse to JSON and publishing it to Kafka.
 * No business logic lives here.</p>
 *
 * <p>Fire-and-forget — Kafka publish is async. If publishing fails,
 * the error is logged but does not block the cron job from continuing
 * with other accounts.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatementEventPublisherImpl implements StatementEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.statement-events}")
    private String statementEventsTopic;

    /**
     * {@inheritDoc}
     *
     * <p>Serialises the StatementResponse to JSON and publishes to
     * the statement-events topic. Uses the statementId as the Kafka
     * message key for ordered delivery per statement.</p>
     */
    @Override
    public void publishStatementReady(StatementResponse statement) {
        try {
            String payload = objectMapper.writeValueAsString(statement);
            kafkaTemplate.send(statementEventsTopic, String.valueOf(statement.getId()), payload);
            log.info("Statement event published — statementId: {}, userId: {}",
                    statement.getId(), statement.getUserId());
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialise statement event — statementId: {}. Reason: {}",
                    statement.getId(), exception.getMessage());
        }
    }
}