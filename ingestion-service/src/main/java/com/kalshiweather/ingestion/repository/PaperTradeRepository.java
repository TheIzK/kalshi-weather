package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaperTradeRepository extends JpaRepository<PaperTrade, UUID> {
    List<PaperTrade> findByStatus(TradeStatus status);
}
