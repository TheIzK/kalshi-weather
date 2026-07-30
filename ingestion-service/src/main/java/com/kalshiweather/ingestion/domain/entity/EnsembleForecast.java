package com.kalshiweather.ingestion.domain.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ensemble_forecasts")
public class EnsembleForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_key", length = 32, nullable = false)
    private String subjectKey;

    @Column(name = "source", length = 64, nullable = false)
    private String source;

    @Column(name = "model_family", length = 64)
    private String modelFamily;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "valid_for", nullable = false)
    private LocalDate validFor;

    @Column(name = "predicted_value_f", precision = 6, scale = 2, nullable = false)
    private BigDecimal predictedValueF;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "member_values_f", columnDefinition = "numeric[]")
    private BigDecimal[] memberValuesF;

    @Column(name = "precip_probability_percent", precision = 6, scale = 3)
    private BigDecimal precipProbabilityPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private String rawPayload;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSubjectKey() {
        return subjectKey;
    }

    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getModelFamily() {
        return modelFamily;
    }

    public void setModelFamily(String modelFamily) {
        this.modelFamily = modelFamily;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public LocalDate getValidFor() {
        return validFor;
    }

    public void setValidFor(LocalDate validFor) {
        this.validFor = validFor;
    }

    public BigDecimal getPredictedValueF() {
        return predictedValueF;
    }

    public void setPredictedValueF(BigDecimal predictedValueF) {
        this.predictedValueF = predictedValueF;
    }

    public BigDecimal[] getMemberValuesF() {
        return memberValuesF;
    }

    public void setMemberValuesF(BigDecimal[] memberValuesF) {
        this.memberValuesF = memberValuesF;
    }

    public BigDecimal getPrecipProbabilityPercent() {
        return precipProbabilityPercent;
    }

    public void setPrecipProbabilityPercent(BigDecimal precipProbabilityPercent) {
        this.precipProbabilityPercent = precipProbabilityPercent;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
