package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.model.NotificationView;
import com.capstone.ledger.frontend.model.UserSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * Controller for notifications and async audit alerts.
 * Matches Kafka consumer alerts and NOTIFICATION_AUDIT rows.
 */
@Controller
public class NotificationController {

    private final BankingApiClient bankingClient;

    public NotificationController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/notifications")
    public String list(HttpSession session, Model model) {
        UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/login";
        }

        List<NotificationView> notifications = bankingClient.getNotificationsByCustomerId(user.getCustomerId());

        model.addAttribute("user", user);
        model.addAttribute("notifications", notifications);
        return "notification/list";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable String id) {
        bankingClient.markNotificationAsRead(id);
        return "redirect:/notifications";
    }
}
