package com.hdfc.accountservice.account;

import com.hdfc.accountservice.account.dto.*;
import com.hdfc.accountservice.common.exception.*;
import com.hdfc.accountservice.common.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Implementation of {@link AccountService}.
 *
 * <p>All monetary arithmetic uses BigDecimal with HALF_EVEN rounding
 * and 4 decimal places — required by banking regulations.
 * HALF_EVEN (banker's rounding) minimises cumulative rounding error
 * across large numbers of transactions.
 *
 * <p>Balance reads use Redis Write-Through cache (TTL: 30s).
 * Balance writes (debit/credit) update both Redis and MySQL atomically
 * within a single SERIALIZABLE transaction.
 *
 * <p>DESIGN PATTERN — Template Method:
 * debit() and credit() share a common structure:
 * 1. Validate account exists and is ACTIVE
 * 2. Acquire PESSIMISTIC_WRITE lock
 * 3. Apply business rules (balance check for debit)
 * 4. Persist updated balance
 * 5. Update Redis cache
 * The steps are the same — only step 3 differs between debit and credit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    // Scale for all monetary BigDecimal operations.
    // HALF_EVEN is banker's rounding — required by banking regulations.
    // It rounds to the nearest even digit when exactly halfway,
    // minimising cumulative rounding bias across many transactions.
    private static final int MONETARY_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    // Default minimum balances per account type in NGN.
    // These are applied at account creation if not overridden.
    private static final BigDecimal SAVINGS_MINIMUM_BALANCE =
            new BigDecimal("1000.0000");
    private static final BigDecimal CURRENT_MINIMUM_BALANCE =
            new BigDecimal("0.0000");
    private static final BigDecimal FIXED_DEPOSIT_MINIMUM_BALANCE =
            new BigDecimal("10000.0000");

    // Default interest rates per account type (annual percentage).
    private static final BigDecimal SAVINGS_INTEREST_RATE =
            new BigDecimal("4.50");
    private static final BigDecimal CURRENT_INTEREST_RATE =
            new BigDecimal("0.00");
    private static final BigDecimal FIXED_DEPOSIT_INTEREST_RATE =
            new BigDecimal("8.50");

    // Redis cache name — must match CacheConfig and key pattern.
    // Full key: account:balance:{accountId}
    private static final String BALANCE_CACHE = "account:balance";

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new account for the user. Enforces:
     * <ul>
     *   <li>One account per type per user</li>
     *   <li>Initial deposit meets minimum balance for account type</li>
     *   <li>Maturity period provided for FIXED_DEPOSIT accounts</li>
     * </ul>
     */
    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, Long userId) {
        validateNoDuplicateAccount(userId, request.getAccountType());

        BigDecimal initialDeposit = round(request.getInitialDeposit());
        BigDecimal minimumBalance = resolveMinimumBalance(request.getAccountType());
        BigDecimal interestRate = resolveInterestRate(request.getAccountType());

        validateInitialDeposit(initialDeposit, minimumBalance, request.getAccountType());

        LocalDateTime maturityDate = resolveMaturityDate(
                request.getAccountType(), request.getMaturityPeriodMonths());

        String accountNumber = accountNumberGenerator.generate();

        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .balance(initialDeposit)
                .currencyCode(request.getCurrencyCode().toUpperCase())
                .status(AccountStatus.ACTIVE)
                .minimumBalance(minimumBalance)
                .interestRate(interestRate)
                .maturityDate(maturityDate)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Created {} account {} for user {}",
                saved.getAccountType(), saved.getAccountNumber(), userId);

        return mapToAccountResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enforces ownership for CUSTOMER role callers.
     */
    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId, Long requestingUserId,
                                          boolean isAdminOrTeller) {
        Account account = findAccountOrThrow(accountId);
        enforceOwnership(account, requestingUserId, isAdminOrTeller);
        return mapToAccountResponse(account);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccountsByUserId(Long userId, Pageable pageable) {
        return accountRepository.findByUserId(userId, pageable)
                .map(this::mapToAccountResponse);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads from Redis Write-Through cache first.
     * Cache key: account:balance:{accountId}
     * On cache miss, Spring loads from MySQL and populates the cache.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = BALANCE_CACHE, key = "#accountId")
    public AccountBalanceResponse getAccountBalance(Long accountId,
                                                    Long requestingUserId,
                                                    boolean isAdminOrTeller) {
        Account account = findAccountOrThrow(accountId);
        enforceOwnership(account, requestingUserId, isAdminOrTeller);
        return mapToBalanceResponse(account);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates status transition before persisting.
     * CLOSED is a terminal state — no transitions out of CLOSED.
     */
    @Override
    @Transactional
    public AccountResponse updateAccountStatus(Long accountId,
                                               UpdateAccountStatusRequest request) {
        Account account = findAccountOrThrow(accountId);
        validateStatusTransition(account.getStatus(), request.getStatus());

        AccountStatus previousStatus = account.getStatus();
        account.setStatus(request.getStatus());
        Account saved = accountRepository.save(account);

        log.info("Account {} status updated from {} to {}",
                account.getAccountNumber(), previousStatus, request.getStatus());

        return mapToAccountResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses SERIALIZABLE isolation and PESSIMISTIC_WRITE lock to prevent
     * concurrent debits from producing a negative balance.
     * Updates Redis cache immediately after successful debit.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CachePut(value = BALANCE_CACHE, key = "#accountId")
    public AccountBalanceResponse debit(Long accountId, DebitCreditRequest request) {
        // PESSIMISTIC_WRITE lock — SELECT ... FOR UPDATE.
        // No other transaction can read or write this row until we commit.
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found with id: " + accountId));

        validateAccountIsActive(account);
        validateCurrencyMatch(account, request.getCurrencyCode());

        BigDecimal amount = round(request.getAmount());
        BigDecimal newBalance = round(account.getBalance().subtract(amount));

        // Enforce minimum balance — debit is rejected if the resulting
        // balance would fall below the account's minimum balance requirement.
        if (newBalance.compareTo(account.getMinimumBalance()) < 0) {
            throw new InsufficientBalanceException(String.format(
                    "Insufficient balance on account %s. " +
                            "Available: %s %s, Required: %s %s, Minimum balance: %s %s",
                    account.getAccountNumber(),
                    account.getBalance(), account.getCurrencyCode(),
                    amount, account.getCurrencyCode(),
                    account.getMinimumBalance(), account.getCurrencyCode()));
        }

        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);

        log.info("Debited {} {} from account {} (ref: {}). New balance: {}",
                amount, account.getCurrencyCode(),
                account.getAccountNumber(),
                request.getTransactionReference(),
                newBalance);

        return mapToBalanceResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses SERIALIZABLE isolation and PESSIMISTIC_WRITE lock.
     * Credits are permitted on ACTIVE accounts only.
     * Updates Redis cache immediately after successful credit.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CachePut(value = BALANCE_CACHE, key = "#accountId")
    public AccountBalanceResponse credit(Long accountId, DebitCreditRequest request) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found with id: " + accountId));

        validateAccountIsActive(account);
        validateCurrencyMatch(account, request.getCurrencyCode());

        BigDecimal amount = round(request.getAmount());
        BigDecimal newBalance = round(account.getBalance().add(amount));

        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);

        log.info("Credited {} {} to account {} (ref: {}). New balance: {}",
                amount, account.getCurrencyCode(),
                account.getAccountNumber(),
                request.getTransactionReference(),
                newBalance);

        return mapToBalanceResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPER METHODS
    // Each method does exactly one thing — SRP at method level.
    // ─────────────────────────────────────────────────────────────────

    /**
     * Finds an account by ID or throws AccountNotFoundException.
     * Centralises the "find or throw" pattern to avoid repetition.
     */
    private Account findAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found with id: " + accountId));
    }

    /**
     * Enforces that a CUSTOMER can only access their own account.
     * TELLER and ADMIN roles bypass this check.
     */
    private void enforceOwnership(Account account, Long requestingUserId,
                                  boolean isAdminOrTeller) {
        if (!isAdminOrTeller &&
                !Objects.equals(account.getUserId(), requestingUserId)) {
            throw new AccountOwnershipException(String.format(
                    "User %d does not have access to account %s",
                    requestingUserId, account.getAccountNumber()));
        }
    }

    /**
     * Validates that the user does not already hold an account
     * of the requested type. One account per type per user.
     */
    private void validateNoDuplicateAccount(Long userId, AccountType accountType) {
        if (accountRepository.existsByUserIdAndAccountType(userId, accountType)) {
            throw new DuplicateAccountException(String.format(
                    "User %d already holds a %s account", userId, accountType));
        }
    }

    /**
     * Validates the initial deposit meets the minimum balance requirement
     * for the requested account type.
     */
    private void validateInitialDeposit(BigDecimal initialDeposit,
                                        BigDecimal minimumBalance,
                                        AccountType accountType) {
        if (initialDeposit.compareTo(minimumBalance) < 0) {
            throw new InvalidAccountOperationException(String.format(
                    "Initial deposit %s is below the minimum balance %s " +
                            "required for a %s account",
                    initialDeposit, minimumBalance, accountType));
        }
    }

    /**
     * Validates that the account is ACTIVE before any debit or credit.
     */
    private void validateAccountIsActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(String.format(
                    "Account %s is %s and cannot be operated on",
                    account.getAccountNumber(), account.getStatus()));
        }
    }

    /**
     * Validates that the request currency matches the account currency.
     * Cross-currency operations are rejected at this layer —
     * Transaction Service must convert before calling debit/credit.
     */
    private void validateCurrencyMatch(Account account, String requestCurrencyCode) {
        if (!account.getCurrencyCode().equalsIgnoreCase(requestCurrencyCode)) {
            throw new InvalidAccountOperationException(String.format(
                    "Currency mismatch: account currency is %s " +
                            "but request currency is %s",
                    account.getCurrencyCode(), requestCurrencyCode));
        }
    }

    /**
     * Validates that the requested status transition is permitted.
     * CLOSED is a terminal state — no transitions out of it.
     */
    private void validateStatusTransition(AccountStatus current,
                                          AccountStatus requested) {
        if (current == AccountStatus.CLOSED) {
            throw new InvalidAccountOperationException(
                    "Cannot change status of a CLOSED account — " +
                            "CLOSED is a terminal state");
        }
        if (current == requested) {
            throw new InvalidAccountOperationException(String.format(
                    "Account is already in %s status", current));
        }
    }

    /**
     * Resolves the default minimum balance for the given account type.
     */
    private BigDecimal resolveMinimumBalance(AccountType accountType) {
        return switch (accountType) {
            case SAVINGS -> SAVINGS_MINIMUM_BALANCE;
            case CURRENT -> CURRENT_MINIMUM_BALANCE;
            case FIXED_DEPOSIT -> FIXED_DEPOSIT_MINIMUM_BALANCE;
        };
    }

    /**
     * Resolves the default interest rate for the given account type.
     */
    private BigDecimal resolveInterestRate(AccountType accountType) {
        return switch (accountType) {
            case SAVINGS -> SAVINGS_INTEREST_RATE;
            case CURRENT -> CURRENT_INTEREST_RATE;
            case FIXED_DEPOSIT -> FIXED_DEPOSIT_INTEREST_RATE;
        };
    }

    /**
     * Resolves the maturity date for FIXED_DEPOSIT accounts.
     * Returns null for SAVINGS and CURRENT accounts.
     * Throws if FIXED_DEPOSIT is requested without a maturity period.
     */
    private LocalDateTime resolveMaturityDate(AccountType accountType,
                                              Integer maturityPeriodMonths) {
        if (accountType != AccountType.FIXED_DEPOSIT) {
            return null;
        }
        if (maturityPeriodMonths == null || maturityPeriodMonths <= 0) {
            throw new InvalidAccountOperationException(
                    "Maturity period in months is required for FIXED_DEPOSIT accounts " +
                            "and must be greater than zero");
        }
        return LocalDateTime.now().plusMonths(maturityPeriodMonths);
    }

    /**
     * Applies HALF_EVEN rounding to 4 decimal places.
     * Called on every monetary value before any arithmetic operation.
     */
    private BigDecimal round(BigDecimal value) {
        return value.setScale(MONETARY_SCALE, ROUNDING_MODE);
    }

    /**
     * Maps an Account entity to an AccountResponse DTO.
     * The entity never crosses the service boundary.
     */
    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalance())
                .currencyCode(account.getCurrencyCode())
                .minimumBalance(account.getMinimumBalance())
                .interestRate(account.getInterestRate())
                .maturityDate(account.getMaturityDate())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    /**
     * Maps an Account entity to a lightweight AccountBalanceResponse DTO.
     * Used for balance-only queries and debit/credit responses.
     */
    private AccountBalanceResponse mapToBalanceResponse(Account account) {
        return AccountBalanceResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .currencyCode(account.getCurrencyCode())
                .status(account.getStatus())
                .asOf(LocalDateTime.now())
                .build();
    }
}