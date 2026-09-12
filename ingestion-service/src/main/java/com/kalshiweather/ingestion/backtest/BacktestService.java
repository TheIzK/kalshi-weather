package com.kalshiweather.ingestion.backtest;

import com.kalshiweather.ingestion.backtest.BacktestResponse.WindowStats;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import com.kalshiweather.ingestion.signal.SignalEligibility;
import com.kalshiweather.ingestion.signal.SignalGenerationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replays the historical {@link Signal}/{@link PaperTrade} population under a candidate
 * {@link SignalConfig}, split into a train/calibration window and a later, untouched test
 * window — per the design doc's mandate to validate a threshold empirically via walk-forward
 * validation rather than curve-fit it to noise. v1 is deliberately single-candidate,
 * human-in-the-loop: a person proposes one config, this reports both windows' stats
 * side-by-side. Not an automated grid-search optimizer — with only weeks of history, an
 * optimizer would just curve-fit faster.
 *
 * This can only ever *filter* signals that were already persisted — see
 * {@link BacktestResponse.WindowStats}'s doc comment for why a looser candidate can't recover
 * trades that were never signaled live.
 */
@Service
public class BacktestService {

    private static final int PNL_SCALE = 4;

    private final SignalRepository signalRepository;
    private final PaperTradeRepository paperTradeRepository;
    private final MarketRepository marketRepository;
    private final EnsembleForecastRepository ensembleForecastRepository;

    public BacktestService(
            SignalRepository signalRepository,
            PaperTradeRepository paperTradeRepository,
            MarketRepository marketRepository,
            EnsembleForecastRepository ensembleForecastRepository
    ) {
        this.signalRepository = signalRepository;
        this.paperTradeRepository = paperTradeRepository;
        this.marketRepository = marketRepository;
        this.ensembleForecastRepository = ensembleForecastRepository;
    }

    public BacktestResponse run(BacktestRequest request) {
        validate(request);
        SignalConfig candidate = toCandidateConfig(request);

        List<Signal> signals = signalRepository.findByComputedAtBetween(request.windowStart(), request.windowEnd());

        Map<String, Market> marketsById = marketRepository
                .findAllById(signals.stream().map(Signal::getMarketId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Market::getId, Function.identity()));

        Map<UUID, Integer> memberCountByForecastId = ensembleForecastRepository
                .findAllById(signals.stream().map(Signal::getForecastId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                        EnsembleForecast::getId,
                        f -> f.getMemberValuesF() != null ? f.getMemberValuesF().length : 0));

        Map<UUID, PaperTrade> tradesBySignalId = paperTradeRepository
                .findBySignalIdIn(signals.stream().map(Signal::getId).toList())
                .stream()
                .collect(Collectors.toMap(PaperTrade::getSignalId, Function.identity()));

        List<Signal> train = new ArrayList<>();
        List<Signal> test = new ArrayList<>();
        for (Signal signal : signals) {
            (signal.getComputedAt().isBefore(request.splitAt()) ? train : test).add(signal);
        }

        WindowStats trainStats = replay(train, candidate, marketsById, memberCountByForecastId, tradesBySignalId,
                request.windowStart(), request.splitAt());
        WindowStats testStats = replay(test, candidate, marketsById, memberCountByForecastId, tradesBySignalId,
                request.splitAt(), request.windowEnd());

        return new BacktestResponse(request, trainStats, testStats);
    }

