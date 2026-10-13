package com.kalshiweather.ingestion.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ground-truth actual daily-high temperature for one subject/day, pulled from Kalshi's own
 * settled-market history (see {@code source}) rather than a separate weather-observations
 * integration — every settled Kalshi weather market already carries the literal observed
 * high as {@code expiration_value}.
 */
@Entity
@Table(name = "actual_observations")
public class ActualObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_key", length = 32, nullable = false)
    private String subjectKey;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(name = "actual_high_f", precision = 6, scale = 2, nullable = false)
    private BigDecimal actualHighF;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

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

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }

    public BigDecimal getActualHighF() {
        return actualHighF;
    }

    public void setActualHighF(BigDecimal actualHighF) {
        this.actualHighF = actualHighF;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
