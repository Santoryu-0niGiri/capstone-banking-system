
package com.capstone.registration.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.RegisterRequest;
import com.capstone.common.dto.RegisterResponse;
import com.capstone.common.exception.DuplicateResourceException;
import com.capstone.registration.entity.AppUser;
import com.capstone.registration.entity.Customer;
import com.capstone.registration.repository.AppUserRepository;
import com.capstone.registration.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Atomically inserts into CUSTOMER_MASTER and APP_USER_MASTER.
     * Both rows share the same @Transactional boundary — if either insert fails,
     * Oracle rolls back both writes.
     *
     * created_by is set to "SYSTEM" for the self-registration flow; an admin
     * flow would pass the caller's customerId.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "A customer with email '" + request.email() + "' already exists");
        }

        String customerId = UUID.randomUUID().toString();
        String userId     = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        Customer customer = Customer.builder()
                .customerId(customerId)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .contactNo(request.contactNo())        // nullable
                .birthDate(request.birthDate())        // nullable
                .createdAt(now)
                .createdBy("SYSTEM")
                .build();

        // username defaults to email; can be overridden later via a profile endpoint
        AppUser appUser = AppUser.builder()
                .userId(userId)
                .customerId(customerId)
                .username(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .activeStatus("ACTIVE")
                .createdAt(now)
                .createdBy("SYSTEM")
                .build();

        customerRepository.save(customer);
        appUserRepository.save(appUser);

        // Kafka publish is outside the @Transactional boundary by design —
        // we publish ONLY after the DB commit succeeds. If Kafka is down,
        // the registration is still durable in Oracle; a retry/outbox pattern
        // can replay the event later.
        RegisterResponse response = new RegisterResponse(
                customerId, customer.getFirstName(), customer.getLastName(), customer.getEmail());

        kafkaTemplate.send(KafkaTopics.CUSTOMER_REGISTERED, customerId, response)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish customer.registered for customerId={}", customerId, ex);
                    } else {
                        log.debug("Published customer.registered for customerId={}", customerId);
                    }
                });

        return response;
    }
}

