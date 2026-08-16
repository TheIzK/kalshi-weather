package com.kalshiweather.ingestion.mlb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "mlb_pitcher_season_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "season", "as_of_date"}))
public class MlbPitcherSeasonStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(nullable = false)
    private Integer season;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    /** True decimal innings (statsapi's .1/.2 "outs" notation already converted) — see
     * FipCalculator.parseInningsPitched. */
    @Column(name = "innings_pitched", nullable = false, precision = 6, scale = 1)
    private BigDecimal inningsPitched;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal era;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal whip;

    @Column(nullable = false)
    private Integer strikeouts;

    @Column(nullable = false)
    private Integer walks;

    @Column(name = "hit_batsmen", nullable = false)
    private Integer hitBatsmen;

    @Column(name = "home_runs_allowed", nullable = false)
    private Integer homeRunsAllowed;

    /** Computed by us via FipCalculator — statsapi doesn't return FIP directly. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal fip;

    @Column(name = "games_started", nullable = false)
    private Integer gamesStarted;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public BigDecimal getInningsPitched() {
        return inningsPitched;
    }

    public void setInningsPitched(BigDecimal inningsPitched) {
        this.inningsPitched = inningsPitched;
    }

    public BigDecimal getEra() {
        return era;
    }

    public void setEra(BigDecimal era) {
        this.era = era;
    }

    public BigDecimal getWhip() {
        return whip;
    }

    public void setWhip(BigDecimal whip) {
        this.whip = whip;
    }

    public Integer getStrikeouts() {
        return strikeouts;
    }

    public void setStrikeouts(Integer strikeouts) {
        this.strikeouts = strikeouts;
    }

    public Integer getWalks() {
        return walks;
    }

    public void setWalks(Integer walks) {
        this.walks = walks;
    }

    public Integer getHitBatsmen() {
        return hitBatsmen;
    }

    public void setHitBatsmen(Integer hitBatsmen) {
        this.hitBatsmen = hitBatsmen;
    }

    public Integer getHomeRunsAllowed() {
        return homeRunsAllowed;
    }

    public void setHomeRunsAllowed(Integer homeRunsAllowed) {
        this.homeRunsAllowed = homeRunsAllowed;
    }

    public BigDecimal getFip() {
        return fip;
    }

    public void setFip(BigDecimal fip) {
        this.fip = fip;
    }

    public Integer getGamesStarted() {
        return gamesStarted;
    }

    public void setGamesStarted(Integer gamesStarted) {
        this.gamesStarted = gamesStarted;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
