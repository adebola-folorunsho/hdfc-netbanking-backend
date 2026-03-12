package com.hdfc.notificationservice.notification;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import com.hdfc.notificationservice.notification.strategy.NotificationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationStrategy emailStrategy;

    @Mock
    private NotificationStrategy smsStrategy;

    private NotificationServiceImpl notificationService;

    private static final String RECIPIENT = "customer@example.com";
    private static final String SUBJECT = "Transaction Alert";
    private static final String MESSAGE = "Your account has been debited NGN 5000.";

    @BeforeEach
    void setUp() {
        // Inject both strategies — service holds a list of all strategies
        notificationService = new NotificationServiceImpl(List.of(emailStrategy, smsStrategy));
    }

    @Test
    @DisplayName("Should send notification via all registered strategies")
    void shouldSendNotification_viaAllRegisteredStrategies() {
        // Arrange
        NotificationRequest request = buildRequest();

        // Act
        notificationService.sendNotification(request);

        // Assert — every registered strategy must be called exactly once
        verify(emailStrategy, times(1)).send(request);
        verify(smsStrategy, times(1)).send(request);
    }

    @Test
    @DisplayName("Should still send via remaining strategies when one strategy fails")
    void shouldContinueSending_whenOneStrategyFails() {
        // Arrange — email strategy throws, SMS strategy should still be called
        NotificationRequest request = buildRequest();
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(emailStrategy).send(request);

        // Act — must not throw, failure is logged and swallowed per design decision
        notificationService.sendNotification(request);

        // Assert — SMS strategy still called despite email failure
        verify(smsStrategy, times(1)).send(request);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when request is null")
    void shouldThrowIllegalArgumentException_whenRequestIsNull() {
        assertThatThrownBy(() -> notificationService.sendNotification(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when recipient is blank")
    void shouldThrowIllegalArgumentException_whenRecipientIsBlank() {
        NotificationRequest request = NotificationRequest.builder()
                .recipient("")
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();

        assertThatThrownBy(() -> notificationService.sendNotification(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(emailStrategy);
        verifyNoInteractions(smsStrategy);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when message is blank")
    void shouldThrowIllegalArgumentException_whenMessageIsBlank() {
        NotificationRequest request = NotificationRequest.builder()
                .recipient(RECIPIENT)
                .subject(SUBJECT)
                .message("")
                .build();

        assertThatThrownBy(() -> notificationService.sendNotification(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(emailStrategy);
        verifyNoInteractions(smsStrategy);
    }

    // ─────────────────────────────────────────────────────────────────
    // Test data builder
    // ─────────────────────────────────────────────────────────────────

    private NotificationRequest buildRequest() {
        return NotificationRequest.builder()
                .recipient(RECIPIENT)
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();
    }
}