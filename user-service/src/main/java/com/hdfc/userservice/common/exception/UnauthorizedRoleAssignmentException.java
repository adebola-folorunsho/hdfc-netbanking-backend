package com.hdfc.userservice.common.exception;

/**
 * Thrown when a non-admin user attempts to assign or revoke roles,
 * or when any user attempts to escalate their own privileges.
 *
 * <p>Maps to HTTP 403 Forbidden via the global exception handler.
 * Role assignment is an Admin-only operation — this exception is the
 * second line of defence after the Admin Gateway and @PreAuthorize checks.
 */
public class UnauthorizedRoleAssignmentException extends UserServiceException {

    /**
     * @param actingUserId the ID of the user who attempted the operation
     */
    public UnauthorizedRoleAssignmentException(Long actingUserId) {
        super("User " + actingUserId + " is not authorised to assign or revoke roles.");
    }
}