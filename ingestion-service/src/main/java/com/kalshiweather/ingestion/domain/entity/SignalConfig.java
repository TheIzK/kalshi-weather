package com.kalshiweather.ingestion.domain.entity;

import com.kalshiweather.ingestion.domain.enums.ThresholdMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "signal_configs")
public class SignalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_mode", length = 20, nullable = false)
    private ThresholdMode thresholdMode;

    @Column(name = "flat_threshold_percent", precision = 6, scale = 3)
    private BigDecimal flatThresholdPercent;

    @Column(name = "min_net_edge_after_fees", precision = 6, scale = 3)
    private BigDecimal minNetEdgeAfterFees;

    @Column(name = "min_z_score", precision = 5, scale = 3)
    private BigDecimal minZScore;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ThresholdMode getThresholdMode() {
        return thresholdMode;
    }

    public void setThresholdMode(ThresholdMode thresholdMode) {
        this.thresholdMode = thresholdMode;
    }

    public BigDecimal getFlatThresholdPercent() {
        return flatThresholdPercent;
    }

    public void setFlatThresholdPercent(BigDecimal flatThresholdPercent) {
        this.flatThresholdPercent = flatThresholdPercent;
    }

    public BigDecimal getMinNetEdgeAfterFees() {
        return minNetEdgeAfterFees;
    }

    public void setMinNetEdgeAfterFees(BigDecimal minNetEdgeAfterFees) {
        this.minNetEdgeAfterFees = minNetEdgeAfterFees;
    }

    public BigDecimal getMinZScore() {
        return minZScore;
    }

    public void setMinZScore(BigDecimal minZScore) {
        this.minZScore = minZScore;
    }
}
