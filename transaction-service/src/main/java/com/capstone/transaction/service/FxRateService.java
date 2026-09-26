package com.capstone.transaction.service;

import com.capstone.transaction.entity.postgres.FxRateCache;
import com.capstone.transaction.repository.postgres.FxRateCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateService {

    private final FxRateCacheRepository fxRateCacheRepository;

    /**
     * Looks up the exchange rate between the specified source and destination currencies.
     * Returns 1.0 if the currencies are the same.
     * Throws an IllegalArgumentException if the rate cannot be found in the cache.
     */
    public BigDecimal getExchangeRate(String sourceCurrency, String destCurrency) {
        if (sourceCurrency.equalsIgnoreCase(destCurrency)) {
            return BigDecimal.ONE;
        }

        Optional<FxRateCache> cachedRate = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(
                sourceCurrency.toUpperCase(), destCurrency.toUpperCase());

        if (cachedRate.isPresent()) {
            return cachedRate.get().getRate();
        } else {
            // Check for reverse pair
            Optional<FxRateCache> reverseCachedRate = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(
                    destCurrency.toUpperCase(), sourceCurrency.toUpperCase());
            
            if (reverseCachedRate.isPresent()) {
                 return BigDecimal.ONE.divide(reverseCachedRate.get().getRate(), 8, java.math.RoundingMode.HALF_UP);
            }
            log.error("Exchange rate for {} to {} not found in cache", sourceCurrency, destCurrency);
            throw new IllegalArgumentException("Exchange rate for " + sourceCurrency + " to " + destCurrency + " not found");
        }
    }
}
