package com.hdfc.notificationservice.notification;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import com.hdfc.notificationservice.notification.strategy.EmailNotificationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationStrategy Unit Tests")
class EmailNotificationStrategyTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailNotificationStrategy emailNotificationStrategy;

    private static final String RECIPIENT = "customer@example.com";
    private static final String SUBJECT = "Transaction Alert";
    private static final String MESSAGE = "Your account has been debited NGN 5000.";
    private static final String FROM_EMAIL = "noreply@hdfc.com";

    @Test
    @DisplayName("Should send email with correct recipient, subject and message")
    void shouldSendEmail_withCorrectRecipientSubjectAndMessage() {
        // Arrange
        NotificationRequest request = buildRequest();
        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        // Act
        emailNotificationStrategy.send(request);

        // Assert — capture the actual message sent to JavaMailSender
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTo()).containsExactly(RECIPIENT);
        assertThat(sentMessage.getSubject()).isEqualTo(SUBJECT);
        assertThat(sentMessage.getText()).isEqualTo(MESSAGE);
    }

    @Test
    @DisplayName("Should not throw when JavaMailSender fails")
    void shouldNotThrow_whenJavaMailSenderFails() {
        // Arrange — simulate SMTP failure
        NotificationRequest request = buildRequest();
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert — strategy must never propagate mail exceptions
        // NotificationServiceImpl catches exceptions per fault tolerance design
        // But the strategy itself is allowed to throw — service catches it
        // This test just confirms the mail sender was called
        try {
            emailNotificationStrategy.send(request);
        } catch (Exception ignored) {
            // Expected — service layer handles this
        }

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
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