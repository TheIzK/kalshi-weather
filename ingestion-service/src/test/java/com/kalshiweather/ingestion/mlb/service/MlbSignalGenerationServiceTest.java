package com.kalshiweather.ingestion.mlb.service;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import com.kalshiweather.ingestion.mlb.entity.MlbTeam;
import com.kalshiweather.ingestion.mlb.entity.MlbWinProbabilitySnapshot;
import com.kalshiweather.ingestion.mlb.repository.MlbGameRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbTeamRepository;
import com.kalshiweather.ingestion.signal.SignalGenerationService;
import com.kalshiweather.ingestion.trade.PaperTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MlbSignalGenerationServiceTest {

    @Mock
    private MlbGameRepository gameRepository;
    @Mock
    private MlbTeamRepository teamRepository;
    @Mock
    private MlbWinProbabilityService winProbabilityService;
    @Mock
    private KalshiClient kalshiClient;
    @Mock
    private SignalGenerationService signalGenerationService;
    @Mock
    private PaperTradeService paperTradeService;

    private MlbSignalGenerationService service;

    private static final short HOME_TEAM = 145; // CWS
    private static final short AWAY_TEAM = 112; // CHC
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 8, 19);

    @BeforeEach
    void setUp() {
        service = new MlbSignalGenerationService(
                gameRepository, teamRepository, winProbabilityService, kalshiClient,
                signalGenerationService, paperTradeService);
    }

    private MlbGame game() {
        MlbGame game = new MlbGame();
        game.setGamePk(1L);
        game.setGameDate(GAME_DATE);
        game.setHomeTeamId(HOME_TEAM);
        game.setAwayTeamId(AWAY_TEAM);
        return game;
    }

    private MlbTeam team(short id, String abbreviation) {
        MlbTeam team = new MlbTeam();
        team.setId(id);
        team.setAbbreviation(abbreviation);
        return team;
    }

    private Market kalshiMarket(String ticker, LocalDate occurrenceDate) {
        Market market = new Market();
        market.setId(ticker);
        market.setEventTicker("KXMLBGAME-26AUG191420CWSCHC");
        market.setOccurrenceDate(occurrenceDate);
        market.setYesBid(new BigDecimal("0.45"));
        market.setYesAsk(new BigDecimal("0.50"));
        market.setNoBid(new BigDecimal("0.50"));
        market.setNoAsk(new BigDecimal("0.55"));
        return market;
    }

    private MlbWinProbabilitySnapshot snapshot(String homeWinProbability) {
        MlbWinProbabilitySnapshot snapshot = new MlbWinProbabilitySnapshot();
        snapshot.setId(42L);
        snapshot.setHomeWinProbability(new BigDecimal(homeWinProbability));
        return snapshot;
    }

    @Test
    void picksHomeTeamMarketWhenHomeIsFavored() {
        when(gameRepository.findGamesWithConfirmedPitchers(any())).thenReturn(List.of(game()));
        when(winProbabilityService.computeAndPersist(any())).thenReturn(Optional.of(snapshot("0.65")));
        when(teamRepository.findById(HOME_TEAM)).thenReturn(Optional.of(team(HOME_TEAM, "CWS")));

        Market homeMarket = kalshiMarket("KXMLBGAME-26AUG191420CWSCHC-CWS", GAME_DATE);
        Market awayMarket = kalshiMarket("KXMLBGAME-26AUG191420CWSCHC-CHC", GAME_DATE);
        when(kalshiClient.fetchOpenMarkets("KXMLBGAME")).thenReturn(List.of(homeMarket, awayMarket));

        Signal signal = new Signal();
        when(signalGenerationService.evaluate(eq(homeMarket), eq(new BigDecimal("0.65")), eq("MLB_WIN_PROBABILITY"), eq("42")))
                .thenReturn(Optional.of(signal));

        service.evaluateTodaysGames();

        verify(paperTradeService).open(signal, homeMarket);
        verify(signalGenerationService, never()).evaluate(eq(awayMarket), any(), any(), any());
    }

    @Test
    void picksAwayTeamMarketWhenAwayIsFavored() {
        when(gameRepository.findGamesWithConfirmedPitchers(any())).thenReturn(List.of(game()));
        when(winProbabilityService.computeAndPersist(any())).thenReturn(Optional.of(snapshot("0.30"))); // home unfavored -> away favored, p=0.70
        when(teamRepository.findById(AWAY_TEAM)).thenReturn(Optional.of(team(AWAY_TEAM, "CHC")));

        Market homeMarket = kalshiMarket("KXMLBGAME-26AUG191420CWSCHC-CWS", GAME_DATE);
        Market awayMarket = kalshiMarket("KXMLBGAME-26AUG191420CWSCHC-CHC", GAME_DATE);
        when(kalshiClient.fetchOpenMarkets("KXMLBGAME")).thenReturn(List.of(homeMarket, awayMarket));

        ArgumentCaptor<BigDecimal> probabilityCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        when(signalGenerationService.evaluate(eq(awayMarket), probabilityCaptor.capture(), eq("MLB_WIN_PROBABILITY"), eq("42")))
                .thenReturn(Optional.empty());

        service.evaluateTodaysGames();

        assertThat(probabilityCaptor.getValue()).isEqualByComparingTo("0.70"); // 1 - 0.30
        verify(paperTradeService, never()).open(any(), any());
    }

    @Test
    void skipsGameWhenNoWinProbabilityYet() {
        when(gameRepository.findGamesWithConfirmedPitchers(any())).thenReturn(List.of(game()));
        when(winProbabilityService.computeAndPersist(any())).thenReturn(Optional.empty());
        when(kalshiClient.fetchOpenMarkets("KXMLBGAME")).thenReturn(List.of());

        service.evaluateTodaysGames();

        verifyNoInteractions(signalGenerationService, paperTradeService);
    }

    @Test
    void skipsGameWhenNoMatchingKalshiMarketExists() {
        when(gameRepository.findGamesWithConfirmedPitchers(any())).thenReturn(List.of(game()));
        when(winProbabilityService.computeAndPersist(any())).thenReturn(Optional.of(snapshot("0.65")));
        when(teamRepository.findById(HOME_TEAM)).thenReturn(Optional.of(team(HOME_TEAM, "CWS")));
        when(kalshiClient.fetchOpenMarkets("KXMLBGAME")).thenReturn(List.of()); // no markets open yet

        service.evaluateTodaysGames();

        verifyNoInteractions(signalGenerationService, paperTradeService);
    }

    @Test
    void oneGameFailingDoesNotStopEvaluationOfTheRest() {
        MlbGame failing = game();
        failing.setGamePk(1L);
        MlbGame healthy = game();
        healthy.setGamePk(2L);

        when(gameRepository.findGamesWithConfirmedPitchers(any())).thenReturn(List.of(failing, healthy));
        when(winProbabilityService.computeAndPersist(failing)).thenThrow(new RuntimeException("boom"));
        when(winProbabilityService.computeAndPersist(healthy)).thenReturn(Optional.of(snapshot("0.65")));
        when(teamRepository.findById(HOME_TEAM)).thenReturn(Optional.of(team(HOME_TEAM, "CWS")));
        when(kalshiClient.fetchOpenMarkets("KXMLBGAME")).thenReturn(List.of());

        service.evaluateTodaysGames();

        verify(winProbabilityService).computeAndPersist(healthy);
    }
}
