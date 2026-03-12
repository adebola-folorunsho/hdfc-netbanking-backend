package com.hdfc.auditservice.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal DTO representing a Kafka event consumed from the
 * transaction-events topic published by Transaction Service.
 *
 * <p>@JsonIgnoreProperties(ignoreUnknown = true) — Transaction Service
 * may publish additional fields in future. We only deserialise what
 * Audit Service needs — unknown fields are safely ignored rather than
 * causing deserialization failures.</p>
 *
 * <p>SRP: this class only exists to deserialise the Kafka message JSON.
 * No business logic lives here.</p>
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent {

    /**
     * The type of event — determines how the audit log is categorised.
     * e.g. "TRANSACTION_CREATED", "FRAUD_ALERT"
     */
    private String eventType;

    /**
     * The service or user that triggered the event.
     * e.g. "transaction-service"
     */
    private String actor;

    /**
     * Human-readable description of the event for the audit record.
     * e.g. "Fund transfer of NGN 5000 from account 1 to account 2"
     */
    private String description;
}