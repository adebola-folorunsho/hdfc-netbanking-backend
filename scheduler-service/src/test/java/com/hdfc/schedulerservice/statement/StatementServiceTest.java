package com.hdfc.schedulerservice.statement;

import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import com.hdfc.schedulerservice.statement.exception.StatementNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementService Unit Tests")
class StatementServiceTest {

    @Mock
    private StatementRepository statementRepository;

    @InjectMocks
    private StatementServiceImpl statementService;

    private static final Long STATEMENT_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long ACCOUNT_ID = 200L;
    private static final YearMonth STATEMENT_PERIOD = YearMonth.of(2026, 2);

    // ─────────────────────────────────────────────────────────────────
    // generateStatement tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should persist statement when valid parameters provided")
    void shouldPersistStatement_whenValidParametersProvided() {
        // Arrange
        Statement statement = buildStatement();
        when(statementRepository.save(any(Statement.class))).thenReturn(statement);

        // Act
        statementService.generateStatement(USER_ID, ACCOUNT_ID, STATEMENT_PERIOD);

        // Assert
        verify(statementRepository, times(1)).save(any(Statement.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null")
    void shouldThrowIllegalArgumentException_whenUserIdIsNull() {
        assertThatThrownBy(() ->
                statementService.generateStatement(null, ACCOUNT_ID, STATEMENT_PERIOD))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when accountId is null")
    void shouldThrowIllegalArgumentException_whenAccountIdIsNull() {
        assertThatThrownBy(() ->
                statementService.generateStatement(USER_ID, null, STATEMENT_PERIOD))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when period is null")
    void shouldThrowIllegalArgumentException_whenPeriodIsNull() {
        assertThatThrownBy(() ->
                statementService.generateStatement(USER_ID, ACCOUNT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(statementRepository);
    }

    // ─────────────────────────────────────────────────────────────────
    // getStatementById tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return statement when it exists")
    void shouldReturnStatement_whenItExists() {
        // Arrange
        Statement statement = buildStatement();
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));

        // Act
        StatementResponse response = statementService.getStatementById(STATEMENT_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);

        verify(statementRepository, times(1)).findById(STATEMENT_ID);
    }

    @Test
    @DisplayName("Should throw StatementNotFoundException when statement does not exist")
    void shouldThrowStatementNotFoundException_whenStatementDoesNotExist() {
        // Arrange
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> statementService.getStatementById(STATEMENT_ID))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining(String.valueOf(STATEMENT_ID));

        verify(statementRepository, times(1)).findById(STATEMENT_ID);
    }

    // ─────────────────────────────────────────────────────────────────
    // getStatementsByUserId tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return paginated statements for a user")
    void shouldReturnPaginatedStatements_forUser() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Statement> statementPage = new PageImpl<>(List.of(buildStatement()));
        when(statementRepository.findByUserId(USER_ID, pageable)).thenReturn(statementPage);

        // Act
        Page<StatementResponse> result = statementService.getStatementsByUserId(USER_ID, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(USER_ID);

        verify(statementRepository, times(1)).findByUserId(USER_ID, pageable);
    }

    @Test
    @DisplayName("Should return empty page when user has no statements")
    void shouldReturnEmptyPage_whenUserHasNoStatements() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(statementRepository.findByUserId(USER_ID, pageable)).thenReturn(Page.empty());

        // Act
        Page<StatementResponse> result = statementService.getStatementsByUserId(USER_ID, pageable);

        // Assert — never return null, always return empty page
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();

        verify(statementRepository, times(1)).findByUserId(USER_ID, pageable);
    }

    // ─────────────────────────────────────────────────────────────────
    // Test data builder
    // ─────────────────────────────────────────────────────────────────

    private Statement buildStatement() {
        Statement statement = new Statement();
        statement.setId(STATEMENT_ID);
        statement.setUserId(USER_ID);
        statement.setAccountId(ACCOUNT_ID);
        statement.setPeriodStart(STATEMENT_PERIOD.atDay(1));
        statement.setPeriodEnd(STATEMENT_PERIOD.atEndOfMonth());
        statement.setGeneratedAt(java.time.Instant.now());
        return statement;
    }
}