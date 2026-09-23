package com.capstone.login.service;

import com.capstone.common.dto.LoginRequest;
import com.capstone.common.dto.LoginResponse;
import com.capstone.common.exception.InvalidCredentialsException;
import com.capstone.common.security.JwtTokenProvider;
import com.capstone.login.entity.Customer;
import com.capstone.login.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(customer.getCustId(), customer.getEmail(), List.of("ROLE_CUSTOMER"));
        long expirySeconds = tokenProvider.getExpirationSeconds();
        tokenBlacklistService.registerActiveToken(token, expirySeconds);

        return LoginResponse.of(token, expirySeconds, customer.getCustId(), customer.getEmail());
    }

    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }
}
