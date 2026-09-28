
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

import java.time.LocalDateTime;

/**
 * Maps to APP_USER_MASTER (Oracle XE 21c).
 * Created atomically alongside CUSTOMER_MASTER inside the same transaction on registration.
 * active_status CHECK ('ACTIVE','SUSPENDED','LOCKED','DISABLED').
 */
@Entity
@Table(name = "app_user_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @Column(name = "user_id", length = 36, updatable = false, nullable = false)
    private String userId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    // username is used as the login identifier; must be unique
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 20)
    private String role;


    // DEFAULT 'ACTIVE' in DDL
    @Column(name = "active_status", nullable = false, length = 20)
    private String activeStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}

