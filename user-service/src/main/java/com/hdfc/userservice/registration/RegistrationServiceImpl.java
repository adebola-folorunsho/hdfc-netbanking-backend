package com.hdfc.userservice.registration;

import com.hdfc.userservice.common.exception.DuplicateUserException;
import com.hdfc.userservice.common.exception.KycVerificationException;
import com.hdfc.userservice.domain.Role;
import com.hdfc.userservice.domain.User;
import com.hdfc.userservice.domain.UserRepository;
import com.hdfc.userservice.registration.dto.UserRegistrationRequest;
import com.hdfc.userservice.registration.dto.UserRegistrationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Implementation of {@link IRegistrationService}.
 *
 * <p>Handles the full user registration flow — duplicate checking,
 * KYC validation, password hashing, role assignment, and persistence.
 *
 * <p>All dependencies are injected via constructor — never field injection.
 * This makes the class fully testable without a Spring context, as
 * demonstrated in {@link RegistrationServiceImplTest}.
 *
 * <p>This class depends on abstractions ({@link UserRepository},
 * {@link PasswordEncoder}, {@link IRegistrationService}) — never on
 * concrete implementations. Satisfies DIP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements IRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     *
     * <p>This method is {@code @Transactional} — the duplicate checks
     * and the save operation must execute within the same database
     * transaction. Without this, a race condition could allow two
     * concurrent registrations with the same email to both pass the
     * duplicate check before either one is saved.
     */
    @Override
    @Transactional
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        log.info("Processing registration for email: {}", request.getEmail());

        // Step 1 — Check for duplicate email
        // Done before any other validation to fail fast on the most
        // common duplicate scenario — a user registering twice
        validateNoDuplicateEmail(request.getEmail());

        // Step 2 — Check for duplicate phone number
        validateNoDuplicatePhoneNumber(request.getPhoneNumber());

        // Step 3 — Check for duplicate government ID
        // Prevents one person from registering multiple accounts —
        // a core fraud prevention requirement
        validateNoDuplicateGovernmentId(request.getGovernmentId());

        // Step 4 — KYC validation
        // Validates the submitted KYC details meet business requirements
        validateKycDetails(request);

        // Step 5 — Hash the password before persistence
        // The raw password is NEVER stored — always BCrypt hashed
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Step 6 — Build the User entity
        // Role is always CUSTOMER — clients cannot specify roles
        // during registration. This is a security invariant.
        User newUser = buildUser(request, hashedPassword);

        // Step 7 — Persist the new user
        User savedUser = userRepository.save(newUser);

        log.info("Successfully registered user with id: {} and email: {}",
                savedUser.getId(), savedUser.getEmail());

        // Step 8 — Map to response DTO and return
        // Never return the raw entity — always map to DTO first
        return mapToResponse(savedUser);
    }

    /**
     * Validates that no user with the given email already exists.
     *
     * <p>Uses {@code existsByEmail} rather than {@code findByEmail} —
     * more efficient as it generates a COUNT query, not a full entity load.
     *
     * @param email the email to check
     * @throws DuplicateUserException if the email is already registered
     */
    private void validateNoDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration attempt with duplicate email: {}", email);
            throw new DuplicateUserException("email", email);
        }
    }

    /**
     * Validates that no user with the given phone number already exists.
     *
     * @param phoneNumber the phone number to check
     * @throws DuplicateUserException if the phone number is already registered
     */
    private void validateNoDuplicatePhoneNumber(String phoneNumber) {
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            log.warn("Registration attempt with duplicate phone number: {}",
                    phoneNumber);
            throw new DuplicateUserException("phoneNumber", phoneNumber);
        }
    }

    /**
     * Validates that no user with the given government ID already exists.
     *
     * @param governmentId the government ID to check
     * @throws DuplicateUserException if the government ID is already registered
     */
    private void validateNoDuplicateGovernmentId(String governmentId) {
        if (userRepository.existsByGovernmentId(governmentId)) {
            log.warn("Registration attempt with duplicate government ID");
            // Deliberately not logging the government ID value —
            // it is sensitive PII and must not appear in logs
            throw new DuplicateUserException("governmentId", governmentId);
        }
    }

    /**
     * Validates KYC details meet business requirements.
     *
     * <p>Bean Validation on the DTO catches format violations.
     * This method catches semantic violations — data that is
     * syntactically valid but semantically unacceptable.
     *
     * @param request the registration request to validate
     * @throws KycVerificationException if any KYC detail fails validation
     */
    private void validateKycDetails(UserRegistrationRequest request) {
        // Full name must not be blank or whitespace-only
        if (!StringUtils.hasText(request.getFullName())) {
            throw new KycVerificationException(
                    "Full name cannot be blank or whitespace only");
        }

        // Address must not be blank or whitespace-only
        if (!StringUtils.hasText(request.getAddress())) {
            throw new KycVerificationException(
                    "Address cannot be blank or whitespace only");
        }

        // Government ID must not be blank or whitespace-only
        if (!StringUtils.hasText(request.getGovernmentId())) {
            throw new KycVerificationException(
                    "Government ID cannot be blank or whitespace only");
        }
    }

    /**
     * Builds a new {@link User} entity from the registration request.
     *
     * <p>Uses the Builder pattern — constructing a User with many fields
     * via a builder is readable and safe. The alternative (telescoping
     * constructor) would be error-prone with this many fields.
     *
     * <p>Role is hardcoded to {@link Role#CUSTOMER} — this is a
     * security invariant that must never be changed here. Role
     * escalation is an Admin-only operation via a separate endpoint.
     *
     * @param request        the validated registration request
     * @param hashedPassword the BCrypt-hashed password
     * @return a fully constructed User entity ready for persistence
     */
    private User buildUser(UserRegistrationRequest request,
                           String hashedPassword) {
        // Builder pattern — clear, readable, safe construction
        // of a complex object with many fields
        return User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(hashedPassword)
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .governmentId(request.getGovernmentId())
                // CUSTOMER is always the default role at registration —
                // never trust the client to specify their own role
                .roles(Set.of(Role.CUSTOMER))
                .isEnabled(true)
                .isKycVerified(false)
                .isTwoFactorEnabled(false)
                .build();
    }

    /**
     * Maps a persisted {@link User} entity to a {@link UserRegistrationResponse} DTO.
     *
     * <p>The entity is never returned directly — always mapped to a DTO
     * first. This decouples the API response from the persistence model.
     *
     * @param user the persisted user entity
     * @return a response DTO containing only the data the client needs
     */
    private UserRegistrationResponse mapToResponse(User user) {
        return UserRegistrationResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .roles(user.getRoles())
                .isEnabled(user.isEnabled())
                .isKycVerified(user.isKycVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}