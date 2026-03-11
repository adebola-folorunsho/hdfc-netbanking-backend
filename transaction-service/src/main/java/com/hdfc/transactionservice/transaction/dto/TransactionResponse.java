package com.hdfc.transactionservice.transaction.dto;

import com.hdfc.transactionservice.transaction.TransactionStatus;
import com.hdfc.transactionservice.transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO representing a single transaction record.
 *
 * <p>Returned by all transaction endpoints — initiation, lookup,
 * and history. Contains the full transaction state including
 * currency conversion details for multi-currency transfers.
 */
@Data
@Builder
public class TransactionResponse {

    private Long id;
    private Long userId;
    private String transactionReference;
    private TransactionType transactionType;
    private TransactionStatus status;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private String currencyCode;
    private BigDecimal convertedAmount;
    private String convertedCurrencyCode;
    private BigDecimal exchangeRate;
    private String description;
    private String paystackReference;

    /**
     * Failure reason populated only when status is FAILED.
     * Null for COMPLETED and REVERSED transactions.
     */
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}