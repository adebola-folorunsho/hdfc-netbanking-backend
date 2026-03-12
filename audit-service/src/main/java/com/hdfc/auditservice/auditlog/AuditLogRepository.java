package com.hdfc.auditservice.auditlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for AuditLog persistence operations.
 *
 * <p>Extends JpaRepository — provides findById, save, findAll(Pageable)
 * out of the box. Only custom query methods are declared here.</p>
 *
 * <p>ISP (Interface Segregation Principle) — this interface declares
 * only the methods actually needed by AuditLogServiceImpl. No unused
 * methods are forced onto consumers.</p>
 *
 * <p>All list methods are paginated — never return unbounded lists
 * from a potentially large audit table.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Returns a paginated list of audit logs filtered by event type.
     *
     * <p>Uses the idx_audit_event_type index for fast lookup.
     * Without pagination, a TRANSACTION_CREATED query over millions
     * of records would exhaust memory.</p>
     *
     * @param eventType the event type to filter by e.g. "TRANSACTION_CREATED"
     * @param pageable  pagination and sorting parameters
     * @return          paginated audit logs matching the event type
     */
    Page<AuditLog> findByEventType(String eventType, Pageable pageable);
}