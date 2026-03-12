package com.hdfc.schedulerservice.statement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for Statement persistence operations.
 *
 * <p>Extends JpaRepository — provides findById, save, findAll(Pageable)
 * out of the box. Only custom query methods are declared here.</p>
 *
 * <p>ISP (Interface Segregation Principle) — this interface declares
 * only the methods actually needed by StatementServiceImpl.</p>
 *
 * <p>All list methods are paginated — never return unbounded lists
 * from a table that grows indefinitely over time.</p>
 */
public interface StatementRepository extends JpaRepository<Statement, Long> {

    /**
     * Returns a paginated list of statements for a given user.
     *
     * <p>Uses idx_statement_user_id index for fast lookup.
     * Without pagination, a user with years of statements would
     * exhaust memory on a single query.</p>
     *
     * @param userId   the user ID to filter by
     * @param pageable pagination and sorting parameters
     * @return         paginated statements for the given user
     */
    Page<Statement> findByUserId(Long userId, Pageable pageable);
}