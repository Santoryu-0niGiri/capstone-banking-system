
package com.capstone.common.dto;

/**
 * customerId is a String UUID matching CUSTOMER_MASTER.customer_id (VARCHAR2 36).
 */
public record RegisterResponse(String customerId, String firstName, String lastName, String email) {
}

