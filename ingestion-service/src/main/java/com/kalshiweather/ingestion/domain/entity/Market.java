package com.kalshiweather.ingestion.domain.entity;

import com.kalshiweather.ingestion.domain.enums.MarketResult;
import com.kalshiweather.ingestion.domain.enums.MarketStatus;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "markets")
public class Market {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "event_ticker", length = 64, nullable = false)
    private String eventTicker;

    @Column(name = "series_ticker", length = 32, nullable = false)
    private String seriesTicker;

    @Enumerated(EnumType.STRING)
    @Column(name = "strike_type", length = 20, nullable = false)
    private StrikeType strikeType;

    @Column(name = "floor_strike", precision = 6, scale = 2)
    private BigDecimal floorStrike;

    @Column(name = "cap_strike", precision = 6, scale = 2)
    private BigDecimal capStrike;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MarketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 10)
    private MarketResult result;

    @Column(name = "yes_bid", precision = 6, scale = 4, nullable = false)
    private BigDecimal yesBid;

    @Column(name = "yes_ask", precision = 6, scale = 4, nullable = false)
    private BigDecimal yesAsk;

    @Column(name = "no_bid", precision = 6, scale = 4, nullable = false)
    private BigDecimal noBid;

    @Column(name = "no_ask", precision = 6, scale = 4, nullable = false)
    private BigDecimal noAsk;

    @Column(name = "liquidity_dollars", precision = 14, scale = 4, nullable = false)
    private BigDecimal liquidityDollars;

    /** Kalshi's volume_24h_fp — unlike liquidityDollars, this is actually populated. */
    @Column(name = "volume_24h", precision = 14, scale = 4, nullable = false)
    private BigDecimal volume24h;

    /** Kalshi's open_interest_fp — used as the real fillability signal for signal generation. */
    @Column(name = "open_interest", precision = 14, scale = 4, nullable = false)
    private BigDecimal openInterest;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventTicker() {
        return eventTicker;
    }

    public void setEventTicker(String eventTicker) {
        this.eventTicker = eventTicker;
    }

    public String getSeriesTicker() {
        return seriesTicker;
    }

    public void setSeriesTicker(String seriesTicker) {
        this.seriesTicker = seriesTicker;
    }

    public StrikeType getStrikeType() {
        return strikeType;
    }

    public void setStrikeType(StrikeType strikeType) {
        this.strikeType = strikeType;
    }

    public BigDecimal getFloorStrike() {
        return floorStrike;
    }

    public void setFloorStrike(BigDecimal floorStrike) {
        this.floorStrike = floorStrike;
    }

    public BigDecimal getCapStrike() {
        return capStrike;
    }

    public void setCapStrike(BigDecimal capStrike) {
        this.capStrike = capStrike;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }

    public MarketStatus getStatus() {
        return status;
    }

    public void setStatus(MarketStatus status) {
        this.status = status;
    }

    public MarketResult getResult() {
        return result;
    }

    public void setResult(MarketResult result) {
        this.result = result;
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

    public BigDecimal getLiquidityDollars() {
        return liquidityDollars;
    }

    public void setLiquidityDollars(BigDecimal liquidityDollars) {
        this.liquidityDollars = liquidityDollars;
    }

    public BigDecimal getVolume24h() {
        return volume24h;
    }

    public void setVolume24h(BigDecimal volume24h) {
        this.volume24h = volume24h;
    }

    public BigDecimal getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(BigDecimal openInterest) {
        this.openInterest = openInterest;
    }
}
