package com.kalshiweather.ingestion.mlb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import com.kalshiweather.ingestion.mlb.entity.MlbPitcherSeasonStats;
import com.kalshiweather.ingestion.mlb.entity.MlbProbablePitcher;
import com.kalshiweather.ingestion.mlb.entity.MlbTeamStandingsSnapshot;
import com.kalshiweather.ingestion.mlb.entity.MlbWinProbabilitySnapshot;
import com.kalshiweather.ingestion.mlb.repository.MlbPitcherSeasonStatsRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbProbablePitcherRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbTeamStandingsSnapshotRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbWinProbabilitySnapshotRepository;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Two evenly matched teams with equal-quality starters should land at ~50/50; a clear
 * talent/FIP gap should push probability meaningfully off 50/50 in the expected direction.
 */
@ExtendWith(MockitoExtension.class)
class MlbWinProbabilityServiceImplTest {

    @Mock
    private MlbTeamStandingsSnapshotRepository standingsRepository;
    @Mock
    private MlbPitcherSeasonStatsRepository pitcherStatsRepository;
    @Mock
    private MlbProbablePitcherRepository probablePitcherRepository;
    @Mock
    private MlbWinProbabilitySnapshotRepository snapshotRepository;

    private MlbWinProbabilityServiceImpl service;

    private static final short HOME_TEAM = 147; // NYY
    private static final short AWAY_TEAM = 111; // BOS
    private static final int HOME_PITCHER_ID = 1001;
    private static final int AWAY_PITCHER_ID = 1002;

    @BeforeEach
    void setUp() {
        service = new MlbWinProbabilityServiceImpl(
                standingsRepository, pitcherStatsRepository, probablePitcherRepository,
                snapshotRepository, new ObjectMapper());

        lenient().when(snapshotRepository.save(any(MlbWinProbabilitySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(probablePitcherRepository.findByGamePkAndTeamId(1L, HOME_TEAM))
                .thenReturn(Optional.of(probablePitcher(HOME_PITCHER_ID)));
        lenient().when(probablePitcherRepository.findByGamePkAndTeamId(1L, AWAY_TEAM))
                .thenReturn(Optional.of(probablePitcher(AWAY_PITCHER_ID)));
    }

    private MlbGame game() {
        MlbGame game = new MlbGame();
        game.setGamePk(1L);
        game.setHomeTeamId(HOME_TEAM);
        game.setAwayTeamId(AWAY_TEAM);
        game.setStatus("Scheduled");
        return game;
    }

    private MlbTeamStandingsSnapshot standings(short teamId, String homeWinPct, String awayWinPct) {
        MlbTeamStandingsSnapshot snapshot = new MlbTeamStandingsSnapshot();
        snapshot.setTeamId(teamId);
        snapshot.setAsOfDate(LocalDate.now());
        snapshot.setHomeWinPct(new BigDecimal(homeWinPct));
        snapshot.setAwayWinPct(new BigDecimal(awayWinPct));
        return snapshot;
    }

    private MlbProbablePitcher probablePitcher(int playerId) {
        MlbProbablePitcher pitcher = new MlbProbablePitcher();
        pitcher.setPlayerId(playerId);
        pitcher.setPlayerName("Test Pitcher " + playerId);
        return pitcher;
    }

    private MlbPitcherSeasonStats pitcherStats(int playerId, String fip) {
        MlbPitcherSeasonStats stats = new MlbPitcherSeasonStats();
        stats.setPlayerId(playerId);
        stats.setFip(new BigDecimal(fip));
        return stats;
    }

    @Test
    void evenlyMatchedTeamsAndPitchersLandNearFiftyFifty() {
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(HOME_TEAM))
                .thenReturn(Optional.of(standings(HOME_TEAM, "0.500", "0.500")));
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(AWAY_TEAM))
                .thenReturn(Optional.of(standings(AWAY_TEAM, "0.500", "0.500")));
        when(pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(eq(HOME_PITCHER_ID), any()))
                .thenReturn(Optional.of(pitcherStats(HOME_PITCHER_ID, "4.00")));
        when(pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(eq(AWAY_PITCHER_ID), any()))
                .thenReturn(Optional.of(pitcherStats(AWAY_PITCHER_ID, "4.00")));

        Optional<MlbWinProbabilitySnapshot> result = service.computeAndPersist(game());

        assertThat(result).isPresent();
        assertThat(result.get().getHomeWinProbability()).isCloseTo(new BigDecimal("0.5000"), Offset.offset(new BigDecimal("0.01")));
        assertThat(result.get().getModelVersion()).isEqualTo("v1-log5-pitcher-adj");
        assertThat(result.get().getInputs()).contains("homeWinPct").contains("fipDelta");
    }

    @Test
    void clearTalentGapPushesProbabilityTowardBetterTeam() {
        // home team wins 70% at home, away team wins only 30% on the road -> home clearly favored
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(HOME_TEAM))
                .thenReturn(Optional.of(standings(HOME_TEAM, "0.700", "0.600")));
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(AWAY_TEAM))
                .thenReturn(Optional.of(standings(AWAY_TEAM, "0.400", "0.300")));
        when(pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(eq(HOME_PITCHER_ID), any()))
                .thenReturn(Optional.of(pitcherStats(HOME_PITCHER_ID, "3.00"))); // better (lower) FIP
        when(pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(eq(AWAY_PITCHER_ID), any()))
                .thenReturn(Optional.of(pitcherStats(AWAY_PITCHER_ID, "5.00")));

        Optional<MlbWinProbabilitySnapshot> result = service.computeAndPersist(game());

        assertThat(result).isPresent();
        assertThat(result.get().getHomeWinProbability()).isGreaterThan(new BigDecimal("0.65"));
    }

    @Test
    void emptyWhenStandingsMissing() {
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(HOME_TEAM)).thenReturn(Optional.empty());
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(AWAY_TEAM))
                .thenReturn(Optional.of(standings(AWAY_TEAM, "0.500", "0.500")));

        Optional<MlbWinProbabilitySnapshot> result = service.computeAndPersist(game());

        assertThat(result).isEmpty();
    }

    @Test
    void emptyWhenConfirmedStarterStatsMissing() {
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(HOME_TEAM))
                .thenReturn(Optional.of(standings(HOME_TEAM, "0.500", "0.500")));
        when(standingsRepository.findFirstByTeamIdOrderByAsOfDateDesc(AWAY_TEAM))
                .thenReturn(Optional.of(standings(AWAY_TEAM, "0.500", "0.500")));
        when(pitcherStatsRepository.findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(eq(HOME_PITCHER_ID), any()))
                .thenReturn(Optional.empty());

        Optional<MlbWinProbabilitySnapshot> result = service.computeAndPersist(game());

        assertThat(result).isEmpty();
    }
}
