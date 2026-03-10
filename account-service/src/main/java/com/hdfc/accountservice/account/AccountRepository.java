package com.hdfc.accountservice.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Account} persistence operations.
 *
 * <p>Extends {@link JpaRepository} to inherit standard CRUD operations.
 * All custom query methods follow Spring Data naming conventions where
 * possible — explicit {@code @Query} is used only when the derived method
 * name would be unreadably long or when a JOIN FETCH is required to
 * avoid the N+1 problem.
 *
 * <p>Pageable is used on all list-returning methods to prevent unbounded
 * result sets — never return all accounts without pagination.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Finds all accounts belonging to a specific user, paginated.
     *
     * <p>This is the most frequently called query in Account Service —
     * every "view my accounts" request hits this method.
     * The idx_accounts_user_id index ensures this is an index scan,
     * not a full table scan.
     *
     * @param userId   the ID of the user whose accounts to retrieve
     * @param pageable pagination and sorting parameters
     * @return a page of accounts belonging to the user
     */
    Page<Account> findByUserId(Long userId, Pageable pageable);

    /**
     * Finds all accounts belonging to a specific user with a given status.
     *
     * <p>Used when Transaction Service needs to verify that the account
     * is ACTIVE before processing a debit or credit operation.
     *
     * @param userId   the ID of the user
     * @param status   the account status to filter by
     * @param pageable pagination parameters
     * @return a page of matching accounts
     */
    Page<Account> findByUserIdAndStatus(Long userId, AccountStatus status, Pageable pageable);

    /**
     * Finds a single account by its unique account number.
     *
     * <p>Account number is the public-facing identifier used in
     * transfer requests. The idx_accounts_account_number index
     * ensures O(log n) lookup time.
     *
     * @param accountNumber the unique account number
     * @return an Optional containing the account, or empty if not found
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Finds all accounts of a specific type belonging to a user.
     *
     * <p>Used to enforce the business rule that a user may only hold
     * one account of each type. Before creating a new account, the
     * service calls this to check for an existing account of that type.
     *
     * @param userId      the ID of the user
     * @param accountType the type of account to look for
     * @return a list of matching accounts (expected size: 0 or 1)
     */
    List<Account> findByUserIdAndAccountType(Long userId, AccountType accountType);

    /**
     * Checks whether a user already holds an account of the given type.
     *
     * <p>More efficient than findByUserIdAndAccountType for the existence
     * check use case — MySQL stops scanning after the first match.
     *
     * @param userId      the ID of the user
     * @param accountType the account type to check for
     * @return true if the user already has an account of this type
     */
    boolean existsByUserIdAndAccountType(Long userId, AccountType accountType);

    /**
     * Checks whether an account number already exists in the database.
     *
     * <p>Called by AccountNumberGenerator after generating a candidate
     * number to guarantee uniqueness before persisting.
     *
     * @param accountNumber the candidate account number to check
     * @return true if the account number is already taken
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Finds an account by ID with a PESSIMISTIC_WRITE lock.
     *
     * <p>DESIGN PATTERN — Pessimistic Locking:
     * Used exclusively for balance debit and credit operations.
     * PESSIMISTIC_WRITE acquires a SELECT ... FOR UPDATE lock in MySQL,
     * preventing any other transaction from reading or writing this row
     * until the current transaction commits or rolls back.
     *
     * <p>This is the correct strategy for a banking system where two
     * concurrent transfers could both read the same balance, both decide
     * the funds are sufficient, and both proceed — resulting in a
     * negative balance. The lock serialises access at the row level.
     *
     * <p>This method is only called from within a @Transactional method
     * with SERIALIZABLE isolation. Calling it outside a transaction
     * will throw an exception.
     *
     * @param id the account ID to lock and retrieve
     * @return an Optional containing the locked account, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    /**
     * Finds all accounts belonging to a user, without pagination.
     *
     * <p>Used internally for account closure validation — we need to
     * check all account balances before allowing a user to be deleted
     * from User Service. Not exposed as a paginated endpoint.
     *
     * @param userId the ID of the user
     * @return list of all accounts for the user
     */
    List<Account> findByUserId(Long userId);
}