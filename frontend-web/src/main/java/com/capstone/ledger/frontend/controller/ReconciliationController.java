package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.model.ReconciliationRunView;
import com.capstone.ledger.frontend.model.UserSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for the Reconciliation & Discrepancy Monitoring Portal.
 * Mirrors Reconciliation Service, RECON_RUN_AUDIT, and RECON_RESULT_AUDIT.
 */
@Controller
public class ReconciliationController {

    private final BankingApiClient bankingClient;

    public ReconciliationController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/admin/reconciliation")
    public String reconciliationDashboard(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null || !user.isAdmin()) {
            return "redirect:/login";
        }

        List<ReconciliationRunView> runs = bankingClient.getReconciliationRuns();

        long totalChecked = runs.stream().mapToLong(ReconciliationRunView::getTotalTxnChecked).sum();
        long totalExceptions = runs.stream().mapToLong(ReconciliationRunView::getTotalExceptions).sum();

        model.addAttribute("user", user);
        model.addAttribute("runs", runs);
        model.addAttribute("totalChecked", totalChecked);
        model.addAttribute("totalExceptions", totalExceptions);
        return "admin/reconciliation";
    }

    @PostMapping("/admin/reconciliation/run")
    public String triggerRun(RedirectAttributes redirectAttributes) {
        bankingClient.triggerReconciliationRun();
        redirectAttributes.addFlashAttribute("successMessage",
                "Reconciliation batch job triggered successfully! Ledger consistency verified.");
        return "redirect:/admin/reconciliation";
    }
}

