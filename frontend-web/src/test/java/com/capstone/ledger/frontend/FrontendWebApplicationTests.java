package com.capstone.ledger.frontend;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.form.RegisterForm;
import com.capstone.ledger.frontend.form.TransactionForm;
import com.capstone.ledger.frontend.model.AccountView;
import com.capstone.ledger.frontend.model.CustomerView;
import com.capstone.ledger.frontend.model.TransactionView;
import com.capstone.ledger.frontend.model.UserSession;
import com.capstone.ledger.frontend.model.enums.AccountStatus;
import com.capstone.ledger.frontend.model.enums.AccountType;
import com.capstone.ledger.frontend.model.enums.KycStatus;
import com.capstone.ledger.frontend.model.enums.TransactionType;
import com.capstone.ledger.frontend.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendWebApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BankingApiClient bankingClient;

    @Test
    void contextLoads() {
        assertNotNull(bankingClient);
    }

    @Test
    void testPublicPagesAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void testAuthAndSessionFlow() throws Exception {
        // Authenticate admin
        Optional<UserSession> admin = bankingClient.authenticate("admin@ledgerbank.com", "admin123");
        assertTrue(admin.isPresent());
        assertEquals(UserRole.ADMIN, admin.get().getRole());

        // Authenticate customer Juan
        Optional<UserSession> juan = bankingClient.authenticate("juan.delacruz@example.com", "password123");
        assertTrue(juan.isPresent());
        assertEquals(UserRole.CUSTOMER, juan.get().getRole());
    }

    @Test
    void testProtectedRoutesRedirectWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testCustomerCannotAccessAdminRoutes() throws Exception {
        Optional<UserSession> juan = bankingClient.authenticate("juan.delacruz@example.com", "password123");
        assertTrue(juan.isPresent());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuthInterceptor.SESSION_USER, juan.get());
        session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, juan.get().getCustomerId());

        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/dashboard?denied=true"));
    }

    @Test
    void testAdminCanAccessAdminRoutes() throws Exception {
        Optional<UserSession> admin = bankingClient.authenticate("admin@ledgerbank.com", "admin123");
        assertTrue(admin.isPresent());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuthInterceptor.SESSION_USER, admin.get());
        session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, admin.get().getCustomerId());

        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("customerCount", "accountCount", "totalBalance"));

        mockMvc.perform(get("/admin/customers").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/customers"));

        mockMvc.perform(get("/admin/accounts").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts"));

        mockMvc.perform(get("/admin/reconciliation").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reconciliation"));
    }

    @Test
    void testCustomerDashboardAndAccountLedger() throws Exception {
        Optional<UserSession> juan = bankingClient.authenticate("juan.delacruz@example.com", "password123");
        assertTrue(juan.isPresent());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuthInterceptor.SESSION_USER, juan.get());
        session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, juan.get().getCustomerId());

        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/dashboard"))
                .andExpect(model().attributeExists("customer", "accounts", "recentTransactions"));

        mockMvc.perform(get("/accounts/ACCT-1001-PHP").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("account/detail"))
                .andExpect(model().attributeExists("account", "transactions"));
    }

    @Test
    void testTransactionDepositWithdrawalAndTransfer() {
        // 1. Test Deposit
        TransactionForm depForm = new TransactionForm();
        depForm.setTxnType(TransactionType.DEPOSIT);
        depForm.setFromAcctNo("ACCT-1001-PHP");
        depForm.setAmount(new BigDecimal("1000.00"));
        List<TransactionView> depResults = bankingClient.executeTransaction(depForm);
        assertFalse(depResults.isEmpty());
        assertEquals("COMMITTED", depResults.get(0).getAuditState().name());

        // 2. Test Withdrawal
        TransactionForm withForm = new TransactionForm();
        withForm.setTxnType(TransactionType.WITHDRAWAL);
        withForm.setFromAcctNo("ACCT-1001-PHP");
        withForm.setAmount(new BigDecimal("500.00"));
        List<TransactionView> withResults = bankingClient.executeTransaction(withForm);
        assertFalse(withResults.isEmpty());

        // 3. Test Transfer (PHP to PHP)
        TransactionForm xferForm = new TransactionForm();
        xferForm.setTxnType(TransactionType.TRANSFER);
        xferForm.setFromAcctNo("ACCT-1001-PHP");
        xferForm.setToAcctNo("ACCT-2001-PHP");
        xferForm.setAmount(new BigDecimal("250.00"));
        List<TransactionView> xferResults = bankingClient.executeTransaction(xferForm);
        assertEquals(2, xferResults.size()); // Debit leg + Credit leg

        // 4. Overdraft guard
        TransactionForm overdraft = new TransactionForm();
        overdraft.setTxnType(TransactionType.WITHDRAWAL);
        overdraft.setFromAcctNo("ACCT-1001-PHP");
        overdraft.setAmount(new BigDecimal("999999999.00"));
        assertThrows(IllegalArgumentException.class, () -> bankingClient.executeTransaction(overdraft));
    }

    @Test
    void testCrossCurrencyTransferCalculation() {
        // Transfer from PHP account to USD account
        TransactionForm fxForm = new TransactionForm();
        fxForm.setTxnType(TransactionType.TRANSFER);
        fxForm.setFromAcctNo("ACCT-2001-PHP");
        fxForm.setToAcctNo("ACCT-1002-USD");
        fxForm.setAmount(new BigDecimal("5650.00"));

        List<TransactionView> results = bankingClient.executeTransaction(fxForm);
        assertEquals(2, results.size());
        assertTrue(results.get(0).isCrossCurrency());
        assertNotNull(results.get(0).getFxRate());
        assertNotNull(results.get(0).getDestAmount());
    }

    @Test
    void testCustomerRegistrationAndKycOnboarding() {
        RegisterForm form = new RegisterForm();
        form.setFirstName("Antonio");
        form.setLastName("Luna");
        form.setEmail("antonio.luna@example.com");
        form.setContactNo("+63 917 111 2222");
        form.setBirthDate(LocalDate.of(1988, 10, 29));
        form.setAddress("Gen. Luna St, Manila");
        form.setIdType("PASSPORT");
        form.setIdNumber("P9988776A");
        form.setPassword("password123");
        form.setConfirmPassword("password123");
        form.setInitialAccountType(AccountType.SAVINGS);
        form.setCurrencyCode("PHP");

        CustomerView registered = bankingClient.registerCustomer(form);
        assertNotNull(registered.getCustomerId());
        assertEquals("Antonio Luna", registered.getFullName());
        assertEquals(KycStatus.PENDING_VERIFICATION, registered.getKycStatus());
        assertFalse(registered.getAccounts().isEmpty());

        // Admin approves KYC
        bankingClient.updateKycStatus(registered.getCustomerId(), KycStatus.VERIFIED);
        CustomerView updated = bankingClient.getCustomerById(registered.getCustomerId()).orElseThrow();
        assertEquals(KycStatus.VERIFIED, updated.getKycStatus());
    }

    @Test
    void testAdminFreezeAndUnfreezeAccount() {
        String testAcct = "ACCT-1001-PHP";
        bankingClient.updateAccountStatus(testAcct, AccountStatus.FROZEN);
        AccountView frozenAcct = bankingClient.getAccountById(testAcct).orElseThrow();
        assertEquals(AccountStatus.FROZEN, frozenAcct.getAccountStatus());

        // Mutation must be rejected on frozen account
        TransactionForm form = new TransactionForm();
        form.setTxnType(TransactionType.DEPOSIT);
        form.setFromAcctNo(testAcct);
        form.setAmount(new BigDecimal("100.00"));
        assertThrows(IllegalStateException.class, () -> bankingClient.executeTransaction(form));

        // Unfreeze
        bankingClient.updateAccountStatus(testAcct, AccountStatus.ACTIVE);
        AccountView activeAcct = bankingClient.getAccountById(testAcct).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, activeAcct.getAccountStatus());
    }

    @Test
    void testFxRateApiEndpoint() throws Exception {
        mockMvc.perform(get("/api/fx-rate?fromCurrency=USD&toCurrency=PHP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCrossCurrency").value(true))
                .andExpect(jsonPath("$.exchangeRate").value(56.5));
    }
}

