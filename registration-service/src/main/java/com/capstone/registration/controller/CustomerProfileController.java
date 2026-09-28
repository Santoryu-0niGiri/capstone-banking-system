package com.capstone.registration.controller;

import com.capstone.common.dto.ApiResponse;
import com.capstone.common.dto.CustomerDTO;
import com.capstone.common.security.SecurityUtils;
import com.capstone.registration.service.AdminCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer profile endpoint accessible by the customer themselves or an admin.
 * Requires ROLE_CUSTOMER, ROLE_ADMIN, or ROLE_INTERNAL.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final AdminCustomerService adminCustomerService;

    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerDTO>> getCustomerProfile(
            @PathVariable("customerId") String customerId) {

        SecurityUtils.checkCustomerAccess(customerId, "view customer profile");
        return ResponseEntity.ok(
                ApiResponse.ok(adminCustomerService.getCustomer(customerId)));
    }
}
