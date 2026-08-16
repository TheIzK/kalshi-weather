package com.kalshiweather.ingestion.domain.entity;

import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.SignalStatus;
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

@Entity
@Table(name = "signals")
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "market_id", length = 64, nullable = false)
    private String marketId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

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

    @Column(name = "forecast_id")
    private UUID forecastId;

    /** Generic provenance for non-weather signals (MLB, future NFL/CFB). Weather signals
     * leave these null and use forecastId instead. */
    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "source_reference_id", length = 64)
    private String sourceReferenceId;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SignalStatus status;

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

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceReferenceId() {
        return sourceReferenceId;
    }

    public void setSourceReferenceId(String sourceReferenceId) {
        this.sourceReferenceId = sourceReferenceId;
    }

    public UUID getConfigId() {
        return configId;
    }

    public void setConfigId(UUID configId) {
        this.configId = configId;
    }

    public SignalStatus getStatus() {
        return status;
    }

    public void setStatus(SignalStatus status) {
        this.status = status;
    }
}
