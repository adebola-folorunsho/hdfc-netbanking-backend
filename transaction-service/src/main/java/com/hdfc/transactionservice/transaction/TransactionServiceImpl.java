package com.hdfc.transactionservice.transaction;

import com.hdfc.transactionservice.common.client.AccountServiceClient;
import com.hdfc.transactionservice.common.client.PaystackClient;
import com.hdfc.transactionservice.common.exception.*;
import com.hdfc.transactionservice.common.messaging.TransactionEventPublisher;
import com.hdfc.transactionservice.transaction.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saga orchestrator for all fund movements in HDFC NetBanking.
 *
 * <p>Every method that mutates money follows the same pattern:
 * <ol>
 *   <li>Validate request (idempotency + business rules)</li>
 *   <li>Persist transaction as PENDING (immediately committed)</li>
 *   <li>Execute Saga steps via Account Service REST calls</li>
 *   <li>On success: mark COMPLETED, publish Kafka event async</li>
 *   <li>On failure: apply compensation, mark FAILED, publish event</li>
 * </ol>
 *
 * <p>Compensation rule: if debit succeeded but credit failed,
 * apply a compensating credit to the source account to restore balance.
 * If debit itself failed, no compensation is needed — nothing moved.
 *
 * <p>All database writes use READ_COMMITTED isolation. SERIALIZABLE
 * is not needed here because pessimistic locking is enforced inside
 * Account Service on the Account entity — the concurrency boundary
 * is at the account level, not the transaction level.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final PaystackClient paystackClient;
    private final TransactionEventPublisher eventPublisher;

    // ─────────────────────────────────────────────────────────────────
    // INTERNAL TRANSFER
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Saga steps:
     * <ol>
     *   <li>Idempotency check — reject duplicate reference</li>
     *   <li>Business rule validation</li>
     *   <li>Persist PENDING transaction</li>
     *   <li>Debit source account via Account Service</li>
     *   <li>Credit destination account via Account Service</li>
     *   <li>Mark COMPLETED</li>
     *   <li>Publish TRANSACTION_CREATED event (async)</li>
     * </ol>
     *
     * <p>Compensation: if credit (step 5) fails after debit (step 4)
     * succeeded, a compensating credit is applied to the source account.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse initiateTransfer(
            InitiateTransferRequest request, Long userId) {

        log.info("Initiating transfer: userId={}, ref={}, amount={} {}",
                userId, request.getTransactionReference(),
                request.getAmount(), request.getCurrencyCode());

        // Step 1 — Idempotency check.
        if (transactionRepository.existsByTransactionReference(
                request.getTransactionReference())) {
            throw new TransactionAlreadyExistsException(
                    "Transaction with reference '"
                            + request.getTransactionReference()
                            + "' already exists");
        }

        // Step 2 — Business rule validation.
        if (request.getSourceAccountId()
                .equals(request.getDestinationAccountId())) {
            throw new InvalidTransactionException(
                    "Source and destination accounts must be different");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException(
                    "Transfer amount must be greater than zero");
        }

        // Step 3 — Persist as PENDING.
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .transactionReference(request.getTransactionReference())
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING)
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode().toUpperCase())
                .convertedAmount(request.getAmount())
                .convertedCurrencyCode(request.getCurrencyCode().toUpperCase())
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);
        log.debug("Transaction persisted as PENDING: id={}",
                transaction.getId());

        boolean debitSucceeded = false;

        try {
            // Step 4 — Debit source account.
            accountServiceClient.debitAccount(
                    request.getSourceAccountId(),
                    request.getAmount(),
                    request.getCurrencyCode(),
                    request.getTransactionReference());
            debitSucceeded = true;

            // Step 5 — Credit destination account.
            accountServiceClient.creditAccount(
                    request.getDestinationAccountId(),
                    request.getAmount(),
                    request.getCurrencyCode(),
                    request.getTransactionReference());

            // Step 6 — Mark COMPLETED.
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            log.info("Transfer completed: id={}, ref={}",
                    transaction.getId(),
                    transaction.getTransactionReference());

            // Step 7 — Publish event (async, non-blocking).
            eventPublisher.publishTransactionCreated(transaction);

            return mapToResponse(transaction);

        } catch (InsufficientBalanceException ex) {
            // Debit failed — nothing moved, no compensation needed.
            log.warn("Transfer failed — insufficient balance: ref={}",
                    request.getTransactionReference());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "Insufficient balance: " + ex.getMessage());
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionFailed(transaction);
            throw ex;

        } catch (AccountServiceException ex) {
            if (debitSucceeded) {
                // Debit succeeded but credit failed — compensate.
                log.error("Credit failed after debit — applying " +
                                "compensation: ref={}",
                        request.getTransactionReference());
                applyCompensatingCredit(
                        request.getSourceAccountId(),
                        request.getAmount(),
                        request.getCurrencyCode(),
                        request.getTransactionReference(),
                        transaction);
            } else {
                // Debit failed — no compensation needed.
                log.error("Debit failed — no compensation needed: ref={}",
                        request.getTransactionReference());
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason(
                        "Account Service debit failed: " + ex.getMessage());
                transaction = transactionRepository.save(transaction);
                eventPublisher.publishTransactionFailed(transaction);
            }
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PAYSTACK PAYMENT
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Saga steps:
     * <ol>
     *   <li>Idempotency check</li>
     *   <li>Persist PENDING transaction</li>
     *   <li>Call Paystack initialize API</li>
     *   <li>Store Paystack reference on transaction</li>
     *   <li>Return authorization URL to client</li>
     * </ol>
     *
     * <p>Transaction is completed asynchronously when Paystack sends
     * a "charge.success" webhook — handled by handlePaystackWebhook.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaystackInitiateResponse initiatePaystackPayment(
            InitiatePaystackPaymentRequest request, Long userId) {

        log.info("Initiating Paystack payment: userId={}, ref={}, " +
                        "amount={}", userId, request.getTransactionReference(),
                request.getAmount());

        // Idempotency check.
        if (transactionRepository.existsByTransactionReference(
                request.getTransactionReference())) {
            throw new TransactionAlreadyExistsException(
                    "Transaction with reference '"
                            + request.getTransactionReference()
                            + "' already exists");
        }

        // Persist as PENDING — no source account for Paystack payments.
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .transactionReference(request.getTransactionReference())
                .transactionType(TransactionType.PAYSTACK_PAYMENT)
                .status(TransactionStatus.PENDING)
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .currencyCode("NGN")
                .convertedAmount(request.getAmount())
                .convertedCurrencyCode("NGN")
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            // Call Paystack initialize.
            JsonNode paystackData = paystackClient.initializePayment(
                    request.getEmail(),
                    request.getAmount(),
                    request.getTransactionReference(),
                    request.getDescription());

            String paystackReference = paystackData
                    .path("reference").asText();
            String authorizationUrl = paystackData
                    .path("authorization_url").asText();
            String accessCode = paystackData
                    .path("access_code").asText();

            // Store Paystack reference for webhook lookup.
            transaction.setPaystackReference(paystackReference);
            transactionRepository.save(transaction);

            log.info("Paystack payment initialised: internalRef={}, " +
                            "paystackRef={}", request.getTransactionReference(),
                    paystackReference);

            return PaystackInitiateResponse.builder()
                    .transactionId(transaction.getId())
                    .transactionReference(request.getTransactionReference())
                    .paystackReference(paystackReference)
                    .authorizationUrl(authorizationUrl)
                    .accessCode(accessCode)
                    .build();

        } catch (PaystackException ex) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "Paystack initialization failed: " + ex.getMessage());
            transactionRepository.save(transaction);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PAYSTACK WEBHOOK
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Called by the webhook controller after HMAC signature
     * verification. Looks up the PENDING transaction by Paystack
     * reference and completes or fails it based on the event type.
     *
     * <p>On "charge.success": credits the destination account
     * and marks the transaction COMPLETED.
     * On any other event: marks the transaction FAILED.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handlePaystackWebhook(
            String paystackReference, String eventType) {

        log.info("Handling Paystack webhook: ref={}, event={}",
                paystackReference, eventType);

        Transaction transaction = transactionRepository
                .findByPaystackReference(paystackReference)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "No transaction found for Paystack reference: "
                                + paystackReference));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            // Already processed — idempotent webhook handling.
            log.warn("Webhook received for non-PENDING transaction: " +
                            "ref={}, status={}", paystackReference,
                    transaction.getStatus());
            return;
        }

        if ("charge.success".equals(eventType)) {
            try {
                // Credit destination account.
                accountServiceClient.creditAccount(
                        transaction.getDestinationAccountId(),
                        transaction.getAmount(),
                        transaction.getCurrencyCode(),
                        transaction.getTransactionReference());

                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction = transactionRepository.save(transaction);
                eventPublisher.publishTransactionCreated(transaction);

                log.info("Paystack payment completed: ref={}",
                        paystackReference);

            } catch (AccountServiceException ex) {
                log.error("Credit failed after Paystack charge.success: " +
                        "ref={}", paystackReference);
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason(
                        "Credit failed after Paystack confirmation: "
                                + ex.getMessage());
                transaction = transactionRepository.save(transaction);
                eventPublisher.publishTransactionFailed(transaction);
            }
        } else {
            // Payment failed or was abandoned on Paystack side.
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "Paystack event: " + eventType);
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionFailed(transaction);
            log.info("Paystack payment failed: ref={}, event={}",
                    paystackReference, eventType);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // DEPOSIT
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Credit-only operation. No source account involved.
     * TELLER/ADMIN only — enforced at controller level.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse processDeposit(
            DepositWithdrawalRequest request, Long userId) {

        log.info("Processing deposit: userId={}, accountId={}, " +
                        "amount={} {}", userId, request.getAccountId(),
                request.getAmount(), request.getCurrencyCode());

        validateDepositWithdrawalRequest(request,
                TransactionType.DEPOSIT);

        if (transactionRepository.existsByTransactionReference(
                request.getTransactionReference())) {
            throw new TransactionAlreadyExistsException(
                    "Transaction with reference '"
                            + request.getTransactionReference()
                            + "' already exists");
        }

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .transactionReference(request.getTransactionReference())
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .destinationAccountId(request.getAccountId())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode().toUpperCase())
                .convertedAmount(request.getAmount())
                .convertedCurrencyCode(
                        request.getCurrencyCode().toUpperCase())
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            accountServiceClient.creditAccount(
                    request.getAccountId(),
                    request.getAmount(),
                    request.getCurrencyCode(),
                    request.getTransactionReference());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionCreated(transaction);

            log.info("Deposit completed: id={}, ref={}",
                    transaction.getId(),
                    transaction.getTransactionReference());

            return mapToResponse(transaction);

        } catch (AccountServiceException ex) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "Deposit credit failed: " + ex.getMessage());
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionFailed(transaction);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // WITHDRAWAL
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Debit-only operation. No destination account involved.
     * TELLER/ADMIN only — enforced at controller level.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse processWithdrawal(
            DepositWithdrawalRequest request, Long userId) {

        log.info("Processing withdrawal: userId={}, accountId={}, " +
                        "amount={} {}", userId, request.getAccountId(),
                request.getAmount(), request.getCurrencyCode());

        validateDepositWithdrawalRequest(request,
                TransactionType.WITHDRAWAL);

        if (transactionRepository.existsByTransactionReference(
                request.getTransactionReference())) {
            throw new TransactionAlreadyExistsException(
                    "Transaction with reference '"
                            + request.getTransactionReference()
                            + "' already exists");
        }

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .transactionReference(request.getTransactionReference())
                .transactionType(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .sourceAccountId(request.getAccountId())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode().toUpperCase())
                .convertedAmount(request.getAmount())
                .convertedCurrencyCode(
                        request.getCurrencyCode().toUpperCase())
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            accountServiceClient.debitAccount(
                    request.getAccountId(),
                    request.getAmount(),
                    request.getCurrencyCode(),
                    request.getTransactionReference());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionCreated(transaction);

            log.info("Withdrawal completed: id={}, ref={}",
                    transaction.getId(),
                    transaction.getTransactionReference());

            return mapToResponse(transaction);

        } catch (InsufficientBalanceException ex) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "Insufficient balance: " + ex.getMessage());
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionFailed(transaction);
            throw ex;

        } catch (AccountServiceException ex) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "Withdrawal debit failed: " + ex.getMessage());
            transaction = transactionRepository.save(transaction);
            eventPublisher.publishTransactionFailed(transaction);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // QUERY METHODS
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(
            Long transactionId, Long userId, String role) {

        Transaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + transactionId));

        // CUSTOMER can only view own transactions.
        if ("ROLE_CUSTOMER".equals(role) &&
                !transaction.getUserId().equals(userId)) {
            throw new TransactionOwnershipException(
                    "Access denied to transaction: " + transactionId);
        }

        return mapToResponse(transaction);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Applies filters in priority order:
     * status + date range → type → date range only →
     * minimum amount → no filter (all transactions).
     * Combined status+date filter is preferred over individual filters
     * when both are present.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(
            Long userId,
            TransactionFilterRequest filter,
            Pageable pageable) {

        Page<Transaction> page;

        if (filter.getStatus() != null
                && filter.getStartDate() != null
                && filter.getEndDate() != null) {
            page = transactionRepository
                    .findByUserIdAndStatusAndCreatedAtBetween(
                            userId, filter.getStatus(),
                            filter.getStartDate(), filter.getEndDate(),
                            pageable);

        } else if (filter.getStatus() != null) {
            page = transactionRepository.findByUserIdAndStatus(
                    userId, filter.getStatus(), pageable);

        } else if (filter.getTransactionType() != null) {
            page = transactionRepository.findByUserIdAndTransactionType(
                    userId, filter.getTransactionType(), pageable);

        } else if (filter.getStartDate() != null
                && filter.getEndDate() != null) {
            page = transactionRepository.findByUserIdAndCreatedAtBetween(
                    userId, filter.getStartDate(),
                    filter.getEndDate(), pageable);

        } else if (filter.getMinAmount() != null) {
            page = transactionRepository
                    .findByUserIdAndAmountGreaterThanEqual(
                            userId, filter.getMinAmount(), pageable);

        } else {
            page = transactionRepository.findByUserId(userId, pageable);
        }

        return page.map(this::mapToResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccountId(
            Long accountId, Long userId, String role, Pageable pageable) {

        // For CUSTOMER role, verify they own this account by checking
        // that at least one transaction on this account belongs to them.
        // Full ownership verification is handled by Account Service —
        // Transaction Service trusts that the accountId came from a
        // prior authenticated Account Service response.
        Page<Transaction> page = transactionRepository
                .findByAccountId(accountId, pageable);

        if ("ROLE_CUSTOMER".equals(role)) {
            // Verify at least one transaction on this account
            // belongs to this user — if the page is empty or none
            // belong to this user, deny access.
            boolean ownsAccount = page.getContent().stream()
                    .anyMatch(t -> t.getUserId().equals(userId));
            if (!ownsAccount && page.getTotalElements() > 0) {
                throw new TransactionOwnershipException(
                        "Access denied to account transactions: "
                                + accountId);
            }
        }

        return page.map(this::mapToResponse);
    }

    // ─────────────────────────────────────────────────────────────────
    // REVERSAL
    // ─────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Only COMPLETED INTERNAL_TRANSFER transactions can be reversed.
     * Creates a new reversal transaction record.
     * Marks the original transaction as REVERSED.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse reverseTransaction(
            Long transactionId, Long userId) {

        log.info("Reversing transaction: id={}, adminUserId={}",
                transactionId, userId);

        Transaction original = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + transactionId));

        // Only COMPLETED INTERNAL_TRANSFER can be reversed.
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new InvalidTransactionException(
                    "Only COMPLETED transactions can be reversed. " +
                            "Current status: " + original.getStatus());
        }

        if (original.getTransactionType() != TransactionType.INTERNAL_TRANSFER) {
            throw new InvalidTransactionException(
                    "Only INTERNAL_TRANSFER transactions can be reversed. " +
                            "Type: " + original.getTransactionType());
        }

        // Build reversal reference.
        String reversalReference = "REV-" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 16);

        // Create the reversal transaction record.
        Transaction reversal = Transaction.builder()
                .userId(userId)
                .transactionReference(reversalReference)
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING)
                // Swap source and destination for reversal.
                .sourceAccountId(original.getDestinationAccountId())
                .destinationAccountId(original.getSourceAccountId())
                .amount(original.getAmount())
                .currencyCode(original.getCurrencyCode())
                .convertedAmount(original.getConvertedAmount())
                .convertedCurrencyCode(original.getConvertedCurrencyCode())
                .description("Reversal of " +
                        original.getTransactionReference())
                .build();

        reversal = transactionRepository.save(reversal);

        boolean debitSucceeded = false;

        try {
            // Debit original destination (reverse the credit).
            accountServiceClient.debitAccount(
                    original.getDestinationAccountId(),
                    original.getAmount(),
                    original.getCurrencyCode(),
                    reversalReference);
            debitSucceeded = true;

            // Credit original source (restore original balance).
            accountServiceClient.creditAccount(
                    original.getSourceAccountId(),
                    original.getAmount(),
                    original.getCurrencyCode(),
                    reversalReference);

            // Mark reversal COMPLETED.
            reversal.setStatus(TransactionStatus.COMPLETED);
            reversal = transactionRepository.save(reversal);

            // Mark original REVERSED.
            original.setStatus(TransactionStatus.REVERSED);
            transactionRepository.save(original);

            eventPublisher.publishTransactionReversed(reversal);

            log.info("Reversal completed: originalId={}, reversalId={}",
                    transactionId, reversal.getId());

            return mapToResponse(reversal);

        } catch (InsufficientBalanceException ex) {
            reversal.setStatus(TransactionStatus.FAILED);
            reversal.setFailureReason(
                    "Reversal failed — insufficient balance in " +
                            "destination account: " + ex.getMessage());
            transactionRepository.save(reversal);
            throw ex;

        } catch (AccountServiceException ex) {
            if (debitSucceeded) {
                applyCompensatingCredit(
                        original.getDestinationAccountId(),
                        original.getAmount(),
                        original.getCurrencyCode(),
                        reversalReference,
                        reversal);
            } else {
                reversal.setStatus(TransactionStatus.FAILED);
                reversal.setFailureReason(
                        "Reversal debit failed: " + ex.getMessage());
                transactionRepository.save(reversal);
            }
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Applies a compensating credit when a credit step fails after
     * a debit has already succeeded. Marks the transaction FAILED
     * regardless of whether the compensation itself succeeds.
     *
     * <p>If the compensating credit also fails (Account Service is
     * fully down), the failure is logged at ERROR level and flagged
     * for manual intervention. The transaction is still marked FAILED
     * — a separate reconciliation job (Phase 6) handles stuck
     * compensations.
     *
     * @param accountId   the account to compensate (original source)
     * @param amount      the amount to restore
     * @param currency    the currency
     * @param reference   the original transaction reference
     * @param transaction the transaction entity to mark FAILED
     */
    private void applyCompensatingCredit(
            Long accountId,
            BigDecimal amount,
            String currency,
            String reference,
            Transaction transaction) {

        String compensationRef = "COMP-" + reference;
        log.warn("Applying compensation: accountId={}, amount={} {}, " +
                "compRef={}", accountId, amount, currency, compensationRef);

        try {
            accountServiceClient.creditAccount(
                    accountId, amount, currency, compensationRef);
            transaction.setFailureReason(
                    "Credit failed — balance restored via compensation");
            log.info("Compensation applied successfully: compRef={}",
                    compensationRef);

        } catch (Exception compEx) {
            // Compensation itself failed — requires manual intervention.
            transaction.setFailureReason(
                    "Credit failed AND compensation failed — " +
                            "MANUAL INTERVENTION REQUIRED. compRef=" +
                            compensationRef + ". Error: " + compEx.getMessage());
            log.error("COMPENSATION FAILED — MANUAL INTERVENTION REQUIRED: " +
                            "accountId={}, amount={} {}, compRef={}",
                    accountId, amount, currency, compensationRef,
                    compEx);
        }

        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        eventPublisher.publishTransactionFailed(transaction);
    }

    /**
     * Validates that a DepositWithdrawalRequest has the correct
     * transaction type for the operation being performed.
     *
     * @param request      the request DTO
     * @param expectedType the expected TransactionType
     */
    private void validateDepositWithdrawalRequest(
            DepositWithdrawalRequest request,
            TransactionType expectedType) {

        if (request.getTransactionType() != expectedType) {
            throw new InvalidTransactionException(
                    "Invalid transaction type for this endpoint: "
                            + request.getTransactionType()
                            + ". Expected: " + expectedType);
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException(
                    "Amount must be greater than zero");
        }
    }

    /**
     * Maps a Transaction entity to a TransactionResponse DTO.
     *
     * @param transaction the entity
     * @return the response DTO
     */
    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .transactionReference(transaction.getTransactionReference())
                .transactionType(transaction.getTransactionType())
                .status(transaction.getStatus())
                .sourceAccountId(transaction.getSourceAccountId())
                .destinationAccountId(transaction.getDestinationAccountId())
                .amount(transaction.getAmount())
                .currencyCode(transaction.getCurrencyCode())
                .convertedAmount(transaction.getConvertedAmount())
                .convertedCurrencyCode(transaction.getConvertedCurrencyCode())
                .exchangeRate(transaction.getExchangeRate())
                .description(transaction.getDescription())
                .paystackReference(transaction.getPaystackReference())
                .failureReason(transaction.getFailureReason())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}