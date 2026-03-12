package com.hdfc.auditservice.auditlog.exception;

/**
 * Thrown when an audit log entry with the requested ID
 * cannot be found in hdfc_audit_db.
 *
 * <p>Extends RuntimeException — unchecked, consistent with the
 * exception strategy used across all HDFC NetBanking services.</p>
 */
public class AuditLogNotFoundException extends RuntimeException {

    /**
     * @param id the audit log ID that was not found
     */
    public AuditLogNotFoundException(Long id) {
        super(String.format("Audit log not found with id: %d", id));
    }
}