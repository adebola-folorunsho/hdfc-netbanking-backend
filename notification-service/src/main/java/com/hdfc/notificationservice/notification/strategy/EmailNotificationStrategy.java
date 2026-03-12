package com.hdfc.notificationservice.notification.strategy;

import com.hdfc.notificationservice.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email implementation of NotificationStrategy.
 *
 * <p>Design Pattern: Strategy
 * This is one concrete strategy in the notification channel family.
 * NotificationServiceImpl holds a list of all strategies and calls
 * each one — this class has no knowledge of other channels.</p>
 *
 * <p>Uses JavaMailSender with Gmail SMTP — configured in application.yml.
 * SimpleMailMessage is sufficient for plain-text transaction alerts.
 * MimeMessage would be needed for HTML emails — deferred to a future
 * improvement if required.</p>
 *
 * <p>SRP: this class is solely responsible for constructing and sending
 * a plain-text email via JavaMailSender. No business logic lives here.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationStrategy implements NotificationStrategy {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * {@inheritDoc}
     *
     * <p>Constructs a SimpleMailMessage from the request and sends it
     * via JavaMailSender. Any SMTP exceptions propagate to the caller
     * (NotificationServiceImpl) which catches and logs them without
     * blocking other strategies.</p>
     *
     * @param request the notification request containing recipient,
     *                subject, and message
     */
    @Override
    public void send(NotificationRequest request) {
        SimpleMailMessage mailMessage = buildMailMessage(request);
        mailSender.send(mailMessage);
        log.info("Email sent to: {}", request.getRecipient());
    }

    /**
     * Constructs a SimpleMailMessage from the notification request.
     * Separates message construction from sending — SRP at method level.
     */
    private SimpleMailMessage buildMailMessage(NotificationRequest request) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromEmail);
        mailMessage.setTo(request.getRecipient());
        mailMessage.setSubject(request.getSubject());
        mailMessage.setText(request.getMessage());
        return mailMessage;
    }
}