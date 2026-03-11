package com.hdfc.transactionservice.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for {@link Transaction} persistence operations.
 *
 * <p>All list-returning methods are paginated — never return unbounded
 * result sets. Transaction history can grow to millions of records
 * for active users; pagination is non-negotiable.
 *
 * <p>Filtering methods support the transaction history endpoint
 * requirements from Section 5.3: filter by date, type, and amount.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Finds all transactions initiated by a specific user, paginated.
     *
     * <p>The most frequently called query — every "my transactions"
     * request hits this. The idx_transactions_user_id index ensures
     * this is an index scan not a full table scan.
     *
     * @param userId   the ID of the user
     * @param pageable pagination and sorting parameters
     * @return a page of transactions for the user
     */
    Page<Transaction> findByUserId(Long userId, Pageable pageable);

    /**
     * Finds all transactions for a specific user filtered by status.
     *
     * @param userId   the ID of the user
     * @param status   the transaction status to filter by
     * @param pageable pagination parameters
     * @return a page of matching transactions
     */
    Page<Transaction> findByUserIdAndStatus(
            Long userId, TransactionStatus status, Pageable pageable);

    /**
     * Finds all transactions for a specific user filtered by type.
     *
     * @param userId          the ID of the user
     * @param transactionType the transaction type to filter by
     * @param pageable        pagination parameters
     * @return a page of matching transactions
     */
    Page<Transaction> findByUserIdAndTransactionType(
            Long userId, TransactionType transactionType, Pageable pageable);

    /**
     * Finds all transactions for a specific account (as source or destination),
     * paginated.
     *
     * <p>Used for account-level transaction history — shows all movements
     * in and out of a specific account regardless of direction.
     *
     * @param accountId the account ID to search for
     * @param pageable  pagination parameters
     * @return a page of transactions involving the account
     */
    @Query("SELECT t FROM Transaction t WHERE " +
            "t.sourceAccountId = :accountId OR " +
            "t.destinationAccountId = :accountId")
    Page<Transaction> findByAccountId(
            @Param("accountId") Long accountId, Pageable pageable);

    /**
     * Finds transactions for a user within a date range, paginated.
     *
     * <p>Supports the date-range filter on the transaction history endpoint
     * per Section 5.3 requirements.
     *
     * @param userId    the ID of the user
     * @param startDate the start of the date range (inclusive)
     * @param endDate   the end of the date range (inclusive)
     * @param pageable  pagination parameters
     * @return a page of transactions within the date range
     */
    Page<Transaction> findByUserIdAndCreatedAtBetween(
            Long userId, LocalDateTime startDate,
            LocalDateTime endDate, Pageable pageable);

    /**
     * Finds transactions for a user above a minimum amount, paginated.
     *
     * <p>Supports the amount filter on the transaction history endpoint.
     *
     * @param userId    the ID of the user
     * @param minAmount the minimum transaction amount (inclusive)
     * @param pageable  pagination parameters
     * @return a page of transactions at or above the minimum amount
     */
    Page<Transaction> findByUserIdAndAmountGreaterThanEqual(
            Long userId, BigDecimal minAmount, Pageable pageable);

    /**
     * Finds a transaction by its unique transaction reference.
     *
     * <p>Used for idempotency checks — if a request arrives with a
     * reference that already exists, we return the existing transaction
     * without reprocessing. Also used by Account Service callback
     * to locate the transaction by reference.
     *
     * @param transactionReference the unique transaction reference
     * @return an Optional containing the transaction, or empty if not found
     */
    Optional<Transaction> findByTransactionReference(String transactionReference);

    /**
     * Finds a transaction by its Paystack payment reference.
     *
     * <p>Called when Paystack sends a webhook to confirm or reject
     * a payment. The Paystack reference is used to locate the
     * pending transaction and update its status accordingly.
     *
     * @param paystackReference the Paystack payment reference
     * @return an Optional containing the transaction, or empty if not found
     */
    Optional<Transaction> findByPaystackReference(String paystackReference);

    /**
     * Checks whether a transaction reference already exists.
     *
     * <p>Faster than findByTransactionReference for pure existence
     * checks — MySQL stops at the first matching row.
     *
     * @param transactionReference the reference to check
     * @return true if the reference already exists
     */
    boolean existsByTransactionReference(String transactionReference);

    /**
     * Finds all transactions for a user with a specific status
     * within a date range, paginated.
     *
     * <p>Combined filter for advanced transaction history queries.
     * Example: "show me all FAILED transactions in the last 7 days"
     *
     * @param userId    the ID of the user
     * @param status    the transaction status
     * @param startDate the start of the date range
     * @param endDate   the end of the date range
     * @param pageable  pagination parameters
     * @return a page of matching transactions
     */
    Page<Transaction> findByUserIdAndStatusAndCreatedAtBetween(
            Long userId, TransactionStatus status,
            LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable);
}