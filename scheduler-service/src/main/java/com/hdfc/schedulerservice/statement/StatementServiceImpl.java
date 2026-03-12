package com.hdfc.schedulerservice.statement;

import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import com.hdfc.schedulerservice.statement.exception.StatementNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * Implementation of StatementService.
 *
 * <p>SRP: this class is solely responsible for coordinating statement
 * persistence and retrieval. It does not handle Kafka publishing,
 * cron scheduling, or HTTP concerns — those belong to their own classes.</p>
 *
 * <p>All write operations are @Transactional — statement records must
 * be persisted atomically. A partial write is unacceptable for a
 * financial statement.</p>
 *
 * <p>All read operations are @Transactional(readOnly = true) —
 * readOnly hint allows Hibernate to skip dirty checking on reads,
 * improving performance on potentially large statement tables.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementServiceImpl implements StatementService {

    private final StatementRepository statementRepository;

    /**
     * {@inheritDoc}
     *
     * <p>Validates all inputs at the entry point — fail fast before
     * any DB interaction. Builds and persists the Statement entity.
     * generatedAt is set automatically via @PrePersist on the entity.</p>
     */
    @Override
    @Transactional
    public StatementResponse generateStatement(Long userId, Long accountId, YearMonth period) {
        validateNotNull(userId, "userId");
        validateNotNull(accountId, "accountId");
        validateNotNull(period, "period");

        Statement statement = buildStatement(userId, accountId, period);
        Statement savedStatement = statementRepository.save(statement);

        log.info("Statement generated — userId: {}, accountId: {}, period: {}",
                userId, accountId, period);

        return mapToResponse(savedStatement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public StatementResponse getStatementById(Long id) {
        validateNotNull(id, "id");

        return statementRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new StatementNotFoundException(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<StatementResponse> getStatementsByUserId(Long userId, Pageable pageable) {
        validateNotNull(userId, "userId");

        return statementRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers — each does exactly one thing (SRP at method level)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Validates that a value is not null.
     * Fails fast — never propagates null state into persistence layer.
     */
    private void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    /**
     * Builds a new Statement entity from the given parameters.
     * generatedAt is intentionally not set here — @PrePersist handles it.
     */
    private Statement buildStatement(Long userId, Long accountId, YearMonth period) {
        Statement statement = new Statement();
        statement.setUserId(userId);
        statement.setAccountId(accountId);
        statement.setPeriodStart(period.atDay(1));
        statement.setPeriodEnd(period.atEndOfMonth());
        return statement;
    }

    /**
     * Maps a Statement entity to a StatementResponse DTO.
     * Never exposes the JPA entity outside the service layer.
     */
    private StatementResponse mapToResponse(Statement statement) {
        return StatementResponse.builder()
                .id(statement.getId())
                .userId(statement.getUserId())
                .accountId(statement.getAccountId())
                .periodStart(statement.getPeriodStart())
                .periodEnd(statement.getPeriodEnd())
                .generatedAt(statement.getGeneratedAt())
                .build();
    }
}