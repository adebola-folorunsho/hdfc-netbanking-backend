package com.hdfc.userservice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Core domain entity representing a registered user in the HDFC NetBanking system.
 *
 * <p>This entity is owned exclusively by the User Service and maps to the
 * {@code users} table in {@code hdfc_user_db}. It holds authentication
 * credentials, KYC details, role assignments, and 2FA configuration.
 *
 * <p>Accounts (Savings, Current, Fixed Deposit) are owned by the Account Service
 * in a separate database. There is no JPA relationship to accounts here —
 * the Account Service holds a plain {@code userId} column as a logical reference.
 *
 * <p>This entity must never be passed across package or service boundaries.
 * Always map to a DTO before returning data to any caller.
 *
 * @see com.hdfc.userservice.domain.Role
 * @see com.hdfc.userservice.domain.UserRepository
 */
@Entity
@Table(
        name = "users",
        indexes = {
                // Columns used in WHERE clauses during login and lookup — must be indexed
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_phone_number", columnList = "phone_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Auto-generated primary key. Uses IDENTITY strategy to delegate
     * ID generation to MySQL's AUTO_INCREMENT — correct for MySQL 8.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User's full legal name as provided during KYC registration.
     * Nullable = false enforced at both JPA and database level.
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * Unique email address — used as the primary login identifier.
     * Indexed for fast lookup during authentication.
     */
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Bcrypt-hashed password. The raw password is NEVER stored.
     * Length 255 to accommodate bcrypt hash output.
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * Unique phone number collected during KYC.
     * Indexed for fast lookup and uniqueness enforced at database level.
     */
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    /**
     * Physical address provided during KYC verification.
     */
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    /**
     * Government-issued ID number provided during KYC.
     * Stored as a unique identifier for fraud prevention.
     */
    @Column(name = "government_id", nullable = false, unique = true, length = 50)
    private String governmentId;

    /**
     * The roles assigned to this user. Stored in a separate join table
     * {@code user_roles}. Uses a Set to enforce uniqueness — a user
     * cannot hold the same role twice.
     *
     * <p>EAGER fetch is justified here because roles are always needed
     * when loading a user for authentication and JWT generation.
     * Loading without roles would require an immediate second query every time.
     *
     * <p>Design pattern note — roles use EnumType.STRING so the database
     * stores human-readable values (CUSTOMER, TELLER, ADMIN) rather than
     * ordinal integers. This prevents silent bugs if enum order ever changes.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles;

    /**
     * Whether this user account is currently active.
     * Inactive accounts cannot authenticate. Admins can suspend accounts
     * by setting this to false.
     *
     * <p>Named {@code isEnabled} to align with Spring Security's
     * {@code UserDetails.isEnabled()} contract.
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    /**
     * Whether KYC verification has been completed for this user.
     * Users with incomplete KYC may have restricted access to certain features.
     */
    @Column(name = "is_kyc_verified", nullable = false)
    @Builder.Default
    private boolean isKycVerified = false;

    /**
     * Whether Two-Factor Authentication is currently enabled for this user.
     * When true, login requires a valid TOTP code after password verification.
     */
    @Column(name = "is_two_factor_enabled", nullable = false)
    @Builder.Default
    private boolean isTwoFactorEnabled = false;

    /**
     * The TOTP secret key for this user's 2FA setup.
     * Generated when the user enables 2FA and used to verify TOTP codes.
     * Null when 2FA is not configured. Stored encrypted in production.
     */
    @Column(name = "two_factor_secret", length = 100)
    private String twoFactorSecret;

    /**
     * Timestamp of when this user record was first created.
     * Set automatically by Hibernate on insert — never updated after that.
     * Indexed implicitly via audit queries.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last update to this user record.
     * Updated automatically by Hibernate on every update operation.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Custom equals based solely on the unique email address.
     * Two User objects with the same email are the same user regardless
     * of other field differences — email is the natural business key.
     *
     * <p>We deliberately exclude the auto-generated {@code id} from equals
     * because a transient (not yet persisted) User has a null id, which
     * would cause incorrect inequality comparisons before persistence.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(email, user.email);
    }

    /**
     * Hash code consistent with equals — based on email only.
     */
    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    /**
     * Safe string representation that deliberately excludes sensitive fields
     * (password, twoFactorSecret, governmentId) to prevent accidental
     * exposure in logs.
     */
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", roles=" + roles +
                ", isEnabled=" + isEnabled +
                ", isKycVerified=" + isKycVerified +
                ", isTwoFactorEnabled=" + isTwoFactorEnabled +
                ", createdAt=" + createdAt +
                '}';
    }
}