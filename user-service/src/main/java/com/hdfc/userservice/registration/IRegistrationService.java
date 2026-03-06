package com.hdfc.userservice.registration;

import com.hdfc.userservice.registration.dto.UserRegistrationRequest;
import com.hdfc.userservice.registration.dto.UserRegistrationResponse;

/**
 * Contract for user registration operations.
 *
 * <p>Defines the registration feature's public API — what callers can do,
 * not how it is done. The controller and any future caller depend on this
 * interface, never on {@link RegistrationServiceImpl} directly.
 *
 * <p>This satisfies DIP (Dependency Inversion Principle) — high-level
 * modules (controller) depend on this abstraction, not on the concrete
 * implementation. It also satisfies OCP (Open/Closed Principle) — the
 * registration behaviour can be changed or extended by providing a new
 * implementation without touching the controller.
 *
 * <p>Follows ISP (Interface Segregation Principle) — this interface
 * covers only registration concerns. Authentication, role management,
 * and 2FA are defined in their own separate interfaces.
 */
public interface IRegistrationService {

    /**
     * Registers a new user in the HDFC NetBanking system.
     *
     * <p>Performs the following steps in order:
     * <ol>
     *   <li>Validates that the email, phone number, and government ID
     *       are not already registered — throws {@link
     *       com.hdfc.userservice.common.exception.DuplicateUserException}
     *       if any duplicate is found</li>
     *   <li>Validates KYC details — throws {@link
     *       com.hdfc.userservice.common.exception.KycVerificationException}
     *       if KYC validation fails</li>
     *   <li>Hashes the password using BCrypt</li>
     *   <li>Assigns the {@link com.hdfc.userservice.domain.Role#CUSTOMER}
     *       role — clients cannot specify roles during registration</li>
     *   <li>Persists the new user to the database</li>
     *   <li>Returns a {@link UserRegistrationResponse} DTO —
     *       never the raw entity</li>
     * </ol>
     *
     * @param request the validated registration data from the client
     * @return a response DTO confirming the created user's details
     * @throws com.hdfc.userservice.common.exception.DuplicateUserException
     *         if the email, phone number, or government ID already exists
     * @throws com.hdfc.userservice.common.exception.KycVerificationException
     *         if KYC validation fails
     */
    UserRegistrationResponse register(UserRegistrationRequest request);
}