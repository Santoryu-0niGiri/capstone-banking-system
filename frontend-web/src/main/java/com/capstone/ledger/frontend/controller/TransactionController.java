package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.form.TransactionForm;
import com.capstone.ledger.frontend.model.AccountView;
import com.capstone.ledger.frontend.model.TransactionView;
import com.capstone.ledger.frontend.model.UserSession;
import com.capstone.ledger.frontend.model.enums.TransactionType;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Controller handling:
 * - Deposit, Withdrawal, and Fund Transfers
 * - Cross-Currency calculation and rate discovery
 * - Transaction receipts and full ledger history
 */
@Controller
public class TransactionController {

    private final BankingApiClient bankingClient;

    public TransactionController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/transactions/new")
    public String newTransactionForm(@RequestParam(required = false) String fromAcctNo,
                                     @RequestParam(required = false) String type,
                                     HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        TransactionForm form = new TransactionForm();
        if (fromAcctNo != null) {
            form.setFromAcctNo(fromAcctNo);
        }
        if (type != null) {
            try {
                form.setTxnType(TransactionType.valueOf(type.toUpperCase()));
            } catch (Exception ignored) {}
        }

        // Customer can only mutate their own accounts; Admin can mutate any
        List<AccountView> userAccounts = user.isAdmin()
                ? bankingClient.getAllAccounts()
                : bankingClient.getAccountsByCustomerId(user.getCustomerId());

        List<AccountView> allAccounts = bankingClient.getAllAccounts();

        model.addAttribute("transactionForm", form);
        model.addAttribute("userAccounts", userAccounts);
        model.addAttribute("allAccounts", allAccounts);
        model.addAttribute("txnTypes", TransactionType.values());
        model.addAttribute("user", user);

        return "transaction/new";
    }

    @PostMapping("/transactions/new")
    public String submitTransaction(@Valid @ModelAttribute("transactionForm") TransactionForm form,
                                    BindingResult bindingResult, HttpSession session, Model model,
                                    RedirectAttributes redirectAttributes) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        if (form.getTxnType() == TransactionType.TRANSFER) {
            if (form.getToAcctNo() == null || form.getToAcctNo().isBlank()) {
                bindingResult.rejectValue("toAcctNo", "required", "Destination account is required for transfers");
            } else if (form.getToAcctNo().equalsIgnoreCase(form.getFromAcctNo())) {
                bindingResult.rejectValue("toAcctNo", "invalid", "Destination account cannot be the same as source account");
            }
        }

        if (bindingResult.hasErrors()) {
            List<AccountView> userAccounts = user.isAdmin()
                    ? bankingClient.getAllAccounts()
                    : bankingClient.getAccountsByCustomerId(user.getCustomerId());
            model.addAttribute("userAccounts", userAccounts);
            model.addAttribute("allAccounts", bankingClient.getAllAccounts());
            model.addAttribute("txnTypes", TransactionType.values());
            model.addAttribute("user", user);
            return "transaction/new";
        }

        try {
            List<TransactionView> results = bankingClient.executeTransaction(form);
            String txnId = results.isEmpty() ? "TXN-UNKNOWN" : results.get(0).getTxnId();

            redirectAttributes.addFlashAttribute("successMessage",
                    form.getTxnType().getDisplayName() + " processed successfully!");
            return "redirect:/transactions/receipt/" + txnId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            List<AccountView> userAccounts = user.isAdmin()
                    ? bankingClient.getAllAccounts()
                    : bankingClient.getAccountsByCustomerId(user.getCustomerId());
            model.addAttribute("userAccounts", userAccounts);
            model.addAttribute("allAccounts", bankingClient.getAllAccounts());
            model.addAttribute("txnTypes", TransactionType.values());
            model.addAttribute("user", user);
            return "transaction/new";
        }
    }

    @GetMapping("/transactions/receipt/{txnId}")
    public String transactionReceipt(@PathVariable String txnId, HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        TransactionView txn = bankingClient.getTransactionById(txnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + txnId));

        model.addAttribute("txn", txn);
        model.addAttribute("user", user);
        return "transaction/receipt";
    }

    @GetMapping("/transactions/history")
    public String history(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        List<TransactionView> transactions = user.isAdmin()
                ? bankingClient.getAllTransactions()
                : bankingClient.getAllTransactions().stream()
                        .filter(t -> {
                            // Only include transactions that involve this customer's accounts
                            List<AccountView> accounts = bankingClient.getAccountsByCustomerId(user.getCustomerId());
                            return accounts.stream().anyMatch(a -> a.getAccountId().equalsIgnoreCase(t.getAccountId()));
                        })
                        .toList();

        model.addAttribute("transactions", transactions);
        model.addAttribute("user", user);
        return "transaction/history";
    }

    /** AJAX helper to fetch real-time FX rate for cross-currency transfers */
    @GetMapping("/api/fx-rate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFxRate(@RequestParam String fromCurrency,
                                                         @RequestParam String toCurrency) {
        BigDecimal rate = bankingClient.getExchangeRate(fromCurrency, toCurrency);
        return ResponseEntity.ok(Map.of(
                "fromCurrency", fromCurrency.toUpperCase(),
                "toCurrency", toCurrency.toUpperCase(),
                "exchangeRate", rate,
                "isCrossCurrency", !fromCurrency.equalsIgnoreCase(toCurrency)
        ));
    }
}
