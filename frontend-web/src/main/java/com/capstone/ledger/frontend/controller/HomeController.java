package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.model.UserSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(HttpSession session) {
        if (session != null && session.getAttribute(SessionAuthInterceptor.SESSION_USER) != null) {
            UserSession user = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
            if (user.isAdmin()) {
                return "redirect:/admin/dashboard";
            }
            return "redirect:/customer/dashboard";
        }
        return "index";
    }
}
