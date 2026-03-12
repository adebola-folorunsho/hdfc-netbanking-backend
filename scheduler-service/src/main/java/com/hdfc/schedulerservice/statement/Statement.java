package com.hdfc.schedulerservice.statement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a monthly account statement record.
 *
 * <p>Written once by the monthly statement cron job and never updated.
 * Append-only by design — statements are historical records and must
 * not be mutated after generation.</p>
 *
 * <p>DB indexes on userId and periodStart — the most common filter
 * columns in admin statement queries. Without indexes, queries over
 * a large statement table will cause full table scans.</p>
 */
@Entity
@Table(name = "statements", indexes = {
        @Index(name = "idx_statement_user_id", columnList = "user_id"),
        @Index(name = "idx_statement_period_start", columnList = "period_start")
})
@Getter
@Setter
@NoArgsConstructor
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The ID of the user this statement belongs to.
     * Indexed for fast lookup of all statements for a given user.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * The ID of the account this statement covers.
     * A user may have multiple accounts — each gets its own statement.
     */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * The first day of the statement period.
     * e.g. 2026-02-01 for a February statement.
     */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /**
     * The last day of the statement period.
     * e.g. 2026-02-28 for a February statement.
     */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /**
     * UTC timestamp of when this statement was generated.
     * Set once at construction via @PrePersist — never updated.
     */
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    /**
     * Sets generatedAt automatically before first persist.
     * updatable = false on the column ensures the DB never
     * allows this field to be overwritten after insert.
     */
    @PrePersist
    protected void onPersist() {
        this.generatedAt = Instant.now();
    }
}