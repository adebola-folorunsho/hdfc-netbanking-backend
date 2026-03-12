package com.hdfc.schedulerservice.job;

import com.hdfc.schedulerservice.kafka.StatementEventPublisher;
import com.hdfc.schedulerservice.statement.StatementService;
import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * Spring @Scheduled cron job orchestrator.
 *
 * <p>Contains three scheduled jobs:
 * 1. Monthly statement generation — runs on the 1st of every month
 * 2. Daily interest accrual — runs every day at midnight
 * 3. OTP cleanup — runs every day at 2 AM</p>
 *
 * <p>Design Pattern: Template Method (implicit via @Scheduled)
 * Each job follows the same structure: log start, execute business
 * logic, log completion. The framework calls each method on schedule —
 * we define the steps, Spring defines when they run.</p>
 *
 * <p>SRP: this class is solely responsible for triggering scheduled
 * operations on time. It delegates all business logic to StatementService
 * and StatementEventPublisher — no business logic lives here.</p>
 *
 * <p>Interest accrual and OTP cleanup are stubbed — they log and
 * complete. Full implementation requires Account Service and User
 * Service integration which is deferred to a future sprint per YAGNI.
 * GitHub issues will be raised for both.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private final StatementService statementService;
    private final StatementEventPublisher statementEventPublisher;

    /**
     * Monthly statement generation job.
     *
     * <p>Runs at 00:00 on the 1st of every month (cron: 0 0 0 1 * *).
     * Generates a Statement record for each active account and publishes
     * a statement-ready event to Notification Service via Kafka.</p>
     *
     * <p>In a full implementation, this job would fetch all active
     * account IDs from Account Service. For portfolio purposes, a
     * hardcoded seed list demonstrates the full flow end-to-end.</p>
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void runMonthlyStatementJob() {
        log.info("Monthly statement job started — period: {}", getPreviousMonth());

        // Seed accounts — in production, fetched from Account Service
        // TODO: Replace with Account Service call (GitHub issue to be raised)
        List<Long[]> seedAccounts = List.of(
                new Long[]{1L, 101L},
                new Long[]{2L, 102L}
        );

        YearMonth period = getPreviousMonth();

        for (Long[] account : seedAccounts) {
            generateAndPublishStatement(account[0], account[1], period);
        }

        log.info("Monthly statement job completed — period: {}", period);
    }

    /**
     * Daily interest accrual job.
     *
     * <p>Runs every day at midnight (cron: 0 0 0 * * *).
     * Calculates and applies daily interest to all eligible SAVINGS
     * and FIXED_DEPOSIT accounts.</p>
     *
     * <p>Stubbed — full implementation requires Account Service
     * integration to fetch balances and apply credits.
     * TODO: Implement with Account Service call (GitHub issue to be raised)</p>
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runDailyInterestAccrualJob() {
        log.info("Daily interest accrual job started");
        // Stub — interest accrual logic deferred pending Account Service integration
        log.info("Daily interest accrual job completed (stub)");
    }

    /**
     * OTP cleanup job.
     *
     * <p>Runs every day at 02:00 (cron: 0 0 2 * * *).
     * Deletes expired OTP records from User Service to prevent
     * unbounded table growth.</p>
     *
     * <p>Stubbed — full implementation requires User Service
     * integration to delete expired OTP records.
     * TODO: Implement with User Service call (GitHub issue to be raised)</p>
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runOtpCleanupJob() {
        log.info("OTP cleanup job started");
        // Stub — OTP cleanup logic deferred pending User Service integration
        log.info("OTP cleanup job completed (stub)");
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Generates a statement for a single account and publishes the
     * statement-ready event to Kafka. Errors are caught and logged
     * so one account failure never blocks the rest.
     */
    private void generateAndPublishStatement(Long userId, Long accountId, YearMonth period) {
        try {
            StatementResponse statement = statementService.generateStatement(
                    userId, accountId, period);
            statementEventPublisher.publishStatementReady(statement);
        } catch (Exception exception) {
            log.error("Failed to generate statement — userId: {}, accountId: {}. Reason: {}",
                    userId, accountId, exception.getMessage());
        }
    }

    /**
     * Returns the previous calendar month as a YearMonth.
     * Monthly statements cover the previous month — not the current one.
     * e.g. job runs on March 1st → generates February statement.
     */
    private YearMonth getPreviousMonth() {
        return YearMonth.now().minusMonths(1);
    }
}