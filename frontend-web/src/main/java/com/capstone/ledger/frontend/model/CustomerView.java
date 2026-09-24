package com.capstone.ledger.frontend.model;

import com.capstone.ledger.frontend.model.enums.KycStatus;
import com.capstone.ledger.frontend.model.enums.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Frontend view model for a customer, mirroring CUSTOMER_MASTER and APP_USER_MASTER.
 */
public class CustomerView {

    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String contactNo;
    private LocalDate birthDate;
    private String address;
    private String idType;
    private String idNumber;
    private KycStatus kycStatus;
    private UserRole role;
    private LocalDateTime createdAt;
    private List<AccountView> accounts = new ArrayList<>();

    public CustomerView() {
        this.kycStatus = KycStatus.VERIFIED;
        this.role = UserRole.CUSTOMER;
        this.createdAt = LocalDateTime.now();
    }

    public CustomerView(String customerId, String firstName, String lastName, String email,
                        String contactNo, LocalDate birthDate, LocalDateTime createdAt) {
        this(customerId, firstName, lastName, email, contactNo, birthDate,
             "Metro Manila, Philippines", "PASSPORT", "P" + System.currentTimeMillis() % 1000000,
             KycStatus.VERIFIED, UserRole.CUSTOMER, createdAt);
    }

    public CustomerView(String customerId, String firstName, String lastName, String email,
                        String contactNo, LocalDate birthDate, String address, String idType,
                        String idNumber, KycStatus kycStatus, UserRole role, LocalDateTime createdAt) {
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.contactNo = contactNo;
        this.birthDate = birthDate;
        this.address = address;
        this.idType = idType;
        this.idNumber = idNumber;
        this.kycStatus = kycStatus != null ? kycStatus : KycStatus.PENDING_VERIFICATION;
        this.role = role != null ? role : UserRole.CUSTOMER;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public int getAccountsCount() {
        return accounts != null ? accounts.size() : 0;
    }

    public BigDecimal getTotalBalanceInPhp() {
        if (accounts == null) return BigDecimal.ZERO;
        // Simple aggregate for dashboard display (treating non-PHP by rough seed rate or 1:1 if PHP)
        return accounts.stream()
                .map(a -> {
                    BigDecimal bal = a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO;
                    if ("USD".equalsIgnoreCase(a.getCurrencyCode())) return bal.multiply(new BigDecimal("56.50"));
                    if ("EUR".equalsIgnoreCase(a.getCurrencyCode())) return bal.multiply(new BigDecimal("61.20"));
                    return bal;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- Aliases for template backwards compatibility ---
    public String getCustId() { return customerId; }
    public void setCustId(String custId) { this.customerId = custId; }

    public String getPhoneNumber() { return contactNo; }
    public void setPhoneNumber(String phoneNumber) { this.contactNo = phoneNumber; }

    public LocalDate getBirthday() { return birthDate; }
    public void setBirthday(LocalDate birthday) { this.birthDate = birthday; }

    // --- Getters and Setters ---
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getContactNo() { return contactNo; }
    public void setContactNo(String contactNo) { this.contactNo = contactNo; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public KycStatus getKycStatus() { return kycStatus; }
    public void setKycStatus(KycStatus kycStatus) { this.kycStatus = kycStatus; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<AccountView> getAccounts() { return accounts; }
    public void setAccounts(List<AccountView> accounts) { this.accounts = accounts; }
}
