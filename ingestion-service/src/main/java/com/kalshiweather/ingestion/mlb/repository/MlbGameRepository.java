package com.kalshiweather.ingestion.mlb.repository;

import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MlbGameRepository extends JpaRepository<MlbGame, Long> {

    /**
     * Games on the given date that have a confirmed starter recorded for BOTH sides.
     * {@code date} is passed in explicitly (America/New_York, matching how MLB's own
     * "officialDate" already normalizes game_date) rather than using JPQL current_date,
     * which would run in whatever zone the DB session is in (UTC here) — the same
     * explicit-normalization discipline this repo already applies to weather dates.
     */
    @Query("""
            select g from MlbGame g
            where g.gameDate = :date
            and g.status = 'Scheduled'
            and (select count(p) from MlbProbablePitcher p where p.gamePk = g.gamePk) = 2
            """)
    List<MlbGame> findGamesWithConfirmedPitchers(@Param("date") LocalDate date);
}
