
package com.capstone.login.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read-only mapping of CUSTOMER_MASTER.
 * Used only to expose customer metadata in LoginResponse (email lookup).
 * Write ownership lives in registration-service.
 */
@Entity
@Table(name = "customer_master")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;
}

