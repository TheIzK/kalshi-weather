package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.Signal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SignalRepository extends JpaRepository<Signal, UUID> {
}
