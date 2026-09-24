package com.capstone.ledger.frontend.config;

import com.capstone.ledger.frontend.model.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Session-based authentication & role authorization guard.
 * Inspects UserSession in HttpSession.
 */
public class SessionAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER = "user";
    public static final String SESSION_CUST_ID = "custId"; // kept for backward compatibility

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();

        // Allow public static assets and auth paths
        if (uri.startsWith("/login") || uri.startsWith("/register") || uri.startsWith("/auth/")
                || uri.startsWith("/css/") || uri.startsWith("/js/") || uri.equals("/")
                || uri.startsWith("/error")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER) == null) {
            response.sendRedirect("/login");
            return false;
        }

        UserSession user = (UserSession) session.getAttribute(SESSION_USER);

        // Role authorization guard: only ADMIN can access /admin/**
        if (uri.startsWith("/admin/") && !user.isAdmin()) {
            response.sendRedirect("/customer/dashboard?denied=true");
            return false;
        }

        return true;
    }
}
