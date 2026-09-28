package com.capstone.forex.controller;

import com.capstone.forex.entity.FxRateCache;
import com.capstone.forex.repository.FxRateCacheRepository;
import com.capstone.forex.service.FxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;
    private final FxRateCacheRepository fxRateCacheRepository;

    @GetMapping("/api/v1/fx-rate")
    public ResponseEntity<Map<String, Object>> getExchangeRate(
            @RequestParam("sourceCurrency") String sourceCurrency,
            @RequestParam("targetCurrency") String targetCurrency) {

        BigDecimal rate = fxRateService.getExchangeRate(sourceCurrency, targetCurrency);

        return ResponseEntity.ok(Map.of(
                "sourceCurrency", sourceCurrency.toUpperCase(),
                "targetCurrency", targetCurrency.toUpperCase(),
                "exchangeRate", rate,
                "isCrossCurrency", !sourceCurrency.equalsIgnoreCase(targetCurrency)
        ));
    }

    @GetMapping("/api/v1/forex/rates")
    public ResponseEntity<List<FxRateCache>> getAllRates() {
        return ResponseEntity.ok(fxRateCacheRepository.findAll());
    }
}
