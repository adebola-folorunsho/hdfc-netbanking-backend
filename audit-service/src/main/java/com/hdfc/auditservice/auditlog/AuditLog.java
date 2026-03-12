package com.hdfc.auditservice.auditlog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable audit log record — the system-wide audit trail.
 *
 * <p>Append-only by design — records are never updated or deleted.
 * This guarantees a tamper-proof audit trail for compliance purposes.
 * No @Version or soft-delete fields — auditability requires permanence.</p>
 *
 * <p>createdAt is set once at construction and never changed.
 * ISO-8601 timestamp stored as Instant — timezone-independent,
 * consistent with the platform-wide timestamp standard.</p>
 *
 * <p>DB indexes on eventType and createdAt — these are the most
 * common filter columns in admin audit queries. Without indexes,
 * queries over large audit tables will cause full table scans.</p>
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The type of event that triggered this audit entry.
     * e.g. TRANSACTION_CREATED, FRAUD_ALERT
     * Indexed for fast filtering by event type in admin queries.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * The service or user that triggered the event.
     * e.g. "transaction-service", "user-123"
     * Identifies who or what caused the audited action.
     */
    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    /**
     * Human-readable description of the audited event.
     * e.g. "Fund transfer of NGN 5000 from account 1 to account 2"
     * columnDefinition TEXT — descriptions can be long.
     */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * UTC timestamp of when this audit record was created.
     * Set once at construction — never updated.
     * Indexed for fast range queries by date in admin reports.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Sets createdAt automatically before first persist.
     * updatable = false on the column ensures the DB never
     * allows this field to be overwritten after insert.
     */
    @PrePersist
    protected void onPersist() {
        this.createdAt = Instant.now();
    }
}