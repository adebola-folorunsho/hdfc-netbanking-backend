package com.hdfc.transactionservice.transaction;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a financial transaction in HDFC NetBanking.
 *
 * <p>A transaction record is immutable after creation except for its
 * status and failureReason fields — all other fields reflect the
 * state at the time the transaction was initiated and must never change.
 *
 * <p>Exchange rate at time-of-transaction is stored for auditability
 * per Section 6 of the project spec — multi-currency transfers log
 * the rate used for conversion so disputes can be resolved accurately.
 *
 * <p>All monetary values stored as DECIMAL(19,4) — BigDecimal with
 * HALF_EVEN rounding enforced at the service layer.
 *
 * <p>Annotated with @Audited for Hibernate Envers revision tracking.
 * Audit Service (Phase 4) will use the transactions_AUD table.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                // userId — most frequent filter: "show my transactions"
                @Index(name = "idx_transactions_user_id", columnList = "user_id"),

                // sourceAccountId — used in account-level transaction history
                @Index(name = "idx_transactions_source_account_id",
                        columnList = "source_account_id"),

                // destinationAccountId — used in credit-side history queries
                @Index(name = "idx_transactions_destination_account_id",
                        columnList = "destination_account_id"),

                // status — used for filtering PENDING, COMPLETED, FAILED
                @Index(name = "idx_transactions_status", columnList = "status"),

                // createdAt — used for date-range filtering and sorting
                @Index(name = "idx_transactions_created_at", columnList = "created_at"),

                // transactionReference — unique lookup for idempotency checks
                @Index(name = "idx_transactions_reference",
                        columnList = "transaction_reference"),

                // paystackReference — lookup by Paystack payment reference
                @Index(name = "idx_transactions_paystack_reference",
                        columnList = "paystack_reference")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The ID of the user who initiated this transaction.
     * Plain Long — not a JPA join to User Service entity.
     * Extracted from the JWT userId claim on every request.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Unique transaction reference generated at initiation.
     * Format: TXN-{UUID} — used for idempotency checks and
     * passed to Account Service debit/credit calls for traceability.
     * Immutable after creation.
     */
    @Column(name = "transaction_reference", nullable = false,
            unique = true, length = 50)
    private String transactionReference;

    /**
     * The type of this transaction.
     * Determines the processing path in TransactionService.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    /**
     * Current status of this transaction.
     * Starts as PENDING, transitions to COMPLETED, FAILED, or REVERSED.
     * The only mutable field after initial creation (along with failureReason).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /**
     * The account ID from which funds are debited.
     * Null for DEPOSIT transactions (credit-only operation).
     * References Account Service account ID — not a JPA join.
     */
    @Column(name = "source_account_id")
    private Long sourceAccountId;

    /**
     * The account ID to which funds are credited.
     * Null for WITHDRAWAL transactions (debit-only operation).
     * References Account Service account ID — not a JPA join.
     */
    @Column(name = "destination_account_id")
    private Long destinationAccountId;

    /**
     * The amount transferred in the source currency.
     * DECIMAL(19,4) — BigDecimal with HALF_EVEN rounding.
     * For multi-currency transfers, this is the amount before
     * conversion. The converted amount is stored separately.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code of the source amount.
     * Primary currency is NGN per project requirements.
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * The amount after currency conversion, in the destination currency.
     * Equal to amount when source and destination currencies match.
     * Populated by Currency Service (Phase 7) for cross-currency transfers.
     */
    @Column(name = "converted_amount", precision = 19, scale = 4)
    private BigDecimal convertedAmount;

    /**
     * ISO 4217 currency code of the converted amount.
     * Equal to currencyCode for same-currency transfers.
     */
    @Column(name = "converted_currency_code", length = 3)
    private String convertedCurrencyCode;

    /**
     * Exchange rate used for currency conversion at time-of-transaction.
     * Stored for auditability per Section 6 of the project spec.
     * Null for same-currency transfers.
     * DECIMAL(19,6) — 6 decimal places for exchange rate precision.
     */
    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    /**
     * Human-readable description of the transaction.
     * Provided by the initiator or auto-generated by the service.
     * Example: "Transfer to John Doe" or "Paystack payment — order #123"
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * Paystack payment reference for PAYSTACK_PAYMENT transactions.
     * Returned by Paystack on payment initialisation.
     * Used to verify payment status via Paystack webhook.
     * Null for non-Paystack transactions.
     */
    @Column(name = "paystack_reference", length = 100)
    private String paystackReference;

    /**
     * Reason for failure if status is FAILED.
     * Records which Saga step failed and why.
     * Example: "Account Service debit failed: insufficient balance"
     * Null for COMPLETED and REVERSED transactions.
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * Timestamp when this transaction was initiated.
     * Populated automatically by JPA Auditing.
     * Immutable after creation — updatable=false.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent status update.
     * Populated automatically by JPA Auditing on every update.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}