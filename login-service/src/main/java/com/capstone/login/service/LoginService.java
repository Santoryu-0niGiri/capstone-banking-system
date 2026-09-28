
package com.capstone.login.service;

import com.capstone.common.dto.LoginRequest;
import com.capstone.common.dto.LoginResponse;
import com.capstone.common.exception.InvalidCredentialsException;
import com.capstone.common.security.JwtTokenProvider;
import com.capstone.login.entity.AppUser;
import com.capstone.login.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Authenticates against APP_USER_MASTER:
     * 1. Lookup by username (== email set at registration).
     * 2. Verify active_status is ACTIVE — suspended/locked/disabled accounts rejected.
     * 3. BCrypt password match against password_hash.
     * 4. Issue JWT with customerId (String UUID) as subject.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser appUser = appUserRepository.findByUsername(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!"ACTIVE".equals(appUser.getActiveStatus())) {
            throw new InvalidCredentialsException(
                    "Account is not active (status=" + appUser.getActiveStatus() + ")");
        }

        if (!passwordEncoder.matches(request.password(), appUser.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // customerId (String UUID) becomes the JWT subject — matches CUSTOMER_MASTER.customer_id
        String dbRole = appUser.getRole() != null ? appUser.getRole() : "CUSTOMER";
        String token = tokenProvider.generateToken(
                appUser.getCustomerId(), request.email(), List.of("ROLE_" + dbRole));
        long expirySeconds = tokenProvider.getExpirationSeconds();

        tokenBlacklistService.registerActiveToken(token, expirySeconds);

        // Read roles from JWT claims (or fallback to basic CUSTOMER)
        List<String> roles = tokenProvider.getRoles(token);
        String roleStr = roles != null && !roles.isEmpty() ? roles.get(0) : "ROLE_CUSTOMER";
        
        return LoginResponse.of(token, expirySeconds, appUser.getCustomerId(), request.email(), roleStr);
    }

    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }
}

