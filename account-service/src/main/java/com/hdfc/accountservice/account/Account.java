package com.hdfc.accountservice.account;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a bank account in the HDFC NetBanking system.
 *
 * <p>Supports three account types: SAVINGS, CURRENT, and FIXED_DEPOSIT.
 * Each account belongs to exactly one user (identified by userId — a foreign
 * key reference to the User Service domain, never a JPA join since services
 * own their own databases).
 *
 * <p>All monetary values are stored as DECIMAL(19,4) per banking precision
 * requirements. BigDecimal with HALF_EVEN rounding is enforced at the
 * service layer.
 *
 * <p>Annotated with @Audited for Hibernate Envers — revision history tables
 * are created automatically when Audit Service (Phase 4) is activated.
 */
@Entity
@Table(
        name = "accounts",
        indexes = {
                // userId is the most frequent WHERE clause column —
                // every "get accounts for this user" query filters by it.
                @Index(name = "idx_accounts_user_id", columnList = "user_id"),

                // accountNumber is used in lookup queries and must be unique.
                @Index(name = "idx_accounts_account_number", columnList = "account_number"),

                // accountType is used in filtering queries
                // e.g. "get all SAVINGS accounts for this user".
                @Index(name = "idx_accounts_account_type", columnList = "account_type"),

                // status is used in WHERE clauses to filter active/inactive accounts.
                @Index(name = "idx_accounts_status", columnList = "status")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The ID of the user who owns this account.
     *
     * <p>This is a plain Long — not a @ManyToOne join to a User entity.
     * Account Service and User Service own separate databases. We never
     * create a JPA foreign key across service boundaries. User existence
     * is validated via a REST call to User Service before account creation.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Unique account number assigned at account creation.
     * Format is determined by AccountNumberGenerator (see account/common).
     * Immutable after creation — never updated.
     */
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    /**
     * The type of this account.
     * Stored as a String in MySQL for readability and forward compatibility —
     * adding a new enum value never requires a schema migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    /**
     * Current balance of this account.
     *
     * <p>DECIMAL(19,4): 19 total digits, 4 decimal places.
     * This matches the JSR-354 / ISO 4217 standard for monetary storage.
     * 19 digits supports balances up to 999,999,999,999,999.9999 NGN —
     * sufficient for any realistic banking scenario.
     *
     * <p>BigDecimal with HALF_EVEN rounding is enforced at the service layer.
     * This field is never read directly by callers — always go through
     * the service layer so caching and rounding are applied correctly.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * The currency code for this account (e.g. "NGN", "USD", "GBP").
     * Defaults to NGN as the primary currency per project requirements.
     * Stored as ISO 4217 currency code string.
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Current status of this account.
     * ACTIVE: normal operation.
     * INACTIVE: temporarily suspended.
     * CLOSED: permanently closed, balance must be zero.
     * FROZEN: flagged by fraud detection, no debits allowed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    /**
     * Minimum balance requirement for this account type.
     * Enforced at the service layer before any debit operation.
     * DECIMAL(19,4) for consistency with balance field.
     */
    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumBalance;

    /**
     * Interest rate applicable to this account.
     * Used by Scheduler Service (Phase 6) to compute periodic interest.
     * Stored as a percentage value e.g. 4.50 means 4.50% per annum.
     * DECIMAL(5,2): supports rates up to 999.99%.
     */
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    /**
     * For Fixed Deposit accounts only — the date on which the FD matures.
     * Null for SAVINGS and CURRENT accounts.
     * Scheduler Service (Phase 6) uses this to trigger maturity processing.
     */
    @Column(name = "maturity_date")
    private LocalDateTime maturityDate;

    /**
     * Timestamp when this account was created.
     * Populated automatically by JPA Auditing (@EnableJpaAuditing).
     * updatable=false ensures this value is never overwritten after insert.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this account.
     * Populated automatically by JPA Auditing on every update.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}