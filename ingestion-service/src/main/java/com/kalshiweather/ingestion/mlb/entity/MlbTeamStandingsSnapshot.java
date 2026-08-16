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
@Table(name = "mlb_team_standings_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "as_of_date"}))
public class MlbTeamStandingsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Short teamId;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(nullable = false)
    private Integer wins;

    @Column(nullable = false)
    private Integer losses;

    /** Split (not overall) win% — feeds Log5 directly, baking in home field advantage
     * without a separate constant. */
    @Column(name = "home_win_pct", nullable = false, precision = 5, scale = 3)
    private BigDecimal homeWinPct;

    @Column(name = "away_win_pct", nullable = false, precision = 5, scale = 3)
    private BigDecimal awayWinPct;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Short getTeamId() {
        return teamId;
    }

    public void setTeamId(Short teamId) {
        this.teamId = teamId;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public BigDecimal getHomeWinPct() {
        return homeWinPct;
    }

    public void setHomeWinPct(BigDecimal homeWinPct) {
        this.homeWinPct = homeWinPct;
    }

    public BigDecimal getAwayWinPct() {
        return awayWinPct;
    }

    public void setAwayWinPct(BigDecimal awayWinPct) {
        this.awayWinPct = awayWinPct;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
