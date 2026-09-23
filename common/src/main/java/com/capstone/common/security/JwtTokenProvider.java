
package com.capstone.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Shared JWT utility used by login-service (issuer), api-gateway (edge validator),
 * and every resource service (local validator).
 *
 * Subject is customerId (String UUID) matching APP_USER_MASTER.customer_id.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                             @Value("${jwt.expiration-ms:3600000}") long expirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * @param customerId String UUID from APP_USER_MASTER / CUSTOMER_MASTER
     */
    public String generateToken(String customerId, String email, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(customerId)
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtException("Token has expired", e);
        }
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Returns the String UUID stored as the JWT subject. */
    public String getCustomerId(String token) {
        return parseClaims(token).getSubject();
    }

    public String getEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return (List<String>) parseClaims(token).get("roles", List.class);
    }
}

