package com.capstone.ledger.frontend.controller;

import com.capstone.ledger.frontend.adapter.BankingApiClient;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import com.capstone.ledger.frontend.form.LoginForm;
import com.capstone.ledger.frontend.form.RegisterForm;
import com.capstone.ledger.frontend.model.CustomerView;
import com.capstone.ledger.frontend.model.UserSession;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Handles Authentication, KYC Onboarding, Demo Account Switching, and Session Termination.
 */
@Controller
public class AuthController {

    private final BankingApiClient bankingClient;

    public AuthController(BankingApiClient bankingClient) {
        this.bankingClient = bankingClient;
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String registered,
                            @RequestParam(required = false) String error,
                            Model model) {
        model.addAttribute("loginForm", new LoginForm());
        if (registered != null) {
            model.addAttribute("successMessage", "Account created successfully! You can now log in.");
        }
        if (error != null) {
            model.addAttribute("errorMessage", "Session expired or access denied. Please log in again.");
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginForm form,
                        BindingResult bindingResult, HttpSession session, Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }

        Optional<UserSession> userOpt = bankingClient.authenticate(form.getEmail(), form.getPassword());
        if (userOpt.isEmpty()) {
            model.addAttribute("errorMessage", "Invalid email or password. Please verify your credentials.");
            return "auth/login";
        }

        UserSession user = userOpt.get();
        session.setAttribute(SessionAuthInterceptor.SESSION_USER, user);
        session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, user.getCustomerId());

        if (user.isAdmin()) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/customer/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        if (session != null) {
            session.invalidate();
        }
        redirectAttributes.addFlashAttribute("successMessage", "You have been logged out successfully.");
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                           BindingResult bindingResult, HttpSession session, Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "match", "Passwords do not match");
            return "auth/register";
        }

        try {
            CustomerView newCustomer = bankingClient.registerCustomer(form);

            // Auto-login the new customer
            Optional<UserSession> userOpt = bankingClient.authenticate(form.getEmail(), form.getPassword());
            if (userOpt.isPresent()) {
                session.setAttribute(SessionAuthInterceptor.SESSION_USER, userOpt.get());
                session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, newCustomer.getCustomerId());
                redirectAttributes.addFlashAttribute("successMessage",
                        "Welcome, " + newCustomer.getFirstName() + "! Your account was opened and KYC verification is in progress.");
                return "redirect:/customer/dashboard";
            }

            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Registration failed: " + e.getMessage());
            return "auth/register";
        }
    }

    /**
     * Demo role switcher to allow seamless toggling between Admin and Customer personas
     * without having to log out and re-type passwords during presentations and defenses.
     */
    @GetMapping("/auth/switch")
    public String switchDemoRole(@RequestParam String role, HttpSession session, RedirectAttributes redirectAttributes) {
        if ("admin".equalsIgnoreCase(role)) {
            Optional<UserSession> opt = bankingClient.authenticate("admin@ledgerbank.com", "admin123");
            opt.ifPresent(u -> {
                session.setAttribute(SessionAuthInterceptor.SESSION_USER, u);
                session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, u.getCustomerId());
            });
            redirectAttributes.addFlashAttribute("infoMessage", "Switched view to System Administrator.");
            return "redirect:/admin/dashboard";
        } else if ("maria".equalsIgnoreCase(role)) {
            Optional<UserSession> opt = bankingClient.authenticate("maria.santos@example.com", "password123");
            opt.ifPresent(u -> {
                session.setAttribute(SessionAuthInterceptor.SESSION_USER, u);
                session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, u.getCustomerId());
            });
            redirectAttributes.addFlashAttribute("infoMessage", "Switched view to Customer: Maria Santos.");
            return "redirect:/customer/dashboard";
        } else {
            // Default Juan Dela Cruz
            Optional<UserSession> opt = bankingClient.authenticate("juan.delacruz@example.com", "password123");
            opt.ifPresent(u -> {
                session.setAttribute(SessionAuthInterceptor.SESSION_USER, u);
                session.setAttribute(SessionAuthInterceptor.SESSION_CUST_ID, u.getCustomerId());
            });
            redirectAttributes.addFlashAttribute("infoMessage", "Switched view to Customer: Juan Dela Cruz.");
            return "redirect:/customer/dashboard";
        }
    }
}
