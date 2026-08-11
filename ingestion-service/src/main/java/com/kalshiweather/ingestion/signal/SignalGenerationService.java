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

    /** Evaluates one market against one ensemble forecast and persists a Signal if it qualifies. */
    public Optional<Signal> evaluate(Market market, EnsembleForecast forecast) {
        if (!hasFillableQuote(market)) {
            log.debug("Skipping {}: no open interest or spread too wide, no fillable price to trust",
                    market.getId());
            return Optional.empty();
        }

        Optional<SignalConfig> config = signalConfigRepository.findAll().stream().findFirst();
        if (config.isEmpty()) {
            log.warn("No SignalConfig found — cannot evaluate {}", market.getId());
            return Optional.empty();
        }

        BigDecimal modelProbability = signalProvider.computeProbability(market, forecast);
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

        if (!clearsThreshold(config.get(), edgePercent, netEdgePercent, modelProbability, forecast)) {
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
        signal.setForecastId(forecast.getId());
        signal.setConfigId(config.get().getId());
        signal.setStatus(SignalStatus.ACTIVE);

        Signal saved = signalRepository.save(signal);
        log.info("Signal: {} {} edge={}% netEdge={}% (model={}, market={})",
                direction, market.getId(), edgePercent, netEdgePercent, modelProbability, marketImpliedProbability);
        return Optional.of(saved);
    }

    private boolean clearsThreshold(
            SignalConfig config, BigDecimal edgePercent, BigDecimal netEdgePercent,
            BigDecimal modelProbability, EnsembleForecast forecast
    ) {
        return switch (config.getThresholdMode()) {
            case FLAT_PERCENT -> config.getFlatThresholdPercent() != null
                    && edgePercent.compareTo(config.getFlatThresholdPercent()) >= 0;
            case FEE_ADJUSTED -> config.getMinNetEdgeAfterFees() != null
                    && netEdgePercent.compareTo(config.getMinNetEdgeAfterFees()) >= 0;
            case CONFIDENCE_ADJUSTED -> config.getMinZScore() != null
                    && zScore(edgePercent, modelProbability, forecast).compareTo(config.getMinZScore()) >= 0;
        };
    }

    /**
     * Not specified precisely in the design doc beyond the mode's name — interpreted here as
     * the edge (as a probability) divided by the binomial standard error of the empirical
     * ensemble probability (sqrt(p*(1-p)/n)), i.e. "how many standard errors is the edge
     * away from noise, given how many ensemble members we're estimating from."
     */
    private BigDecimal zScore(BigDecimal edgePercent, BigDecimal modelProbability, EnsembleForecast forecast) {
        int n = forecast.getMemberValuesF() != null ? forecast.getMemberValuesF().length : 0;
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        double p = modelProbability.doubleValue();
        double standardError = Math.sqrt(p * (1 - p) / n);
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
