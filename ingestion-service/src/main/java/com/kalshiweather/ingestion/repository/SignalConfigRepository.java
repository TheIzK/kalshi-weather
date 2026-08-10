package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SignalConfigRepository extends JpaRepository<SignalConfig, UUID> {
}
