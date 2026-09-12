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

    /** Optional floor on the model's stated probability for the side actually being traded
     * (as a percent, e.g. 50.00). Independent of thresholdMode — applies to all three modes as
     * an additional gate. Null means no floor. See buildSignal's guardrail comment for why. */
    @Column(name = "min_model_confidence_percent", precision = 5, scale = 2)
    private BigDecimal minModelConfidencePercent;

    /** When true, skip BETWEEN-strike weather markets (narrow fixed-width bins) entirely —
     * see preCheck's guardrail comment for why. Null/false means no exclusion; doesn't affect
     * GREATER/LESS weather markets or non-weather (e.g. MLB) sources, which have no strikeType. */
    @Column(name = "exclude_between_strike_type")
    private Boolean excludeBetweenStrikeType;

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

    public BigDecimal getMinModelConfidencePercent() {
        return minModelConfidencePercent;
    }

    public void setMinModelConfidencePercent(BigDecimal minModelConfidencePercent) {
        this.minModelConfidencePercent = minModelConfidencePercent;
    }

    public Boolean getExcludeBetweenStrikeType() {
        return excludeBetweenStrikeType;
    }

    public void setExcludeBetweenStrikeType(Boolean excludeBetweenStrikeType) {
        this.excludeBetweenStrikeType = excludeBetweenStrikeType;
    }
}
