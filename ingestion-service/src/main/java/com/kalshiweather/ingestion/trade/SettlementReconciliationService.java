package com.kalshiweather.ingestion.trade;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.enums.MarketResult;
import com.kalshiweather.ingestion.domain.enums.MarketStatus;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Periodically checks every open paper trade's market for settlement, and closes the trade
 * (computing pnl per Kalshi's documented fee formula) once it has. Runs on its own, slower
 * schedule than the 15-minute ingestion cycle — settlement doesn't need that granularity, and
 * this is what actually makes signal history scoreable instead of just accumulating.
 */
@Component
public class SettlementReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(SettlementReconciliationService.class);

    /** Kalshi taker fee: round_up(0.07 * C * P * (1-P)) — same formula as SignalGenerationService
     * estimates pre-trade, applied here as the actual booked fee at settlement. */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.07");

    private static final int MONEY_SCALE = 4; // matches entry_price/exit_price/pnl column scale

    private final PaperTradeRepository paperTradeRepository;
    private final MarketRepository marketRepository;
    private final KalshiClient kalshiClient;

    public SettlementReconciliationService(
            PaperTradeRepository paperTradeRepository,
            MarketRepository marketRepository,
            KalshiClient kalshiClient
    ) {
        this.paperTradeRepository = paperTradeRepository;
        this.marketRepository = marketRepository;
        this.kalshiClient = kalshiClient;
    }

    @Scheduled(
            initialDelayString = "${ingestion.schedule.initial-delay-ms:5000}",
            fixedDelayString = "${ingestion.schedule.settlement-check-delay-ms:3600000}"
    )
    public void reconcileSettlements() {
        List<PaperTrade> openTrades = paperTradeRepository.findByStatus(TradeStatus.OPEN);
        for (PaperTrade trade : openTrades) {
            try {
                reconcile(trade);
            } catch (Exception e) {
                log.error("Settlement check failed for paper trade {} (market {}): {}",
                        trade.getId(), trade.getMarketId(), e.getMessage(), e);
            }
        }
    }

    private void reconcile(PaperTrade trade) {
        Optional<Market> storedMarket = marketRepository.findById(trade.getMarketId());
        if (storedMarket.isEmpty()) {
            log.warn("No stored market for paper trade {} (market {})", trade.getId(), trade.getMarketId());
            return;
        }

        Market fetched = kalshiClient.fetchMarket(trade.getMarketId(), storedMarket.get().getSeriesTicker());
        if (fetched.getStatus() != MarketStatus.RESOLVED || fetched.getResult() == null) {
            return; // not settled yet — check again next cycle
        }

        marketRepository.save(fetched); // upsert by natural key, same pattern as ingestion's saveAll
        closeTrade(trade, fetched.getResult());
    }

    private void closeTrade(PaperTrade trade, MarketResult result) {
        boolean won = (trade.getSide() == SignalDirection.BUY_YES) == (result == MarketResult.YES);
        BigDecimal exitPrice = (won ? BigDecimal.ONE : BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);

        BigDecimal quantity = BigDecimal.valueOf(trade.getQuantity());
        BigDecimal fee = FEE_RATE
                .multiply(quantity)
                .multiply(trade.getEntryPrice())
                .multiply(BigDecimal.ONE.subtract(trade.getEntryPrice()))
                .setScale(MONEY_SCALE, RoundingMode.UP);

        BigDecimal pnl = exitPrice.subtract(trade.getEntryPrice())
                .multiply(quantity)
                .subtract(fee)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        trade.setExitPrice(exitPrice);
        trade.setPnl(pnl);
        trade.setClosedAt(Instant.now());
        trade.setStatus(TradeStatus.CLOSED);
        paperTradeRepository.save(trade);

        log.info("Closed paper trade {}: {} {} result={} exitPrice={} pnl={}",
                trade.getId(), trade.getSide(), trade.getMarketId(), result, exitPrice, pnl);
    }
}
