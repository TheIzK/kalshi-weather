package com.kalshiweather.ingestion.report;

import com.kalshiweather.ingestion.calibration.GroundTruthResolver;
import com.kalshiweather.ingestion.calibration.ResolvedTrade;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.DomainBucket;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.PeriodBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceTrendServiceTest {

    @Mock
    private GroundTruthResolver groundTruthResolver;

    private PerformanceTrendService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceTrendService(groundTruthResolver);
    }

    private PaperTrade trade(Instant closedAt, BigDecimal pnl) {
        PaperTrade trade = new PaperTrade();
        trade.setClosedAt(closedAt);
        trade.setPnl(pnl);
        return trade;
    }

    private Signal signal(BigDecimal modelProbability, BigDecimal marketImpliedProbability) {
        Signal signal = new Signal();
        signal.setModelProbability(modelProbability);
        signal.setMarketImpliedProbability(marketImpliedProbability);
        return signal;
    }

    private DomainBucket onlyBucket(PerformanceTrendResponse response) {
        assertThat(response.periods()).hasSize(1);
        PeriodBucket period = response.periods().get(0);
        assertThat(period.domains()).hasSize(1);
        return period.domains().get(0);
    }

    @Test
    void aggregatesDollarAndBrierStatsForOneDomainBucket() {
        // resolvedYes=true -> modelBrier=(0.70-1)^2=0.09, marketBrier=(0.55-1)^2=0.2025
        Instant closedAt = Instant.parse("2026-10-06T18:00:00Z"); // Tuesday
        ResolvedTrade resolved = new ResolvedTrade(
                trade(closedAt, new BigDecimal("0.42")), signal(new BigDecimal("0.70"), new BigDecimal("0.55")),
                null, "WEATHER", StrikeType.GREATER, true);
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(resolved));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        DomainBucket bucket = onlyBucket(response);
        assertThat(bucket.domain()).isEqualTo("WEATHER");
        assertThat(bucket.n()).isEqualTo(1);
        assertThat(bucket.winRate()).isEqualByComparingTo("1.0000");
        assertThat(bucket.totalPnl()).isEqualByComparingTo("0.4200");
        assertThat(bucket.avgPnl()).isEqualByComparingTo("0.4200");
        assertThat(bucket.brierSampleSize()).isEqualTo(1);
        assertThat(bucket.modelBrier()).isEqualByComparingTo("0.0900");
        assertThat(bucket.marketBrier()).isEqualByComparingTo("0.2025");
        assertThat(bucket.edgeScore()).isEqualByComparingTo("0.1125");
    }

    @Test
    void weekGranularityBucketsToMondayStartOfIsoWeek() {
        // Wednesday 2026-10-07 -> week start should be Monday 2026-10-05
        Instant closedAt = Instant.parse("2026-10-07T12:00:00Z");
        ResolvedTrade resolved = new ResolvedTrade(
                trade(closedAt, new BigDecimal("0.10")), signal(new BigDecimal("0.60"), new BigDecimal("0.55")),
                null, "MLB", null, null);
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(resolved));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        assertThat(response.periods()).hasSize(1);
        assertThat(response.periods().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void monthGranularityBucketsToFirstOfMonth() {
        Instant closedAt = Instant.parse("2026-10-23T12:00:00Z");
        ResolvedTrade resolved = new ResolvedTrade(
                trade(closedAt, new BigDecimal("0.10")), signal(new BigDecimal("0.60"), new BigDecimal("0.55")),
                null, "MLB", null, null);
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(resolved));

        PerformanceTrendResponse response = service.trend(TrendGranularity.MONTH);

        assertThat(response.periods()).hasSize(1);
        assertThat(response.periods().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void dollarStatsStillComputedWhenGroundTruthUnavailable() {
        Instant closedAt = Instant.parse("2026-10-06T18:00:00Z");
        ResolvedTrade resolved = new ResolvedTrade(
                trade(closedAt, new BigDecimal("0.30")), signal(new BigDecimal("0.60"), new BigDecimal("0.55")),
                null, "MLB", null, null); // resolvedYes unknown
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(resolved));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        DomainBucket bucket = onlyBucket(response);
        assertThat(bucket.n()).isEqualTo(1);
        assertThat(bucket.totalPnl()).isEqualByComparingTo("0.3000");
        assertThat(bucket.brierSampleSize()).isZero();
        assertThat(bucket.modelBrier()).isNull();
        assertThat(bucket.marketBrier()).isNull();
        assertThat(bucket.edgeScore()).isNull();
    }

    @Test
    void skipsTradeWithNullClosedAt() {
        ResolvedTrade resolved = new ResolvedTrade(
                trade(null, new BigDecimal("0.30")), signal(new BigDecimal("0.60"), new BigDecimal("0.55")),
                null, "MLB", null, null);
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(resolved));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        assertThat(response.periods()).isEmpty();
    }

    @Test
    void separatesDomainsWithinTheSamePeriod() {
        Instant closedAt = Instant.parse("2026-10-06T18:00:00Z"); // same week for both
        ResolvedTrade weather = new ResolvedTrade(
                trade(closedAt, new BigDecimal("0.10")), signal(new BigDecimal("0.60"), new BigDecimal("0.55")),
                null, "WEATHER", StrikeType.GREATER, null);
        ResolvedTrade mlb = new ResolvedTrade(
                trade(closedAt, new BigDecimal("0.20")), signal(new BigDecimal("0.60"), new BigDecimal("0.55")),
                null, "MLB", null, null);
        when(groundTruthResolver.resolveAll()).thenReturn(List.of(weather, mlb));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        assertThat(response.periods()).hasSize(1);
        List<String> domains = response.periods().get(0).domains().stream().map(DomainBucket::domain).toList();
        assertThat(domains).containsExactlyInAnyOrder("WEATHER", "MLB");
    }
}
