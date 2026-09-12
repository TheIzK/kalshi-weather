package com.kalshiweather.ingestion.backtest;

import com.kalshiweather.ingestion.backtest.BacktestResponse.WindowStats;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.domain.enums.ThresholdMode;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {

    private static final Instant WINDOW_START = Instant.parse("2026-08-15T00:00:00Z");
    private static final Instant SPLIT_AT = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-09-01T00:00:00Z");

    @Mock
    private SignalRepository signalRepository;
    @Mock
    private PaperTradeRepository paperTradeRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private EnsembleForecastRepository ensembleForecastRepository;

    private BacktestService service;

    @BeforeEach
    void setUp() {
        service = new BacktestService(signalRepository, paperTradeRepository, marketRepository, ensembleForecastRepository);
        lenient().when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of());
    }

    private BacktestRequest feeAdjustedRequest(BigDecimal minNetEdge, BigDecimal minConfidence, Boolean excludeBetween) {
        return new BacktestRequest(
                ThresholdMode.FEE_ADJUSTED, null, minNetEdge, null, minConfidence, excludeBetween,
                WINDOW_START, SPLIT_AT, WINDOW_END);
    }

    private Signal signal(
            UUID id, String marketId, Instant computedAt, SignalDirection direction,
            BigDecimal modelProbability, BigDecimal edgePercent, BigDecimal netEdgePercent
    ) {
        Signal s = new Signal();
        s.setId(id);
        s.setMarketId(marketId);
        s.setComputedAt(computedAt);
        s.setDirection(direction);
        s.setModelProbability(modelProbability);
        s.setEdgePercent(edgePercent);
        s.setNetEdgePercent(netEdgePercent);
        return s;
    }

    private Market market(String id, StrikeType strikeType) {
        Market m = new Market();
        m.setId(id);
        m.setStrikeType(strikeType);
        return m;
    }

    private PaperTrade trade(UUID signalId, BigDecimal entryPrice, TradeStatus status, BigDecimal pnl) {
        PaperTrade t = new PaperTrade();
        t.setSignalId(signalId);
        t.setEntryPrice(entryPrice);
        t.setStatus(status);
        t.setPnl(pnl);
        return t;
    }

    @Test
    void retainsAndExcludesByReason_inSingleWindow() {
        Instant at = WINDOW_START.plusSeconds(10);
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();

        // A: clears every gate -> retained (edge=7.5, entryPrice=0.60 -> recomputed net = 7.5 - 7*0.6*0.4 = 5.82 >= 3.0)
        Signal a = signal(idA, "MKT-A", at, SignalDirection.BUY_YES, new BigDecimal("0.65"),
                new BigDecimal("7.500"), new BigDecimal("999.000")); // stored net edge deliberately wrong, must be ignored
        // B: BETWEEN market -> excluded by strike type before trade lookup
        Signal b = signal(idB, "MKT-B", at, SignalDirection.BUY_YES, new BigDecimal("0.65"),
                new BigDecimal("7.500"), new BigDecimal("5.820"));
        // C: confidence 30% < 50% floor -> excluded by confidence floor before trade lookup
        Signal c = signal(idC, "MKT-C", at, SignalDirection.BUY_YES, new BigDecimal("0.30"),
                new BigDecimal("7.500"), new BigDecimal("5.820"));
        // D: confidence ok, but recomputed net edge (2.0 - 7*0.5*0.5=0.25) misses the 3.0 threshold
        Signal d = signal(idD, "MKT-D", at, SignalDirection.BUY_YES, new BigDecimal("0.65"),
                new BigDecimal("2.000"), new BigDecimal("999.000"));

        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(a, b, c, d));
        when(marketRepository.findAllById(any())).thenReturn(List.of(
                market("MKT-A", StrikeType.GREATER),
                market("MKT-B", StrikeType.BETWEEN),
                market("MKT-C", StrikeType.GREATER),
                market("MKT-D", StrikeType.GREATER)));
        when(paperTradeRepository.findBySignalIdIn(any())).thenReturn(List.of(
                trade(idA, new BigDecimal("0.60"), TradeStatus.CLOSED, new BigDecimal("0.42")),
                trade(idD, new BigDecimal("0.50"), TradeStatus.CLOSED, new BigDecimal("-0.10"))));

        BacktestResponse response = service.run(feeAdjustedRequest(new BigDecimal("3.000"), new BigDecimal("50.00"), true));

        WindowStats train = response.train();
        assertThat(train.historicalSignalCount()).isEqualTo(4);
        assertThat(train.retainedCount()).isEqualTo(1);
        assertThat(train.excludedByStrikeType()).isEqualTo(1);
        assertThat(train.excludedByConfidenceFloor()).isEqualTo(1);
        assertThat(train.excludedByThreshold()).isEqualTo(1);
        assertThat(train.excludedMissingTrade()).isZero();
        assertThat(train.settledCount()).isEqualTo(1);
        assertThat(train.totalPnl()).isEqualByComparingTo("0.4200");
    }

    @Test
    void feeRecomputation_usesEntryPriceNotStaleStoredNetEdge() {
        // stored netEdgePercent (10.000) would clear a 6.0 threshold, but the true recomputed
        // value from edgePercent=7.5 and entryPrice=0.60 is 5.82, which misses it. Proves the
        // stale stored value (as pre-commit-1883388 data would have) is never trusted.
        UUID id = UUID.randomUUID();
        Instant at = WINDOW_START.plusSeconds(10);
        Signal signal = signal(id, "MKT-A", at, SignalDirection.BUY_YES, new BigDecimal("0.65"),
                new BigDecimal("7.500"), new BigDecimal("10.000"));

        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(signal));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER)));
        when(paperTradeRepository.findBySignalIdIn(any())).thenReturn(List.of(
                trade(id, new BigDecimal("0.60"), TradeStatus.CLOSED, new BigDecimal("0.42"))));

        BacktestResponse response = service.run(feeAdjustedRequest(new BigDecimal("6.000"), null, null));

        assertThat(response.train().retainedCount()).isZero();
        assertThat(response.train().excludedByThreshold()).isEqualTo(1);
    }

    @Test
    void trainTestBoundary_signalExactlyAtSplitLandsInTest() {
        Signal atSplit = signal(UUID.randomUUID(), "MKT-A", SPLIT_AT, SignalDirection.BUY_YES,
                new BigDecimal("0.65"), new BigDecimal("7.500"), new BigDecimal("5.820"));

        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(atSplit));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER)));
        when(paperTradeRepository.findBySignalIdIn(any())).thenReturn(List.of());

        BacktestResponse response = service.run(feeAdjustedRequest(new BigDecimal("3.000"), null, null));

        assertThat(response.train().historicalSignalCount()).isZero();
        assertThat(response.test().historicalSignalCount()).isEqualTo(1);
    }

    @Test
    void aggregation_openTradesCountedButExcludedFromPnlMath() {
        Instant at = WINDOW_START.plusSeconds(10);
        UUID idWin = UUID.randomUUID();
        UUID idLose = UUID.randomUUID();
        UUID idOpen = UUID.randomUUID();

        List<Signal> signals = List.of(
                signal(idWin, "MKT-A", at, SignalDirection.BUY_YES, new BigDecimal("0.65"), new BigDecimal("7.500"), new BigDecimal("5.820")),
                signal(idLose, "MKT-A", at, SignalDirection.BUY_YES, new BigDecimal("0.65"), new BigDecimal("7.500"), new BigDecimal("5.820")),
                signal(idOpen, "MKT-A", at, SignalDirection.BUY_YES, new BigDecimal("0.65"), new BigDecimal("7.500"), new BigDecimal("5.820")));

        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(signals);
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER)));
        when(paperTradeRepository.findBySignalIdIn(any())).thenReturn(List.of(
                trade(idWin, new BigDecimal("0.60"), TradeStatus.CLOSED, new BigDecimal("1.00")),
                trade(idLose, new BigDecimal("0.60"), TradeStatus.CLOSED, new BigDecimal("-0.50")),
                trade(idOpen, new BigDecimal("0.60"), TradeStatus.OPEN, null)));

        BacktestResponse response = service.run(feeAdjustedRequest(new BigDecimal("3.000"), null, null));

        WindowStats train = response.train();
        assertThat(train.retainedCount()).isEqualTo(3);
        assertThat(train.settledCount()).isEqualTo(2);
        assertThat(train.openCount()).isEqualTo(1);
        assertThat(train.winRate()).isEqualByComparingTo("50.0");
        assertThat(train.totalPnl()).isEqualByComparingTo("0.5000");
        assertThat(train.avgPnl()).isEqualByComparingTo("0.2500");
    }

    @Test
    void zeroSettledWindow_yieldsNullStatsNotDivideByZero() {
        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of());

        BacktestResponse response = service.run(feeAdjustedRequest(new BigDecimal("3.000"), null, null));

        assertThat(response.train().settledCount()).isZero();
        assertThat(response.train().winRate()).isNull();
        assertThat(response.train().totalPnl()).isNull();
        assertThat(response.train().avgPnl()).isNull();
    }

    @Test
    void missingTrade_excludedDefensivelyWithoutThrowing() {
        Signal signal = signal(UUID.randomUUID(), "MKT-A", WINDOW_START.plusSeconds(10), SignalDirection.BUY_YES,
                new BigDecimal("0.65"), new BigDecimal("7.500"), new BigDecimal("5.820"));

        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(signal));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER)));
        when(paperTradeRepository.findBySignalIdIn(any())).thenReturn(List.of()); // no matching trade

        BacktestResponse response = service.run(feeAdjustedRequest(new BigDecimal("3.000"), null, null));

        assertThat(response.train().excludedMissingTrade()).isEqualTo(1);
        assertThat(response.train().retainedCount()).isZero();
    }

    @Test
    void validation_missingModeRequiredFieldThrowsBeforeAnyRepositoryCall() {
        BacktestRequest request = new BacktestRequest(
                ThresholdMode.FEE_ADJUSTED, null, null, null, null, null, WINDOW_START, SPLIT_AT, WINDOW_END);

        assertThatThrownBy(() -> service.run(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(signalRepository, paperTradeRepository, marketRepository, ensembleForecastRepository);
    }

    @Test
    void validation_badDateOrderingThrowsBeforeAnyRepositoryCall() {
        BacktestRequest request = new BacktestRequest(
                ThresholdMode.FEE_ADJUSTED, null, new BigDecimal("3.000"), null, null, null,
                SPLIT_AT, WINDOW_START, WINDOW_END); // windowStart after splitAt

        assertThatThrownBy(() -> service.run(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(signalRepository, paperTradeRepository, marketRepository, ensembleForecastRepository);
    }

    @Test
    void ensembleMemberCountFeedsConfidenceAdjustedThreshold() {
        UUID forecastId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
        Signal signal = signal(signalId, "MKT-A", WINDOW_START.plusSeconds(10), SignalDirection.BUY_YES,
                new BigDecimal("0.65"), new BigDecimal("7.500"), new BigDecimal("5.820"));
        signal.setForecastId(forecastId);

        EnsembleForecast forecast = new EnsembleForecast();
        forecast.setId(forecastId);
        forecast.setMemberValuesF(new BigDecimal[100]);

        when(signalRepository.findByComputedAtBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(signal));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("MKT-A", StrikeType.GREATER)));
        when(paperTradeRepository.findBySignalIdIn(any())).thenReturn(List.of(
                trade(signalId, new BigDecimal("0.60"), TradeStatus.CLOSED, new BigDecimal("0.42"))));
        when(ensembleForecastRepository.findAllById(any())).thenReturn(List.of(forecast));

        // edge=7.5%=0.075, p=0.65, n=100 -> z=1.57, clears a 1.0 threshold
        BacktestRequest request = new BacktestRequest(
                ThresholdMode.CONFIDENCE_ADJUSTED, null, null, new BigDecimal("1.0"), null, null,
                WINDOW_START, SPLIT_AT, WINDOW_END);

        BacktestResponse response = service.run(request);

        assertThat(response.train().retainedCount()).isEqualTo(1);
    }
}
