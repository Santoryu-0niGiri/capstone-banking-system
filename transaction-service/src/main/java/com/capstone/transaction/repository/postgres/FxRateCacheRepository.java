package com.capstone.transaction.repository.postgres;

import com.capstone.transaction.entity.postgres.FxRateCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FxRateCacheRepository extends JpaRepository<FxRateCache, UUID> {
    Optional<FxRateCache> findByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);
}

