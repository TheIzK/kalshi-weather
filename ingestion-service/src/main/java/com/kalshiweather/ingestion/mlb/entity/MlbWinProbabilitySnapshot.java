package com.kalshiweather.ingestion.mlb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "mlb_win_probability_snapshots")
public class MlbWinProbabilitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "game_pk", nullable = false)
    private Long gamePk;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "home_win_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal homeWinProbability;

    @Column(name = "model_version", nullable = false, length = 24)
    private String modelVersion;

    /** Raw feature values (both win%, both FIP, the delta) so the FIP-to-win-probability
     * scale factor can be recalibrated later against realized outcomes. Same JSONB pattern
     * as EnsembleForecast.rawPayload. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String inputs;

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

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    public BigDecimal getHomeWinProbability() {
        return homeWinProbability;
    }

    public void setHomeWinProbability(BigDecimal homeWinProbability) {
        this.homeWinProbability = homeWinProbability;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getInputs() {
        return inputs;
    }

    public void setInputs(String inputs) {
        this.inputs = inputs;
    }
}
