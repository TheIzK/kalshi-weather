package com.kalshiweather.ingestion.mlb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import com.kalshiweather.ingestion.mlb.entity.MlbPitcherSeasonStats;
import com.kalshiweather.ingestion.mlb.entity.MlbTeamStandingsSnapshot;
import com.kalshiweather.ingestion.mlb.entity.MlbWinProbabilitySnapshot;
import com.kalshiweather.ingestion.mlb.repository.MlbPitcherSeasonStatsRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbProbablePitcherRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbTeamStandingsSnapshotRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbWinProbabilitySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * v1 model: Log5 (Bill James) on home/away split win% -- which bakes in home field
 * advantage without a separate constant -- adjusted by the confirmed starters' FIP
 * differential. FIP_TO_WINPROB_SCALE is a starting heuristic, not a fitted value; every
 * prediction's inputs are persisted alongside the result specifically so this can be
 * recalibrated later against realized outcomes.
 */
@Service
public class MlbWinProbabilityServiceImpl implements MlbWinProbabilityService {

    private static final Logger log = LoggerFactory.getLogger(MlbWinProbabilityServiceImpl.class);

    private static final String MODEL_VERSION = "v1-log5-pitcher-adj";
    private static final BigDecimal FIP_TO_WINPROB_SCALE = new BigDecimal("0.03");
    private static final BigDecimal FLOOR = new BigDecimal("0.05");
    private static final BigDecimal CEIL = new BigDecimal("0.95");
    private static final int SNAPSHOT_SCALE = 4; // matches home_win_probability NUMERIC(5,4)

    private static final ZoneId MLB_ZONE = ZoneId.of("America/New_York");

    private final MlbTeamStandingsSnapshotRepository standingsRepository;
    private final MlbPitcherSeasonStatsRepository pitcherStatsRepository;
    private final MlbProbablePitcherRepository probablePitcherRepository;
    private final MlbWinProbabilitySnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public MlbWinProbabilityServiceImpl(
            MlbTeamStandingsSnapshotRepository standingsRepository,
            MlbPitcherSeasonStatsRepository pitcherStatsRepository,
            MlbProbablePitcherRepository probablePitcherRepository,
            MlbWinProbabilitySnapshotRepository snapshotRepository,
            ObjectMapper objectMapper
    ) {
        this.standingsRepository = standingsRepository;
        this.pitcherStatsRepository = pitcherStatsRepository;
        this.probablePitcherRepository = probablePitcherRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MlbWinProbabilitySnapshot> computeAndPersist(MlbGame game) {
        Optional<MlbTeamStandingsSnapshot> home = standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(game.getHomeTeamId());
        Optional<MlbTeamStandingsSnapshot> away = standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(game.getAwayTeamId());
        if (home.isEmpty() || away.isEmpty()) {
            log.debug("Skipping game {}: missing standings snapshot (home={}, away={})",
                    game.getGamePk(), home.isPresent(), away.isPresent());
            return Optional.empty();
        }

        Optional<MlbPitcherSeasonStats> homeStats = confirmedStarterStats(game.getGamePk(), game.getHomeTeamId());
        Optional<MlbPitcherSeasonStats> awayStats = confirmedStarterStats(game.getGamePk(), game.getAwayTeamId());
        if (homeStats.isEmpty() || awayStats.isEmpty()) {
            log.debug("Skipping game {}: missing confirmed starter stats (home={}, away={})",
                    game.getGamePk(), homeStats.isPresent(), awayStats.isPresent());
            return Optional.empty();
        }

        BigDecimal pHome = home.get().getHomeWinPct();
        BigDecimal pAway = away.get().getAwayWinPct();
        BigDecimal baseHomeProb = log5(pHome, pAway);

        BigDecimal homeFip = homeStats.get().getFip();
        BigDecimal awayFip = awayStats.get().getFip();
        BigDecimal fipDelta = awayFip.subtract(homeFip); // positive favors home
        BigDecimal pitcherAdjustment = fipDelta.multiply(FIP_TO_WINPROB_SCALE);

        BigDecimal adjustedHomeProb = clamp(baseHomeProb.add(pitcherAdjustment), FLOOR, CEIL)
                .setScale(SNAPSHOT_SCALE, RoundingMode.HALF_UP);

        MlbWinProbabilitySnapshot snapshot = new MlbWinProbabilitySnapshot();
        snapshot.setGamePk(game.getGamePk());
        snapshot.setComputedAt(Instant.now());
        snapshot.setHomeWinProbability(adjustedHomeProb);
        snapshot.setModelVersion(MODEL_VERSION);
        snapshot.setInputs(serializeInputs(pHome, pAway, homeFip, awayFip, fipDelta, baseHomeProb));

        return Optional.of(snapshotRepository.save(snapshot));
    }

    private Optional<MlbPitcherSeasonStats> confirmedStarterStats(Long gamePk, Short teamId) {
        return probablePitcherRepository.findByGamePkAndTeamId(gamePk, teamId)
                .flatMap(pitcher -> pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(
                        pitcher.getPlayerId(), currentSeason()));
    }

    private int currentSeason() {
        return LocalDate.now(MLB_ZONE).getYear();
    }

    private BigDecimal log5(BigDecimal pHome, BigDecimal pAway) {
        BigDecimal numerator = pHome.subtract(pHome.multiply(pAway));
        BigDecimal denominator = pHome.add(pAway).subtract(pHome.multiply(pAway).multiply(BigDecimal.valueOf(2)));
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal floor, BigDecimal ceil) {
        if (value.compareTo(floor) < 0) return floor;
        if (value.compareTo(ceil) > 0) return ceil;
        return value;
    }

    private String serializeInputs(
            BigDecimal homeWinPct, BigDecimal awayWinPct, BigDecimal homeFip,
            BigDecimal awayFip, BigDecimal fipDelta, BigDecimal baseHomeProb
    ) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("homeWinPct", homeWinPct);
        inputs.put("awayWinPct", awayWinPct);
        inputs.put("homeFip", homeFip);
        inputs.put("awayFip", awayFip);
        inputs.put("fipDelta", fipDelta);
        inputs.put("baseHomeProb", baseHomeProb);
        try {
            return objectMapper.writeValueAsString(inputs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize win probability inputs", e);
        }
    }
}
