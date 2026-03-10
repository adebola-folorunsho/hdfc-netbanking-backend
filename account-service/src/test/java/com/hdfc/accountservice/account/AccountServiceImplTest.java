package com.hdfc.accountservice.account;

import com.hdfc.accountservice.account.dto.*;
import com.hdfc.accountservice.common.exception.*;
import com.hdfc.accountservice.common.util.AccountNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccountServiceImpl}.
 *
 * <p>All dependencies are mocked via Mockito — no Spring context,
 * no database, no Redis. Tests run in milliseconds.
 *
 * <p>Nested test classes group related tests together for readability.
 * Each nested class covers one method of AccountServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountServiceImpl")
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @InjectMocks
    private AccountServiceImpl accountService;

    // ─────────────────────────────────────────────────────────────────
    // SHARED TEST FIXTURES
    // ─────────────────────────────────────────────────────────────────

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final String ACCOUNT_NUMBER = "HDFC1234567890";
    private static final String CURRENCY_NGN = "NGN";

    /**
     * Builds a standard ACTIVE SAVINGS account for use in tests.
     * Using a builder method keeps each test focused on what it
     * is testing — not on object construction boilerplate.
     */
    private Account buildSavingsAccount() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .accountNumber(ACCOUNT_NUMBER)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("5000.0000"))
                .currencyCode(CURRENCY_NGN)
                .minimumBalance(new BigDecimal("1000.0000"))
                .interestRate(new BigDecimal("4.50"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Account buildCurrentAccount() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .accountNumber(ACCOUNT_NUMBER)
                .accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000.0000"))
                .currencyCode(CURRENCY_NGN)
                .minimumBalance(new BigDecimal("0.0000"))
                .interestRate(new BigDecimal("0.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Account buildFixedDepositAccount() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .accountNumber(ACCOUNT_NUMBER)
                .accountType(AccountType.FIXED_DEPOSIT)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("50000.0000"))
                .currencyCode(CURRENCY_NGN)
                .minimumBalance(new BigDecimal("10000.0000"))
                .interestRate(new BigDecimal("8.50"))
                .maturityDate(LocalDateTime.now().plusMonths(12))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // CREATE ACCOUNT
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAccount")
    class CreateAccountTests {

        @Test
        @DisplayName("creates a SAVINGS account successfully")
        void createSavingsAccount_success() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.SAVINGS)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("5000.00"))
                    .build();

            Account saved = buildSavingsAccount();

            when(accountRepository.existsByUserIdAndAccountType(USER_ID, AccountType.SAVINGS))
                    .thenReturn(false);
            when(accountNumberGenerator.generate()).thenReturn(ACCOUNT_NUMBER);
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            AccountResponse response = accountService.createAccount(request, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getAccountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(response.getCurrencyCode()).isEqualTo(CURRENCY_NGN);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("creates a CURRENT account successfully")
        void createCurrentAccount_success() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.CURRENT)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("500.00"))
                    .build();

            Account saved = buildCurrentAccount();

            when(accountRepository.existsByUserIdAndAccountType(USER_ID, AccountType.CURRENT))
                    .thenReturn(false);
            when(accountNumberGenerator.generate()).thenReturn(ACCOUNT_NUMBER);
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            AccountResponse response = accountService.createAccount(request, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getAccountType()).isEqualTo(AccountType.CURRENT);
        }

        @Test
        @DisplayName("creates a FIXED_DEPOSIT account successfully")
        void createFixedDepositAccount_success() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.FIXED_DEPOSIT)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("50000.00"))
                    .maturityPeriodMonths(12)
                    .build();

            Account saved = buildFixedDepositAccount();

            when(accountRepository.existsByUserIdAndAccountType(
                    USER_ID, AccountType.FIXED_DEPOSIT)).thenReturn(false);
            when(accountNumberGenerator.generate()).thenReturn(ACCOUNT_NUMBER);
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            AccountResponse response = accountService.createAccount(request, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getAccountType()).isEqualTo(AccountType.FIXED_DEPOSIT);
            assertThat(response.getMaturityDate()).isNotNull();
        }

        @Test
        @DisplayName("throws DuplicateAccountException when user already has a SAVINGS account")
        void createAccount_duplicateSavings_throwsDuplicateAccountException() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.SAVINGS)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("5000.00"))
                    .build();

            when(accountRepository.existsByUserIdAndAccountType(USER_ID, AccountType.SAVINGS))
                    .thenReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(request, USER_ID))
                    .isInstanceOf(DuplicateAccountException.class)
                    .hasMessageContaining(String.valueOf(USER_ID))
                    .hasMessageContaining("SAVINGS");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException when initial deposit is below minimum balance")
        void createAccount_depositBelowMinimum_throwsInvalidAccountOperationException() {
            // SAVINGS minimum balance is 1000 NGN
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.SAVINGS)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("500.00"))
                    .build();

            when(accountRepository.existsByUserIdAndAccountType(USER_ID, AccountType.SAVINGS))
                    .thenReturn(false);

            assertThatThrownBy(() -> accountService.createAccount(request, USER_ID))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("minimum balance");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException when FIXED_DEPOSIT has no maturity period")
        void createAccount_fixedDepositNoMaturity_throwsInvalidAccountOperationException() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.FIXED_DEPOSIT)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("50000.00"))
                    .maturityPeriodMonths(null)
                    .build();

            when(accountRepository.existsByUserIdAndAccountType(
                    USER_ID, AccountType.FIXED_DEPOSIT)).thenReturn(false);

            assertThatThrownBy(() -> accountService.createAccount(request, USER_ID))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("Maturity period");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException when FIXED_DEPOSIT maturity period is zero")
        void createAccount_fixedDepositZeroMaturity_throwsInvalidAccountOperationException() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(AccountType.FIXED_DEPOSIT)
                    .currencyCode(CURRENCY_NGN)
                    .initialDeposit(new BigDecimal("50000.00"))
                    .maturityPeriodMonths(0)
                    .build();

            when(accountRepository.existsByUserIdAndAccountType(
                    USER_ID, AccountType.FIXED_DEPOSIT)).thenReturn(false);

            assertThatThrownBy(() -> accountService.createAccount(request, USER_ID))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("Maturity period");

            verify(accountRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET ACCOUNT BY ID
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountById")
    class GetAccountByIdTests {

        @Test
        @DisplayName("returns account when owner requests their own account")
        void getAccountById_ownerRequest_returnsAccount() {
            Account account = buildSavingsAccount();
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            AccountResponse response =
                    accountService.getAccountById(ACCOUNT_ID, USER_ID, false);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(ACCOUNT_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("returns account when ADMIN requests any account")
        void getAccountById_adminRequest_returnsAccount() {
            Account account = buildSavingsAccount();
            Long adminUserId = 999L;
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            // isAdminOrTeller = true bypasses ownership check
            AccountResponse response =
                    accountService.getAccountById(ACCOUNT_ID, adminUserId, true);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(ACCOUNT_ID);
        }

        @Test
        @DisplayName("throws AccountNotFoundException when account does not exist")
        void getAccountById_notFound_throwsAccountNotFoundException() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    accountService.getAccountById(ACCOUNT_ID, USER_ID, false))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(String.valueOf(ACCOUNT_ID));
        }

        @Test
        @DisplayName("throws AccountOwnershipException when CUSTOMER requests another user's account")
        void getAccountById_wrongOwner_throwsAccountOwnershipException() {
            Account account = buildSavingsAccount();
            Long differentUserId = 999L;
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            assertThatThrownBy(() ->
                    accountService.getAccountById(ACCOUNT_ID, differentUserId, false))
                    .isInstanceOf(AccountOwnershipException.class)
                    .hasMessageContaining(String.valueOf(differentUserId));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET ACCOUNTS BY USER ID
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountsByUserId")
    class GetAccountsByUserIdTests {

        @Test
        @DisplayName("returns paginated accounts for a user")
        void getAccountsByUserId_returnsPage() {
            Account account = buildSavingsAccount();
            Pageable pageable = PageRequest.of(0, 10);
            Page<Account> page = new PageImpl<>(List.of(account), pageable, 1);

            when(accountRepository.findByUserId(USER_ID, pageable)).thenReturn(page);

            Page<AccountResponse> result =
                    accountService.getAccountsByUserId(USER_ID, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("returns empty page when user has no accounts")
        void getAccountsByUserId_noAccounts_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Account> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(accountRepository.findByUserId(USER_ID, pageable)).thenReturn(emptyPage);

            Page<AccountResponse> result =
                    accountService.getAccountsByUserId(USER_ID, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET ACCOUNT BALANCE
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountBalance")
    class GetAccountBalanceTests {

        @Test
        @DisplayName("returns balance for account owner")
        void getAccountBalance_owner_returnsBalance() {
            Account account = buildSavingsAccount();
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            AccountBalanceResponse response =
                    accountService.getAccountBalance(ACCOUNT_ID, USER_ID, false);

            assertThat(response).isNotNull();
            assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(response.getBalance())
                    .isEqualByComparingTo(new BigDecimal("5000.0000"));
            assertThat(response.getCurrencyCode()).isEqualTo(CURRENCY_NGN);
        }

        @Test
        @DisplayName("throws AccountNotFoundException when account does not exist")
        void getAccountBalance_notFound_throwsAccountNotFoundException() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    accountService.getAccountBalance(ACCOUNT_ID, USER_ID, false))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("throws AccountOwnershipException when CUSTOMER checks another user's balance")
        void getAccountBalance_wrongOwner_throwsAccountOwnershipException() {
            Account account = buildSavingsAccount();
            Long differentUserId = 999L;
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            assertThatThrownBy(() ->
                    accountService.getAccountBalance(ACCOUNT_ID, differentUserId, false))
                    .isInstanceOf(AccountOwnershipException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE ACCOUNT STATUS
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateAccountStatus")
    class UpdateAccountStatusTests {

        @Test
        @DisplayName("updates status from ACTIVE to INACTIVE successfully")
        void updateAccountStatus_activeToInactive_success() {
            Account account = buildSavingsAccount();
            UpdateAccountStatusRequest request = UpdateAccountStatusRequest.builder()
                    .status(AccountStatus.INACTIVE)
                    .reason("Customer request")
                    .build();

            Account updated = buildSavingsAccount();
            updated.setStatus(AccountStatus.INACTIVE);

            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(updated);

            AccountResponse response =
                    accountService.updateAccountStatus(ACCOUNT_ID, request);

            assertThat(response.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException when account is CLOSED")
        void updateAccountStatus_closedAccount_throwsInvalidAccountOperationException() {
            Account account = buildSavingsAccount();
            account.setStatus(AccountStatus.CLOSED);

            UpdateAccountStatusRequest request = UpdateAccountStatusRequest.builder()
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            assertThatThrownBy(() ->
                    accountService.updateAccountStatus(ACCOUNT_ID, request))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("CLOSED")
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException when status is unchanged")
        void updateAccountStatus_sameStatus_throwsInvalidAccountOperationException() {
            Account account = buildSavingsAccount(); // ACTIVE
            UpdateAccountStatusRequest request = UpdateAccountStatusRequest.builder()
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            assertThatThrownBy(() ->
                    accountService.updateAccountStatus(ACCOUNT_ID, request))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("already in");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // DEBIT
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("debit")
    class DebitTests {

        @Test
        @DisplayName("debits amount successfully when balance is sufficient")
        void debit_sufficientBalance_success() {
            Account account = buildSavingsAccount(); // balance: 5000, min: 1000
            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("2000.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-001")
                    .build();

            Account debited = buildSavingsAccount();
            debited.setBalance(new BigDecimal("3000.0000"));

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(debited);

            AccountBalanceResponse response =
                    accountService.debit(ACCOUNT_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getBalance())
                    .isEqualByComparingTo(new BigDecimal("3000.0000"));
        }

        @Test
        @DisplayName("throws InsufficientBalanceException when debit would breach minimum balance")
        void debit_belowMinimumBalance_throwsInsufficientBalanceException() {
            Account account = buildSavingsAccount(); // balance: 5000, min: 1000
            // Debiting 4500 would leave 500 — below minimum of 1000
            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("4500.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-002")
                    .build();

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debit(ACCOUNT_ID, request))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccountNotActiveException when account is FROZEN")
        void debit_frozenAccount_throwsAccountNotActiveException() {
            Account account = buildSavingsAccount();
            account.setStatus(AccountStatus.FROZEN);

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-003")
                    .build();

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debit(ACCOUNT_ID, request))
                    .isInstanceOf(AccountNotActiveException.class)
                    .hasMessageContaining("FROZEN");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccountNotActiveException when account is INACTIVE")
        void debit_inactiveAccount_throwsAccountNotActiveException() {
            Account account = buildSavingsAccount();
            account.setStatus(AccountStatus.INACTIVE);

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-004")
                    .build();

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debit(ACCOUNT_ID, request))
                    .isInstanceOf(AccountNotActiveException.class)
                    .hasMessageContaining("INACTIVE");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException on currency mismatch")
        void debit_currencyMismatch_throwsInvalidAccountOperationException() {
            Account account = buildSavingsAccount(); // NGN account

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currencyCode("USD") // wrong currency
                    .transactionReference("TXN-005")
                    .build();

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debit(ACCOUNT_ID, request))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("Currency mismatch");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccountNotFoundException when account does not exist")
        void debit_accountNotFound_throwsAccountNotFoundException() {
            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-006")
                    .build();

            assertThatThrownBy(() -> accountService.debit(ACCOUNT_ID, request))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(String.valueOf(ACCOUNT_ID));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // CREDIT
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("credit")
    class CreditTests {

        @Test
        @DisplayName("credits amount successfully to ACTIVE account")
        void credit_activeAccount_success() {
            Account account = buildSavingsAccount(); // balance: 5000
            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("1000.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-007")
                    .build();

            Account credited = buildSavingsAccount();
            credited.setBalance(new BigDecimal("6000.0000"));

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(credited);

            AccountBalanceResponse response =
                    accountService.credit(ACCOUNT_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getBalance())
                    .isEqualByComparingTo(new BigDecimal("6000.0000"));
        }

        @Test
        @DisplayName("throws AccountNotActiveException when crediting a CLOSED account")
        void credit_closedAccount_throwsAccountNotActiveException() {
            Account account = buildSavingsAccount();
            account.setStatus(AccountStatus.CLOSED);

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("1000.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-008")
                    .build();

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.credit(ACCOUNT_ID, request))
                    .isInstanceOf(AccountNotActiveException.class)
                    .hasMessageContaining("CLOSED");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidAccountOperationException on currency mismatch")
        void credit_currencyMismatch_throwsInvalidAccountOperationException() {
            Account account = buildSavingsAccount(); // NGN account

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("1000.00"))
                    .currencyCode("GBP") // wrong currency
                    .transactionReference("TXN-009")
                    .build();

            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.credit(ACCOUNT_ID, request))
                    .isInstanceOf(InvalidAccountOperationException.class)
                    .hasMessageContaining("Currency mismatch");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccountNotFoundException when account does not exist")
        void credit_accountNotFound_throwsAccountNotFoundException() {
            when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            DebitCreditRequest request = DebitCreditRequest.builder()
                    .amount(new BigDecimal("1000.00"))
                    .currencyCode(CURRENCY_NGN)
                    .transactionReference("TXN-010")
                    .build();

            assertThatThrownBy(() -> accountService.credit(ACCOUNT_ID, request))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(String.valueOf(ACCOUNT_ID));
        }
    }
}