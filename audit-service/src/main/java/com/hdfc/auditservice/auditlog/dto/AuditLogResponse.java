package com.hdfc.auditservice.auditlog.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * DTO returned by AuditLogService to callers.
 *
 * <p>Never exposes the AuditLog JPA entity directly across service
 * or package boundaries — DTOs at all boundaries is a strict rule
 * in this codebase to prevent tight coupling between the persistence
 * layer and the API layer.</p>
 *
 * <p>Immutable by design — all fields set at construction via Builder.
 * Audit records must never be mutated after creation.</p>
 *
 * @see com.hdfc.auditservice.auditlog.AuditLogService
 */
@Getter
@Builder
public class AuditLogResponse {

    /** The unique identifier of this audit log entry. */
    private final Long id;

    /**
     * The type of event that triggered this audit entry.
     * e.g. TRANSACTION_CREATED, FRAUD_ALERT
     */
    private final String eventType;

    /**
     * The service or user that triggered the event.
     * e.g. "transaction-service", "user-123"
     */
    private final String actor;

    /**
     * Human-readable description of the audited event.
     * e.g. "Fund transfer of NGN 5000 from account 1 to account 2"
     */
    private final String description;

    /**
     * UTC timestamp of when this audit record was created.
     * ISO-8601 format — consistent with platform-wide timestamp standard.
     */
    private final Instant createdAt;
}