package com.hdfc.schedulerservice.statement.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO returned by StatementService to callers.
 *
 * <p>Never exposes the Statement JPA entity directly across service
 * or package boundaries — DTOs at all boundaries is a strict rule
 * in this codebase to prevent tight coupling between the persistence
 * layer and the API layer.</p>
 *
 * <p>Immutable by design — all fields set at construction via Builder.
 * Statement records must never be mutated after generation.</p>
 */
@Getter
@Builder
public class StatementResponse {

    /** The unique identifier of this statement record. */
    private final Long id;

    /** The ID of the user this statement belongs to. */
    private final Long userId;

    /** The ID of the account this statement covers. */
    private final Long accountId;

    /**
     * The first day of the statement period.
     * e.g. 2026-02-01 for a February statement.
     */
    private final LocalDate periodStart;

    /**
     * The last day of the statement period.
     * e.g. 2026-02-28 for a February statement.
     */
    private final LocalDate periodEnd;

    /**
     * UTC timestamp of when this statement was generated.
     * ISO-8601 format — consistent with platform-wide timestamp standard.
     */
    private final Instant generatedAt;
}