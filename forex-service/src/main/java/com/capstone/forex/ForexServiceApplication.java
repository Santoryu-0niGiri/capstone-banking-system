package com.capstone.forex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForexServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForexServiceApplication.class, args);
    }
}
