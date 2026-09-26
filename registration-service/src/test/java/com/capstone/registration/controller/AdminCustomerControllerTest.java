package com.capstone.registration.controller;

import com.capstone.common.dto.CustomerDTO;
import com.capstone.registration.service.AdminCustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCustomerControllerTest {

    @Mock
    private AdminCustomerService adminCustomerService;

    @InjectMocks
    private AdminCustomerController adminCustomerController;

    private final CustomerDTO sampleCustomer = new CustomerDTO(
            "cust-1",
            "Alice",
            "Reyes",
            "alice@example.com",
            "+63-917-123-4567",
            LocalDate.of(1990, 5, 15)
    );

    @Test
    @DisplayName("listAllCustomers should return directory of all registered customers")
    void listAllCustomers() {
        when(adminCustomerService.listAllCustomers()).thenReturn(List.of(sampleCustomer));

        var response = adminCustomerController.listAllCustomers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSize(1);
        verify(adminCustomerService).listAllCustomers();
    }

    @Test
    @DisplayName("getCustomer by customerId should return specific customer")
    void getCustomer() {
        when(adminCustomerService.getCustomer("cust-1")).thenReturn(sampleCustomer);

        var response = adminCustomerController.getCustomer("cust-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().email()).isEqualTo("alice@example.com");
        verify(adminCustomerService).getCustomer("cust-1");
    }
}
