
package com.capstone.common.dto;

import java.time.LocalDate;

/**
 * Mirrors CUSTOMER_MASTER columns.
 * contactNo -> contact_no, birthDate -> birth_date.
 */
public record CustomerDTO(
        String customerId,
        String firstName,
        String lastName,
        String email,
        String contactNo,
        LocalDate birthDate
) {
}

