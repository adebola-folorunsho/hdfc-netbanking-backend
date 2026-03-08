package com.hdfc.userservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access interface for {@link User} persistence operations.
 *
 * <p>Extends {@link JpaRepository} which provides standard CRUD operations
 * out of the box — save, findById, findAll, delete, count, and more.
 * No implementation class is needed — Spring Data JPA generates the
 * implementation at runtime via proxy.
 *
 * <p>All query methods return {@link Optional} to force callers to handle
 * the absent case explicitly — null is never returned from this interface.
 * This satisfies the null handling principle: never return null from a method.
 *
 * <p>This interface lives in the {@code domain} package because it is a
 * stable abstraction — it changes only when the domain model changes.
 * Feature packages depend on this interface, never the reverse.
 * This satisfies SDP (Stable Dependencies Principle) and DIP
 * (Dependency Inversion Principle).
 *
 * @see User
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique email address.
     *
     * <p>Used during authentication to load the user by login identifier,
     * and during registration to check for duplicate accounts.
     * The {@code email} column is indexed — this query is O(log n).
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the user if found, or empty if not
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their unique phone number.
     *
     * <p>Used during KYC validation to prevent duplicate registrations
     * with the same phone number. The {@code phone_number} column is indexed.
     *
     * @param phoneNumber the phone number to search for
     * @return an {@link Optional} containing the user if found, or empty if not
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Finds a user by their unique government-issued ID number.
     *
     * <p>Used during KYC validation to prevent duplicate registrations
     * with the same government ID — a fraud prevention measure.
     *
     * @param governmentId the government ID to search for
     * @return an {@link Optional} containing the user if found, or empty if not
     */
    Optional<User> findByGovernmentId(String governmentId);

    /**
     * Checks whether a user with the given email already exists.
     *
     * <p>More efficient than {@link #findByEmail(String)} for existence checks
     * because it generates a {@code SELECT COUNT} or {@code EXISTS} query
     * rather than loading the full entity. Used in registration validation.
     *
     * @param email the email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether a user with the given phone number already exists.
     *
     * <p>Used in registration validation to enforce phone number uniqueness
     * before attempting to persist — avoiding a database constraint violation.
     *
     * @param phoneNumber the phone number to check
     * @return true if a user with this phone number exists, false otherwise
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Checks whether a user with the given government ID already exists.
     *
     * <p>Used in KYC validation to prevent identity fraud — one person
     * cannot register multiple accounts with the same government ID.
     *
     * @param governmentId the government ID to check
     * @return true if a user with this government ID exists, false otherwise
     */
    boolean existsByGovernmentId(String governmentId);
}