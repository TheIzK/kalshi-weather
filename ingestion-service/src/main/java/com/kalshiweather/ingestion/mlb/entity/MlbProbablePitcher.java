package com.kalshiweather.ingestion.mlb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "mlb_probable_pitchers", uniqueConstraints = @UniqueConstraint(columnNames = {"game_pk", "team_id"}))
public class MlbProbablePitcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "game_pk", nullable = false)
    private Long gamePk;

    @Column(name = "team_id", nullable = false)
    private Short teamId;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(name = "player_name", nullable = false, length = 128)
    private String playerName;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGamePk() {
        return gamePk;
    }

    public void setGamePk(Long gamePk) {
        this.gamePk = gamePk;
    }

    public Short getTeamId() {
        return teamId;
    }

    public void setTeamId(Short teamId) {
        this.teamId = teamId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
