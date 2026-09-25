package com.capstone.ledger.frontend.form;

import com.capstone.ledger.frontend.model.enums.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Backs the registration and KYC onboarding page (register.html).
 * Enforces banking customer identification standards matching CUSTOMER_MASTER and APP_USER_MASTER.
 */
public class RegisterForm {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "Enter a valid contact number (7-20 digits)")
    private String contactNo;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @NotBlank(message = "Residential address is required for KYC verification")
    private String address = "128 Pioneer St, Mandaluyong, Metro Manila";

    @NotBlank(message = "Select an ID type for KYC verification")
    private String idType = "PASSPORT";

    @NotBlank(message = "Valid ID number is required")
    private String idNumber = "P8291044A";

    @NotNull(message = "Select an initial account type")
    private AccountType initialAccountType = AccountType.SAVINGS;

    @NotBlank(message = "Currency is required")
    private String currencyCode = "PHP";

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    public RegisterForm() {
        this.birthDate = LocalDate.of(1995, 5, 20);
    }

    // --- Aliases for template backwards compatibility ---
    public String getPhoneNumber() { return contactNo; }
    public void setPhoneNumber(String phoneNumber) { this.contactNo = phoneNumber; }

    public LocalDate getBirthday() { return birthDate; }
    public void setBirthday(LocalDate birthday) { this.birthDate = birthday; }

    // --- Getters & Setters ---
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

    public AccountType getInitialAccountType() { return initialAccountType; }
    public void setInitialAccountType(AccountType initialAccountType) { this.initialAccountType = initialAccountType; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
