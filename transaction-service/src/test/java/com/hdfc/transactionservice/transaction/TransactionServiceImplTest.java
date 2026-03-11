package com.hdfc.transactionservice.transaction;

import com.hdfc.transactionservice.common.client.AccountServiceClient;
import com.hdfc.transactionservice.common.client.PaystackClient;
import com.hdfc.transactionservice.common.exception.*;
import com.hdfc.transactionservice.common.messaging.TransactionEventPublisher;
import com.hdfc.transactionservice.transaction.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionServiceImpl")
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private PaystackClient paystackClient;

    @Mock
    private TransactionEventPublisher eventPublisher;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private static final Long USER_ID = 1L;
    private static final Long SOURCE_ACCOUNT_ID = 10L;
    private static final Long DEST_ACCOUNT_ID = 20L;
    private static final String REF = "TXN-TEST-001";
    private static final BigDecimal AMOUNT = new BigDecimal("5000.00");
    private static final String CURRENCY = "NGN";

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Transaction pendingTransfer() {
        return Transaction.builder()
                .id(1L)
                .userId(USER_ID)
                .transactionReference(REF)
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING)
                .sourceAccountId(SOURCE_ACCOUNT_ID)
                .destinationAccountId(DEST_ACCOUNT_ID)
                .amount(AMOUNT)
                .currencyCode(CURRENCY)
                .convertedAmount(AMOUNT)
                .convertedCurrencyCode(CURRENCY)
                .build();
    }

    private Transaction completedTransfer() {
        Transaction t = pendingTransfer();
        t.setStatus(TransactionStatus.COMPLETED);
        return t;
    }

    private InitiateTransferRequest transferRequest() {
        InitiateTransferRequest req = new InitiateTransferRequest();
        req.setSourceAccountId(SOURCE_ACCOUNT_ID);
        req.setDestinationAccountId(DEST_ACCOUNT_ID);
        req.setAmount(AMOUNT);
        req.setCurrencyCode(CURRENCY);
        req.setTransactionReference(REF);
        req.setDescription("Test transfer");
        return req;
    }

    private DepositWithdrawalRequest depositRequest() {
        DepositWithdrawalRequest req = new DepositWithdrawalRequest();
        req.setAccountId(DEST_ACCOUNT_ID);
        req.setAmount(AMOUNT);
        req.setCurrencyCode(CURRENCY);
        req.setTransactionReference(REF);
        req.setTransactionType(TransactionType.DEPOSIT);
        req.setDescription("Test deposit");
        return req;
    }

    private DepositWithdrawalRequest withdrawalRequest() {
        DepositWithdrawalRequest req = new DepositWithdrawalRequest();
        req.setAccountId(SOURCE_ACCOUNT_ID);
        req.setAmount(AMOUNT);
        req.setCurrencyCode(CURRENCY);
        req.setTransactionReference(REF);
        req.setTransactionType(TransactionType.WITHDRAWAL);
        req.setDescription("Test withdrawal");
        return req;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initiateTransfer
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiateTransfer")
    class InitiateTransferTests {

        @Test
        @DisplayName("happy path — debit and credit succeed, returns COMPLETED")
        void transfer_success() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction saved = pendingTransfer();
            when(transactionRepository.save(any())).thenReturn(saved);
            doNothing().when(accountServiceClient)
                    .debitAccount(eq(SOURCE_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));
            doNothing().when(accountServiceClient)
                    .creditAccount(eq(DEST_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));

            TransactionResponse response =
                    transactionService.initiateTransfer(transferRequest(), USER_ID);

            assertThat(response).isNotNull();
            verify(accountServiceClient).debitAccount(SOURCE_ACCOUNT_ID, AMOUNT, CURRENCY, REF);
            verify(accountServiceClient).creditAccount(DEST_ACCOUNT_ID, AMOUNT, CURRENCY, REF);
            verify(eventPublisher).publishTransactionCreated(any());
        }

        @Test
        @DisplayName("duplicate reference — throws TransactionAlreadyExistsException")
        void transfer_duplicateReference_throws() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(true);

            assertThatThrownBy(() ->
                    transactionService.initiateTransfer(transferRequest(), USER_ID))
                    .isInstanceOf(TransactionAlreadyExistsException.class)
                    .hasMessageContaining(REF);

            verifyNoInteractions(accountServiceClient);
        }

        @Test
        @DisplayName("same source and destination — throws InvalidTransactionException")
        void transfer_sameAccounts_throws() {
            InitiateTransferRequest req = transferRequest();
            req.setDestinationAccountId(SOURCE_ACCOUNT_ID); // same as source

            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);

            assertThatThrownBy(() ->
                    transactionService.initiateTransfer(req, USER_ID))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("different");

            verifyNoInteractions(accountServiceClient);
        }

        @Test
        @DisplayName("zero amount — throws InvalidTransactionException")
        void transfer_zeroAmount_throws() {
            InitiateTransferRequest req = transferRequest();
            req.setAmount(BigDecimal.ZERO);

            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);

            assertThatThrownBy(() ->
                    transactionService.initiateTransfer(req, USER_ID))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("insufficient balance — marks FAILED, no compensation, throws")
        void transfer_insufficientBalance_failsWithNoCompensation() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = pendingTransfer();
            when(transactionRepository.save(any())).thenReturn(pending);
            doThrow(new InsufficientBalanceException("Insufficient balance"))
                    .when(accountServiceClient)
                    .debitAccount(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    transactionService.initiateTransfer(transferRequest(), USER_ID))
                    .isInstanceOf(InsufficientBalanceException.class);

            // Verify transaction marked FAILED
            ArgumentCaptor<Transaction> captor =
                    ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository, atLeastOnce()).save(captor.capture());
            Transaction lastSaved = captor.getAllValues()
                    .get(captor.getAllValues().size() - 1);
            assertThat(lastSaved.getStatus()).isEqualTo(TransactionStatus.FAILED);

            // No credit attempted, no compensation attempted
            verify(accountServiceClient, never())
                    .creditAccount(any(), any(), any(), any());
            verify(eventPublisher).publishTransactionFailed(any());
        }

        @Test
        @DisplayName("credit fails after debit — applies compensating credit")
        void transfer_creditFailsAfterDebit_appliesCompensation() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = pendingTransfer();
            when(transactionRepository.save(any())).thenReturn(pending);

            // Debit succeeds
            doNothing().when(accountServiceClient)
                    .debitAccount(eq(SOURCE_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));
            // Credit to destination fails
            doThrow(new AccountServiceException("Credit failed"))
                    .when(accountServiceClient)
                    .creditAccount(eq(DEST_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));
            // Compensating credit to source succeeds
            doNothing().when(accountServiceClient)
                    .creditAccount(eq(SOURCE_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY),
                            startsWith("COMP-"));

            assertThatThrownBy(() ->
                    transactionService.initiateTransfer(transferRequest(), USER_ID))
                    .isInstanceOf(AccountServiceException.class);

            // Compensation credit applied to source account
            verify(accountServiceClient).creditAccount(eq(SOURCE_ACCOUNT_ID),
                    eq(AMOUNT), eq(CURRENCY), startsWith("COMP-"));

            // Transaction marked FAILED
            ArgumentCaptor<Transaction> captor =
                    ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository, atLeastOnce()).save(captor.capture());
            Transaction lastSaved = captor.getAllValues()
                    .get(captor.getAllValues().size() - 1);
            assertThat(lastSaved.getStatus()).isEqualTo(TransactionStatus.FAILED);
            verify(eventPublisher).publishTransactionFailed(any());
        }

        @Test
        @DisplayName("debit fails (not balance) — marks FAILED, no compensation")
        void transfer_debitAccountServiceException_failsWithNoCompensation() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = pendingTransfer();
            when(transactionRepository.save(any())).thenReturn(pending);

            doThrow(new AccountServiceException("Debit service error"))
                    .when(accountServiceClient)
                    .debitAccount(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    transactionService.initiateTransfer(transferRequest(), USER_ID))
                    .isInstanceOf(AccountServiceException.class);

            // No credit or compensation attempted
            verify(accountServiceClient, never())
                    .creditAccount(any(), any(), any(), any());
            verify(eventPublisher).publishTransactionFailed(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processDeposit
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processDeposit")
    class ProcessDepositTests {

        @Test
        @DisplayName("happy path — credit succeeds, returns COMPLETED")
        void deposit_success() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = Transaction.builder()
                    .id(1L).userId(USER_ID)
                    .transactionReference(REF)
                    .transactionType(TransactionType.DEPOSIT)
                    .status(TransactionStatus.PENDING)
                    .destinationAccountId(DEST_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .convertedAmount(AMOUNT).convertedCurrencyCode(CURRENCY)
                    .build();
            when(transactionRepository.save(any())).thenReturn(pending);
            doNothing().when(accountServiceClient)
                    .creditAccount(eq(DEST_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));

            TransactionResponse response =
                    transactionService.processDeposit(depositRequest(), USER_ID);

            assertThat(response).isNotNull();
            verify(accountServiceClient).creditAccount(DEST_ACCOUNT_ID, AMOUNT, CURRENCY, REF);
            verify(accountServiceClient, never()).debitAccount(any(), any(), any(), any());
            verify(eventPublisher).publishTransactionCreated(any());
        }

        @Test
        @DisplayName("wrong transaction type — throws InvalidTransactionException")
        void deposit_wrongType_throws() {
            DepositWithdrawalRequest req = depositRequest();
            req.setTransactionType(TransactionType.WITHDRAWAL); // wrong

            assertThatThrownBy(() ->
                    transactionService.processDeposit(req, USER_ID))
                    .isInstanceOf(InvalidTransactionException.class);
        }

        @Test
        @DisplayName("duplicate reference — throws TransactionAlreadyExistsException")
        void deposit_duplicateRef_throws() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(true);

            assertThatThrownBy(() ->
                    transactionService.processDeposit(depositRequest(), USER_ID))
                    .isInstanceOf(TransactionAlreadyExistsException.class);
        }

        @Test
        @DisplayName("credit fails — marks FAILED, publishes failure event")
        void deposit_creditFails_marksFailedAndPublishes() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = Transaction.builder()
                    .id(1L).userId(USER_ID)
                    .transactionReference(REF)
                    .transactionType(TransactionType.DEPOSIT)
                    .status(TransactionStatus.PENDING)
                    .destinationAccountId(DEST_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .convertedAmount(AMOUNT).convertedCurrencyCode(CURRENCY)
                    .build();
            when(transactionRepository.save(any())).thenReturn(pending);
            doThrow(new AccountServiceException("Credit failed"))
                    .when(accountServiceClient)
                    .creditAccount(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    transactionService.processDeposit(depositRequest(), USER_ID))
                    .isInstanceOf(AccountServiceException.class);

            verify(eventPublisher).publishTransactionFailed(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processWithdrawal
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processWithdrawal")
    class ProcessWithdrawalTests {

        @Test
        @DisplayName("happy path — debit succeeds, returns COMPLETED")
        void withdrawal_success() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = Transaction.builder()
                    .id(1L).userId(USER_ID)
                    .transactionReference(REF)
                    .transactionType(TransactionType.WITHDRAWAL)
                    .status(TransactionStatus.PENDING)
                    .sourceAccountId(SOURCE_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .convertedAmount(AMOUNT).convertedCurrencyCode(CURRENCY)
                    .build();
            when(transactionRepository.save(any())).thenReturn(pending);
            doNothing().when(accountServiceClient)
                    .debitAccount(eq(SOURCE_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));

            TransactionResponse response =
                    transactionService.processWithdrawal(withdrawalRequest(), USER_ID);

            assertThat(response).isNotNull();
            verify(accountServiceClient).debitAccount(SOURCE_ACCOUNT_ID, AMOUNT, CURRENCY, REF);
            verify(accountServiceClient, never()).creditAccount(any(), any(), any(), any());
            verify(eventPublisher).publishTransactionCreated(any());
        }

        @Test
        @DisplayName("insufficient balance — marks FAILED, publishes failure event")
        void withdrawal_insufficientBalance_fails() {
            when(transactionRepository.existsByTransactionReference(REF))
                    .thenReturn(false);
            Transaction pending = Transaction.builder()
                    .id(1L).userId(USER_ID)
                    .transactionReference(REF)
                    .transactionType(TransactionType.WITHDRAWAL)
                    .status(TransactionStatus.PENDING)
                    .sourceAccountId(SOURCE_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .convertedAmount(AMOUNT).convertedCurrencyCode(CURRENCY)
                    .build();
            when(transactionRepository.save(any())).thenReturn(pending);
            doThrow(new InsufficientBalanceException("Insufficient balance"))
                    .when(accountServiceClient)
                    .debitAccount(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    transactionService.processWithdrawal(withdrawalRequest(), USER_ID))
                    .isInstanceOf(InsufficientBalanceException.class);

            verify(eventPublisher).publishTransactionFailed(any());
            // No credit attempted
            verify(accountServiceClient, never())
                    .creditAccount(any(), any(), any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handlePaystackWebhook
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaystackWebhook")
    class HandlePaystackWebhookTests {

        private static final String PAYSTACK_REF = "PSK-REF-001";

        private Transaction pendingPaystackTxn() {
            return Transaction.builder()
                    .id(2L).userId(USER_ID)
                    .transactionReference(REF)
                    .paystackReference(PAYSTACK_REF)
                    .transactionType(TransactionType.PAYSTACK_PAYMENT)
                    .status(TransactionStatus.PENDING)
                    .destinationAccountId(DEST_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .convertedAmount(AMOUNT).convertedCurrencyCode(CURRENCY)
                    .build();
        }

        @Test
        @DisplayName("charge.success — credits destination, marks COMPLETED")
        void webhook_chargeSuccess_completesTransaction() {
            Transaction pending = pendingPaystackTxn();
            when(transactionRepository.findByPaystackReference(PAYSTACK_REF))
                    .thenReturn(Optional.of(pending));
            when(transactionRepository.save(any())).thenReturn(pending);
            doNothing().when(accountServiceClient)
                    .creditAccount(any(), any(), any(), any());

            transactionService.handlePaystackWebhook(PAYSTACK_REF, "charge.success");

            verify(accountServiceClient)
                    .creditAccount(eq(DEST_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), eq(REF));
            verify(eventPublisher).publishTransactionCreated(any());
        }

        @Test
        @DisplayName("charge.failed — marks FAILED, publishes failure event")
        void webhook_chargeFailed_failsTransaction() {
            Transaction pending = pendingPaystackTxn();
            when(transactionRepository.findByPaystackReference(PAYSTACK_REF))
                    .thenReturn(Optional.of(pending));
            when(transactionRepository.save(any())).thenReturn(pending);

            transactionService.handlePaystackWebhook(PAYSTACK_REF, "charge.failed");

            verify(accountServiceClient, never())
                    .creditAccount(any(), any(), any(), any());
            verify(eventPublisher).publishTransactionFailed(any());
        }

        @Test
        @DisplayName("unknown paystack reference — throws TransactionNotFoundException")
        void webhook_unknownReference_throws() {
            when(transactionRepository.findByPaystackReference(PAYSTACK_REF))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.handlePaystackWebhook(PAYSTACK_REF, "charge.success"))
                    .isInstanceOf(TransactionNotFoundException.class);
        }

        @Test
        @DisplayName("already processed webhook — idempotent, no duplicate credit")
        void webhook_alreadyProcessed_idempotent() {
            Transaction completed = pendingPaystackTxn();
            completed.setStatus(TransactionStatus.COMPLETED); // already done
            when(transactionRepository.findByPaystackReference(PAYSTACK_REF))
                    .thenReturn(Optional.of(completed));

            transactionService.handlePaystackWebhook(PAYSTACK_REF, "charge.success");

            // No credit, no event
            verifyNoInteractions(accountServiceClient);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("credit fails after charge.success — marks FAILED")
        void webhook_creditFailsAfterChargeSuccess_marksFailedAndPublishes() {
            Transaction pending = pendingPaystackTxn();
            when(transactionRepository.findByPaystackReference(PAYSTACK_REF))
                    .thenReturn(Optional.of(pending));
            when(transactionRepository.save(any())).thenReturn(pending);
            doThrow(new AccountServiceException("Credit service error"))
                    .when(accountServiceClient)
                    .creditAccount(any(), any(), any(), any());

            transactionService.handlePaystackWebhook(PAYSTACK_REF, "charge.success");

            verify(eventPublisher).publishTransactionFailed(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTransactionById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTransactionById")
    class GetTransactionByIdTests {

        @Test
        @DisplayName("CUSTOMER retrieves own transaction — succeeds")
        void getById_customerOwnsTransaction_succeeds() {
            Transaction txn = completedTransfer();
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

            TransactionResponse response =
                    transactionService.getTransactionById(1L, USER_ID, "ROLE_CUSTOMER");

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("CUSTOMER retrieves other user's transaction — throws TransactionOwnershipException")
        void getById_customerAccessOtherUser_throws() {
            Transaction txn = completedTransfer(); // userId = 1L
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

            assertThatThrownBy(() ->
                    transactionService.getTransactionById(1L, 999L, "ROLE_CUSTOMER"))
                    .isInstanceOf(TransactionOwnershipException.class);
        }

        @Test
        @DisplayName("ADMIN retrieves any transaction — succeeds")
        void getById_adminAnyTransaction_succeeds() {
            Transaction txn = completedTransfer(); // userId = 1L
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

            // Admin with different userId — should still succeed
            TransactionResponse response =
                    transactionService.getTransactionById(1L, 999L, "ROLE_ADMIN");

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("transaction not found — throws TransactionNotFoundException")
        void getById_notFound_throws() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.getTransactionById(99L, USER_ID, "ROLE_ADMIN"))
                    .isInstanceOf(TransactionNotFoundException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // reverseTransaction
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reverseTransaction")
    class ReverseTransactionTests {

        @Test
        @DisplayName("happy path — reversal succeeds, original marked REVERSED")
        void reverse_success() {
            Transaction original = completedTransfer();
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(original));

            Transaction reversal = Transaction.builder()
                    .id(2L).userId(USER_ID)
                    .transactionReference("REV-xxx")
                    .transactionType(TransactionType.INTERNAL_TRANSFER)
                    .status(TransactionStatus.PENDING)
                    .sourceAccountId(DEST_ACCOUNT_ID)
                    .destinationAccountId(SOURCE_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .convertedAmount(AMOUNT).convertedCurrencyCode(CURRENCY)
                    .build();
            when(transactionRepository.save(any())).thenReturn(reversal);
            doNothing().when(accountServiceClient).debitAccount(any(), any(), any(), any());
            doNothing().when(accountServiceClient).creditAccount(any(), any(), any(), any());

            TransactionResponse response =
                    transactionService.reverseTransaction(1L, USER_ID);

            assertThat(response).isNotNull();
            // Debit original destination, credit original source
            verify(accountServiceClient).debitAccount(
                    eq(DEST_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), any());
            verify(accountServiceClient).creditAccount(
                    eq(SOURCE_ACCOUNT_ID), eq(AMOUNT), eq(CURRENCY), any());
            verify(eventPublisher).publishTransactionReversed(any());
        }

        @Test
        @DisplayName("transaction not COMPLETED — throws InvalidTransactionException")
        void reverse_notCompleted_throws() {
            Transaction pending = pendingTransfer(); // PENDING status
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() ->
                    transactionService.reverseTransaction(1L, USER_ID))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("PAYSTACK_PAYMENT transaction — throws InvalidTransactionException")
        void reverse_notInternalTransfer_throws() {
            Transaction paystackTxn = completedTransfer();
            paystackTxn.setTransactionType(TransactionType.PAYSTACK_PAYMENT);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(paystackTxn));

            assertThatThrownBy(() ->
                    transactionService.reverseTransaction(1L, USER_ID))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("INTERNAL_TRANSFER");
        }

        @Test
        @DisplayName("transaction not found — throws TransactionNotFoundException")
        void reverse_notFound_throws() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.reverseTransaction(99L, USER_ID))
                    .isInstanceOf(TransactionNotFoundException.class);
        }

        @Test
        @DisplayName("insufficient balance in destination — reversal fails, no compensation")
        void reverse_insufficientBalance_fails() {
            Transaction original = completedTransfer();
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(original));

            Transaction reversal = Transaction.builder()
                    .id(2L).userId(USER_ID)
                    .transactionReference("REV-xxx")
                    .transactionType(TransactionType.INTERNAL_TRANSFER)
                    .status(TransactionStatus.PENDING)
                    .sourceAccountId(DEST_ACCOUNT_ID)
                    .destinationAccountId(SOURCE_ACCOUNT_ID)
                    .amount(AMOUNT).currencyCode(CURRENCY)
                    .build();
            when(transactionRepository.save(any())).thenReturn(reversal);
            doThrow(new InsufficientBalanceException("Insufficient balance"))
                    .when(accountServiceClient)
                    .debitAccount(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    transactionService.reverseTransaction(1L, USER_ID))
                    .isInstanceOf(InsufficientBalanceException.class);

            // No credit attempted
            verify(accountServiceClient, never())
                    .creditAccount(any(), any(), any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyTransactions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyTransactions")
    class GetMyTransactionsTests {

        @Test
        @DisplayName("no filter — returns all user transactions")
        void noFilter_returnsAll() {
            Transaction txn = completedTransfer();
            Page<Transaction> page = new PageImpl<>(List.of(txn));
            Pageable pageable = PageRequest.of(0, 20);

            when(transactionRepository.findByUserId(USER_ID, pageable))
                    .thenReturn(page);

            TransactionFilterRequest filter = new TransactionFilterRequest();
            Page<TransactionResponse> result =
                    transactionService.getMyTransactions(USER_ID, filter, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("status filter — delegates to findByUserIdAndStatus")
        void statusFilter_delegatesCorrectly() {
            Page<Transaction> page = new PageImpl<>(List.of());
            Pageable pageable = PageRequest.of(0, 20);

            when(transactionRepository.findByUserIdAndStatus(
                    USER_ID, TransactionStatus.COMPLETED, pageable))
                    .thenReturn(page);

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setStatus(TransactionStatus.COMPLETED);

            transactionService.getMyTransactions(USER_ID, filter, pageable);

            verify(transactionRepository)
                    .findByUserIdAndStatus(USER_ID, TransactionStatus.COMPLETED, pageable);
        }

        @Test
        @DisplayName("type filter — delegates to findByUserIdAndTransactionType")
        void typeFilter_delegatesCorrectly() {
            Page<Transaction> page = new PageImpl<>(List.of());
            Pageable pageable = PageRequest.of(0, 20);

            when(transactionRepository.findByUserIdAndTransactionType(
                    USER_ID, TransactionType.INTERNAL_TRANSFER, pageable))
                    .thenReturn(page);

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setTransactionType(TransactionType.INTERNAL_TRANSFER);

            transactionService.getMyTransactions(USER_ID, filter, pageable);

            verify(transactionRepository).findByUserIdAndTransactionType(
                    USER_ID, TransactionType.INTERNAL_TRANSFER, pageable);
        }
    }
}