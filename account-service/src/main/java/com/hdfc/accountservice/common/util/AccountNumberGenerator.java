package com.hdfc.accountservice.common.util;

import com.hdfc.accountservice.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique, collision-resistant account numbers for HDFC NetBanking.
 *
 * <p>DESIGN PATTERN — Strategy:
 * AccountNumberGenerator encapsulates the account number generation
 * algorithm behind a single component. If the generation strategy
 * changes (e.g. switching from random to sequential), only this class
 * changes — no service or controller is affected.
 *
 * <p>Account number format: "HDFC" + 10 random digits = 14 characters total.
 * Example: HDFC3847291056
 *
 * <p>Collision resistance: after generating a candidate number, we check
 * the database for existence. In the astronomically unlikely event of a
 * collision (1 in 10^10), we regenerate. The loop guarantees uniqueness
 * before returning.
 *
 * <p>SecureRandom is used instead of Random — SecureRandom is
 * cryptographically strong and unpredictable. Using Random would make
 * account numbers guessable given enough samples, which is a security
 * risk for a banking application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;

    // SecureRandom is thread-safe and expensive to initialise —
    // we create it once and reuse it across all generation calls.
    private final SecureRandom secureRandom = new SecureRandom();

    // Account number format constants.
    private static final String PREFIX = "HDFC";
    private static final int DIGIT_COUNT = 10;
    private static final long MIN_VALUE = 1_000_000_000L; // 10^9
    private static final long MAX_VALUE = 9_999_999_999L; // 10^10 - 1

    /**
     * Generates a unique account number guaranteed not to exist
     * in the database at the time of generation.
     *
     * <p>The uniqueness guarantee holds within a single request.
     * For concurrent account creations, the unique constraint on
     * the accounts.account_number column in MySQL is the final
     * safety net against race conditions.
     *
     * @return a unique account number string e.g. "HDFC3847291056"
     */
    public String generate() {
        String candidate;
        int attempts = 0;

        do {
            candidate = buildAccountNumber();
            attempts++;

            // This should virtually never loop more than once.
            // If it does loop, something is seriously wrong with
            // the random number generator or the database is nearly full.
            if (attempts > 10) {
                log.error("Account number generation required {} attempts — " +
                        "investigate potential collision rate issue", attempts);
            }

        } while (accountRepository.existsByAccountNumber(candidate));

        log.debug("Generated account number {} in {} attempt(s)", candidate, attempts);
        return candidate;
    }

    /**
     * Builds a single candidate account number.
     *
     * <p>Generates a random long in the range [10^9, 10^10 - 1]
     * to guarantee exactly 10 digits — no leading zeros.
     * Prepends the "HDFC" prefix for brand identification.
     *
     * @return a candidate account number string
     */
    private String buildAccountNumber() {
        // nextLong(bound) generates a value in [0, bound).
        // We shift the range to [MIN_VALUE, MAX_VALUE] to ensure
        // exactly 10 digits with no leading zeros.
        long digits = MIN_VALUE +
                (long) (secureRandom.nextDouble() * (MAX_VALUE - MIN_VALUE + 1));
        return PREFIX + digits;
    }
}