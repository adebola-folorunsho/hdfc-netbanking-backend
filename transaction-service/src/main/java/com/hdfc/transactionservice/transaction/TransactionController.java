package com.hdfc.transactionservice.transaction;

import com.hdfc.transactionservice.common.client.PaystackClient;
import com.hdfc.transactionservice.common.dto.ApiResponse;
import com.hdfc.transactionservice.transaction.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for all transaction endpoints in HDFC NetBanking.
 *
 * <p>userId is extracted from {@code authentication.getCredentials()}
 * — populated by JwtAuthenticationFilter from the JWT userId claim.
 * This is consistent with the Account Service pattern.
 *
 * <p>Role is extracted from the first granted authority on the
 * authentication object — set by JwtAuthenticationFilter from
 * the JWT role claim.
 *
 * <p>Controllers never catch exceptions — all exception handling
 * is delegated to GlobalExceptionHandler via @RestControllerAdvice.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;
    private final PaystackClient paystackClient;

    // ─────────────────────────────────────────────────────────────────
    // INTERNAL TRANSFER
    // ─────────────────────────────────────────────────────────────────

    /**
     * Initiates an internal fund transfer between two HDFC accounts.
     *
     * <p>Available to all authenticated roles.
     * CUSTOMER can only transfer from their own accounts —
     * enforced by Account Service ownership check during debit.
     *
     * POST /api/v1/transactions/transfer
     */
    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateTransfer(
            @Valid @RequestBody InitiateTransferRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();

        log.debug("Transfer request: userId={}, ref={}",
                userId, request.getTransactionReference());

        TransactionResponse response =
                transactionService.initiateTransfer(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Transfer initiated successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // PAYSTACK PAYMENT
    // ─────────────────────────────────────────────────────────────────

    /**
     * Initiates a Paystack payment for external funding.
     *
     * <p>Returns the Paystack authorization URL for client redirect.
     * Transaction is completed asynchronously via webhook.
     *
     * POST /api/v1/transactions/paystack/initiate
     */
    @PostMapping("/paystack/initiate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaystackInitiateResponse>> initiatePaystackPayment(
            @Valid @RequestBody InitiatePaystackPaymentRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();

        log.debug("Paystack payment request: userId={}, ref={}",
                userId, request.getTransactionReference());

        PaystackInitiateResponse response =
                transactionService.initiatePaystackPayment(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Paystack payment initiated successfully", response));
    }

    /**
     * Handles Paystack webhook events.
     *
     * <p>This endpoint is permitted without JWT authentication —
     * it is called by Paystack servers, not by users.
     * Authentication is via HMAC-SHA512 signature verification
     * on the raw request body bytes using PAYSTACK_WEBHOOK_SECRET.
     *
     * POST /api/v1/transactions/webhook/paystack
     */
    @PostMapping("/webhook/paystack")
    public ResponseEntity<Void> handlePaystackWebhook(
            @RequestBody byte[] rawPayload,
            @RequestHeader("x-paystack-signature") String signature) {

        log.debug("Paystack webhook received");

        // Verify HMAC-SHA512 signature — reject if invalid.
        // This prevents malicious actors from faking webhook events.
        if (!paystackClient.isValidWebhookSignature(rawPayload, signature)) {
            log.warn("Paystack webhook rejected — invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node =
                    mapper.readTree(rawPayload);

            String eventType = node.path("event").asText();
            String paystackReference = node
                    .path("data").path("reference").asText();

            if (eventType.isBlank() || paystackReference.isBlank()) {
                log.warn("Paystack webhook missing event or reference");
                return ResponseEntity.badRequest().build();
            }

            transactionService.handlePaystackWebhook(
                    paystackReference, eventType);

            return ResponseEntity.ok().build();

        } catch (Exception ex) {
            log.error("Paystack webhook processing error: {}",
                    ex.getMessage(), ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // DEPOSIT AND WITHDRAWAL
    // ─────────────────────────────────────────────────────────────────

    /**
     * Processes a deposit into an account.
     * TELLER and ADMIN only.
     *
     * POST /api/v1/transactions/deposit
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> processDeposit(
            @Valid @RequestBody DepositWithdrawalRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();

        log.debug("Deposit request: userId={}, accountId={}, amount={}",
                userId, request.getAccountId(), request.getAmount());

        TransactionResponse response =
                transactionService.processDeposit(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Deposit processed successfully", response));
    }

    /**
     * Processes a withdrawal from an account.
     * TELLER and ADMIN only.
     *
     * POST /api/v1/transactions/withdrawal
     */
    @PostMapping("/withdrawal")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> processWithdrawal(
            @Valid @RequestBody DepositWithdrawalRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();

        log.debug("Withdrawal request: userId={}, accountId={}, amount={}",
                userId, request.getAccountId(), request.getAmount());

        TransactionResponse response =
                transactionService.processWithdrawal(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Withdrawal processed successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // QUERY ENDPOINTS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single transaction by ID.
     *
     * <p>CUSTOMER can only retrieve their own transactions.
     * TELLER and ADMIN can retrieve any transaction.
     *
     * GET /api/v1/transactions/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();
        String role = authentication.getAuthorities()
                .iterator().next().getAuthority();

        TransactionResponse response =
                transactionService.getTransactionById(id, userId, role);

        return ResponseEntity.ok(ApiResponse.success(
                "Transaction retrieved successfully", response));
    }

    /**
     * Retrieves paginated transaction history for the authenticated user.
     *
     * <p>Supports optional filtering via query parameters:
     * ?status=COMPLETED
     * &transactionType=INTERNAL_TRANSFER
     * &startDate=2026-01-01T00:00:00
     * &endDate=2026-03-10T23:59:59
     * &minAmount=1000.00
     *
     * <p>Default sort: createdAt DESC (most recent first).
     * Default page size: 20.
     *
     * GET /api/v1/transactions/my-transactions
     */
    @GetMapping("/my-transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getMyTransactions(
            @ModelAttribute TransactionFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();

        Page<TransactionResponse> page =
                transactionService.getMyTransactions(userId, filter, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Transactions retrieved successfully", page));
    }

    /**
     * Retrieves paginated transaction history for a specific account.
     *
     * <p>Returns all transactions where the account is source
     * or destination. CUSTOMER can only query their own accounts.
     *
     * GET /api/v1/transactions/account/{accountId}
     */
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>>
    getTransactionsByAccountId(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();
        String role = authentication.getAuthorities()
                .iterator().next().getAuthority();

        Page<TransactionResponse> page =
                transactionService.getTransactionsByAccountId(
                        accountId, userId, role, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Account transactions retrieved successfully", page));
    }

    // ─────────────────────────────────────────────────────────────────
    // REVERSAL
    // ─────────────────────────────────────────────────────────────────

    /**
     * Reverses a completed internal transfer.
     * ADMIN only.
     *
     * POST /api/v1/transactions/{id}/reverse
     */
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverseTransaction(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = (Long) authentication.getCredentials();

        log.debug("Reversal request: transactionId={}, adminUserId={}",
                id, userId);

        TransactionResponse response =
                transactionService.reverseTransaction(id, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Transaction reversed successfully", response));
    }
}