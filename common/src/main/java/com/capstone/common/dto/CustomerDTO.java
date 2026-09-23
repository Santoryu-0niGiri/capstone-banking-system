package com.capstone.common.dto;

import java.time.LocalDate;

public record CustomerDTO(Long custId, String firstName, String lastName, String email,
                           String phoneNumber, LocalDate birthday) {
}
