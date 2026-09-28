package com.capstone.registration.controller;

import com.capstone.common.dto.ApiResponse;
import com.capstone.common.dto.CustomerDTO;
import com.capstone.registration.service.AdminCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only customer directory endpoints.
 *
 * Routed via API Gateway at /api/admin/customers/**  (requires ROLE_ADMIN JWT claim).
 *
 * Provides read-only access to CUSTOMER_MASTER for admin governance
 * (KYC review, customer lookup, etc.).
 */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    /**
     * List all registered customers.
     * Supports the Admin Customer Directory view.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDTO>>> listAllCustomers() {
        return ResponseEntity.ok(
                ApiResponse.ok(adminCustomerService.listAllCustomers()));
    }

    /**
     * Get a single customer by customerId.
     * Useful for admin drill-down and KYC verification.
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerDTO>> getCustomer(
            @PathVariable("customerId") String customerId) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminCustomerService.getCustomer(customerId)));
    }
}
