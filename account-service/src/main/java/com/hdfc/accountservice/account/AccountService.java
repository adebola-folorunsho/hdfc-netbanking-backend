package com.hdfc.accountservice.account;

import com.hdfc.accountservice.account.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for all account management operations in HDFC NetBanking.
 *
 * <p>DESIGN PATTERN — Dependency Inversion (DIP):
 * Controllers and any other callers depend on this interface, never
 * on AccountServiceImpl directly. This decouples the caller from the
 * implementation — the implementation can change (caching strategy,
 * validation rules) without any change to the controller.
 *
 * <p>DESIGN PATTERN — Interface Segregation (ISP):
 * This interface covers account lifecycle operations only.
 * Balance operations that are called by Transaction Service are
 * intentionally included here because they operate on the same
 * Account aggregate — not split into a separate interface, which
 * would violate CCP (Common Closure Principle).
 */
public interface AccountService {

    /**
     * Creates a new bank account for the authenticated user.
     *
     * @param request  the account creation request
     * @param userId   the ID of the authenticated user (from JWT)
     * @return the created account response
     */
    AccountResponse createAccount(CreateAccountRequest request, Long userId);

    /**
     * Retrieves a single account by its ID.
     * Enforces ownership — a CUSTOMER can only retrieve their own account.
     *
     * @param accountId       the account ID to retrieve
     * @param requestingUserId the ID of the user making the request
     * @param isAdminOrTeller  true if the requester holds TELLER or ADMIN role
     * @return the account response
     */
    AccountResponse getAccountById(Long accountId, Long requestingUserId,
                                   boolean isAdminOrTeller);

    /**
     * Retrieves all accounts belonging to a user, paginated.
     *
     * @param userId   the ID of the user whose accounts to retrieve
     * @param pageable pagination and sorting parameters
     * @return a page of account responses
     */
    Page<AccountResponse> getAccountsByUserId(Long userId, Pageable pageable);

    /**
     * Retrieves the current balance of an account.
     * Reads from Redis Write-Through cache first (TTL: 30s).
     * Called by Transaction Service before every transfer.
     *
     * @param accountId        the account ID
     * @param requestingUserId the ID of the user making the request
     * @param isAdminOrTeller  true if the requester holds TELLER or ADMIN role
     * @return the balance response
     */
    AccountBalanceResponse getAccountBalance(Long accountId, Long requestingUserId,
                                             boolean isAdminOrTeller);

    /**
     * Updates the status of an account (ACTIVE, INACTIVE, FROZEN, CLOSED).
     * Enforces valid status transitions at the service layer.
     *
     * @param accountId the account ID to update
     * @param request   the status update request
     * @return the updated account response
     */
    AccountResponse updateAccountStatus(Long accountId,
                                        UpdateAccountStatusRequest request);

    /**
     * Debits an amount from an account.
     * Acquires a PESSIMISTIC_WRITE lock on the account row.
     * Validates: account ACTIVE, sufficient balance above minimum.
     * Updates Redis cache immediately after successful debit (Write-Through).
     *
     * @param accountId the account ID to debit
     * @param request   the debit request containing amount and reference
     * @return the updated balance response
     */
    AccountBalanceResponse debit(Long accountId, DebitCreditRequest request);

    /**
     * Credits an amount to an account.
     * Acquires a PESSIMISTIC_WRITE lock on the account row.
     * Updates Redis cache immediately after successful credit (Write-Through).
     *
     * @param accountId the account ID to credit
     * @param request   the credit request containing amount and reference
     * @return the updated balance response
     */
    AccountBalanceResponse credit(Long accountId, DebitCreditRequest request);
}