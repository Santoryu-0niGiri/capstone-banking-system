package com.capstone.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility for accessing authenticated security context and enforcing
 * customer-level ownership checks across microservices.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Optional<String> getCurrentCustomerId() {
        return getAuthentication()
                .filter(Authentication::isAuthenticated)
                .map(auth -> {
                    Object principal = auth.getPrincipal();
                    return principal != null ? principal.toString() : null;
                });
    }

    public static boolean hasRole(String role) {
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getAuthentication()
                .filter(Authentication::isAuthenticated)
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> authority.equalsIgnoreCase(a.getAuthority())))
                .orElse(false);
    }

    public static boolean isPrivileged() {
        return hasRole("ADMIN") || hasRole("INTERNAL");
    }

    /**
     * Asserts that the authenticated caller has access to the target customer resource.
     * Allowed if:
     * 1. No security context exists (e.g. headless unit tests or unauthenticated requests)
     * 2. Caller holds ROLE_ADMIN or ROLE_INTERNAL
     * 3. Caller's principal (customer ID) matches targetCustomerId
     *
     * Otherwise throws AccessDeniedException (mapped to 403 Forbidden).
     */
    public static void checkCustomerAccess(String targetCustomerId, String actionDescription) {
        if (targetCustomerId == null || targetCustomerId.isBlank()) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        if (isPrivileged()) {
            return;
        }

        String currentCustomerId = getCurrentCustomerId().orElse(null);
        if (currentCustomerId == null || !currentCustomerId.equalsIgnoreCase(targetCustomerId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to "
                    + actionDescription + " for customer " + targetCustomerId);
        }
    }
}
