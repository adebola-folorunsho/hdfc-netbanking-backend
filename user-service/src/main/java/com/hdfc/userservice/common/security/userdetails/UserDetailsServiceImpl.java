package com.hdfc.userservice.common.security.userdetails;

import com.hdfc.userservice.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security's entry point for loading a user during authentication.
 *
 * <p>Spring Security calls {@link #loadUserByUsername(String)} automatically
 * during the authentication process — passing the value the user submitted
 * as their "username". In this system, the username is the email address.
 *
 * <p>This class bridges the gap between our {@link com.hdfc.userservice.domain.User}
 * domain entity and Spring Security's {@link UserDetails} contract. Spring Security
 * knows nothing about our User class — it only understands UserDetails. This
 * class performs that translation.
 *
 * <p>Implements {@link UserDetailsService} — a Spring Security interface with
 * a single method. This satisfies ISP (Interface Segregation Principle) —
 * we implement only the interface we need, nothing more.
 *
 * <p>Depends on {@link UserRepository} — an abstraction, never a concrete class.
 * This satisfies DIP (Dependency Inversion Principle).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // Depends on the repository interface — never the concrete implementation
    private final UserRepository userRepository;

    /**
     * Loads a user by their email address for Spring Security authentication.
     *
     * <p>Spring Security calls this method during login. The returned
     * {@link UserDetails} object is used to verify the submitted password
     * and populate the {@link org.springframework.security.core.Authentication}
     * object in the security context.
     *
     * <p>The method is {@code @Transactional} because loading the user also
     * triggers loading of their roles via the {@code user_roles} join table
     * (EAGER fetch). The transaction ensures both the user row and the roles
     * collection are loaded within the same database session — avoiding a
     * LazyInitializationException if the session closes between the two loads.
     *
     * @param email the email address submitted by the user during login
     * @return a fully populated {@link UserDetails} object
     * @throws UsernameNotFoundException if no user exists with the given email.
     *         Note: Spring Security catches this and converts it to a
     *         BadCredentialsException to prevent user enumeration.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        log.debug("Loading user by email: {}", email);

        // Find the user — throw if not found. Spring Security catches
        // UsernameNotFoundException and converts it to BadCredentialsException
        // so the client never knows whether the email exists or not.
        com.hdfc.userservice.domain.User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authentication attempt for unknown email: {}", email);
                    return new UsernameNotFoundException(
                            "No user found with email: " + email);
                });

        // Convert our Role enum set into Spring Security GrantedAuthority objects.
        // Spring Security requires the ROLE_ prefix when using hasRole() in
        // @PreAuthorize — we add it here during the conversion.
        // Example: Role.ADMIN becomes SimpleGrantedAuthority("ROLE_ADMIN")
        Set<SimpleGrantedAuthority> authorities = user.getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        // Build and return Spring Security's UserDetails using our user's data.
        // We use Spring Security's built-in User builder — not our domain User —
        // because Spring Security only knows how to work with UserDetails objects.
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(!user.isEnabled())
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}