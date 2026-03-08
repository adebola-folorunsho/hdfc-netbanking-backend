package com.hdfc.userservice.domain;

/**
 * Represents the access control roles available in the HDFC NetBanking system.
 *
 * <p>The three roles form a strict hierarchy where each role inherits
 * all permissions of the role below it:
 * <pre>
 *   ADMIN > TELLER > CUSTOMER
 * </pre>
 *
 * <p>Roles are stored in the database and loaded into the JWT token at login
 * as Spring Security authorities (e.g. {@code ROLE_ADMIN}). Both the API Gateway
 * (port 8080) and the Admin Gateway (port 8090) read the role claim from the
 * JWT to enforce access control independently — without calling this service.
 *
 * <p>Spring Security requires the {@code ROLE_} prefix when using
 * {@code hasRole()} in {@code @PreAuthorize}. The prefix is NOT stored here —
 * it is applied by Spring Security automatically at the authority level.
 *
 * @see com.hdfc.userservice.domain.User
 * @see com.hdfc.userservice.common.security.config.SecurityConfig
 */
public enum Role {

    /**
     * Standard bank customer.
     * Permissions: view own accounts and balances, initiate transfers,
     * view own transaction history, update own profile, enable/disable 2FA.
     */
    CUSTOMER,

    /**
     * Bank teller — all CUSTOMER permissions plus:
     * view any customer account (read-only), manually trigger statements,
     * flag suspicious accounts for review.
     */
    TELLER,

    /**
     * System administrator — all TELLER permissions plus:
     * manage users (create/suspend/delete), access fraud dashboard,
     * view all audit logs, configure system settings, assign/revoke roles.
     *
     * <p>Admin endpoints are additionally protected by the Admin Gateway
     * (port 8090) as the first line of defence — defence in depth.
     */
    ADMIN
}