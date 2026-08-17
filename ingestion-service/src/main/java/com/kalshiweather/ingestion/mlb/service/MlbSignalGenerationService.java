package com.kalshiweather.ingestion.mlb.service;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import com.kalshiweather.ingestion.mlb.entity.MlbTeam;
import com.kalshiweather.ingestion.mlb.entity.MlbWinProbabilitySnapshot;
import com.kalshiweather.ingestion.mlb.repository.MlbGameRepository;
import com.kalshiweather.ingestion.mlb.repository.MlbTeamRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.signal.SignalGenerationService;
import com.kalshiweather.ingestion.trade.PaperTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * The MLB analogue of what SignalGenerationService + IngestionOrchestrator's per-market
 * loop do for weather: for each of today's confirmed-pitcher games, compute a win
 * probability, find the matching Kalshi market, and hand off to the shared
 * (domain-agnostic) Signal/PaperTrade pipeline — no changes needed to either of those for
 * MLB to work, per the plan's whole point.
 *
 * Kalshi models an MLB game as two separate per-team binary markets sharing one event
 * (verified against live data 2026-08-16: e.g. KXMLBGAME-26AUG191420CWSCHC-CWS /
 * -CHC), not one moneyline market. The ticker suffix after the last hyphen is the team
 * abbreviation — matching on that avoids needing to add yes_sub_title or any other new
 * field to the shared Market entity just for this.
 */
@Service
public class MlbSignalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(MlbSignalGenerationService.class);

    private static final String SOURCE_TYPE = "MLB_WIN_PROBABILITY";
    private static final String SERIES_TICKER = "KXMLBGAME";
    private static final BigDecimal HALF = new BigDecimal("0.5");

    // Games are scheduled/settled on an MLB business day, which this repo already treats as
    // America/New_York elsewhere (KalshiClient.MARKET_ZONE, weather's occurrence dates).
    private static final ZoneId MLB_ZONE = ZoneId.of("America/New_York");

    private final MlbGameRepository gameRepository;
    private final MlbTeamRepository teamRepository;
    private final MlbWinProbabilityService winProbabilityService;
    private final KalshiClient kalshiClient;
    private final MarketRepository marketRepository;
    private final SignalGenerationService signalGenerationService;
    private final PaperTradeService paperTradeService;

    public MlbSignalGenerationService(
            MlbGameRepository gameRepository,
            MlbTeamRepository teamRepository,
            MlbWinProbabilityService winProbabilityService,
            KalshiClient kalshiClient,
            MarketRepository marketRepository,
            SignalGenerationService signalGenerationService,
            PaperTradeService paperTradeService
    ) {
        this.gameRepository = gameRepository;
        this.teamRepository = teamRepository;
        this.winProbabilityService = winProbabilityService;
        this.kalshiClient = kalshiClient;
        this.marketRepository = marketRepository;
        this.signalGenerationService = signalGenerationService;
        this.paperTradeService = paperTradeService;
    }

    /** Evaluates every one of today's games that has a confirmed starter on both sides. */
    public void evaluateTodaysGames() {
        LocalDate today = LocalDate.now(MLB_ZONE);
        List<MlbGame> games = gameRepository.findGamesWithConfirmedPitchers(today);
        if (games.isEmpty()) {
            return;
        }

        List<Market> openMarkets;
        try {
            openMarkets = kalshiClient.fetchOpenMarkets(SERIES_TICKER);
            // signals.market_id and paper_trades.market_id are FKs to markets(id) — these
            // must be persisted before any signal/trade can reference them, same as
            // IngestionOrchestrator.ingestMarkets() already does for weather.
            openMarkets = marketRepository.saveAll(openMarkets);
        } catch (Exception e) {
            log.error("Failed to fetch/persist {} markets: {}", SERIES_TICKER, e.getMessage(), e);
            return;
        }

        for (MlbGame game : games) {
            try {
                evaluateGame(game, openMarkets);
            } catch (Exception e) {
                log.error("Failed to evaluate MLB game {}: {}", game.getGamePk(), e.getMessage(), e);
            }
        }
    }

    private void evaluateGame(MlbGame game, List<Market> openMarkets) {
        Optional<MlbWinProbabilitySnapshot> snapshotOpt = winProbabilityService.computeAndPersist(game);
        if (snapshotOpt.isEmpty()) {
            log.debug("Skipping game {}: not enough data yet for a win probability", game.getGamePk());
            return;
        }
        MlbWinProbabilitySnapshot snapshot = snapshotOpt.get();

        BigDecimal homeWinProbability = snapshot.getHomeWinProbability();
        boolean favorsHome = homeWinProbability.compareTo(HALF) >= 0;
        Short favoredTeamId = favorsHome ? game.getHomeTeamId() : game.getAwayTeamId();
        BigDecimal favoredTeamWinProbability = favorsHome ? homeWinProbability : BigDecimal.ONE.subtract(homeWinProbability);

        Optional<MlbTeam> favoredTeam = teamRepository.findById(favoredTeamId);
        if (favoredTeam.isEmpty()) {
            log.warn("No MlbTeam row for team id {} (game {})", favoredTeamId, game.getGamePk());
            return;
        }

        Optional<Market> market = findMarketForTeam(openMarkets, game.getGameDate(), favoredTeam.get().getAbbreviation());
        if (market.isEmpty()) {
            log.debug("No open {} market found for game {} ({})", SERIES_TICKER, game.getGamePk(), favoredTeam.get().getAbbreviation());
            return;
        }

        signalGenerationService.evaluate(market.get(), favoredTeamWinProbability, SOURCE_TYPE, snapshot.getId().toString())
                .ifPresent(signal -> paperTradeService.open(signal, market.get()));
    }

    /**
     * Known limitation, not handled: if the same two teams play a doubleheader (two games,
     * same date, same matchup), this can't distinguish between the two legs and will match
     * whichever comes first. Rare enough not to be worth solving before it's ever observed.
     */
    private Optional<Market> findMarketForTeam(List<Market> openMarkets, LocalDate gameDate, String teamAbbreviation) {
        String tickerSuffix = "-" + teamAbbreviation;
        return openMarkets.stream()
                .filter(market -> gameDate.equals(market.getOccurrenceDate()))
                .filter(market -> market.getId().endsWith(tickerSuffix))
                .findFirst();
    }
}
