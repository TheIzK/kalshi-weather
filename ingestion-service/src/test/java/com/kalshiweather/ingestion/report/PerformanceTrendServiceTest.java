package com.kalshiweather.ingestion.report;

import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.MarketResult;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.ActualObservationRepository;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.DomainBucket;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.PeriodBucket;
import com.kalshiweather.ingestion.service.ActualObservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceTrendServiceTest {

    @Mock
    private PaperTradeRepository paperTradeRepository;
    @Mock
    private SignalRepository signalRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private EnsembleForecastRepository ensembleForecastRepository;
    @Mock
    private ActualObservationRepository actualObservationRepository;

    private PerformanceTrendService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceTrendService(
                paperTradeRepository, signalRepository, marketRepository, ensembleForecastRepository, actualObservationRepository);
        lenient().when(signalRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(marketRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of());
    }

    private PaperTrade trade(UUID signalId, String marketId, Instant closedAt, BigDecimal pnl) {
        PaperTrade trade = new PaperTrade();
        trade.setSignalId(signalId);
        trade.setMarketId(marketId);
        trade.setClosedAt(closedAt);
        trade.setPnl(pnl);
        return trade;
    }

    private Signal weatherSignal(UUID id, UUID forecastId, BigDecimal modelProbability, BigDecimal marketImpliedProbability) {
        Signal signal = new Signal();
        signal.setId(id);
        signal.setForecastId(forecastId);
        signal.setModelProbability(modelProbability);
        signal.setMarketImpliedProbability(marketImpliedProbability);
        return signal;
    }

    private Signal sportSignal(UUID id, String sourceType, BigDecimal modelProbability, BigDecimal marketImpliedProbability) {
        Signal signal = new Signal();
        signal.setId(id);
        signal.setSourceType(sourceType);
        signal.setModelProbability(modelProbability);
        signal.setMarketImpliedProbability(marketImpliedProbability);
        return signal;
    }

    private Market market(String id, StrikeType strikeType, BigDecimal floorStrike, BigDecimal capStrike) {
        Market market = new Market();
        market.setId(id);
        market.setStrikeType(strikeType);
        market.setFloorStrike(floorStrike);
        market.setCapStrike(capStrike);
        return market;
    }

    private Market mlbMarket(String id, MarketResult result) {
        Market market = new Market();
        market.setId(id);
        market.setResult(result);
        return market;
    }

    private EnsembleForecast forecast(UUID id, String subjectKey, LocalDate validFor) {
        EnsembleForecast forecast = new EnsembleForecast();
        forecast.setId(id);
        forecast.setSubjectKey(subjectKey);
        forecast.setValidFor(validFor);
        return forecast;
    }

    private ActualObservation observation(String subjectKey, LocalDate date, BigDecimal actualHighF) {
        ActualObservation observation = new ActualObservation();
        observation.setSubjectKey(subjectKey);
        observation.setOccurrenceDate(date);
        observation.setActualHighF(actualHighF);
        observation.setSource(ActualObservationService.SOURCE_KALSHI_SETTLEMENT);
        return observation;
    }

    private DomainBucket onlyBucket(PerformanceTrendResponse response) {
        assertThat(response.periods()).hasSize(1);
        PeriodBucket period = response.periods().get(0);
        assertThat(period.domains()).hasSize(1);
        return period.domains().get(0);
    }

    @Test
    void aggregatesDollarStatsForWeatherWithGroundTruthViaActualObservations() {
        // GREATER market, floorStrike=80; actual high=85 -> resolves YES
        UUID signalId = UUID.randomUUID();
        UUID forecastId = UUID.randomUUID();
        LocalDate validFor = LocalDate.of(2026, 10, 6); // Tuesday
        Instant closedAt = validFor.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-A", closedAt, new BigDecimal("0.42"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                weatherSignal(signalId, forecastId, new BigDecimal("0.70"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(
                market("MKT-A", StrikeType.GREATER, new BigDecimal("80"), null)));
        when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of(
                forecast(forecastId, "NYC", validFor)));
        when(actualObservationRepository.findBySubjectKeyAndSource("NYC", ActualObservationService.SOURCE_KALSHI_SETTLEMENT))
                .thenReturn(List.of(observation("NYC", validFor, new BigDecimal("85.00"))));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        DomainBucket bucket = onlyBucket(response);
        assertThat(bucket.domain()).isEqualTo("WEATHER");
        assertThat(bucket.n()).isEqualTo(1);
        assertThat(bucket.winRate()).isEqualByComparingTo("1.0000");
        assertThat(bucket.totalPnl()).isEqualByComparingTo("0.4200");
        assertThat(bucket.avgPnl()).isEqualByComparingTo("0.4200");
        assertThat(bucket.brierSampleSize()).isEqualTo(1);
        // resolvedYes=true -> modelBrier=(0.70-1)^2=0.09, marketBrier=(0.55-1)^2=0.2025
        assertThat(bucket.modelBrier()).isEqualByComparingTo("0.0900");
        assertThat(bucket.marketBrier()).isEqualByComparingTo("0.2025");
        assertThat(bucket.edgeScore()).isEqualByComparingTo("0.1125"); // marketBrier - modelBrier > 0 -> model beats market here
    }

    @Test
    void classifiesSportDomainFromSourceTypePrefixAndUsesMarketResultAsGroundTruth() {
        UUID signalId = UUID.randomUUID();
        Instant closedAt = Instant.parse("2026-10-06T18:00:00Z");

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-MLB", closedAt, new BigDecimal("-0.50"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                sportSignal(signalId, "MLB_WIN_PROBABILITY", new BigDecimal("0.60"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(mlbMarket("MKT-MLB", MarketResult.NO)));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        DomainBucket bucket = onlyBucket(response);
        assertThat(bucket.domain()).isEqualTo("MLB");
        assertThat(bucket.brierSampleSize()).isEqualTo(1);
        // resolvedYes=false -> modelBrier=(0.60-0)^2=0.36, marketBrier=(0.55-0)^2=0.3025
        assertThat(bucket.modelBrier()).isEqualByComparingTo("0.3600");
        assertThat(bucket.marketBrier()).isEqualByComparingTo("0.3025");
        assertThat(bucket.edgeScore()).isEqualByComparingTo("-0.0575"); // negative -> market beats model
    }

    @Test
    void weekGranularityBucketsToMondayStartOfIsoWeek() {
        UUID signalId = UUID.randomUUID();
        // Wednesday 2026-10-07 -> week start should be Monday 2026-10-05
        Instant closedAt = Instant.parse("2026-10-07T12:00:00Z");

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-MLB", closedAt, new BigDecimal("0.10"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                sportSignal(signalId, "MLB_WIN_PROBABILITY", new BigDecimal("0.60"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(mlbMarket("MKT-MLB", null)));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        assertThat(response.periods()).hasSize(1);
        assertThat(response.periods().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void monthGranularityBucketsToFirstOfMonth() {
        UUID signalId = UUID.randomUUID();
        Instant closedAt = Instant.parse("2026-10-23T12:00:00Z");

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-MLB", closedAt, new BigDecimal("0.10"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                sportSignal(signalId, "MLB_WIN_PROBABILITY", new BigDecimal("0.60"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(mlbMarket("MKT-MLB", null)));

        PerformanceTrendResponse response = service.trend(TrendGranularity.MONTH);

        assertThat(response.periods()).hasSize(1);
        assertThat(response.periods().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void dollarStatsStillComputedWhenGroundTruthUnavailable() {
        // market.result is null (not yet reconciled at the DB level, even though the trade itself closed)
        UUID signalId = UUID.randomUUID();
        Instant closedAt = Instant.parse("2026-10-06T18:00:00Z");

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-MLB", closedAt, new BigDecimal("0.30"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                sportSignal(signalId, "MLB_WIN_PROBABILITY", new BigDecimal("0.60"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(mlbMarket("MKT-MLB", null)));

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
    void weatherGroundTruthUnavailableWhenNoMatchingActualObservationExists() {
        UUID signalId = UUID.randomUUID();
        UUID forecastId = UUID.randomUUID();
        LocalDate validFor = LocalDate.of(2026, 10, 6);
        Instant closedAt = validFor.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-A", closedAt, new BigDecimal("0.42"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                weatherSignal(signalId, forecastId, new BigDecimal("0.70"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(
                market("MKT-A", StrikeType.GREATER, new BigDecimal("80"), null)));
        when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of(
                forecast(forecastId, "NYC", validFor)));
        when(actualObservationRepository.findBySubjectKeyAndSource("NYC", ActualObservationService.SOURCE_KALSHI_SETTLEMENT))
                .thenReturn(List.of()); // no observation backfilled yet for this date

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        DomainBucket bucket = onlyBucket(response);
        assertThat(bucket.brierSampleSize()).isZero();
        assertThat(bucket.modelBrier()).isNull();
    }

    @Test
    void skipsTradeWhenSignalIsMissing() {
        UUID signalId = UUID.randomUUID();
        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-A", Instant.parse("2026-10-06T18:00:00Z"), new BigDecimal("0.42"))));
        // signalRepository.findAllById returns empty -- defensive setup default already covers this

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        assertThat(response.periods()).isEmpty();
    }

    @Test
    void separatesDomainsWithinTheSamePeriod() {
        UUID weatherSignalId = UUID.randomUUID();
        UUID mlbSignalId = UUID.randomUUID();
        Instant closedAt = Instant.parse("2026-10-06T18:00:00Z"); // same week for both

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(weatherSignalId, "MKT-W", closedAt, new BigDecimal("0.10")),
                trade(mlbSignalId, "MKT-M", closedAt, new BigDecimal("0.20"))));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                weatherSignal(weatherSignalId, UUID.randomUUID(), new BigDecimal("0.60"), new BigDecimal("0.55")),
                sportSignal(mlbSignalId, "MLB_WIN_PROBABILITY", new BigDecimal("0.60"), new BigDecimal("0.55"))));
        when(marketRepository.findAllById(any())).thenReturn(List.of(
                market("MKT-W", StrikeType.GREATER, new BigDecimal("80"), null),
                mlbMarket("MKT-M", null)));

        PerformanceTrendResponse response = service.trend(TrendGranularity.WEEK);

        assertThat(response.periods()).hasSize(1);
        List<String> domains = response.periods().get(0).domains().stream().map(DomainBucket::domain).toList();
        assertThat(domains).containsExactlyInAnyOrder("WEATHER", "MLB");
    }
}
