package com.capstone.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(

        @NotNull(message = "Customer id is required")
        Long custId,

        @NotNull(message = "Account type is required")
        @Pattern(regexp = "SAVINGS|CHECKING|WALLET", message = "Account type must be SAVINGS, CHECKING or WALLET")
        String acctType
) {
}
