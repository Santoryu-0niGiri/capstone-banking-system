package com.capstone.registration.service;

import com.capstone.common.dto.CustomerDTO;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.registration.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin read-only operations against CUSTOMER_MASTER (Oracle).
 * Provides the data backing the Admin Customer Directory.
 */
@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerDTO> listAllCustomers() {
        return customerRepository.findAll().stream()
                .map(c -> new CustomerDTO(
                        c.getCustomerId(),
                        c.getFirstName(),
                        c.getLastName(),
                        c.getEmail(),
                        c.getContactNo(),
                        c.getBirthDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerDTO getCustomer(String customerId) {
        return customerRepository.findById(customerId)
                .map(c -> new CustomerDTO(
                        c.getCustomerId(),
                        c.getFirstName(),
                        c.getLastName(),
                        c.getEmail(),
                        c.getContactNo(),
                        c.getBirthDate()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer " + customerId + " not found"));
    }
}
