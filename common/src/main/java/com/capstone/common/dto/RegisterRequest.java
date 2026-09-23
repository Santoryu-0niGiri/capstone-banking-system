
package com.capstone.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * contactNo maps to CUSTOMER_MASTER.contact_no (nullable VARCHAR2 20).
 * birthDate maps to CUSTOMER_MASTER.birth_date (nullable DATE).
 */
public record RegisterRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        // nullable in schema — phone is optional at registration
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "Phone number format is invalid")
        String contactNo,

        @NotNull(message = "Birthday is required")
        @Past(message = "Birthday must be in the past")
        LocalDate birthDate,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password
) {
}

