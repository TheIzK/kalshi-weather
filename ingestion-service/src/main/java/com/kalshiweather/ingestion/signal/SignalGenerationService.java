package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.MarketSnapshot;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.SignalStatus;
import com.kalshiweather.ingestion.repository.MarketSnapshotRepository;
import com.kalshiweather.ingestion.repository.SignalConfigRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Turns a model probability into a persisted {@link Signal}, if the edge clears the
 * configured threshold. Market-implied probability is the mid of yes bid/ask (a real
 * fillable price), never {@code last_price}, per the design doc: several real markets had
 * last_price far from a fillable quote on thin books.
 *
 * Fillability is gated on real activity (open interest) and spread width, not Kalshi's
 * {@code liquidity_dollars} field — verified against live data on 2026-08-11 that field
 * reads exactly 0.0000 on every KXHIGHNY market regardless of actual trading activity
 * (markets with $11k+ 24h volume and 1-cent spreads still report it as zero).
 *
 * Domain-agnostic by design: the core evaluation only needs a model probability, not an
 * {@link EnsembleForecast} — the weather-specific overload below computes one via
 * {@link SignalProvider} and delegates, so other sources (MLB, future NFL/CFB) can call
 * the generic overload directly with their own model's probability.
 */
@Service
public class SignalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);

    /** Kalshi taker fee: round_up(0.07 * C * P * (1-P)). edgePercent is expected profit per
     * $1 of notional (modelProbability - marketImpliedProbability, as a percent), so the fee
     * must be expressed in that same unit — 7% * P * (1-P) per contract — not as a percent of
     * stake (7% * (1-P)), which is a different denominator and would net against edgePercent
     * incorrectly. Independent of contract count C either way. */
    private static final BigDecimal FEE_RATE_PERCENT = BigDecimal.valueOf(7);

    /** A starting heuristic, not empirically tuned: wider than this and a quote isn't trusted. */
    private static final BigDecimal MAX_TRUSTED_SPREAD = new BigDecimal("0.10");

    private static final int PROBABILITY_SCALE = 5; // matches NUMERIC(6,5)
    private static final int PERCENT_SCALE = 3;      // matches NUMERIC(6,3)

    /** A market already tracked by one of these doesn't get a duplicate signal every cycle. */
    private static final List<SignalStatus> OPEN_SIGNAL_STATUSES = List.of(SignalStatus.ACTIVE, SignalStatus.ACTED_ON);

    private final SignalProvider signalProvider;
    private final SignalConfigRepository signalConfigRepository;
    private final SignalRepository signalRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;

    public SignalGenerationService(
            SignalProvider signalProvider,
            SignalConfigRepository signalConfigRepository,
            SignalRepository signalRepository,
            MarketSnapshotRepository marketSnapshotRepository
    ) {
        this.signalProvider = signalProvider;
        this.signalConfigRepository = signalConfigRepository;
        this.signalRepository = signalRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
    }

    /**
     * Weather entry point. Unlike the generic overload below, this always records a
     * {@link MarketSnapshot} once dedup/config pass — fillability and the BETWEEN exclusion
     * are recorded facts here, not silent skips, so markets we look at but don't trade leave
     * a permanent trace instead of being overwritten by the next ingestion cycle. Only dedup
     * and a missing config remain hard gates before the model is ever computed. Mirrors the
     * MLB precedent ({@code MlbWinProbabilityServiceImpl.computeAndPersist} runs unconditionally
     * before any market-match or Signal check) — weather never had the equivalent.
     */
    public Optional<Signal> evaluate(Market market, EnsembleForecast forecast) {
        if (signalRepository.existsByMarketIdAndStatusIn(market.getId(), OPEN_SIGNAL_STATUSES)) {
            log.debug("Skipping {}: already has an active or acted-on signal", market.getId());
            return Optional.empty();
        }

        Optional<SignalConfig> configOpt = signalConfigRepository.findAll().stream().findFirst();
        if (configOpt.isEmpty()) {
            log.warn("No SignalConfig found — cannot evaluate {}", market.getId());
            return Optional.empty();
        }
        SignalConfig config = configOpt.get();

        BigDecimal modelProbability = signalProvider.computeProbability(market, forecast);
        int ensembleMemberCount = forecast.getMemberValuesF() != null ? forecast.getMemberValuesF().length : 0;

        Optional<EdgeMath> edgeMathOpt = computeEdgeMath(market, modelProbability);
        if (edgeMathOpt.isEmpty()) {
            return Optional.empty(); // model and market agree exactly — no edge either direction, nothing to record
        }
        EdgeMath edgeMath = edgeMathOpt.get();
        boolean fillable = hasFillableQuote(market);

        MarketSnapshot snapshot = newSnapshot(market, forecast, modelProbability, edgeMath, fillable);

        Optional<Signal> result = Optional.empty();
        if (fillable
                && !SignalEligibility.isExcludedByStrikeType(config, market.getStrikeType())
                && SignalEligibility.meetsConfidenceFloor(config, edgeMath.direction(), modelProbability)
                && SignalEligibility.clearsThreshold(config, edgeMath.edgePercent(), edgeMath.netEdgePercent(), modelProbability, ensembleMemberCount)) {
            Signal signal = newSignal(market, modelProbability, edgeMath, config);
            signal.setForecastId(forecast.getId());
            Signal saved = signalRepository.save(signal);
            logSignal(saved);
            snapshot.setResultedInSignalId(saved.getId());
            result = Optional.of(saved);
        }

        marketSnapshotRepository.save(snapshot);
        return result;
    }

    /**
     * Generic entry point for any non-weather source (MLB, future NFL/CFB) that already has
     * a model probability computed. {@code sourceType}/{@code sourceReferenceId} are a
     * lightweight, generic provenance pair — e.g. ("MLB_WIN_PROBABILITY", the
     * mlb_win_probability_snapshots id) — instead of a dedicated nullable FK per domain.
     * No ensemble member count applies here, so CONFIDENCE_ADJUSTED threshold mode never
     * qualifies for these sources — that's expected, not a bug: there's no meaningful
     * ensemble-based confidence measure for a non-ensemble model.
     */
    public Optional<Signal> evaluate(Market market, BigDecimal modelProbability, String sourceType, String sourceReferenceId) {
        Optional<SignalConfig> config = preCheck(market);
        if (config.isEmpty()) {
            return Optional.empty();
        }

        return buildSignal(market, modelProbability, 0, config.get()).map(signal -> {
            signal.setSourceType(sourceType);
            signal.setSourceReferenceId(sourceReferenceId);
            Signal saved = signalRepository.save(signal);
            logSignal(saved);
            return saved;
        });
    }

    /** Dedup, fillability, and config-exists — cheap enough to run before touching the model. */
    private Optional<SignalConfig> preCheck(Market market) {
        if (signalRepository.existsByMarketIdAndStatusIn(market.getId(), OPEN_SIGNAL_STATUSES)) {
            log.debug("Skipping {}: already has an active or acted-on signal", market.getId());
            return Optional.empty();
        }

        if (!hasFillableQuote(market)) {
            log.debug("Skipping {}: no open interest or spread too wide, no fillable price to trust",
                    market.getId());
            return Optional.empty();
        }

        Optional<SignalConfig> config = signalConfigRepository.findAll().stream().findFirst();
        if (config.isEmpty()) {
            log.warn("No SignalConfig found — cannot evaluate {}", market.getId());
            return config;
        }

        if (SignalEligibility.isExcludedByStrikeType(config.get(), market.getStrikeType())) {
            log.debug("Skipping {}: BETWEEN strike type excluded by config", market.getId());
            return Optional.empty();
        }

        return config;
    }

    /** Edge/fee math and thresholding once pre-checks have passed. Returns an unsaved Signal. */
    private Optional<Signal> buildSignal(Market market, BigDecimal modelProbability, int ensembleMemberCount, SignalConfig config) {
        Optional<EdgeMath> edgeMathOpt = computeEdgeMath(market, modelProbability);
        if (edgeMathOpt.isEmpty()) {
            return Optional.empty();
        }
        EdgeMath edgeMath = edgeMathOpt.get();

        if (!SignalEligibility.meetsConfidenceFloor(config, edgeMath.direction(), modelProbability)) {
            return Optional.empty();
        }
        if (!SignalEligibility.clearsThreshold(config, edgeMath.edgePercent(), edgeMath.netEdgePercent(), modelProbability, ensembleMemberCount)) {
            return Optional.empty();
        }

        return Optional.of(newSignal(market, modelProbability, edgeMath, config));
    }

    /** Direction/edge/fee math shared by the weather (always-compute) and generic
     * (gate-then-compute) evaluation paths. Empty iff model and market agree exactly — no
     * edge either direction, and no meaningful direction to report. */
    private Optional<EdgeMath> computeEdgeMath(Market market, BigDecimal modelProbability) {
        BigDecimal marketImpliedProbability = midpoint(market.getYesBid(), market.getYesAsk());

        BigDecimal diff = modelProbability.subtract(marketImpliedProbability);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        SignalDirection direction = diff.compareTo(BigDecimal.ZERO) > 0 ? SignalDirection.BUY_YES : SignalDirection.BUY_NO;
        BigDecimal edgePercent = diff.abs().multiply(BigDecimal.valueOf(100)).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

        BigDecimal fillPrice = direction == SignalDirection.BUY_YES ? market.getYesAsk() : market.getNoAsk();
        BigDecimal netEdgePercent = computeNetEdgePercent(edgePercent, fillPrice);

        return Optional.of(new EdgeMath(direction, marketImpliedProbability, edgePercent, netEdgePercent));
    }

    /** Exposed so backtest replay can recompute net edge from a stored {@code edgePercent} and
     * the paper trade's {@code entryPrice} (the exact same fill-price expression used here)
     * under the current fee formula, rather than trusting a possibly stale stored value —
     * see commit 1883388's fee/edge unit-mismatch fix for why the stored value can't always
     * be trusted as-is for signals created before that fix. */
    public static BigDecimal computeNetEdgePercent(BigDecimal edgePercent, BigDecimal fillPrice) {
        BigDecimal feePercent = FEE_RATE_PERCENT.multiply(fillPrice).multiply(BigDecimal.ONE.subtract(fillPrice))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return edgePercent.subtract(feePercent);
    }

    private Signal newSignal(Market market, BigDecimal modelProbability, EdgeMath edgeMath, SignalConfig config) {
        Signal signal = new Signal();
        signal.setMarketId(market.getId());
        signal.setComputedAt(Instant.now());
        signal.setModelProbability(modelProbability.setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
        signal.setMarketImpliedProbability(edgeMath.marketImpliedProbability().setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
        signal.setEdgePercent(edgeMath.edgePercent());
        signal.setNetEdgePercent(edgeMath.netEdgePercent());
        signal.setDirection(edgeMath.direction());
        signal.setConfigId(config.getId());
        signal.setStatus(SignalStatus.ACTIVE);
        return signal;
    }

    private MarketSnapshot newSnapshot(
            Market market, EnsembleForecast forecast, BigDecimal modelProbability, EdgeMath edgeMath, boolean fillable
    ) {
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setMarketId(market.getId());
        snapshot.setObservedAt(Instant.now());
        snapshot.setYesBid(market.getYesBid());
        snapshot.setYesAsk(market.getYesAsk());
        snapshot.setNoBid(market.getNoBid());
        snapshot.setNoAsk(market.getNoAsk());
        snapshot.setOpenInterest(market.getOpenInterest());
        snapshot.setFillable(fillable);
        snapshot.setModelProbability(modelProbability.setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
        snapshot.setMarketImpliedProbability(edgeMath.marketImpliedProbability().setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
        snapshot.setEdgePercent(edgeMath.edgePercent());
        snapshot.setNetEdgePercent(edgeMath.netEdgePercent());
        snapshot.setDirection(edgeMath.direction());
        snapshot.setForecastId(forecast.getId());
        return snapshot;
    }

    private record EdgeMath(
            SignalDirection direction, BigDecimal marketImpliedProbability, BigDecimal edgePercent, BigDecimal netEdgePercent
    ) {
    }

    private void logSignal(Signal signal) {
        log.info("Signal: {} {} edge={}% netEdge={}% (model={}, market={})",
                signal.getDirection(), signal.getMarketId(), signal.getEdgePercent(),
                signal.getNetEdgePercent(), signal.getModelProbability(), signal.getMarketImpliedProbability());
    }

    private BigDecimal midpoint(BigDecimal a, BigDecimal b) {
        return a.add(b).divide(BigDecimal.valueOf(2), PROBABILITY_SCALE, RoundingMode.HALF_UP);
    }

    /** Real open interest (someone actually holds a position) plus a spread tight enough to trust. */
    private boolean hasFillableQuote(Market market) {
        boolean hasOpenInterest = market.getOpenInterest() != null
                && market.getOpenInterest().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal spread = market.getYesAsk().subtract(market.getYesBid());
        return hasOpenInterest && spread.compareTo(MAX_TRUSTED_SPREAD) <= 0;
    }
}
