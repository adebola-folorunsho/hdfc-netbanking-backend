package com.hdfc.accountservice.account;

import com.hdfc.accountservice.account.dto.*;
import com.hdfc.accountservice.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for account management endpoints.
 *
 * <p>Controllers in this codebase follow a strict rule:
 * no business logic here. Every method does exactly three things:
 * <ol>
 *   <li>Extract what is needed from the request and SecurityContext</li>
 *   <li>Delegate entirely to AccountService</li>
 *   <li>Wrap the result in ApiResponse and return it</li>
 * </ol>
 *
 * <p>All endpoints are versioned under /api/v1/ per project standards.
 * The API Gateway routes /api/v1/accounts/** to this service.
 *
 * <p>userId is extracted from the JWT credentials field — populated
 * by JwtAuthenticationFilter from the "userId" custom claim.
 * No REST call to User Service is needed on the hot path.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    // ─────────────────────────────────────────────────────────────────
    // ACCOUNT CREATION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new bank account for the authenticated user.
     *
     * <p>userId is extracted from the JWT credentials — never from
     * the request body. This prevents a user from creating an account
     * under a different user's ID.
     *
     * @param request        the account creation request body
     * @param authentication the JWT-derived authentication principal
     * @return 201 Created with the new account details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        AccountResponse response = accountService.createAccount(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // ACCOUNT RETRIEVAL
    // ─────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single account by its ID.
     *
     * <p>CUSTOMER: can only retrieve their own accounts.
     * TELLER / ADMIN: can retrieve any account.
     * Ownership enforcement is delegated to AccountService.
     *
     * @param accountId      the account ID to retrieve
     * @param authentication the JWT-derived authentication principal
     * @return 200 OK with the account details
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable Long accountId,
            Authentication authentication) {

        Long requestingUserId = extractUserId(authentication);
        boolean isAdminOrTeller = hasAdminOrTellerRole(authentication);

        AccountResponse response = accountService.getAccountById(
                accountId, requestingUserId, isAdminOrTeller);

        return ResponseEntity.ok(
                ApiResponse.success("Account retrieved successfully", response));
    }

    /**
     * Retrieves all accounts belonging to the authenticated user, paginated.
     *
     * <p>CUSTOMER: retrieves only their own accounts (userId from JWT).
     * TELLER / ADMIN: use /user/{userId} to retrieve any user's accounts.
     *
     * @param pageable       pagination parameters (default: page=0, size=10)
     * @param authentication the JWT-derived authentication principal
     * @return 200 OK with a page of accounts
     */
    @GetMapping("/my-accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<AccountResponse>>> getMyAccounts(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        Page<AccountResponse> response =
                accountService.getAccountsByUserId(userId, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("Accounts retrieved successfully", response));
    }

    /**
     * Retrieves all accounts for a specific user by userId.
     * Restricted to TELLER and ADMIN roles.
     *
     * @param userId         the ID of the user whose accounts to retrieve
     * @param pageable       pagination parameters
     * @return 200 OK with a page of accounts
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<AccountResponse>>> getAccountsByUserId(
            @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        Page<AccountResponse> response =
                accountService.getAccountsByUserId(userId, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("Accounts retrieved successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // BALANCE
    // ─────────────────────────────────────────────────────────────────

    /**
     * Retrieves the current balance of an account.
     *
     * <p>Reads from Redis Write-Through cache (TTL: 30s) first.
     * On cache miss, falls through to MySQL.
     * Called by Transaction Service before every transfer — must be fast.
     *
     * <p>CUSTOMER: can only check their own account balance.
     * TELLER / ADMIN: can check any account balance.
     *
     * @param accountId      the account ID
     * @param authentication the JWT-derived authentication principal
     * @return 200 OK with the balance response
     */
    @GetMapping("/{accountId}/balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountBalanceResponse>> getAccountBalance(
            @PathVariable Long accountId,
            Authentication authentication) {

        Long requestingUserId = extractUserId(authentication);
        boolean isAdminOrTeller = hasAdminOrTellerRole(authentication);

        AccountBalanceResponse response = accountService.getAccountBalance(
                accountId, requestingUserId, isAdminOrTeller);

        return ResponseEntity.ok(
                ApiResponse.success("Balance retrieved successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // ACCOUNT STATUS MANAGEMENT
    // ─────────────────────────────────────────────────────────────────

    /**
     * Updates the status of an account.
     * Restricted to TELLER and ADMIN roles.
     *
     * <p>Valid transitions enforced at the service layer:
     * CLOSED is terminal — no transitions out of CLOSED.
     *
     * @param accountId the account ID to update
     * @param request   the status update request
     * @return 200 OK with the updated account
     */
    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccountStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {

        AccountResponse response =
                accountService.updateAccountStatus(accountId, request);

        return ResponseEntity.ok(
                ApiResponse.success("Account status updated successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // INTERNAL ENDPOINTS — called by Transaction Service only
    // ─────────────────────────────────────────────────────────────────

    /**
     * Debits an amount from an account.
     *
     * <p>Called exclusively by Transaction Service as part of the
     * Saga orchestration flow. Not intended for direct end-user access.
     *
     * <p>Acquires PESSIMISTIC_WRITE lock on the account row.
     * Validates sufficient balance above minimum balance requirement.
     * Updates Redis cache immediately after successful debit (Write-Through).
     *
     * @param accountId the account ID to debit
     * @param request   the debit request
     * @return 200 OK with the updated balance
     */
    @PostMapping("/{accountId}/debit")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountBalanceResponse>> debit(
            @PathVariable Long accountId,
            @Valid @RequestBody DebitCreditRequest request) {

        AccountBalanceResponse response = accountService.debit(accountId, request);

        return ResponseEntity.ok(
                ApiResponse.success("Account debited successfully", response));
    }

    /**
     * Credits an amount to an account.
     *
     * <p>Called exclusively by Transaction Service as part of the
     * Saga orchestration flow. Not intended for direct end-user access.
     *
     * <p>Acquires PESSIMISTIC_WRITE lock on the account row.
     * Updates Redis cache immediately after successful credit (Write-Through).
     *
     * @param accountId the account ID to credit
     * @param request   the credit request
     * @return 200 OK with the updated balance
     */
    @PostMapping("/{accountId}/credit")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountBalanceResponse>> credit(
            @PathVariable Long accountId,
            @Valid @RequestBody DebitCreditRequest request) {

        AccountBalanceResponse response = accountService.credit(accountId, request);

        return ResponseEntity.ok(
                ApiResponse.success("Account credited successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Extracts the userId from the JWT authentication credentials.
     *
     * <p>JwtAuthenticationFilter stores the userId (parsed from the
     * "userId" JWT claim) in the credentials field of the
     * UsernamePasswordAuthenticationToken. This avoids re-parsing
     * the JWT or making a REST call to User Service on every request.
     *
     * @param authentication the Spring Security authentication object
     * @return the authenticated user's ID
     */
    private Long extractUserId(Authentication authentication) {
        // Credentials field is populated by JwtAuthenticationFilter
        // with the userId extracted from the JWT "userId" claim.
        return (Long) authentication.getCredentials();
    }

    /**
     * Checks whether the authenticated user holds TELLER or ADMIN role.
     *
     * @param authentication the Spring Security authentication object
     * @return true if the user is a TELLER or ADMIN
     */
    private boolean hasAdminOrTellerRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role ->
                        role.equals("ROLE_ADMIN") || role.equals("ROLE_TELLER"));
    }
}