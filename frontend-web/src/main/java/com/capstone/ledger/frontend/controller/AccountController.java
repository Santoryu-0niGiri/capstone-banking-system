package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.form.OpenAccountForm;
import com.capstone.ledger.frontend.model.AccountView;
import com.capstone.ledger.frontend.model.TransactionView;
import com.capstone.ledger.frontend.model.UserSession;
import com.capstone.ledger.frontend.model.enums.AccountType;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for Account operations:
 * - Single-account detail view & ledger history
 * - Opening new multi-currency accounts
 */
@Controller
public class AccountController {

    private final BankingApiClient bankingClient;

    public AccountController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/accounts/{acctId}")
    public String accountDetail(@PathVariable String acctId, HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        AccountView account = bankingClient.getAccountById(acctId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + acctId));

        // Ownership guard: Admin can see any account; customer only their own
        if (!user.isAdmin() && !user.getCustomerId().equalsIgnoreCase(account.getCustomerId())) {
            return "redirect:/customer/dashboard?denied=true";
        }

        List<TransactionView> transactions = bankingClient.getTransactionsByAccountId(acctId);

        model.addAttribute("user", user);
        model.addAttribute("account", account);
        model.addAttribute("transactions", transactions);
        return "account/detail";
    }

    @GetMapping("/accounts/open")
    public String openAccountForm(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        OpenAccountForm form = new OpenAccountForm(user.getCustomerId());
        model.addAttribute("openAccountForm", form);
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("currencies", List.of("PHP", "USD", "EUR", "GBP", "SGD"));
        model.addAttribute("user", user);
        return "account/open";
    }

    @PostMapping("/accounts/open")
    public String openAccount(@Valid @ModelAttribute("openAccountForm") OpenAccountForm form,
                              BindingResult bindingResult, HttpSession session, Model model,
                              RedirectAttributes redirectAttributes) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("currencies", List.of("PHP", "USD", "EUR", "GBP", "SGD"));
            model.addAttribute("user", user);
            return "account/open";
        }

        String targetCustomerId = user.isAdmin() && form.getCustomerId() != null && !form.getCustomerId().isBlank()
                ? form.getCustomerId()
                : user.getCustomerId();

        AccountView newAccount = bankingClient.openAccount(
                targetCustomerId, form.getAccountType(), form.getCurrencyCode(), form.getInitialDeposit()
        );

        redirectAttributes.addFlashAttribute("successMessage",
                "New " + newAccount.getCurrencyCode() + " " + newAccount.getAcctType().getDisplayName() +
                " (" + newAccount.getAccountId() + ") successfully opened!");

        return "redirect:/accounts/" + newAccount.getAccountId();
    }

    /** Legacy endpoint for quick open-account from customer detail page */
    @PostMapping("/accounts/new")
    public String openAccountQuick(@RequestParam(required = false) AccountType acctType,
                                   @RequestParam(required = false, defaultValue = "PHP") String currencyCode,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        AccountType type = acctType != null ? acctType : AccountType.SAVINGS;
        AccountView newAccount = bankingClient.openAccount(user.getCustomerId(), type, currencyCode, BigDecimal.ZERO);

        redirectAttributes.addFlashAttribute("successMessage", "New " + type.getDisplayName() + " opened!");
        return "redirect:/accounts/" + newAccount.getAccountId();
    }
}
