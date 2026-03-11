package com.hdfc.transactionservice.transaction;

import com.hdfc.transactionservice.transaction.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for all transaction operations in HDFC NetBanking.
 *
 * <p>DESIGN PATTERN — Dependency Inversion Principle (DIP):
 * Controllers and tests depend on this interface, never on the
 * implementation directly. Enables mock injection in unit tests
 * without loading Spring context.
 *
 * <p>This service is the Saga orchestrator for all fund movements:
 * <ol>
 *   <li>Validate request (idempotency, business rules)</li>
 *   <li>Persist transaction as PENDING</li>
 *   <li>Call Account Service for debit (source account)</li>
 *   <li>Call Account Service for credit (destination account)</li>
 *   <li>Mark transaction COMPLETED</li>
 *   <li>Publish TRANSACTION_CREATED Kafka event (async)</li>
 * </ol>
 *
 * <p>If step 4 fails after step 3 succeeded, a compensating credit
 * is applied to the source account and the transaction is marked FAILED.
 */
public interface TransactionService {

    /**
     * Initiates an internal fund transfer between two HDFC accounts.
     *
     * <p>Orchestrates the full Saga: debit source, credit destination,
     * compensate on failure.
     *
     * @param request the transfer request
     * @param userId  the ID of the authenticated user
     * @return the completed or failed transaction response
     */
    TransactionResponse initiateTransfer(
            InitiateTransferRequest request, Long userId);

    /**
     * Initiates a Paystack payment for external funding.
     *
     * <p>Creates a PENDING transaction and calls Paystack API to
     * initialise payment. Returns the Paystack authorization URL
     * for client redirect. Transaction is completed when Paystack
     * webhook confirms payment.
     *
     * @param request the Paystack payment request
     * @param userId  the ID of the authenticated user
     * @return Paystack initialisation response with authorization URL
     */
    PaystackInitiateResponse initiatePaystackPayment(
            InitiatePaystackPaymentRequest request, Long userId);

    /**
     * Handles Paystack webhook events.
     *
     * <p>Called by the webhook controller after signature verification.
     * Looks up the pending transaction by Paystack reference and
     * updates its status based on the webhook event type.
     *
     * @param paystackReference the Paystack payment reference
     * @param eventType         the Paystack event type (e.g. "charge.success")
     */
    void handlePaystackWebhook(String paystackReference, String eventType);

    /**
     * Processes a TELLER/ADMIN deposit into an account.
     *
     * @param request the deposit request
     * @param userId  the ID of the authenticated teller or admin
     * @return the completed transaction response
     */
    TransactionResponse processDeposit(
            DepositWithdrawalRequest request, Long userId);

    /**
     * Processes a TELLER/ADMIN withdrawal from an account.
     *
     * @param request the withdrawal request
     * @param userId  the ID of the authenticated teller or admin
     * @return the completed transaction response
     */
    TransactionResponse processWithdrawal(
            DepositWithdrawalRequest request, Long userId);

    /**
     * Retrieves a single transaction by ID.
     *
     * <p>CUSTOMER can only retrieve their own transactions.
     * TELLER and ADMIN can retrieve any transaction.
     * Ownership check is enforced inside the implementation.
     *
     * @param transactionId the transaction ID
     * @param userId        the ID of the authenticated user
     * @param role          the role of the authenticated user
     * @return the transaction response
     */
    TransactionResponse getTransactionById(
            Long transactionId, Long userId, String role);

    /**
     * Retrieves paginated transaction history for the authenticated user.
     *
     * <p>Supports optional filtering by status, type, date range,
     * and minimum amount per Section 5.3 requirements.
     *
     * @param userId   the ID of the authenticated user
     * @param filter   optional filter parameters
     * @param pageable pagination and sorting parameters
     * @return a page of transaction responses
     */
    Page<TransactionResponse> getMyTransactions(
            Long userId, TransactionFilterRequest filter, Pageable pageable);

    /**
     * Retrieves paginated transaction history for a specific account.
     *
     * <p>Returns all transactions where the account is source or destination.
     * CUSTOMER can only query their own accounts — enforced via Account
     * Service ownership check.
     *
     * @param accountId the account ID
     * @param userId    the ID of the authenticated user
     * @param role      the role of the authenticated user
     * @param pageable  pagination parameters
     * @return a page of transaction responses
     */
    Page<TransactionResponse> getTransactionsByAccountId(
            Long accountId, Long userId, String role, Pageable pageable);

    /**
     * Reverses a completed transaction (ADMIN only).
     *
     * <p>Creates compensating debit/credit pair:
     * debit the destination account, credit the source account.
     * Marks the original transaction as REVERSED.
     * A new Transaction record is created for the reversal.
     *
     * @param transactionId the ID of the transaction to reverse
     * @param userId        the ID of the authenticated admin
     * @return the reversal transaction response
     */
    TransactionResponse reverseTransaction(Long transactionId, Long userId);
}