    private WindowStats replay(
            List<Signal> signals, SignalConfig candidate, Map<String, Market> marketsById,
            Map<UUID, Integer> memberCountByForecastId, Map<UUID, PaperTrade> tradesBySignalId,
            Instant windowStart, Instant windowEnd
    ) {
        int excludedByStrikeType = 0;
        int excludedByConfidenceFloor = 0;
        int excludedByThreshold = 0;
        int excludedMissingTrade = 0;
        List<PaperTrade> retainedTrades = new ArrayList<>();

        for (Signal signal : signals) {
            Market market = marketsById.get(signal.getMarketId());
            if (SignalEligibility.isExcludedByStrikeType(candidate, market != null ? market.getStrikeType() : null)) {
                excludedByStrikeType++;
                continue;
            }
            if (!SignalEligibility.meetsConfidenceFloor(candidate, signal.getDirection(), signal.getModelProbability())) {
                excludedByConfidenceFloor++;
                continue;
            }
            PaperTrade trade = tradesBySignalId.get(signal.getId());
            if (trade == null) {
                excludedMissingTrade++;
                continue;
            }
            BigDecimal recomputedNetEdgePercent = SignalGenerationService.computeNetEdgePercent(
                    signal.getEdgePercent(), trade.getEntryPrice());
            int ensembleMemberCount = signal.getForecastId() != null
                    ? memberCountByForecastId.getOrDefault(signal.getForecastId(), 0)
                    : 0;
            if (!SignalEligibility.clearsThreshold(
                    candidate, signal.getEdgePercent(), recomputedNetEdgePercent, signal.getModelProbability(), ensembleMemberCount)) {
                excludedByThreshold++;
                continue;
            }
            retainedTrades.add(trade);
        }

        List<PaperTrade> settled = retainedTrades.stream().filter(t -> t.getStatus() == TradeStatus.CLOSED).toList();
        int openCount = retainedTrades.size() - settled.size();

        BigDecimal winRate = null;
        BigDecimal totalPnl = null;
        BigDecimal avgPnl = null;
        if (!settled.isEmpty()) {
            long wins = settled.stream().filter(t -> t.getPnl().signum() > 0).count();
            winRate = BigDecimal.valueOf(wins)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(settled.size()), 1, RoundingMode.HALF_UP);
            totalPnl = settled.stream().map(PaperTrade::getPnl).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(PNL_SCALE, RoundingMode.HALF_UP);
            avgPnl = totalPnl.divide(BigDecimal.valueOf(settled.size()), PNL_SCALE, RoundingMode.HALF_UP);
        }

        return new WindowStats(
                windowStart, windowEnd, signals.size(), retainedTrades.size(),
                excludedByStrikeType, excludedByConfidenceFloor, excludedByThreshold, excludedMissingTrade,
                settled.size(), openCount, winRate, totalPnl, avgPnl);
    }

    private void validate(BacktestRequest request) {
        if (request.thresholdMode() == null) {
            throw new IllegalArgumentException("thresholdMode is required");
        }
        switch (request.thresholdMode()) {
            case FLAT_PERCENT -> requireField(request.flatThresholdPercent(), "flatThresholdPercent");
            case FEE_ADJUSTED -> requireField(request.minNetEdgeAfterFees(), "minNetEdgeAfterFees");
            case CONFIDENCE_ADJUSTED -> requireField(request.minZScore(), "minZScore");
        }
        if (request.windowStart() == null || request.splitAt() == null || request.windowEnd() == null) {
            throw new IllegalArgumentException("windowStart, splitAt, and windowEnd are all required");
        }
        if (!request.windowStart().isBefore(request.splitAt()) || !request.splitAt().isBefore(request.windowEnd())) {
            throw new IllegalArgumentException("require windowStart < splitAt < windowEnd");
        }
    }

    private void requireField(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required for this thresholdMode");
        }
    }

    private SignalConfig toCandidateConfig(BacktestRequest request) {
        SignalConfig config = new SignalConfig();
        config.setThresholdMode(request.thresholdMode());
        config.setFlatThresholdPercent(request.flatThresholdPercent());
        config.setMinNetEdgeAfterFees(request.minNetEdgeAfterFees());
        config.setMinZScore(request.minZScore());
        config.setMinModelConfidencePercent(request.minModelConfidencePercent());
        config.setExcludeBetweenStrikeType(request.excludeBetweenStrikeType());
        return config;
    }
}
