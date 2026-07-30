package com.kalshiweather.ingestion.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "subject_stations")
public class SubjectStation {

    @Id
    @Column(name = "subject_key", length = 32, nullable = false)
    private String subjectKey;

    @Column(name = "series_ticker", length = 32, nullable = false, unique = true)
    private String seriesTicker;

    @Column(name = "nws_grid_id", length = 10, nullable = false)
    private String nwsGridId;

    @Column(name = "nws_grid_x", nullable = false)
    private Integer nwsGridX;

    @Column(name = "nws_grid_y", nullable = false)
    private Integer nwsGridY;

    @Column(name = "latitude", precision = 8, scale = 5, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 8, scale = 5, nullable = false)
    private BigDecimal longitude;

    public String getSubjectKey() {
        return subjectKey;
    }

    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public String getSeriesTicker() {
        return seriesTicker;
    }

    public void setSeriesTicker(String seriesTicker) {
        this.seriesTicker = seriesTicker;
    }

    public String getNwsGridId() {
        return nwsGridId;
    }

    public void setNwsGridId(String nwsGridId) {
        this.nwsGridId = nwsGridId;
    }

    public Integer getNwsGridX() {
        return nwsGridX;
    }

    public void setNwsGridX(Integer nwsGridX) {
        this.nwsGridX = nwsGridX;
    }

    public Integer getNwsGridY() {
        return nwsGridY;
    }

    public void setNwsGridY(Integer nwsGridY) {
        this.nwsGridY = nwsGridY;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }
}
