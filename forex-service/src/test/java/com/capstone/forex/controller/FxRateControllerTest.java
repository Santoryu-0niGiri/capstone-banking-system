package com.capstone.forex.controller;

import com.capstone.forex.repository.FxRateCacheRepository;
import com.capstone.forex.service.FxRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FxRateController.class)
@AutoConfigureMockMvc(addFilters = false)
class FxRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FxRateService fxRateService;

    @MockBean
    private FxRateCacheRepository fxRateCacheRepository;

    @Test
    @DisplayName("GET /api/v1/fx-rate returns exchange rate and currency details")
    void getExchangeRate_success() throws Exception {
        when(fxRateService.getExchangeRate("PHP", "USD"))
                .thenReturn(new BigDecimal("0.01709402"));

        mockMvc.perform(get("/api/v1/fx-rate")
                        .param("sourceCurrency", "PHP")
                        .param("targetCurrency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency").value("PHP"))
                .andExpect(jsonPath("$.targetCurrency").value("USD"))
                .andExpect(jsonPath("$.exchangeRate").value(0.01709402))
                .andExpect(jsonPath("$.isCrossCurrency").value(true));
    }
}
