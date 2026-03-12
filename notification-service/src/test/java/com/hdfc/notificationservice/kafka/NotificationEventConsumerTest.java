package com.hdfc.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.notificationservice.notification.NotificationService;
import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventConsumer Unit Tests")
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationEventConsumer notificationEventConsumer;

    @BeforeEach
    void setUp() {
        notificationEventConsumer = new NotificationEventConsumer(notificationService, objectMapper);
    }

    private static final String TRANSACTION_CREATED_EVENT =
            "{\"eventType\":\"TRANSACTION_CREATED\",\"recipientEmail\":\"customer@example.com\"," +
                    "\"description\":\"Fund transfer of NGN 5000 from account 1 to account 2\"}";

    private static final String FRAUD_ALERT_EVENT =
            "{\"eventType\":\"FRAUD_ALERT\",\"recipientEmail\":\"customer@example.com\"," +
                    "\"description\":\"Suspicious transaction detected on your account\"}";

    private static final String MALFORMED_EVENT = "not-valid-json";

    @Test
    @DisplayName("Should send transaction alert notification when TRANSACTION_CREATED event received")
    void shouldSendTransactionAlert_whenTransactionCreatedEventReceived() {
        // Act
        notificationEventConsumer.consume(TRANSACTION_CREATED_EVENT);

        // Assert — notification service must be called with correct request
        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(captor.capture());

        NotificationRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getRecipient()).isEqualTo("customer@example.com");
        assertThat(sentRequest.getMessage()).contains("NGN 5000");
    }

    @Test
    @DisplayName("Should send fraud alert notification when FRAUD_ALERT event received")
    void shouldSendFraudAlert_whenFraudAlertEventReceived() {
        // Act
        notificationEventConsumer.consume(FRAUD_ALERT_EVENT);

        // Assert
        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(captor.capture());

        NotificationRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getRecipient()).isEqualTo("customer@example.com");
        assertThat(sentRequest.getSubject()).containsIgnoringCase("fraud");
    }

    @Test
    @DisplayName("Should not send notification when malformed event received")
    void shouldNotSendNotification_whenMalformedEventReceived() {
        // Act — must not throw
        notificationEventConsumer.consume(MALFORMED_EVENT);

        // Assert
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Should not send notification when null payload received")
    void shouldNotSendNotification_whenNullPayloadReceived() {
        // Act — must not throw
        notificationEventConsumer.consume(null);

        // Assert
        verifyNoInteractions(notificationService);
    }
}