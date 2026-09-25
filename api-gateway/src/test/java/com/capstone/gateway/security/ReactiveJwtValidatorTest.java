package com.capstone.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveJwtValidatorTest {

    private static final String SECRET = "CapstoneBankingSuperSecretSigningKeyMustBe256BitsLong!";
    private ReactiveJwtValidator validator;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        validator = new ReactiveJwtValidator(SECRET);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Valid token passes validation and extracts claims")
    void validToken_passesValidation() {
        String token = Jwts.builder()
                .subject("cust-uuid-12345")
                .claim("email", "john.doe@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        Optional<Claims> result = validator.validate(token);

        assertThat(result).isPresent();
        assertThat(result.get().getSubject()).isEqualTo("cust-uuid-12345");
        assertThat(result.get().get("email", String.class)).isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("Expired token returns empty (fails validation)")
    void expiredToken_returnsEmpty() {
        String expiredToken = Jwts.builder()
                .subject("cust-uuid-12345")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(signingKey)
                .compact();

        Optional<Claims> result = validator.validate(expiredToken);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Forged token signed with wrong secret key returns empty")
    void forgedToken_wrongSignature_returnsEmpty() {
        SecretKey attackerKey = Keys.hmacShaKeyFor("AttackerForgedKeyMustBeAtLeast256BitsLongForHmacShaSecret!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .subject("attacker-pretending-to-be-admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(attackerKey)
                .compact();

        Optional<Claims> result = validator.validate(forgedToken);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Forged token with tampered payload returns empty")
    void forgedToken_tamperedPayload_returnsEmpty() {
        String validToken = Jwts.builder()
                .subject("cust-uuid-12345")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        // Tamper with the token string
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "tampered" + "." + parts[2];

        Optional<Claims> result = validator.validate(tamperedToken);

        assertThat(result).isEmpty();
    }
}
