package com.capstone.registration.service;

import com.capstone.common.dto.RegisterRequest;
import com.capstone.common.dto.RegisterResponse;
import com.capstone.common.exception.DuplicateResourceException;
import com.capstone.registration.entity.Customer;
import com.capstone.registration.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A customer with email '" + request.email() + "' already exists");
        }

        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .birthday(request.birthday())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        Customer saved = customerRepository.save(customer);
        return new RegisterResponse(saved.getCustId(), saved.getFirstName(), saved.getLastName(), saved.getEmail());
    }
}
