package com.kalshiweather.ingestion.mlb;

import com.kalshiweather.ingestion.mlb.client.MlbStatsApiClient;
import com.kalshiweather.ingestion.mlb.dto.MlbPitcherStatsResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbScheduleResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbStandingsResponse;
import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import com.kalshiweather.ingestion.mlb.entity.MlbPitcherSeasonStats;
import com.kalshiweather.ingestion.mlb.entity.MlbProbablePitcher;
import com.kalshiweather.ingestion.mlb.entity.MlbTeamStandingsSnapshot;
import com.kalshiweather.ingestion.mlb.repository.MlbGameRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbPitcherSeasonStatsRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbProbablePitcherRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbTeamStandingsSnapshotRepository;
import com.kalshiweather.ingestion.mlb.service.FipCalculator;
import com.kalshiweather.ingestion.mlb.service.MlbSignalGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * MLB's analogue of IngestionOrchestrator: pulls schedule/probable-pitcher/stats/standings
 * data from statsapi on the cadence the design doc calls for, then hands off to
 * MlbSignalGenerationService. Every external call is isolated in its own try/catch, same
 * philosophy as IngestionOrchestrator's three weather sources — one failing game or player
 * lookup shouldn't stop the rest of the batch.
 */
@Component
public class MlbIngestionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MlbIngestionOrchestrator.class);
    private static final ZoneId MLB_ZONE = ZoneId.of("America/New_York");

    private final MlbStatsApiClient statsApiClient;
    private final MlbGameRepository gameRepository;
    private final MlbProbablePitcherRepository probablePitcherRepository;
    private final MlbPitcherSeasonStatsRepository pitcherStatsRepository;
    private final MlbTeamStandingsSnapshotRepository standingsRepository;
    private final FipCalculator fipCalculator;
    private final MlbSignalGenerationService mlbSignalGenerationService;

    public MlbIngestionOrchestrator(
            MlbStatsApiClient statsApiClient,
            MlbGameRepository gameRepository,
            MlbProbablePitcherRepository probablePitcherRepository,
            MlbPitcherSeasonStatsRepository pitcherStatsRepository,
            MlbTeamStandingsSnapshotRepository standingsRepository,
            FipCalculator fipCalculator,
            MlbSignalGenerationService mlbSignalGenerationService
    ) {
        this.statsApiClient = statsApiClient;
        this.gameRepository = gameRepository;
        this.probablePitcherRepository = probablePitcherRepository;
        this.pitcherStatsRepository = pitcherStatsRepository;
        this.standingsRepository = standingsRepository;
        this.fipCalculator = fipCalculator;
        this.mlbSignalGenerationService = mlbSignalGenerationService;
    }

    /** Full refresh: schedule, probable pitchers, pitcher stats, team standings. */
    @Scheduled(cron = "${mlb.schedule.daily-refresh-cron:0 0 7 * * *}", zone = "America/New_York")
    public void dailyRefresh() {
        LocalDate today = LocalDate.now(MLB_ZONE);
        log.info("MLB daily refresh starting for {}", today);
        refreshSchedule(today);
        refreshStandings();
    }

    /**
     * Re-checks the schedule (pitcher scratches move win probability more than almost any
     * other in-game event — this is the guard against trading on a stale starter) and
     * re-evaluates today's games.
     */
    @Scheduled(
            initialDelayString = "${mlb.schedule.initial-delay-ms:10000}",
            fixedDelayString = "${mlb.schedule.hourly-recheck-delay-ms:3600000}"
    )
    public void hourlyRecheck() {
        refreshSchedule(LocalDate.now(MLB_ZONE));
        mlbSignalGenerationService.evaluateTodaysGames();
    }

    private void refreshSchedule(LocalDate date) {
        MlbScheduleResponse response;
        try {
            response = statsApiClient.getScheduleForDate(date);
        } catch (Exception e) {
            log.error("MLB schedule fetch failed for {}: {}", date, e.getMessage(), e);
            return;
        }
        if (response == null || response.dates() == null) {
            return;
        }

        int gameCount = 0;
        for (MlbScheduleResponse.ScheduleDate scheduleDate : response.dates()) {
            for (MlbScheduleResponse.ScheduleGame game : scheduleDate.games()) {
                try {
                    upsertGame(game);
                    gameCount++;
                } catch (Exception e) {
                    log.error("Failed to upsert MLB game {}: {}", game.gamePk(), e.getMessage(), e);
                }
            }
        }
        log.info("Ingested {} MLB game(s) for {}", gameCount, date);
    }

    private void upsertGame(MlbScheduleResponse.ScheduleGame dto) {
        Instant now = Instant.now();
        Optional<MlbGame> existing = gameRepository.findById(dto.gamePk());
        MlbGame game = existing.orElseGet(MlbGame::new);

        game.setGamePk(dto.gamePk());
        game.setGameDate(LocalDate.parse(dto.officialDate()));
        game.setGameDatetimeUtc(Instant.parse(dto.gameDate()));
        game.setHomeTeamId((short) dto.teams().home().team().id());
        game.setAwayTeamId((short) dto.teams().away().team().id());
        game.setStatus(dto.status().detailedState());
        game.setGameType(dto.gameType());
        if (existing.isEmpty()) {
            game.setCreatedAt(now);
        }
        game.setUpdatedAt(now);
        gameRepository.save(game);

        upsertProbablePitcher(dto.gamePk(), (short) dto.teams().home().team().id(), dto.teams().home().probablePitcher(), now);
        upsertProbablePitcher(dto.gamePk(), (short) dto.teams().away().team().id(), dto.teams().away().probablePitcher(), now);
    }

    private void upsertProbablePitcher(Long gamePk, Short teamId, MlbScheduleResponse.ProbablePitcherRef pitcherRef, Instant now) {
        if (pitcherRef == null) {
            return; // not confirmed yet
        }

        MlbProbablePitcher pitcher = probablePitcherRepository.findByGamePkAndTeamId(gamePk, teamId)
                .orElseGet(MlbProbablePitcher::new);
        pitcher.setGamePk(gamePk);
        pitcher.setTeamId(teamId);
        pitcher.setPlayerId(pitcherRef.id());
        pitcher.setPlayerName(pitcherRef.fullName());
        pitcher.setConfirmedAt(now);
        probablePitcherRepository.save(pitcher);

        refreshPitcherStatsIfMissingForToday(pitcherRef.id());
    }

    /** Skips the fetch if we already have today's snapshot for this pitcher — stats don't
     * shift meaningfully within a day, and this keeps a scratch-driven hourly re-check from
     * needlessly re-fetching every already-known starter's stats. */
    private void refreshPitcherStatsIfMissingForToday(int playerId) {
        LocalDate today = LocalDate.now(MLB_ZONE);
        int season = today.getYear();

        Optional<MlbPitcherSeasonStats> existing =
                pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(playerId, season);
        if (existing.isPresent() && today.equals(existing.get().getAsOfDate())) {
            return;
        }

        try {
            MlbPitcherStatsResponse.PitchingStat stat = extractStat(statsApiClient.getPitcherSeasonStats(playerId, season));
            if (stat == null) {
                log.debug("No season pitching stats yet for MLB player {}", playerId);
                return;
            }

            BigDecimal inningsPitched = fipCalculator.parseInningsPitched(stat.inningsPitched());
            BigDecimal fip = fipCalculator.calculate(
                    stat.homeRuns(), stat.baseOnBalls(), stat.hitByPitch(), stat.strikeOuts(), inningsPitched);

            MlbPitcherSeasonStats stats = new MlbPitcherSeasonStats();
            stats.setPlayerId(playerId);
            stats.setSeason(season);
            stats.setAsOfDate(today);
            stats.setInningsPitched(inningsPitched);
            stats.setEra(stat.era());
            stats.setWhip(stat.whip());
            stats.setStrikeouts(stat.strikeOuts());
            stats.setWalks(stat.baseOnBalls());
            stats.setHitBatsmen(stat.hitByPitch());
            stats.setHomeRunsAllowed(stat.homeRuns());
            stats.setFip(fip);
            stats.setGamesStarted(stat.gamesStarted());
            stats.setFetchedAt(Instant.now());
            pitcherStatsRepository.save(stats);
        } catch (Exception e) {
            log.error("Failed to refresh season stats for MLB player {}: {}", playerId, e.getMessage(), e);
        }
    }

    private MlbPitcherStatsResponse.PitchingStat extractStat(MlbPitcherStatsResponse response) {
        if (response == null || response.stats() == null || response.stats().isEmpty()) {
            return null;
        }
        List<MlbPitcherStatsResponse.Split> splits = response.stats().get(0).splits();
        if (splits == null || splits.isEmpty()) {
            return null;
        }
        return splits.get(0).stat();
    }

    private void refreshStandings() {
        MlbStandingsResponse response;
        try {
            response = statsApiClient.getStandings(LocalDate.now(MLB_ZONE).getYear());
        } catch (Exception e) {
            log.error("MLB standings fetch failed: {}", e.getMessage(), e);
            return;
        }
        if (response == null || response.records() == null) {
            return;
        }

        LocalDate today = LocalDate.now(MLB_ZONE);
        int teamCount = 0;
        for (MlbStandingsResponse.StandingsRecord leagueRecord : response.records()) {
            if (leagueRecord.teamRecords() == null) {
                continue;
            }
            for (MlbStandingsResponse.TeamRecord teamRecord : leagueRecord.teamRecords()) {
                try {
                    upsertStandings(teamRecord, today);
                    teamCount++;
                } catch (Exception e) {
                    log.error("Failed to upsert MLB standings for team {}: {}", teamRecord.team().id(), e.getMessage(), e);
                }
            }
        }
        log.info("Ingested MLB standings for {} team(s)", teamCount);
    }

    private void upsertStandings(MlbStandingsResponse.TeamRecord teamRecord, LocalDate today) {
        Optional<MlbStandingsResponse.SplitRecord> home = findSplit(teamRecord, "home");
        Optional<MlbStandingsResponse.SplitRecord> away = findSplit(teamRecord, "away");
        if (home.isEmpty() || away.isEmpty()) {
            log.warn("Missing home/away split for MLB team {}", teamRecord.team().id());
            return;
        }

        short teamId = (short) teamRecord.team().id();
        // Reuse today's row if this is a second run today; otherwise start a fresh
        // historical row rather than overwriting a prior day's snapshot.
        MlbTeamStandingsSnapshot snapshot = standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(teamId)
                .filter(existing -> today.equals(existing.getAsOfDate()))
                .orElseGet(MlbTeamStandingsSnapshot::new);

        snapshot.setTeamId(teamId);
        snapshot.setAsOfDate(today);
        snapshot.setWins(teamRecord.wins());
        snapshot.setLosses(teamRecord.losses());
        snapshot.setHomeWinPct(winPct(home.get()));
        snapshot.setAwayWinPct(winPct(away.get()));
        snapshot.setFetchedAt(Instant.now());
        standingsRepository.save(snapshot);
    }

    private Optional<MlbStandingsResponse.SplitRecord> findSplit(MlbStandingsResponse.TeamRecord teamRecord, String type) {
        if (teamRecord.records() == null || teamRecord.records().splitRecords() == null) {
            return Optional.empty();
        }
        return teamRecord.records().splitRecords().stream()
                .filter(split -> type.equals(split.type()))
                .findFirst();
    }

    private BigDecimal winPct(MlbStandingsResponse.SplitRecord split) {
        int total = split.wins() + split.losses();
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(split.wins()).divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP);
    }
}
