package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Richer, token-gated read endpoint for an external dashboard (isaacmelton.dev/kalshi) —
 * open positions and recent closed trades grouped by {@code series_ticker}, per-series and
 * overall P&amp;L/win-rate stats, and a last-ingested-at health signal per series. Same
 * auth pattern as {@link StatusController}, which stays as-is for its existing lightweight
 * consumer. Grouping by {@code series_ticker} rather than {@code subject_stations} is
 * deliberate: it covers MLB's {@code KXMLBGAME} for free alongside every weather city, with
 * no sport-specific code, consistent with how the rest of this codebase treats weather and
 * MLB as instances of the same domain-agnostic Signal/PaperTrade pipeline.
 */
@RestController
@RequestMapping("/internal")
public class DashboardController {

    private static final int RECENT_CLOSED_PER_SERIES = 15;

    private final PaperTradeRepository paperTradeRepository;
    private final SignalRepository signalRepository;
    private final MarketRepository marketRepository;
    private final String expectedToken;

    public DashboardController(
            PaperTradeRepository paperTradeRepository,
            SignalRepository signalRepository,
            MarketRepository marketRepository,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.paperTradeRepository = paperTradeRepository;
        this.signalRepository = signalRepository;
        this.marketRepository = marketRepository;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestHeader(value = "X-Status-Token", required = false) String token) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<PaperTrade> open = paperTradeRepository.findByStatus(TradeStatus.OPEN);
        List<PaperTrade> closed = paperTradeRepository.findByStatus(TradeStatus.CLOSED);

        Set<String> marketIds = Stream.concat(open.stream(), closed.stream())
                .map(PaperTrade::getMarketId)
                .collect(Collectors.toSet());
        Map<String, Market> marketsById = marketRepository.findAllById(marketIds).stream()
                .collect(Collectors.toMap(Market::getId, m -> m));

        Set<UUID> signalIds = open.stream().map(PaperTrade::getSignalId).collect(Collectors.toSet());
        Map<UUID, Signal> signalsById = signalRepository.findAllById(signalIds).stream()
                .collect(Collectors.toMap(Signal::getId, s -> s));

        Map<String, Instant> lastIngestedBySeries = marketRepository.findLastUpdatedPerSeries().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Instant) row[1]));

        Map<String, List<PaperTrade>> openBySeries = groupBySeries(open, marketsById);
        Map<String, List<PaperTrade>> closedBySeries = groupBySeries(closed, marketsById);

        Set<String> allSeries = new TreeSet<>();
        allSeries.addAll(openBySeries.keySet());
        allSeries.addAll(closedBySeries.keySet());
        allSeries.addAll(lastIngestedBySeries.keySet());

        List<SeriesSummary> series = allSeries.stream()
                .map(seriesTicker -> buildSeriesSummary(
                        seriesTicker,
                        openBySeries.getOrDefault(seriesTicker, List.of()),
                        closedBySeries.getOrDefault(seriesTicker, List.of()),
                        marketsById,
                        signalsById,
                        lastIngestedBySeries.get(seriesTicker)))
                .toList();

        OverallStats overall = new OverallStats(open.size(), closed.size(), totalPnl(closed), winRate(closed));

        return ResponseEntity.ok(new DashboardResponse(Instant.now(), overall, series));
    }

    private Map<String, List<PaperTrade>> groupBySeries(List<PaperTrade> trades, Map<String, Market> marketsById) {
        return trades.stream()
                .filter(t -> marketsById.containsKey(t.getMarketId()))
                .collect(Collectors.groupingBy(t -> marketsById.get(t.getMarketId()).getSeriesTicker()));
    }

    private SeriesSummary buildSeriesSummary(
            String seriesTicker,
            List<PaperTrade> open,
            List<PaperTrade> closed,
            Map<String, Market> marketsById,
            Map<UUID, Signal> signalsById,
            Instant lastIngestedAt) {

        List<OpenPosition> openPositions = open.stream()
                .map(t -> toOpenPosition(t, marketsById.get(t.getMarketId()), signalsById.get(t.getSignalId())))
                .sorted(Comparator.comparing(OpenPosition::openedAt).reversed())
                .toList();

        List<ClosedTrade> recentClosed = closed.stream()
                .sorted(Comparator.comparing(PaperTrade::getClosedAt).reversed())
                .limit(RECENT_CLOSED_PER_SERIES)
                .map(this::toClosedTrade)
                .toList();

        return new SeriesSummary(
                seriesTicker,
                lastIngestedAt,
                open.size(),
                closed.size(),
                totalPnl(closed),
                winRate(closed),
                openPositions,
                recentClosed);
    }

    private OpenPosition toOpenPosition(PaperTrade trade, Market market, Signal signal) {
        return new OpenPosition(
                trade.getMarketId(),
                trade.getSide().name(),
                trade.getEntryPrice(),
                trade.getQuantity(),
                trade.getOpenedAt(),
                signal == null ? null : signal.getEdgePercent(),
                signal == null ? null : signal.getNetEdgePercent(),
                market == null ? null : market.getFloorStrike(),
                market == null ? null : market.getCapStrike(),
                market == null || market.getStrikeType() == null ? null : market.getStrikeType().name(),
                market == null ? null : market.getOccurrenceDate());
    }

    private ClosedTrade toClosedTrade(PaperTrade trade) {
        return new ClosedTrade(
                trade.getMarketId(),
                trade.getSide().name(),
                trade.getEntryPrice(),
                trade.getExitPrice(),
                trade.getPnl(),
                trade.getClosedAt());
    }

    private BigDecimal totalPnl(List<PaperTrade> closed) {
        return closed.stream()
                .map(PaperTrade::getPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Fraction in [0,1], null when there's no closed history yet to compute a rate from. */
    private BigDecimal winRate(List<PaperTrade> closed) {
        if (closed.isEmpty()) {
            return null;
        }
        long wins = closed.stream()
                .filter(t -> t.getPnl() != null && t.getPnl().signum() > 0)
                .count();
        return BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closed.size()), 4, RoundingMode.HALF_UP);
    }

    public record DashboardResponse(Instant generatedAt, OverallStats overall, List<SeriesSummary> series) {
    }

    public record OverallStats(int openCount, int closedCount, BigDecimal totalPnl, BigDecimal winRate) {
    }

    public record SeriesSummary(
            String seriesTicker,
            Instant lastIngestedAt,
            int openCount,
            int closedCount,
            BigDecimal totalPnl,
            BigDecimal winRate,
            List<OpenPosition> openPositions,
            List<ClosedTrade> recentClosedTrades) {
    }

    public record OpenPosition(
            String marketId,
            String side,
            BigDecimal entryPrice,
            Integer quantity,
            Instant openedAt,
            BigDecimal edgePercent,
            BigDecimal netEdgePercent,
            BigDecimal floorStrike,
            BigDecimal capStrike,
            String strikeType,
            LocalDate occurrenceDate) {
    }

    public record ClosedTrade(
            String marketId,
            String side,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal pnl,
            Instant closedAt) {
    }
}
