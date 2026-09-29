package com.capstone.ledger.frontend.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.model.ReconciliationRunView;
import com.capstone.ledger.frontend.model.UserSession;

import jakarta.servlet.http.HttpSession;

/**
 * Controller for the Reconciliation & Discrepancy Monitoring Portal.
 * Mirrors Reconciliation Service, RECON_RUN_AUDIT, and RECON_RESULT_AUDIT.
 */
@Controller
public class ReconciliationController {

    private static final ZoneId BANKING_ZONE = ZoneId.of("Asia/Manila");

    private final BankingApiClient bankingClient;

    public ReconciliationController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/admin/reconciliation")
    public String reconciliationDashboard(@RequestParam(required = false) String runId,
                                          HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null || !user.isAdmin()) {
            return "redirect:/login";
        }

        List<ReconciliationRunView> runs = bankingClient.getReconciliationRuns();
        ReconciliationRunView selectedRun = runs.stream()
                .filter(run -> runId != null && runId.equalsIgnoreCase(run.getRunId()))
                .findFirst()
                .orElse(runs.isEmpty() ? null : runs.get(0));
            if (selectedRun != null) {
            selectedRun.setResults(selectedRun.getResults().stream().sorted(Comparator.comparing(
                    com.capstone.ledger.frontend.model.ReconciliationResultView::getTransactionDateTime,
                    Comparator.nullsLast(Comparator.reverseOrder()))).toList());
            }

        model.addAttribute("user", user);
        model.addAttribute("runs", runs);
        model.addAttribute("selectedRun", selectedRun);
        model.addAttribute("startDate", LocalDate.now(BANKING_ZONE));
        model.addAttribute("endDate", LocalDate.now(BANKING_ZONE));
        model.addAttribute("totalChecked", selectedRun != null ? selectedRun.getTotalTxnChecked() : 0);
        model.addAttribute("totalMatched", selectedRun != null ? selectedRun.getTotalMatched() : 0);
        model.addAttribute("totalExceptions", selectedRun != null ? selectedRun.getTotalExceptions() : 0);
        return "admin/reconciliation";
    }

    @PostMapping("/admin/reconciliation/run")
    public String triggerRun(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                             RedirectAttributes redirectAttributes) {
        if (startDate.isAfter(endDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Window start date must be on or before the end date.");
            return "redirect:/admin/reconciliation";
        }

        bankingClient.triggerReconciliationRun(startDate, endDate);
        redirectAttributes.addFlashAttribute("successMessage",
                "Reconciliation completed for " + startDate + " through " + endDate + ".");
        return "redirect:/admin/reconciliation";
    }
}

