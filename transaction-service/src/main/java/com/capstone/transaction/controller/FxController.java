package com.capstone.transaction.controller;

import com.capstone.transaction.service.FxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fx-rate")
@RequiredArgsConstructor
public class FxController {

    private final FxRateService fxRateService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getExchangeRate(
            @RequestParam String sourceCurrency,
            @RequestParam String targetCurrency) {

        BigDecimal rate = fxRateService.getExchangeRate(sourceCurrency, targetCurrency);

        return ResponseEntity.ok(Map.of(
                "sourceCurrency", sourceCurrency.toUpperCase(),
                "targetCurrency", targetCurrency.toUpperCase(),
                "exchangeRate", rate,
                "isCrossCurrency", !sourceCurrency.equalsIgnoreCase(targetCurrency)
        ));
    }
}