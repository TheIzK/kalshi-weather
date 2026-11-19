package com.kalshiweather.ingestion.calibration;

import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.ActualObservationRepository;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
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
class GroundTruthResolverTest {

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

    private GroundTruthResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GroundTruthResolver(
                paperTradeRepository, signalRepository, marketRepository, ensembleForecastRepository, actualObservationRepository);
        lenient().when(signalRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(marketRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of());
    }

    private PaperTrade trade(UUID signalId, String marketId, SignalDirection side, BigDecimal exitPrice) {
        PaperTrade trade = new PaperTrade();
        trade.setSignalId(signalId);
        trade.setMarketId(marketId);
        trade.setSide(side);
        trade.setExitPrice(exitPrice);
        trade.setClosedAt(Instant.parse("2026-10-06T18:00:00Z"));
        trade.setPnl(BigDecimal.ZERO);
        return trade;
    }

    private Signal weatherSignal(UUID id, UUID forecastId) {
        Signal signal = new Signal();
        signal.setId(id);
        signal.setForecastId(forecastId);
        signal.setModelProbability(new BigDecimal("0.70"));
        signal.setMarketImpliedProbability(new BigDecimal("0.55"));
        return signal;
    }

    private Signal sportSignal(UUID id, String sourceType) {
        Signal signal = new Signal();
        signal.setId(id);
        signal.setSourceType(sourceType);
        signal.setModelProbability(new BigDecimal("0.60"));
        signal.setMarketImpliedProbability(new BigDecimal("0.55"));
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

    @Test
    void resolvesWeatherGroundTruthViaActualObservationsAndStrike() {
        UUID signalId = UUID.randomUUID();
        UUID forecastId = UUID.randomUUID();
        LocalDate validFor = LocalDate.of(2026, 10, 6);

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-A", SignalDirection.BUY_YES, BigDecimal.ONE)));
        when(signalRepository.findAllById(any())).thenReturn(List.of(weatherSignal(signalId, forecastId)));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER, new BigDecimal("80"), null)));
        when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of(forecast(forecastId, "NYC", validFor)));
        when(actualObservationRepository.findBySubjectKeyAndSource("NYC", ActualObservationService.SOURCE_KALSHI_SETTLEMENT))
                .thenReturn(List.of(observation("NYC", validFor, new BigDecimal("85.00")))); // > 80 -> resolves YES

        List<ResolvedTrade> resolved = resolver.resolveAll();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).domain()).isEqualTo("WEATHER");
        assertThat(resolved.get(0).strikeType()).isEqualTo(StrikeType.GREATER);
        assertThat(resolved.get(0).resolvedYes()).isTrue();
    }

    @Test
    void weatherResolvedYesNullWhenNoMatchingObservationExists() {
        UUID signalId = UUID.randomUUID();
        UUID forecastId = UUID.randomUUID();
        LocalDate validFor = LocalDate.of(2026, 10, 6);

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(signalId, "MKT-A", SignalDirection.BUY_YES, BigDecimal.ONE)));
        when(signalRepository.findAllById(any())).thenReturn(List.of(weatherSignal(signalId, forecastId)));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER, new BigDecimal("80"), null)));
        when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of(forecast(forecastId, "NYC", validFor)));
        when(actualObservationRepository.findBySubjectKeyAndSource("NYC", ActualObservationService.SOURCE_KALSHI_SETTLEMENT))
                .thenReturn(List.of()); // not backfilled yet

        List<ResolvedTrade> resolved = resolver.resolveAll();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).resolvedYes()).isNull();
    }

    /** The important regression case: resolvedYes must be derived from the trade's own side +
     * exitPrice, not market.result — confirmed via real data (2026-11-19) that relying on
     * market.result would've been equivalent here, but exitPrice is the one guaranteed not to
     * depend on the markets table staying fresh after settlement. */
    @Test
    void resolvesSportGroundTruthFromSideAndExitPriceNotMarketResult() {
        UUID buyYesWin = UUID.randomUUID();
        UUID buyNoWin = UUID.randomUUID();
        UUID buyYesLoss = UUID.randomUUID();
        UUID buyNoLoss = UUID.randomUUID();

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(buyYesWin, "MKT-1", SignalDirection.BUY_YES, BigDecimal.ONE),   // won buying YES -> market resolved YES
                trade(buyNoWin, "MKT-2", SignalDirection.BUY_NO, BigDecimal.ONE),     // won buying NO -> market resolved NO
                trade(buyYesLoss, "MKT-3", SignalDirection.BUY_YES, BigDecimal.ZERO), // lost buying YES -> market resolved NO
                trade(buyNoLoss, "MKT-4", SignalDirection.BUY_NO, BigDecimal.ZERO)    // lost buying NO -> market resolved YES
        ));
        when(signalRepository.findAllById(any())).thenReturn(List.of(
                sportSignal(buyYesWin, "MLB_WIN_PROBABILITY"),
                sportSignal(buyNoWin, "MLB_WIN_PROBABILITY"),
                sportSignal(buyYesLoss, "MLB_WIN_PROBABILITY"),
                sportSignal(buyNoLoss, "MLB_WIN_PROBABILITY")));
        // deliberately no markets.findAllById stub beyond the default empty list -- ground
        // truth for sports must not depend on the Market row being present at all

        List<ResolvedTrade> resolved = resolver.resolveAll();

        assertThat(resolved).hasSize(4);
        assertThat(findByMarketId(resolved, "MKT-1").resolvedYes()).isTrue();
        assertThat(findByMarketId(resolved, "MKT-2").resolvedYes()).isFalse();
        assertThat(findByMarketId(resolved, "MKT-3").resolvedYes()).isFalse();
        assertThat(findByMarketId(resolved, "MKT-4").resolvedYes()).isTrue();
    }

    @Test
    void sportResolvedYesNullWhenExitPriceMissing() {
        UUID signalId = UUID.randomUUID();
        PaperTrade trade = trade(signalId, "MKT-A", SignalDirection.BUY_YES, null);

        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(trade));
        when(signalRepository.findAllById(any())).thenReturn(List.of(sportSignal(signalId, "MLB_WIN_PROBABILITY")));

        List<ResolvedTrade> resolved = resolver.resolveAll();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).resolvedYes()).isNull();
        assertThat(resolved.get(0).domain()).isEqualTo("MLB");
    }

    @Test
    void skipsTradeWhenSignalIsMissing() {
        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(
                trade(UUID.randomUUID(), "MKT-A", SignalDirection.BUY_YES, BigDecimal.ONE)));
        // signalRepository.findAllById defaults to empty list

        List<ResolvedTrade> resolved = resolver.resolveAll();

        assertThat(resolved).isEmpty();
    }

    private ResolvedTrade findByMarketId(List<ResolvedTrade> resolved, String marketId) {
        return resolved.stream().filter(r -> r.trade().getMarketId().equals(marketId)).findFirst().orElseThrow();
    }
}
