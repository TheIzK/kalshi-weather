package com.kalshiweather.ingestion.mlb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "mlb_games")
public class MlbGame {

    @Id
    @Column(name = "game_pk", nullable = false)
    private Long gamePk;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "game_datetime_utc", nullable = false)
    private Instant gameDatetimeUtc;

    @Column(name = "home_team_id", nullable = false)
    private Short homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    private Short awayTeamId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "game_type", nullable = false, length = 1)
    private String gameType;

    @Column(name = "kalshi_event_ticker", length = 64)
    private String kalshiEventTicker;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getGamePk() {
        return gamePk;
    }

    public void setGamePk(Long gamePk) {
        this.gamePk = gamePk;
    }

    public LocalDate getGameDate() {
        return gameDate;
    }

    public void setGameDate(LocalDate gameDate) {
        this.gameDate = gameDate;
    }

    public Instant getGameDatetimeUtc() {
        return gameDatetimeUtc;
    }

    public void setGameDatetimeUtc(Instant gameDatetimeUtc) {
        this.gameDatetimeUtc = gameDatetimeUtc;
    }

    public Short getHomeTeamId() {
        return homeTeamId;
    }

    public void setHomeTeamId(Short homeTeamId) {
        this.homeTeamId = homeTeamId;
    }

    public Short getAwayTeamId() {
        return awayTeamId;
    }

    public void setAwayTeamId(Short awayTeamId) {
        this.awayTeamId = awayTeamId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getKalshiEventTicker() {
        return kalshiEventTicker;
    }

    public void setKalshiEventTicker(String kalshiEventTicker) {
        this.kalshiEventTicker = kalshiEventTicker;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
