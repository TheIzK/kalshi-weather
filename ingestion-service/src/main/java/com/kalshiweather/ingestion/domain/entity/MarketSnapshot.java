package com.kalshiweather.ingestion.domain.entity;

import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A record of every weather market evaluation, whether or not it produced a {@link Signal} —
 * unlike {@code Market}, which is overwritten every ingestion cycle, this is append-only, so
 * it's the only place a point-in-time price/probability snapshot survives for markets that
 * never cleared the live thresholds. Weather-only; MLB has its own equivalent audit trail
 * ({@code MlbWinProbabilitySnapshot}).
 */
@Entity
@Table(name = "market_snapshots")
public class MarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "market_id", length = 64, nullable = false)
    private String marketId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "yes_bid", precision = 6, scale = 4, nullable = false)
    private BigDecimal yesBid;

    @Column(name = "yes_ask", precision = 6, scale = 4, nullable = false)
    private BigDecimal yesAsk;

    @Column(name = "no_bid", precision = 6, scale = 4, nullable = false)
    private BigDecimal noBid;

    @Column(name = "no_ask", precision = 6, scale = 4, nullable = false)
    private BigDecimal noAsk;

    @Column(name = "open_interest", precision = 14, scale = 4)
    private BigDecimal openInterest;

    @Column(name = "fillable", nullable = false)
    private boolean fillable;

    @Column(name = "model_probability", precision = 6, scale = 5, nullable = false)
    private BigDecimal modelProbability;

    @Column(name = "market_implied_probability", precision = 6, scale = 5, nullable = false)
    private BigDecimal marketImpliedProbability;

    @Column(name = "edge_percent", precision = 6, scale = 3, nullable = false)
    private BigDecimal edgePercent;

    @Column(name = "net_edge_percent", precision = 6, scale = 3, nullable = false)
    private BigDecimal netEdgePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private SignalDirection direction;

    @Column(name = "forecast_id", nullable = false)
    private UUID forecastId;

    /** Set iff this observation also cleared every gate and produced a {@link Signal}. */
    @Column(name = "resulted_in_signal_id")
    private UUID resultedInSignalId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMarketId() {
        return marketId;
    }

    public void setMarketId(String marketId) {
        this.marketId = marketId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public BigDecimal getYesBid() {
        return yesBid;
    }

    public void setYesBid(BigDecimal yesBid) {
        this.yesBid = yesBid;
    }

    public BigDecimal getYesAsk() {
        return yesAsk;
    }

    public void setYesAsk(BigDecimal yesAsk) {
        this.yesAsk = yesAsk;
    }

    public BigDecimal getNoBid() {
        return noBid;
    }

    public void setNoBid(BigDecimal noBid) {
        this.noBid = noBid;
    }

    public BigDecimal getNoAsk() {
        return noAsk;
    }

    public void setNoAsk(BigDecimal noAsk) {
        this.noAsk = noAsk;
    }

    public BigDecimal getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(BigDecimal openInterest) {
        this.openInterest = openInterest;
    }

    public boolean isFillable() {
        return fillable;
    }

    public void setFillable(boolean fillable) {
        this.fillable = fillable;
    }

    public BigDecimal getModelProbability() {
        return modelProbability;
    }

    public void setModelProbability(BigDecimal modelProbability) {
        this.modelProbability = modelProbability;
    }

    public BigDecimal getMarketImpliedProbability() {
        return marketImpliedProbability;
    }

    public void setMarketImpliedProbability(BigDecimal marketImpliedProbability) {
        this.marketImpliedProbability = marketImpliedProbability;
    }

    public BigDecimal getEdgePercent() {
        return edgePercent;
    }

    public void setEdgePercent(BigDecimal edgePercent) {
        this.edgePercent = edgePercent;
    }

    public BigDecimal getNetEdgePercent() {
        return netEdgePercent;
    }

    public void setNetEdgePercent(BigDecimal netEdgePercent) {
        this.netEdgePercent = netEdgePercent;
    }

    public SignalDirection getDirection() {
        return direction;
    }

    public void setDirection(SignalDirection direction) {
        this.direction = direction;
    }

    public UUID getForecastId() {
        return forecastId;
    }

    public void setForecastId(UUID forecastId) {
        this.forecastId = forecastId;
    }

    public UUID getResultedInSignalId() {
        return resultedInSignalId;
    }

    public void setResultedInSignalId(UUID resultedInSignalId) {
        this.resultedInSignalId = resultedInSignalId;
    }
}
