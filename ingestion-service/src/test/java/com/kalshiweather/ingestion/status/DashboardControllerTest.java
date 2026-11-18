package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.entity.SubjectStation;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import com.kalshiweather.ingestion.repository.SubjectStationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private PaperTradeRepository paperTradeRepository;
    @Mock
    private SignalRepository signalRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private SubjectStationRepository subjectStationRepository;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(paperTradeRepository, signalRepository, marketRepository, subjectStationRepository, "correct-token");

        SubjectStation nyc = new SubjectStation();
        nyc.setSubjectKey("NYC");
        nyc.setSeriesTicker("KXHIGHNY");
        lenient().when(subjectStationRepository.findAll()).thenReturn(List.of(nyc));
        lenient().when(marketRepository.findLastUpdatedPerSeries()).thenReturn(List.of());
    }

    private Market market(String id) {
        Market market = new Market();
        market.setId(id);
        market.setSeriesTicker("KXHIGHNY");
        return market;
    }

    private Signal signal(UUID id) {
        Signal signal = new Signal();
        signal.setId(id);
        signal.setEdgePercent(new BigDecimal("10.000"));
        signal.setNetEdgePercent(new BigDecimal("7.000"));
        return signal;
    }

    private PaperTrade closedTrade(String marketId, UUID signalId, Instant closedAt, BigDecimal pnl) {
        PaperTrade trade = new PaperTrade();
        trade.setMarketId(marketId);
        trade.setSignalId(signalId);
        trade.setSide(SignalDirection.BUY_YES);
        trade.setEntryPrice(new BigDecimal("0.60"));
        trade.setExitPrice(pnl.signum() > 0 ? BigDecimal.ONE : BigDecimal.ZERO);
        trade.setStatus(TradeStatus.CLOSED);
        trade.setOpenedAt(closedAt.minus(1, ChronoUnit.HOURS));
        trade.setClosedAt(closedAt);
        trade.setPnl(pnl);
        return trade;
    }

    @Test
    void rejectsMissingOrWrongToken() {
        assertThat(controller.dashboard(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.dashboard("wrong-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The core behavior this test guards: the dashboard's displayed closedTrades list is
     * windowed to the last 30 days (otherwise it grows unbounded forever — 1,290 closed
     * trades and counting as of 2026-11-18, all shipped on every page load back to the first
     * trade in August), but every summary stat (closedCount, totalPnl, winRate) at every
     * level — series, domain, and overall — must still reflect ALL-time history, not just
     * what's inside the display window. A regression here would either silently drop real
     * P&L from the headline numbers, or let the trade list grow unbounded again.
     */
    @Test
    void closedTradesWindowedTo30DaysButStatsReflectFullHistory() {
        Instant now = Instant.now();
        UUID oldSignalId = UUID.randomUUID();
        UUID recentSignalId = UUID.randomUUID();

        PaperTrade oldTrade = closedTrade("KXHIGHNY-OLD", oldSignalId, now.minus(40, ChronoUnit.DAYS), new BigDecimal("1.00"));
        PaperTrade recentTrade = closedTrade("KXHIGHNY-NEW", recentSignalId, now.minus(5, ChronoUnit.DAYS), new BigDecimal("-0.50"));

        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of());
        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(oldTrade, recentTrade));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("KXHIGHNY-OLD"), market("KXHIGHNY-NEW")));
        when(signalRepository.findAllById(any())).thenReturn(List.of(signal(oldSignalId), signal(recentSignalId)));

        ResponseEntity<DashboardController.DashboardResponse> response = controller.dashboard("correct-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.DashboardResponse body = response.getBody();

        // overall and domain stats: full history, both trades counted
        assertThat(body.overall().closedCount()).isEqualTo(2);
        assertThat(body.overall().totalPnl()).isEqualByComparingTo("0.50");
        assertThat(body.overall().winRate()).isEqualByComparingTo("0.5000");

        DashboardController.DomainSummary weatherDomain = body.domains().stream()
                .filter(d -> d.domain().equals("WEATHER"))
                .findFirst().orElseThrow();
        assertThat(weatherDomain.stats().closedCount()).isEqualTo(2);
        assertThat(weatherDomain.stats().totalPnl()).isEqualByComparingTo("0.50");

        DashboardController.SeriesSummary series = weatherDomain.series().get(0);
        assertThat(series.closedCount()).isEqualTo(2); // all-time, unwindowed
        assertThat(series.totalPnl()).isEqualByComparingTo("0.50"); // all-time, unwindowed
        assertThat(series.winRate()).isEqualByComparingTo("0.5000"); // all-time, unwindowed

        // but the displayed list only contains the trade inside the 30-day window
        assertThat(series.closedTrades()).hasSize(1);
        assertThat(series.closedTrades().get(0).marketId()).isEqualTo("KXHIGHNY-NEW");
    }

    @Test
    void returnsEmptyClosedTradesListWhenAllHistoryIsOutsideTheWindow() {
        Instant now = Instant.now();
        UUID signalId = UUID.randomUUID();
        PaperTrade oldTrade = closedTrade("KXHIGHNY-OLD", signalId, now.minus(90, ChronoUnit.DAYS), new BigDecimal("0.25"));

        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of());
        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(oldTrade));
        when(marketRepository.findAllById(any())).thenReturn(List.of(market("KXHIGHNY-OLD")));
        when(signalRepository.findAllById(any())).thenReturn(List.of(signal(signalId)));

        ResponseEntity<DashboardController.DashboardResponse> response = controller.dashboard("correct-token");

        DashboardController.SeriesSummary series = response.getBody().domains().get(0).series().get(0);
        assertThat(series.closedTrades()).isEmpty();
        assertThat(series.closedCount()).isEqualTo(1); // still counted in the all-time total
        assertThat(series.totalPnl()).isEqualByComparingTo("0.25");
    }
}
