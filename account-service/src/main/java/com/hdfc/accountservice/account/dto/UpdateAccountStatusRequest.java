package com.hdfc.accountservice.account.dto;

import com.hdfc.accountservice.account.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

/**
 * Request DTO for updating an account's status.
 *
 * <p>Used by Teller and Admin endpoints to activate, suspend,
 * freeze, or close an account. Status transition rules are
 * enforced at the service layer — not all transitions are valid
 * (e.g. CLOSED is a terminal state).
 */
@Getter
@Builder
public class UpdateAccountStatusRequest {

    @NotNull(message = "Account status is required")
    private final AccountStatus status;

    /**
     * Optional reason for the status change.
     * Required when status is FROZEN — fraud detection pipeline
     * must record why the account was frozen.
     * Stored in the audit log via Hibernate Envers (Phase 4).
     */
    private final String reason;
}