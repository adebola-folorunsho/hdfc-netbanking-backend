package com.hdfc.transactionservice.transaction.dto;

import com.hdfc.transactionservice.transaction.TransactionStatus;
import com.hdfc.transactionservice.transaction.TransactionType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Query parameter wrapper for transaction history filtering.
 *
 * <p>All fields are optional — omitting a field means no filter
 * is applied for that dimension. Multiple filters are AND-ed together.
 *
 * <p>Used as @ModelAttribute on the GET /my-transactions endpoint
 * to bind query parameters directly to this object.
 *
 * <p>Example request:
 * GET /api/v1/transactions/my-transactions
 *     ?status=COMPLETED
 *     &startDate=2026-01-01T00:00:00
 *     &endDate=2026-03-10T23:59:59
 *     &minAmount=1000.00
 */
@Data
public class TransactionFilterRequest {

    /**
     * Filter by transaction status.
     * Null means no status filter — return all statuses.
     */
    private TransactionStatus status;

    /**
     * Filter by transaction type.
     * Null means no type filter — return all types.
     */
    private TransactionType transactionType;

    /**
     * Filter transactions created on or after this date.
     * ISO 8601 format: 2026-01-01T00:00:00
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;

    /**
     * Filter transactions created on or before this date.
     * ISO 8601 format: 2026-03-10T23:59:59
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;

    /**
     * Filter transactions with amount greater than or equal to this value.
     * Null means no minimum amount filter.
     */
    private BigDecimal minAmount;
}