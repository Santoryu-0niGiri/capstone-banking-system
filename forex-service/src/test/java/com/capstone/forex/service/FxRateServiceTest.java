package com.capstone.forex.service;

import com.capstone.forex.entity.FxRateCache;
import com.capstone.forex.repository.FxRateCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock
    private FxRateCacheRepository fxRateCacheRepository;

    private FxRateService fxRateService;

    @BeforeEach
    void setUp() {
        fxRateService = new FxRateService(fxRateCacheRepository);
    }

    @Test
    @DisplayName("getExchangeRate: same currency returns 1.00000000")
    void sameCurrency_returnsOne() {
        BigDecimal rate = fxRateService.getExchangeRate("PHP", "PHP");
        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("getExchangeRate: direct pair in cache returns cached rate")
    void directPair_inCache_returnsRate() {
        FxRateCache cached = FxRateCache.builder()
                .rateId(UUID.randomUUID())
                .baseCurrency("USD")
                .quoteCurrency("EUR")
                .rate(new BigDecimal("0.92000000"))
                .fetchedAt(OffsetDateTime.now())
                .source("api.frankfurter.dev")
                .build();

        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("USD", "EUR"))
                .thenReturn(Optional.of(cached));

        BigDecimal rate = fxRateService.getExchangeRate("USD", "EUR");
        assertThat(rate).isEqualByComparingTo("0.92000000");
    }

    @Test
    @DisplayName("getExchangeRate: reverse pair in cache returns reciprocal")
    void reversePair_inCache_returnsReciprocal() {
        FxRateCache cached = FxRateCache.builder()
                .rateId(UUID.randomUUID())
                .baseCurrency("USD")
                .quoteCurrency("PHP")
                .rate(new BigDecimal("50.00000000"))
                .fetchedAt(OffsetDateTime.now())
                .source("api.frankfurter.dev")
                .build();

        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("PHP", "USD"))
                .thenReturn(Optional.empty());
        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("USD", "PHP"))
                .thenReturn(Optional.of(cached));

        BigDecimal rate = fxRateService.getExchangeRate("PHP", "USD");
        // 1 / 50 = 0.02
        assertThat(rate).isEqualByComparingTo("0.02000000");
    }

    @Test
    @DisplayName("getExchangeRate: fallback table returns hardcoded default when not in cache")
    void fallbackTable_returnsDefaultRate() {
        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("USD", "PHP"))
                .thenReturn(Optional.empty());
        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("PHP", "USD"))
                .thenReturn(Optional.empty());

        BigDecimal rate = fxRateService.getExchangeRate("USD", "PHP");
        assertThat(rate).isEqualByComparingTo("58.50000000");
    }

    @Test
    @DisplayName("getExchangeRate: throws IllegalArgumentException on unknown currency pair")
    void unknownPair_throwsException() {
        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("XYZ", "ABC"))
                .thenReturn(Optional.empty());
        when(fxRateCacheRepository.findByBaseCurrencyAndQuoteCurrency("ABC", "XYZ"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fxRateService.getExchangeRate("XYZ", "ABC"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
