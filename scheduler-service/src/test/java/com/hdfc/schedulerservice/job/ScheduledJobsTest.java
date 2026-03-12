package com.hdfc.schedulerservice.job;

import com.hdfc.schedulerservice.kafka.StatementEventPublisher;
import com.hdfc.schedulerservice.statement.StatementService;
import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledJobs Unit Tests")
class ScheduledJobsTest {

    @Mock
    private StatementService statementService;

    @Mock
    private StatementEventPublisher statementEventPublisher;

    @InjectMocks
    private ScheduledJobs scheduledJobs;

    // ─────────────────────────────────────────────────────────────────
    // Monthly statement job tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should generate statement and publish event for each account")
    void shouldGenerateStatementAndPublishEvent_forEachAccount() {
        // Arrange
        StatementResponse statementResponse = buildStatementResponse();
        when(statementService.generateStatement(any(), any(), any()))
                .thenReturn(statementResponse);

        // Act
        scheduledJobs.runMonthlyStatementJob();

        // Assert — statement generated and event published for each account
        verify(statementService, atLeastOnce())
                .generateStatement(any(), any(), any(YearMonth.class));
        verify(statementEventPublisher, atLeastOnce())
                .publishStatementReady(any(StatementResponse.class));
    }

    // ─────────────────────────────────────────────────────────────────
    // Daily interest accrual job tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should run interest accrual job without throwing")
    void shouldRunInterestAccrualJob_withoutThrowing() {
        // Act — job runs, logs, completes — no service calls yet (stub)
        scheduledJobs.runDailyInterestAccrualJob();

        // Assert — no interactions with statement service for this job
        verifyNoInteractions(statementService);
        verifyNoInteractions(statementEventPublisher);
    }

    // ─────────────────────────────────────────────────────────────────
    // OTP cleanup job tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should run OTP cleanup job without throwing")
    void shouldRunOtpCleanupJob_withoutThrowing() {
        // Act — job runs, logs, completes — no service calls yet (stub)
        scheduledJobs.runOtpCleanupJob();

        // Assert — no interactions with statement service for this job
        verifyNoInteractions(statementService);
        verifyNoInteractions(statementEventPublisher);
    }

    // ─────────────────────────────────────────────────────────────────
    // Test data builder
    // ─────────────────────────────────────────────────────────────────

    private StatementResponse buildStatementResponse() {
        return StatementResponse.builder()
                .id(1L)
                .userId(100L)
                .accountId(200L)
                .periodStart(LocalDate.of(2026, 2, 1))
                .periodEnd(LocalDate.of(2026, 2, 28))
                .generatedAt(Instant.now())
                .build();
    }
}