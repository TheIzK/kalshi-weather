package com.kalshiweather.ingestion.mlb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mlb_teams")
public class MlbTeam {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(nullable = false, length = 5)
    private String abbreviation;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "league_id", nullable = false)
    private Short leagueId;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Short getLeagueId() {
        return leagueId;
    }

    public void setLeagueId(Short leagueId) {
        this.leagueId = leagueId;
    }
}
