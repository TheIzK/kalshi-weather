package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.SignalStatus;
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

    /** Kalshi taker fee: round_up(0.07 * C * P * (1-P)). As a percent of stake this cancels
     * to 7% * (1 - P) per contract, independent of contract count C. */
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

    public SignalGenerationService(
            SignalProvider signalProvider,
            SignalConfigRepository signalConfigRepository,
            SignalRepository signalRepository
    ) {
        this.signalProvider = signalProvider;
        this.signalConfigRepository = signalConfigRepository;
        this.signalRepository = signalRepository;
    }

    /**
     * Weather entry point. The dedup/fillable-quote/config pre-checks run first and can
     * short-circuit before the model probability is ever computed — computeProbability
     * isn't free, and several tests pin exactly this ordering (verifyNoInteractions on
     * SignalProvider for markets that should skip before reaching the model at all).
     */
    public Optional<Signal> evaluate(Market market, EnsembleForecast forecast) {
        Optional<SignalConfig> config = preCheck(market);
        if (config.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal modelProbability = signalProvider.computeProbability(market, forecast);
        int ensembleMemberCount = forecast.getMemberValuesF() != null ? forecast.getMemberValuesF().length : 0;

        return buildSignal(market, modelProbability, ensembleMemberCount, config.get()).map(signal -> {
            signal.setForecastId(forecast.getId());
            Signal saved = signalRepository.save(signal);
            logSignal(saved);
            return saved;
        });
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
        }
        return config;
    }

    /** Edge/fee math and thresholding once pre-checks have passed. Returns an unsaved Signal. */
    private Optional<Signal> buildSignal(Market market, BigDecimal modelProbability, int ensembleMemberCount, SignalConfig config) {
        BigDecimal marketImpliedProbability = midpoint(market.getYesBid(), market.getYesAsk());

        BigDecimal diff = modelProbability.subtract(marketImpliedProbability);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty(); // model and market agree exactly — no edge either direction
        }

        SignalDirection direction = diff.compareTo(BigDecimal.ZERO) > 0 ? SignalDirection.BUY_YES : SignalDirection.BUY_NO;
        BigDecimal edgePercent = diff.abs().multiply(BigDecimal.valueOf(100)).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

        BigDecimal fillPrice = direction == SignalDirection.BUY_YES ? market.getYesAsk() : market.getNoAsk();
        BigDecimal feePercent = FEE_RATE_PERCENT.multiply(BigDecimal.ONE.subtract(fillPrice))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        BigDecimal netEdgePercent = edgePercent.subtract(feePercent);

        if (!clearsThreshold(config, edgePercent, netEdgePercent, modelProbability, ensembleMemberCount)) {
            return Optional.empty();
        }

        Signal signal = new Signal();
        signal.setMarketId(market.getId());
        signal.setComputedAt(Instant.now());
        signal.setModelProbability(modelProbability.setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
        signal.setMarketImpliedProbability(marketImpliedProbability.setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
        signal.setEdgePercent(edgePercent);
        signal.setNetEdgePercent(netEdgePercent);
        signal.setDirection(direction);
        signal.setConfigId(config.getId());
        signal.setStatus(SignalStatus.ACTIVE);

        return Optional.of(signal);
    }

    private void logSignal(Signal signal) {
        log.info("Signal: {} {} edge={}% netEdge={}% (model={}, market={})",
                signal.getDirection(), signal.getMarketId(), signal.getEdgePercent(),
                signal.getNetEdgePercent(), signal.getModelProbability(), signal.getMarketImpliedProbability());
    }

    private boolean clearsThreshold(
            SignalConfig config, BigDecimal edgePercent, BigDecimal netEdgePercent,
            BigDecimal modelProbability, int ensembleMemberCount
    ) {
        return switch (config.getThresholdMode()) {
            case FLAT_PERCENT -> config.getFlatThresholdPercent() != null
                    && edgePercent.compareTo(config.getFlatThresholdPercent()) >= 0;
            case FEE_ADJUSTED -> config.getMinNetEdgeAfterFees() != null
                    && netEdgePercent.compareTo(config.getMinNetEdgeAfterFees()) >= 0;
            case CONFIDENCE_ADJUSTED -> config.getMinZScore() != null
                    && zScore(edgePercent, modelProbability, ensembleMemberCount).compareTo(config.getMinZScore()) >= 0;
        };
    }

    /**
     * Not specified precisely in the design doc beyond the mode's name — interpreted here as
     * the edge (as a probability) divided by the binomial standard error of the empirical
     * ensemble probability (sqrt(p*(1-p)/n)), i.e. "how many standard errors is the edge
     * away from noise, given how many ensemble members we're estimating from." Zero for
     * non-ensemble sources (n=0), which correctly never clears a positive threshold.
     */
    private BigDecimal zScore(BigDecimal edgePercent, BigDecimal modelProbability, int ensembleMemberCount) {
        if (ensembleMemberCount == 0) {
            return BigDecimal.ZERO;
        }
        double p = modelProbability.doubleValue();
        double standardError = Math.sqrt(p * (1 - p) / ensembleMemberCount);
        if (standardError == 0) {
            return BigDecimal.valueOf(Double.MAX_VALUE); // a degenerate, unanimous ensemble is maximally confident
        }
        double edgeFraction = edgePercent.doubleValue() / 100.0;
        return BigDecimal.valueOf(edgeFraction / standardError);
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
