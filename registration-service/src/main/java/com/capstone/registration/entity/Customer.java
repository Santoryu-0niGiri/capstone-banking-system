
package com.capstone.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to CUSTOMER_MASTER (Oracle XE 21c).
 * customer_id is a client-assigned VARCHAR2(36) UUID — no IDENTITY column.
 */
@Entity
@Table(name = "customer_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(name = "customer_id", length = 36, updatable = false, nullable = false)
    private String customerId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    // nullable per DDL — contact_no VARCHAR2(20)
    @Column(name = "contact_no", length = 20)
    private String contactNo;

    // nullable per DDL — birth_date DATE
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}

