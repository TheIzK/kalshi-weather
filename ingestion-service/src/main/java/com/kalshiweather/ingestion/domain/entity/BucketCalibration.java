package com.kalshiweather.ingestion.domain.entity;

import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Whether a (domain, strikeType, direction) bucket has, historically, actually beaten the
 * market on calibration — recomputed in full daily by BucketCalibrationService from every
 * closed, ground-truthed trade, and read by the live trading path
 * ({@code SignalEligibility.hasProvenCalibration}) to decide whether a bucket has earned the
 * right to keep trading, rather than trusting a threshold computed only against our own
 * model's self-reported probability.
 */
@Entity
@Table(name = "bucket_calibrations")
public class BucketCalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "domain", length = 32, nullable = false)
    private String domain;

    /** Null for non-weather domains, which have no strike shape. */
    @Enumerated(EnumType.STRING)
    @Column(name = "strike_type", length = 20)
    private StrikeType strikeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private SignalDirection direction;

    /** How many closed, ground-truthed trades this bucket's Brier scores are based on. */
    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "model_brier", precision = 6, scale = 4, nullable = false)
    private BigDecimal modelBrier;

    @Column(name = "market_brier", precision = 6, scale = 4, nullable = false)
    private BigDecimal marketBrier;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public StrikeType getStrikeType() {
        return strikeType;
    }

    public void setStrikeType(StrikeType strikeType) {
        this.strikeType = strikeType;
    }

    public SignalDirection getDirection() {
        return direction;
    }

    public void setDirection(SignalDirection direction) {
        this.direction = direction;
    }

    public Integer getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Integer sampleSize) {
        this.sampleSize = sampleSize;
    }

    public BigDecimal getModelBrier() {
        return modelBrier;
    }

    public void setModelBrier(BigDecimal modelBrier) {
        this.modelBrier = modelBrier;
    }

    public BigDecimal getMarketBrier() {
        return marketBrier;
    }

    public void setMarketBrier(BigDecimal marketBrier) {
        this.marketBrier = marketBrier;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
