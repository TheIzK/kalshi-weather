package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.domain.enums.ThresholdMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SignalEligibilityTest {

    private SignalConfig config(ThresholdMode mode, BigDecimal flat, BigDecimal netEdge, BigDecimal zScore) {
        return config(mode, flat, netEdge, zScore, null, null);
    }

    private SignalConfig config(
            ThresholdMode mode, BigDecimal flat, BigDecimal netEdge, BigDecimal zScore,
            BigDecimal minModelConfidencePercent, Boolean excludeBetweenStrikeType
    ) {
        SignalConfig config = new SignalConfig();
        config.setId(UUID.randomUUID());
        config.setThresholdMode(mode);
        config.setFlatThresholdPercent(flat);
        config.setMinNetEdgeAfterFees(netEdge);
        config.setMinZScore(zScore);
        config.setMinModelConfidencePercent(minModelConfidencePercent);
        config.setExcludeBetweenStrikeType(excludeBetweenStrikeType);
        return config;
    }

    @Test
    void isExcludedByStrikeType_excludesBetweenWhenFlagTrue() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null, null, true);
        assertThat(SignalEligibility.isExcludedByStrikeType(config, StrikeType.BETWEEN)).isTrue();
    }

    @Test
    void isExcludedByStrikeType_doesNotExcludeWhenFlagFalseOrNull() {
        SignalConfig falseConfig = config(ThresholdMode.FLAT_PERCENT, null, null, null, null, false);
        SignalConfig nullConfig = config(ThresholdMode.FLAT_PERCENT, null, null, null, null, null);
        assertThat(SignalEligibility.isExcludedByStrikeType(falseConfig, StrikeType.BETWEEN)).isFalse();
        assertThat(SignalEligibility.isExcludedByStrikeType(nullConfig, StrikeType.BETWEEN)).isFalse();
    }

    @Test
    void isExcludedByStrikeType_neverExcludesGreaterOrLessRegardlessOfFlag() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null, null, true);
        assertThat(SignalEligibility.isExcludedByStrikeType(config, StrikeType.GREATER)).isFalse();
        assertThat(SignalEligibility.isExcludedByStrikeType(config, StrikeType.LESS)).isFalse();
        assertThat(SignalEligibility.isExcludedByStrikeType(config, null)).isFalse();
    }

    @Test
    void meetsConfidenceFloor_passesWhenFloorNotConfigured() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null, null, null);
        assertThat(SignalEligibility.meetsConfidenceFloor(config, SignalDirection.BUY_YES, new BigDecimal("0.01"))).isTrue();
    }

    @Test
    void meetsConfidenceFloor_buyYesUsesModelProbabilityDirectly() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null, new BigDecimal("50.00"), null);
        assertThat(SignalEligibility.meetsConfidenceFloor(config, SignalDirection.BUY_YES, new BigDecimal("0.49"))).isFalse();
        assertThat(SignalEligibility.meetsConfidenceFloor(config, SignalDirection.BUY_YES, new BigDecimal("0.51"))).isTrue();
    }

    @Test
    void meetsConfidenceFloor_buyNoUsesOneMinusModelProbability() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null, new BigDecimal("50.00"), null);
        // model=0.60 -> confidence in NO is 0.40 -> below a 50% floor
        assertThat(SignalEligibility.meetsConfidenceFloor(config, SignalDirection.BUY_NO, new BigDecimal("0.60"))).isFalse();
        // model=0.40 -> confidence in NO is 0.60 -> clears a 50% floor
        assertThat(SignalEligibility.meetsConfidenceFloor(config, SignalDirection.BUY_NO, new BigDecimal("0.40"))).isTrue();
    }

    @Test
    void meetsConfidenceFloor_boundaryEqualToFloorPasses() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null, new BigDecimal("50.00"), null);
        assertThat(SignalEligibility.meetsConfidenceFloor(config, SignalDirection.BUY_YES, new BigDecimal("0.50"))).isTrue();
    }

    @Test
    void clearsThreshold_flatPercentUsesRawEdge() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, new BigDecimal("5.000"), null, null);
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("5.000"), new BigDecimal("1.000"), new BigDecimal("0.65"), 0)).isTrue();
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("4.999"), new BigDecimal("10.000"), new BigDecimal("0.65"), 0)).isFalse();
    }

    @Test
    void clearsThreshold_flatPercentMissingThresholdNeverClears() {
        SignalConfig config = config(ThresholdMode.FLAT_PERCENT, null, null, null);
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("50.000"), new BigDecimal("50.000"), new BigDecimal("0.65"), 0)).isFalse();
    }

    @Test
    void clearsThreshold_feeAdjustedUsesNetEdge() {
        SignalConfig config = config(ThresholdMode.FEE_ADJUSTED, null, new BigDecimal("3.000"), null);
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("50.000"), new BigDecimal("3.000"), new BigDecimal("0.65"), 0)).isTrue();
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("50.000"), new BigDecimal("2.999"), new BigDecimal("0.65"), 0)).isFalse();
    }

    @Test
    void clearsThreshold_feeAdjustedMissingThresholdNeverClears() {
        SignalConfig config = config(ThresholdMode.FEE_ADJUSTED, null, null, null);
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("50.000"), new BigDecimal("50.000"), new BigDecimal("0.65"), 0)).isFalse();
    }

    @Test
    void clearsThreshold_confidenceAdjustedUsesZScoreOfEnsembleSize() {
        // edge=7.5%=0.075, p=0.65, n=100 -> SE=sqrt(0.65*0.35/100)=0.0477 -> z=0.075/0.0477=1.57
        SignalConfig config = config(ThresholdMode.CONFIDENCE_ADJUSTED, null, null, new BigDecimal("1.0"));
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("7.500"), new BigDecimal("5.000"), new BigDecimal("0.65"), 100)).isTrue();
        // same edge/p but far fewer members -> larger standard error -> smaller z-score, misses the same threshold
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("7.500"), new BigDecimal("5.000"), new BigDecimal("0.65"), 10)).isFalse();
    }

    @Test
    void clearsThreshold_confidenceAdjustedNeverClearsWithZeroEnsembleMembers() {
        SignalConfig config = config(ThresholdMode.CONFIDENCE_ADJUSTED, null, null, new BigDecimal("0.1"));
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("7.500"), new BigDecimal("5.000"), new BigDecimal("0.65"), 0)).isFalse();
    }

    @Test
    void clearsThreshold_confidenceAdjustedMissingThresholdNeverClears() {
        SignalConfig config = config(ThresholdMode.CONFIDENCE_ADJUSTED, null, null, null);
        assertThat(SignalEligibility.clearsThreshold(config, new BigDecimal("50.000"), new BigDecimal("50.000"), new BigDecimal("0.65"), 100)).isFalse();
    }
}
