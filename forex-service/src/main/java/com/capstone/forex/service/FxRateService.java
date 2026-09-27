package com.capstone.forex.service;

import com.capstone.forex.entity.FxRateCache;
import com.capstone.forex.repository.FxRateCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Manages foreign exchange rate retrieval, canonical pair caching,
 * and scheduled updates from api.frankfurter.dev (FC-44).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateService {

    private final FxRateCacheRepository fxRateCacheRepository;

    @Value("${forex.frankfurter-url:https://api.frankfurter.dev/v1/latest}")
    private String frankfurterUrl;

    // Resilient fallback rates (guarantees system operation offline or if Frankfurter is down)
    private static final Map<String, BigDecimal> FALLBACK_RATES = Map.of(
            "USD:PHP", new BigDecimal("58.50000000"),
            "EUR:PHP", new BigDecimal("63.50000000"),
            "EUR:USD", new BigDecimal("1.08500000"),
            "GBP:USD", new BigDecimal("1.30000000"),
            "USD:JPY", new BigDecimal("149.50000000")
    );

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        seedInitialRatesIfEmpty();
        refreshRatesFromExternalApi();
    }

    /**
     * Looks up the exchange rate between the specified source and destination currencies.
     */
    public BigDecimal getExchangeRate(String sourceCurrency, String destCurrency) {
        if (sourceCurrency == null || destCurrency == null) {
            throw new IllegalArgumentException("Currency codes cannot be null");
        }

        String base = sourceCurrency.trim().toUpperCase();
        String quote = destCurrency.trim().toUpperCase();

        if (base.equals(quote)) {
            return BigDecimal.ONE.setScale(8, RoundingMode.HALF_UP);
        }

        // 1. Direct pair in DB cache
        Optional<FxRateCache> direct = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(base, quote);
        if (direct.isPresent()) {
            return direct.get().getRate();
        }

        // 2. Reverse pair in DB cache
        Optional<FxRateCache> reverse = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(quote, base);
        if (reverse.isPresent() && reverse.get().getRate().compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ONE.divide(reverse.get().getRate(), 8, RoundingMode.HALF_UP);
        }

        // 3. Fallback table
        String pairKey = base + ":" + quote;
        if (FALLBACK_RATES.containsKey(pairKey)) {
            return FALLBACK_RATES.get(pairKey);
        }

        String revKey = quote + ":" + base;
        if (FALLBACK_RATES.containsKey(revKey)) {
            return BigDecimal.ONE.divide(FALLBACK_RATES.get(revKey), 8, RoundingMode.HALF_UP);
        }

        // 4. Triangulation via USD if neither currency is USD
        if (!base.equals("USD") && !quote.equals("USD")) {
            Optional<BigDecimal> baseToUsd = lookupDirectOrReverseOrFallback(base, "USD");
            Optional<BigDecimal> usdToQuote = lookupDirectOrReverseOrFallback("USD", quote);
            if (baseToUsd.isPresent() && usdToQuote.isPresent()) {
                return baseToUsd.get().multiply(usdToQuote.get()).setScale(8, RoundingMode.HALF_UP);
            }
        }

        log.error("Unable to resolve exchange rate for {} to {}", base, quote);
        throw new IllegalArgumentException("Exchange rate for " + base + " to " + quote + " not found");
    }

    private Optional<BigDecimal> lookupDirectOrReverseOrFallback(String base, String quote) {
        Optional<FxRateCache> direct = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(base, quote);
        if (direct.isPresent()) {
            return Optional.of(direct.get().getRate());
        }
        Optional<FxRateCache> reverse = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(quote, base);
        if (reverse.isPresent() && reverse.get().getRate().compareTo(BigDecimal.ZERO) > 0) {
            return Optional.of(BigDecimal.ONE.divide(reverse.get().getRate(), 8, RoundingMode.HALF_UP));
        }
        String pairKey = base + ":" + quote;
        if (FALLBACK_RATES.containsKey(pairKey)) {
            return Optional.of(FALLBACK_RATES.get(pairKey));
        }
        String revKey = quote + ":" + base;
        if (FALLBACK_RATES.containsKey(revKey)) {
            return Optional.of(BigDecimal.ONE.divide(FALLBACK_RATES.get(revKey), 8, RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    /**
     * Scheduled hourly poll of external Frankfurter API (FC-44).
     */
    @Scheduled(fixedRateString = "${forex.poll-interval-ms:3600000}", initialDelay = 60000)
    public void scheduledRatePoll() {
        refreshRatesFromExternalApi();
    }

    @Transactional
    public void refreshRatesFromExternalApi() {
        try {
            log.info("Polling external FX rates from {}", frankfurterUrl);
            RestClient restClient = RestClient.builder().build();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(frankfurterUrl + "?base=USD")
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("rates")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rates = (Map<String, Object>) response.get("rates");

                for (Map.Entry<String, Object> entry : rates.entrySet()) {
                    String quote = entry.getKey().toUpperCase();
                    BigDecimal rate = new BigDecimal(entry.getValue().toString());
                    upsertRate("USD", quote, rate, "api.frankfurter.dev");
                }
                log.info("Successfully refreshed {} FX rates from Frankfurter API", rates.size());
            }
        } catch (Exception ex) {
            log.warn("External FX rate poll failed (fallback rates active): {}", ex.getMessage());
        }
    }

    @Transactional
    public void upsertRate(String base, String quote, BigDecimal rate, String source) {
        Optional<FxRateCache> existing = fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency(base, quote);
        FxRateCache record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setRate(rate);
            record.setFetchedAt(OffsetDateTime.now());
            record.setSource(source);
        } else {
            record = FxRateCache.builder()
                    .baseCurrency(base)
                    .quoteCurrency(quote)
                    .rate(rate)
                    .fetchedAt(OffsetDateTime.now())
                    .source(source)
                    .build();
        }
        fxRateCacheRepository.save(record);
    }

    @Transactional
    public void seedInitialRatesIfEmpty() {
        if (fxRateCacheRepository.count() == 0) {
            log.info("Seeding initial FX rates into fx_rate_cache");
            FALLBACK_RATES.forEach((pair, rate) -> {
                String[] parts = pair.split(":");
                upsertRate(parts[0], parts[1], rate, "initial-seed");
            });
        }
    }
}
