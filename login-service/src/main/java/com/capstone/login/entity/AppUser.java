
package com.capstone.login.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read-only mapping of APP_USER_MASTER.
 * Login-service authenticates against this table; it never writes to it.
 * Ownership of row creation lives in registration-service.
 */
@Entity
@Table(name = "app_user_master")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    // FK to CUSTOMER_MASTER — stored in the JWT subject claim
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    // username == email set at registration; used as the login identifier
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // CHECK ('ACTIVE','SUSPENDED','LOCKED','DISABLED')
    @Column(name = "active_status", nullable = false, length = 20)
    private String activeStatus;
}

