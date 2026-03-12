package com.hdfc.auditservice.auditlog;

import com.hdfc.auditservice.auditlog.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for audit log operations.
 *
 * <p>DIP (Dependency Inversion Principle) — all callers depend on this
 * interface, never on the concrete implementation. The Kafka consumer
 * and the REST controller both depend on this interface.</p>
 *
 * <p>OCP (Open/Closed Principle) — new audit strategies are added by
 * creating new implementations, never by modifying this interface.</p>
 */
public interface AuditLogService {

    /**
     * Records a new immutable audit log entry.
     *
     * <p>Called by the Kafka consumer when a TRANSACTION_CREATED
     * or FRAUD_ALERT event is received. Persists an append-only
     * record to hdfc_audit_db.</p>
     *
     * @param eventType   the type of event e.g. "TRANSACTION_CREATED"
     * @param actor       the service or user that triggered the event
     * @param description human-readable description of the event
     * @throws IllegalArgumentException if any argument is blank
     */
    void recordAuditLog(String eventType, String actor, String description);

    /**
     * Returns a paginated list of all audit log entries.
     *
     * <p>Never returns an unbounded list — pagination is mandatory
     * for a table that grows indefinitely over time.</p>
     *
     * @param pageable pagination and sorting parameters
     * @return         paginated audit log responses
     */
    Page<AuditLogResponse> getAllAuditLogs(Pageable pageable);

    /**
     * Returns a single audit log entry by its ID.
     *
     * @param id the audit log ID to look up
     * @return   the audit log response
     * @throws com.hdfc.auditservice.auditlog.exception.AuditLogNotFoundException
     *           if no audit log exists with the given ID
     */
    AuditLogResponse getAuditLogById(Long id);

    /**
     * Returns a paginated list of audit logs filtered by event type.
     *
     * @param eventType the event type to filter by e.g. "TRANSACTION_CREATED"
     * @param pageable  pagination and sorting parameters
     * @return          paginated audit log responses matching the event type
     */
    Page<AuditLogResponse> getAuditLogsByEventType(String eventType, Pageable pageable);
}