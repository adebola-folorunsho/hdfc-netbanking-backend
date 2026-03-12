package com.hdfc.schedulerservice.statement.exception;

/**
 * Thrown when a statement record with the requested ID
 * cannot be found in hdfc_scheduler_db.
 *
 * <p>Extends RuntimeException — unchecked, consistent with the
 * exception strategy used across all HDFC NetBanking services.</p>
 */
public class StatementNotFoundException extends RuntimeException {

    /**
     * @param id the statement ID that was not found
     */
    public StatementNotFoundException(Long id) {
        super(String.format("Statement not found with id: %d", id));
    }
}