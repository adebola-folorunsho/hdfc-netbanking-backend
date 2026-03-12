package com.hdfc.schedulerservice.statement;

import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.YearMonth;

/**
 * Contract for statement generation and retrieval operations.
 *
 * <p>DIP (Dependency Inversion Principle) — the cron job and the
 * REST controller both depend on this interface, never on the
 * concrete implementation. This allows the statement generation
 * strategy to be swapped without touching callers.</p>
 *
 * <p>OCP (Open/Closed Principle) — new statement behaviours are
 * added by creating new implementations, never by modifying this
 * interface or its existing implementation.</p>
 */
public interface StatementService {

    /**
     * Generates and persists a monthly statement record for a given
     * user account and period.
     *
     * <p>Called by the monthly statement cron job. Persists an
     * append-only Statement record to hdfc_scheduler_db and returns
     * the persisted statement for Kafka event publishing.</p>
     *
     * @param userId    the ID of the user the statement belongs to
     * @param accountId the ID of the account the statement covers
     * @param period    the year and month of the statement period
     * @return          the persisted statement response
     * @throws IllegalArgumentException if any argument is null
     */
    StatementResponse generateStatement(Long userId, Long accountId, YearMonth period);

    /**
     * Returns a single statement record by its ID.
     *
     * @param id the statement ID to look up
     * @return   the statement response
     * @throws com.hdfc.schedulerservice.statement.exception.StatementNotFoundException
     *           if no statement exists with the given ID
     */
    StatementResponse getStatementById(Long id);

    /**
     * Returns a paginated list of statements for a given user.
     *
     * @param userId   the user ID to filter by
     * @param pageable pagination and sorting parameters
     * @return         paginated statement responses for the user
     */
    Page<StatementResponse> getStatementsByUserId(Long userId, Pageable pageable);
}