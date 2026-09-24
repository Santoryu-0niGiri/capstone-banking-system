package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.model.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Customer Portal views:
 * - Customer Dashboard (aggregated balances, account cards, recent transactions)
 * - Customer Profile / KYC verification details
 * - Per-customer summary views
 */
@Controller
public class CustomerController {

    private final BankingApiClient bankingClient;

    public CustomerController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/customer/dashboard")
    public String customerDashboard(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        CustomerView customer = bankingClient.getCustomerById(user.getCustomerId())
                .orElse(new CustomerView(user.getCustomerId(), user.getFullName(), "", user.getEmail(),
                        "", null, null));

        List<AccountView> accounts = bankingClient.getAccountsByCustomerId(user.getCustomerId());
        customer.setAccounts(accounts);

        // Fetch recent transactions across all customer accounts
        List<TransactionView> customerTransactions = new ArrayList<>();
        for (AccountView account : accounts) {
            customerTransactions.addAll(bankingClient.getTransactionsByAccountId(account.getAccountId()));
        }
        customerTransactions.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        List<NotificationView> notifications = bankingClient.getNotificationsByCustomerId(user.getCustomerId());
        long unreadCount = notifications.stream().filter(n -> !n.isRead()).count();

        model.addAttribute("user", user);
        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        model.addAttribute("recentTransactions", customerTransactions.stream().limit(8).toList());
        model.addAttribute("notifications", notifications.stream().limit(4).toList());
        model.addAttribute("unreadCount", unreadCount);

        return "customer/dashboard";
    }

    @GetMapping("/customer/profile")
    public String customerProfile(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        CustomerView customer = bankingClient.getCustomerById(user.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));

        model.addAttribute("user", user);
        model.addAttribute("customer", customer);
        return "customer/profile";
    }

    @GetMapping("/customers/{custId}")
    public String customerDetail(@PathVariable String custId, HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        // Allow Admin to view any customer; allow Customer to view only their own details
        if (!user.isAdmin() && !user.getCustomerId().equalsIgnoreCase(custId)) {
            return "redirect:/customer/dashboard?denied=true";
        }

        CustomerView customer = bankingClient.getCustomerById(custId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + custId));

        List<AccountView> accounts = bankingClient.getAccountsByCustomerId(custId);
        customer.setAccounts(accounts);

        model.addAttribute("user", user);
        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        return "customer/detail";
    }
}
