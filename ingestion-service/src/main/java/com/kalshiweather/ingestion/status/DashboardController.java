package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.entity.SubjectStation;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import com.kalshiweather.ingestion.repository.SubjectStationRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Richer, token-gated read endpoint for an external dashboard (isaacmelton.dev/kalshi) —
 * open positions and full closed-trade history grouped by domain (weather vs. each sport)
 * and then by {@code series_ticker} within it, with per-series/per-domain/overall P&amp;L and
 * win-rate stats, and a last-ingested-at health signal per series. Same auth pattern as
 * {@link StatusController}, which stays as-is for its existing lightweight consumer.
 *
 * <p>Domain is weather iff the series is in {@code subject_stations} (the authoritative,
 * always-populated source of truth for "is this a weather subject" — reliable even for a
 * series that hasn't produced a single signal yet). Everything else is a sport, named from
 * {@link Signal#getSourceType()} — the token before its first {@code _} (e.g.
 * {@code "MLB_WIN_PROBABILITY"} → {@code "MLB"}) — falling back to the raw series ticker only
 * in the on-paper-only case where a brand-new sport has ingested markets but has never yet
 * produced a qualifying signal to name itself from. This is generic on purpose — a future
 * NFL/CFB series buckets and names itself correctly with zero further backend changes,
 * consistent with how this codebase already treats every sport as a domain-agnostic instance
 * of the same Signal/PaperTrade pipeline.
 */
@RestController
@RequestMapping("/internal")
public class DashboardController {

    /** Generous safety cap, not a real limit — the whole system has ~45 closed trades ever
     * as of writing, so this only guards against unbounded growth much later. */
    private static final int MAX_CLOSED_TRADES_PER_SERIES = 500;

    private static final String WEATHER_DOMAIN = "WEATHER";

    private final PaperTradeRepository paperTradeRepository;
    private final SignalRepository signalRepository;
    private final MarketRepository marketRepository;
    private final SubjectStationRepository subjectStationRepository;
    private final String expectedToken;

    public DashboardController(
            PaperTradeRepository paperTradeRepository,
            SignalRepository signalRepository,
            MarketRepository marketRepository,
            SubjectStationRepository subjectStationRepository,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.paperTradeRepository = paperTradeRepository;
        this.signalRepository = signalRepository;
        this.marketRepository = marketRepository;
        this.subjectStationRepository = subjectStationRepository;
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
        List<PaperTrade> all = Stream.concat(open.stream(), closed.stream()).toList();

        Set<String> marketIds = all.stream().map(PaperTrade::getMarketId).collect(Collectors.toSet());
        Map<String, Market> marketsById = marketRepository.findAllById(marketIds).stream()
                .collect(Collectors.toMap(Market::getId, m -> m));

        Set<UUID> signalIds = all.stream().map(PaperTrade::getSignalId).collect(Collectors.toSet());
        Map<UUID, Signal> signalsById = signalRepository.findAllById(signalIds).stream()
                .collect(Collectors.toMap(Signal::getId, s -> s));

        Map<String, Instant> lastIngestedBySeries = marketRepository.findLastUpdatedPerSeries().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Instant) row[1]));

        Set<String> weatherSeriesTickers = subjectStationRepository.findAll().stream()
                .map(SubjectStation::getSeriesTicker)
                .collect(Collectors.toSet());

        Map<String, List<PaperTrade>> openBySeries = groupBySeries(open, marketsById);
        Map<String, List<PaperTrade>> closedBySeries = groupBySeries(closed, marketsById);

        Set<String> allSeries = new TreeSet<>();
        allSeries.addAll(openBySeries.keySet());
        allSeries.addAll(closedBySeries.keySet());
        allSeries.addAll(lastIngestedBySeries.keySet());

        Map<String, List<String>> seriesByDomain = new TreeMap<>();
        for (String seriesTicker : allSeries) {
            String domain = domainFor(seriesTicker, weatherSeriesTickers, openBySeries, closedBySeries, signalsById);
            seriesByDomain.computeIfAbsent(domain, d -> new ArrayList<>()).add(seriesTicker);
        }

        List<DomainSummary> domains = seriesByDomain.entrySet().stream()
                .map(entry -> buildDomainSummary(
                        entry.getKey(),
                        entry.getValue(),
                        openBySeries,
                        closedBySeries,
                        marketsById,
                        signalsById,
                        lastIngestedBySeries))
                .toList();

        OverallStats overall = new OverallStats(open.size(), closed.size(), totalPnl(closed), winRate(closed));

        return ResponseEntity.ok(new DashboardResponse(Instant.now(), overall, domains));
    }

    private String domainFor(
            String seriesTicker,
            Set<String> weatherSeriesTickers,
            Map<String, List<PaperTrade>> openBySeries,
            Map<String, List<PaperTrade>> closedBySeries,
            Map<UUID, Signal> signalsById) {

        if (weatherSeriesTickers.contains(seriesTicker)) {
            return WEATHER_DOMAIN;
        }

        Stream<PaperTrade> seriesTrades = Stream.concat(
                openBySeries.getOrDefault(seriesTicker, List.of()).stream(),
                closedBySeries.getOrDefault(seriesTicker, List.of()).stream());

        return seriesTrades
                .map(t -> signalsById.get(t.getSignalId()))
                .filter(Objects::nonNull)
                .map(Signal::getSourceType)
                .filter(Objects::nonNull)
                .findFirst()
                .map(sourceType -> sourceType.split("_", 2)[0])
                // Not classifiable yet (a sport series that has ingested markets but never
                // produced a qualifying signal) — show its own ticker rather than mislabel it.
                .orElse(seriesTicker);
    }

    private Map<String, List<PaperTrade>> groupBySeries(List<PaperTrade> trades, Map<String, Market> marketsById) {
        return trades.stream()
                .filter(t -> marketsById.containsKey(t.getMarketId()))
                .collect(Collectors.groupingBy(t -> marketsById.get(t.getMarketId()).getSeriesTicker()));
    }

    private DomainSummary buildDomainSummary(
            String domain,
            List<String> seriesTickers,
            Map<String, List<PaperTrade>> openBySeries,
            Map<String, List<PaperTrade>> closedBySeries,
            Map<String, Market> marketsById,
            Map<UUID, Signal> signalsById,
            Map<String, Instant> lastIngestedBySeries) {

        List<SeriesSummary> series = seriesTickers.stream()
                .map(seriesTicker -> buildSeriesSummary(
                        seriesTicker,
                        openBySeries.getOrDefault(seriesTicker, List.of()),
                        closedBySeries.getOrDefault(seriesTicker, List.of()),
                        marketsById,
                        signalsById,
                        lastIngestedBySeries.get(seriesTicker)))
                .toList();

        List<PaperTrade> domainOpen = seriesTickers.stream()
                .flatMap(s -> openBySeries.getOrDefault(s, List.of()).stream())
                .toList();
        List<PaperTrade> domainClosed = seriesTickers.stream()
                .flatMap(s -> closedBySeries.getOrDefault(s, List.of()).stream())
                .toList();

        OverallStats stats = new OverallStats(
                domainOpen.size(), domainClosed.size(), totalPnl(domainClosed), winRate(domainClosed));

        return new DomainSummary(domain, stats, series);
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

        List<ClosedTrade> closedTrades = closed.stream()
                .sorted(Comparator.comparing(PaperTrade::getClosedAt).reversed())
                .limit(MAX_CLOSED_TRADES_PER_SERIES)
                .map(t -> toClosedTrade(t, signalsById.get(t.getSignalId())))
                .toList();

        return new SeriesSummary(
                seriesTicker,
                lastIngestedAt,
                open.size(),
                closed.size(),
                totalPnl(closed),
                winRate(closed),
                openPositions,
                closedTrades);
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

    private ClosedTrade toClosedTrade(PaperTrade trade, Signal signal) {
        return new ClosedTrade(
                trade.getMarketId(),
                trade.getSide().name(),
                trade.getEntryPrice(),
                trade.getExitPrice(),
                trade.getPnl(),
                trade.getClosedAt(),
                signal == null ? null : signal.getEdgePercent(),
                signal == null ? null : signal.getNetEdgePercent());
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

    public record DashboardResponse(Instant generatedAt, OverallStats overall, List<DomainSummary> domains) {
    }

    public record OverallStats(int openCount, int closedCount, BigDecimal totalPnl, BigDecimal winRate) {
    }

    public record DomainSummary(String domain, OverallStats stats, List<SeriesSummary> series) {
    }

    public record SeriesSummary(
            String seriesTicker,
            Instant lastIngestedAt,
            int openCount,
            int closedCount,
            BigDecimal totalPnl,
            BigDecimal winRate,
            List<OpenPosition> openPositions,
            List<ClosedTrade> closedTrades) {
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
            Instant closedAt,
            BigDecimal edgePercent,
            BigDecimal netEdgePercent) {
    }
}
