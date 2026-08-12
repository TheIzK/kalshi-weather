package com.kalshiweather.ingestion.trade;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.SignalStatus;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Opens a {@link PaperTrade} the first time a market gets a qualifying signal, so signal
 * history becomes scoreable later against real settlement outcomes instead of just
 * accumulating. Sized at a fixed 1 contract — see the plan's "future considerations" for why
 * configurable sizing isn't built yet.
 */
@Service
public class PaperTradeService {

    private static final Logger log = LoggerFactory.getLogger(PaperTradeService.class);

    private static final int FIXED_QUANTITY = 1;

    private final PaperTradeRepository paperTradeRepository;
    private final SignalRepository signalRepository;

    public PaperTradeService(PaperTradeRepository paperTradeRepository, SignalRepository signalRepository) {
        this.paperTradeRepository = paperTradeRepository;
        this.signalRepository = signalRepository;
    }

    /** Opens a paper trade against a freshly-created signal and marks it acted on. */
    @Transactional
    public void open(Signal signal, Market market) {
        PaperTrade trade = new PaperTrade();
        trade.setSignalId(signal.getId());
        trade.setMarketId(market.getId());
        trade.setSide(signal.getDirection());
        trade.setEntryPrice(entryPrice(signal.getDirection(), market));
        trade.setQuantity(FIXED_QUANTITY);
        trade.setOpenedAt(Instant.now());
        trade.setStatus(TradeStatus.OPEN);
        paperTradeRepository.save(trade);

        signal.setStatus(SignalStatus.ACTED_ON);
        signalRepository.save(signal);

        log.info("Opened paper trade: {} {} @ {}", signal.getDirection(), market.getId(), trade.getEntryPrice());
    }

    private BigDecimal entryPrice(SignalDirection direction, Market market) {
        return direction == SignalDirection.BUY_YES ? market.getYesAsk() : market.getNoAsk();
    }
}
