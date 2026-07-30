package com.kalshiweather.ingestion.domain.entity;

import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
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
@Table(name = "paper_trades")
public class PaperTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "signal_id", nullable = false)
    private UUID signalId;

    @Column(name = "market_id", length = 64, nullable = false)
    private String marketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 20, nullable = false)
    private SignalDirection side;

    @Column(name = "entry_price", precision = 6, scale = 4, nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "exit_price", precision = 6, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "pnl", precision = 14, scale = 4)
    private BigDecimal pnl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TradeStatus status;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSignalId() {
        return signalId;
    }

    public void setSignalId(UUID signalId) {
        this.signalId = signalId;
    }

    public String getMarketId() {
        return marketId;
    }

    public void setMarketId(String marketId) {
        this.marketId = marketId;
    }

    public SignalDirection getSide() {
        return side;
    }

    public void setSide(SignalDirection side) {
        this.side = side;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public TradeStatus getStatus() {
        return status;
    }

    public void setStatus(TradeStatus status) {
        this.status = status;
    }
}
