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

    /** When true, skip LESS-strike markets where the model favors BUY_YES (betting on an
     * unusually cold day) — see SignalEligibility.isExcludedByLessBuyYes's doc comment for why.
     * Null/false means no exclusion; doesn't affect BUY_NO on LESS, GREATER either direction,
     * or non-weather sources (no strikeType). */
    @Column(name = "exclude_less_buy_yes")
    private Boolean excludeLessBuyYes;

    /** When true, skip GREATER-strike markets where the model favors BUY_YES (betting on an
     * unusually hot day) — the mirror-image problem to excludeLessBuyYes, see
     * SignalEligibility.isExcludedByGreaterBuyYes's doc comment for why. Null/false means no
     * exclusion; doesn't affect BUY_NO on GREATER, LESS either direction, or non-weather
     * sources (no strikeType). */
    @Column(name = "exclude_greater_buy_yes")
    private Boolean excludeGreaterBuyYes;

    /** When true, a trade must fall in a (domain, strikeType, direction) bucket with a
     * proven calibration track record (BucketCalibration.modelBrier < marketBrier, with
     * enough sample size) to be taken at all — see
     * SignalEligibility.hasProvenCalibration's doc comment for why this exists alongside the
     * strike-type/confidence guardrails above rather than replacing them. Null/false means no
     * such requirement (today's edge/confidence thresholds are the only gate, as before). */
    @Column(name = "require_proven_calibration")
    private Boolean requireProvenCalibration;

    /** Minimum closed, ground-truthed trades a bucket needs before its calibration is trusted.
     * Null falls back to SignalEligibility's built-in default. Only meaningful when
     * requireProvenCalibration is true. */
    @Column(name = "min_calibration_sample_size")
    private Integer minCalibrationSampleSize;

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

    public Boolean getExcludeLessBuyYes() {
        return excludeLessBuyYes;
    }

    public void setExcludeLessBuyYes(Boolean excludeLessBuyYes) {
        this.excludeLessBuyYes = excludeLessBuyYes;
    }

    public Boolean getExcludeGreaterBuyYes() {
        return excludeGreaterBuyYes;
    }

    public void setExcludeGreaterBuyYes(Boolean excludeGreaterBuyYes) {
        this.excludeGreaterBuyYes = excludeGreaterBuyYes;
    }

    public Boolean getRequireProvenCalibration() {
        return requireProvenCalibration;
    }

    public void setRequireProvenCalibration(Boolean requireProvenCalibration) {
        this.requireProvenCalibration = requireProvenCalibration;
    }

    public Integer getMinCalibrationSampleSize() {
        return minCalibrationSampleSize;
    }

    public void setMinCalibrationSampleSize(Integer minCalibrationSampleSize) {
        this.minCalibrationSampleSize = minCalibrationSampleSize;
    }
}
