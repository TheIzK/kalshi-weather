package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock
    private PaperTradeRepository paperTradeRepository;

    private PaperTrade trade(String marketId, TradeStatus status, Instant openedAt, Instant closedAt, BigDecimal pnl) {
        PaperTrade trade = new PaperTrade();
        trade.setMarketId(marketId);
        trade.setSide(SignalDirection.BUY_YES);
        trade.setEntryPrice(new BigDecimal("0.60"));
        trade.setExitPrice(status == TradeStatus.CLOSED ? BigDecimal.ONE : null);
        trade.setStatus(status);
        trade.setOpenedAt(openedAt);
        trade.setClosedAt(closedAt);
        trade.setPnl(pnl);
        return trade;
    }

    @Test
    void rejectsMissingOrWrongToken() {
        StatusController controller = new StatusController(paperTradeRepository, "correct-token");

        assertThat(controller.status(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.status("wrong-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsAnyTokenWhenNoneConfigured() {
        StatusController controller = new StatusController(paperTradeRepository, "");

        assertThat(controller.status("anything").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsCountsAndMostRecentTradeOnEachSide() {
        Instant now = Instant.now();
        PaperTrade olderOpen = trade("KXHIGHNY-OLD", TradeStatus.OPEN, now.minus(1, ChronoUnit.HOURS), null, null);
        PaperTrade newerOpen = trade("KXHIGHNY-NEW", TradeStatus.OPEN, now, null, null);
        PaperTrade closedTrade = trade("KXHIGHNY-DONE", TradeStatus.CLOSED, now.minus(2, ChronoUnit.HOURS), now, new BigDecimal("0.3832"));

        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of(olderOpen, newerOpen));
        when(paperTradeRepository.findByStatus(TradeStatus.CLOSED)).thenReturn(List.of(closedTrade));

        StatusController controller = new StatusController(paperTradeRepository, "correct-token");
        ResponseEntity<StatusController.StatusResponse> response = controller.status("correct-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatusController.StatusResponse body = response.getBody();
        assertThat(body.openTradeCount()).isEqualTo(2);
        assertThat(body.closedTradeCount()).isEqualTo(1);
        assertThat(body.latestOpen().marketId()).isEqualTo("KXHIGHNY-NEW"); // most recently opened
        assertThat(body.latestClosed().marketId()).isEqualTo("KXHIGHNY-DONE");
        assertThat(body.latestClosed().pnl()).isEqualByComparingTo("0.3832");
    }
}
