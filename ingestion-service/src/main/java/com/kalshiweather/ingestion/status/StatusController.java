package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * A minimal, token-gated read endpoint so an external checker (a scheduled cloud routine) can
 * poll paper-trade activity without SSH/local access — Postgres itself stays loopback-only per
 * the ops doc's security decisions. Not meant to grow into a real API; that's api-service's job
 * per the design doc's build order.
 */
@RestController
@RequestMapping("/internal")
public class StatusController {

    private final PaperTradeRepository paperTradeRepository;
    private final String expectedToken;

    /** Empty default so local dev (no token configured) boots fine — the endpoint just always
     * 401s locally, which is fine since nothing local calls it. */
    public StatusController(
            PaperTradeRepository paperTradeRepository,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.paperTradeRepository = paperTradeRepository;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(
            @RequestHeader(value = "X-Status-Token", required = false) String token) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<PaperTrade> open = paperTradeRepository.findByStatus(TradeStatus.OPEN);
        List<PaperTrade> closed = paperTradeRepository.findByStatus(TradeStatus.CLOSED);

        TradeSummary latestOpen = open.stream()
                .max(Comparator.comparing(PaperTrade::getOpenedAt))
                .map(t -> new TradeSummary(t.getMarketId(), t.getSide().name(), t.getEntryPrice(), null, t.getOpenedAt()))
                .orElse(null);

        TradeSummary latestClosed = closed.stream()
                .max(Comparator.comparing(PaperTrade::getClosedAt))
                .map(t -> new TradeSummary(t.getMarketId(), t.getSide().name(), t.getExitPrice(), t.getPnl(), t.getClosedAt()))
                .orElse(null);

        return ResponseEntity.ok(new StatusResponse(open.size(), closed.size(), latestOpen, latestClosed));
    }

    public record StatusResponse(
            int openTradeCount,
            int closedTradeCount,
            TradeSummary latestOpen,
            TradeSummary latestClosed
    ) {
    }

    public record TradeSummary(
            String marketId,
            String side,
            BigDecimal price,
            BigDecimal pnl,
            Instant at
    ) {
    }
}
