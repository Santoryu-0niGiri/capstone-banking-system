
package com.capstone.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * customerId -> ACCOUNT_MASTER.customer_id (String UUID).
 * accountType -> CHECK SAVINGS|CHECKING|WALLET.
 * currencyCode -> CHAR(3), e.g. PHP, USD.
 */
public record CreateAccountRequest(

        @NotBlank(message = "Customer ID is required")
        @Size(max = 36)
        String customerId,

        @NotBlank(message = "Account type is required")
        @Pattern(regexp = "SAVINGS|CHECKING|WALLET",
                message = "Account type must be SAVINGS, CHECKING, or WALLET")
        String accountType,

        @NotBlank(message = "Currency code is required")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be 3 uppercase letters")
        String currencyCode
) {
}

