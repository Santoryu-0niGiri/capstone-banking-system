package com.capstone.ledger.frontend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes application-wide backend mode and gateway connection info to all Thymeleaf templates.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @Value("${banking.backend.mode:gateway}")
    private String backendMode;

    @Value("${banking.backend.gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    @ModelAttribute("backendMode")
    public String getBackendMode() {
        return backendMode;
    }

    @ModelAttribute("gatewayUrl")
    public String getGatewayUrl() {
        return gatewayUrl;
    }

    @ModelAttribute("isGatewayMode")
    public boolean isGatewayMode() {
        return "gateway".equalsIgnoreCase(backendMode);
    }
}
