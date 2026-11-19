package com.kalshiweather.ingestion.calibration;

import com.kalshiweather.ingestion.domain.entity.BucketCalibration;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.repository.BucketCalibrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BucketCalibrationServiceTest {

    @Mock
    private GroundTruthResolver groundTruthResolver;
    @Mock
    private BucketCalibrationRepository bucketCalibrationRepository;

    private BucketCalibrationService service;

    @BeforeEach
    void setUp() {
        service = new BucketCalibrationService(groundTruthResolver, bucketCalibrationRepository);
    }

    private PaperTrade trade(BigDecimal pnl) {
        PaperTrade trade = new PaperTrade();
        trade.setPnl(pnl);
        return trade;
    }

    private Signal signal(SignalDirection direction, BigDecimal modelProbability, BigDecimal marketImpliedProbability) {
        Signal signal = new Signal();
        signal.setDirection(direction);
        signal.setModelProbability(modelProbability);
        signal.setMarketImpliedProbability(marketImpliedProbability);
        return signal;
    }

    private ResolvedTrade resolved(String domain, StrikeType strikeType, SignalDirection direction, BigDecimal modelP, BigDecimal marketP, Boolean resolvedYes) {
        return new ResolvedTrade(trade(BigDecimal.ZERO), signal(direction, modelP, marketP), null, domain, strikeType, resolvedYes);
    }

    @Test
    void computesModelAndMarketBrierPerBucketAndPersists() {
        // two GREATER+BUY_YES trades: resolvedYes=true both times
        // modelBrier = avg((0.70-1)^2, (0.80-1)^2) = avg(0.09, 0.04) = 0.065
        // marketBrier = avg((0.55-1)^2, (0.60-1)^2) = avg(0.2025, 0.16) = 0.18125 -> rounds to 0.1813 (HALF_UP)
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(
                resolved("WEATHER", StrikeType.GREATER, SignalDirection.BUY_YES, new BigDecimal("0.70"), new BigDecimal("0.55"), true),
                resolved("WEATHER", StrikeType.GREATER, SignalDirection.BUY_YES, new BigDecimal("0.80"), new BigDecimal("0.60"), true)));

        service.recompute();

        ArgumentCaptor<List<BucketCalibration>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketCalibrationRepository).saveAll(captor.capture());
        List<BucketCalibration> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        BucketCalibration bucket = saved.get(0);
        assertThat(bucket.getDomain()).isEqualTo("WEATHER");
        assertThat(bucket.getStrikeType()).isEqualTo(StrikeType.GREATER);
        assertThat(bucket.getDirection()).isEqualTo(SignalDirection.BUY_YES);
        assertThat(bucket.getSampleSize()).isEqualTo(2);
        assertThat(bucket.getModelBrier()).isEqualByComparingTo("0.0650");
        assertThat(bucket.getMarketBrier()).isEqualByComparingTo("0.1813");
        assertThat(bucket.getComputedAt()).isNotNull();
    }

    @Test
    void excludesTradesWithoutGroundTruthFromCalibration() {
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(
                resolved("WEATHER", StrikeType.GREATER, SignalDirection.BUY_YES, new BigDecimal("0.70"), new BigDecimal("0.55"), true),
                resolved("WEATHER", StrikeType.GREATER, SignalDirection.BUY_YES, new BigDecimal("0.90"), new BigDecimal("0.90"), null)));

        service.recompute();

        ArgumentCaptor<List<BucketCalibration>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketCalibrationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSampleSize()).isEqualTo(1); // only the one with known ground truth
    }

    @Test
    void separatesBucketsByDomainStrikeTypeAndDirection() {
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(
                resolved("WEATHER", StrikeType.GREATER, SignalDirection.BUY_YES, new BigDecimal("0.70"), new BigDecimal("0.55"), true),
                resolved("WEATHER", StrikeType.GREATER, SignalDirection.BUY_NO, new BigDecimal("0.30"), new BigDecimal("0.45"), false),
                resolved("WEATHER", StrikeType.LESS, SignalDirection.BUY_YES, new BigDecimal("0.70"), new BigDecimal("0.55"), true),
                resolved("MLB", null, SignalDirection.BUY_YES, new BigDecimal("0.70"), new BigDecimal("0.55"), true)));

        service.recompute();

        ArgumentCaptor<List<BucketCalibration>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketCalibrationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(4);
    }

    @Test
    void deletesAllExistingRowsBeforeInsertingFreshOnes() {
        when(groundTruthResolver.resolveAll()).thenReturn(List.of());

        service.recompute();

        verify(bucketCalibrationRepository).deleteAll();
        verify(bucketCalibrationRepository).saveAll(List.of());
    }
}
