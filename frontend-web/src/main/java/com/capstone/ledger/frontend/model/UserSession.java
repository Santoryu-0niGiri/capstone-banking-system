package com.capstone.ledger.frontend.model;

import com.capstone.ledger.frontend.model.enums.UserRole;
import java.io.Serializable;

/**
 * Encapsulates the authenticated user's session context.
 * Kept in HttpSession and checked by SessionAuthInterceptor.
 */
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String customerId;
    private String username;
    private String fullName;
    private String email;
    private UserRole role;
    private String token;

    public UserSession() {
    }

    public UserSession(String userId, String customerId, String username, String fullName, String email, UserRole role, String token) {
        this.userId = userId;
        this.customerId = customerId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.token = token;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isCustomer() {
        return role == UserRole.CUSTOMER;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}

