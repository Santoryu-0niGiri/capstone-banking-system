package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.model.AccountView;
import com.capstone.ledger.frontend.model.CustomerView;
import com.capstone.ledger.frontend.model.TransactionView;
import com.capstone.ledger.frontend.model.UserSession;
import com.capstone.ledger.frontend.model.enums.AccountStatus;
import com.capstone.ledger.frontend.model.enums.KycStatus;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for Bank Administrator operations:
 * - Executive Banking Dashboard (KPIs, Liquidity, Volume)
 * - Customer Directory & KYC Review
 * - System Accounts Registry & Freeze/Unfreeze Controls
 */
@Controller
public class AdminController {

    private final BankingApiClient bankingClient;

    public AdminController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping({"/admin/dashboard", "/dashboard"})
    public String adminDashboard(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }
        if (!user.isAdmin()) {
            return "redirect:/customer/dashboard";
        }

        List<CustomerView> customers = bankingClient.getAllCustomers();
        List<AccountView> accounts = bankingClient.getAllAccounts();
        List<TransactionView> transactions = bankingClient.getAllTransactions();

        // Calculate consolidated liquidity across all accounts in PHP
        BigDecimal totalLiquidityPhp = accounts.stream()
                .map(a -> {
                    BigDecimal rate = bankingClient.getExchangeRate(a.getCurrencyCode(), "PHP");
                    return a.getBalance().multiply(rate);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Account status counts
        long activeAccounts = accounts.stream().filter(a -> a.getAccountStatus() == AccountStatus.ACTIVE).count();
        long frozenAccounts = accounts.stream().filter(a -> a.getAccountStatus() == AccountStatus.FROZEN).count();

        // KYC Pending count
        long pendingKyc = customers.stream().filter(c -> c.getKycStatus() == KycStatus.PENDING_VERIFICATION).count();

        model.addAttribute("user", user);
        model.addAttribute("customers", customers);
        model.addAttribute("accounts", accounts);
        model.addAttribute("totalBalance", totalLiquidityPhp);
        model.addAttribute("customerCount", customers.size());
        model.addAttribute("accountCount", accounts.size());
        model.addAttribute("activeAccounts", activeAccounts);
        model.addAttribute("frozenAccounts", frozenAccounts);
        model.addAttribute("pendingKyc", pendingKyc);
        model.addAttribute("recentTransactions", transactions.stream().limit(10).toList());

        return "admin/dashboard";
    }

    @GetMapping("/admin/customers")
    public String customerDirectory(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null || !user.isAdmin()) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("customers", bankingClient.getAllCustomers());
        return "admin/customers";
    }

    @PostMapping("/admin/customers/{customerId}/kyc")
    public String updateCustomerKyc(@PathVariable String customerId,
                                    @RequestParam KycStatus status,
                                    RedirectAttributes redirectAttributes) {
        bankingClient.updateKycStatus(customerId, status);
        redirectAttributes.addFlashAttribute("successMessage",
                "Customer " + customerId + " KYC status updated to " + status.getDisplayName());
        return "redirect:/admin/customers";
    }

    @GetMapping("/admin/accounts")
    public String accountsRegistry(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null || !user.isAdmin()) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("accounts", bankingClient.getAllAccounts());
        return "admin/accounts";
    }

    @PostMapping("/admin/accounts/{accountId}/status")
    public String toggleAccountStatus(@PathVariable String accountId,
                                      @RequestParam AccountStatus status,
                                      RedirectAttributes redirectAttributes) {
        bankingClient.updateAccountStatus(accountId, status);
        redirectAttributes.addFlashAttribute("successMessage",
                "Account " + accountId + " status updated to " + status.getDisplayName());
        return "redirect:/admin/accounts";
    }
}

