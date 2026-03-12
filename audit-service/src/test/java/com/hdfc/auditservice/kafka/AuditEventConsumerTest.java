package com.hdfc.auditservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.auditservice.auditlog.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventConsumer Unit Tests")
class AuditEventConsumerTest {

    @Mock
    private AuditLogService auditLogService;

    // Real ObjectMapper — we want actual JSON deserialisation in tests,
    // not a mock. ObjectMapper is a stateless utility, safe to instantiate directly.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditEventConsumer auditEventConsumer;

    @BeforeEach
    void setUp() {
        // Manually construct with both dependencies — real ObjectMapper, mock service
        auditEventConsumer = new AuditEventConsumer(auditLogService, objectMapper);
    }

    private static final String TRANSACTION_CREATED_EVENT =
            "{\"eventType\":\"TRANSACTION_CREATED\",\"actor\":\"transaction-service\"," +
                    "\"description\":\"Fund transfer of NGN 5000 from account 1 to account 2\"}";

    private static final String FRAUD_ALERT_EVENT =
            "{\"eventType\":\"FRAUD_ALERT\",\"actor\":\"transaction-service\"," +
                    "\"description\":\"Suspicious transaction detected on account 1\"}";

    private static final String MALFORMED_EVENT = "not-valid-json";

    @Test
    @DisplayName("Should record audit log when TRANSACTION_CREATED event is received")
    void shouldRecordAuditLog_whenTransactionCreatedEventIsReceived() {
        // Act
        auditEventConsumer.consume(TRANSACTION_CREATED_EVENT);

        // Assert — service must be called exactly once with correct args
        verify(auditLogService, times(1)).recordAuditLog(
                "TRANSACTION_CREATED",
                "transaction-service",
                "Fund transfer of NGN 5000 from account 1 to account 2"
        );
    }

    @Test
    @DisplayName("Should record audit log when FRAUD_ALERT event is received")
    void shouldRecordAuditLog_whenFraudAlertEventIsReceived() {
        // Act
        auditEventConsumer.consume(FRAUD_ALERT_EVENT);

        // Assert
        verify(auditLogService, times(1)).recordAuditLog(
                "FRAUD_ALERT",
                "transaction-service",
                "Suspicious transaction detected on account 1"
        );
    }

    @Test
    @DisplayName("Should not record audit log when malformed event is received")
    void shouldNotRecordAuditLog_whenMalformedEventIsReceived() {
        // Act — malformed JSON must be handled gracefully, never throw
        auditEventConsumer.consume(MALFORMED_EVENT);

        // Assert — service must never be called with invalid data
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Should not record audit log when event payload is null")
    void shouldNotRecordAuditLog_whenEventPayloadIsNull() {
        // Act — null payload must be handled gracefully, never throw
        auditEventConsumer.consume(null);

        // Assert
        verifyNoInteractions(auditLogService);
    }
}