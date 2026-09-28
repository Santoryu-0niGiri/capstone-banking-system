
package com.capstone.common.dto;

/**
 * customerId is a String UUID matching APP_USER_MASTER.customer_id.
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String customerId,
        String email,
        String role
) {
    public static LoginResponse of(String token, long expiresInSeconds, String customerId, String email) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, customerId, email, "ROLE_CUSTOMER");
    }

    public static LoginResponse of(String token, long expiresInSeconds, String customerId, String email, String role) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, customerId, email, role);
    }
}

