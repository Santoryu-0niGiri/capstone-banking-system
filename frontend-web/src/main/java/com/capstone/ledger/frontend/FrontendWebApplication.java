package com.capstone.ledger.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the standalone, fully-working Thymeleaf frontend.
 * Run with `mvn spring-boot:run` from frontend-web/ — see README.md at the repo
 * root for the seeded demo login.
 */
@SpringBootApplication
public class FrontendWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrontendWebApplication.class, args);
    }
}
