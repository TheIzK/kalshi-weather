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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementReconciliationServiceTest {

    @Mock
    private PaperTradeRepository paperTradeRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private KalshiClient kalshiClient;

    private SettlementReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new SettlementReconciliationService(paperTradeRepository, marketRepository, kalshiClient);
    }

    private PaperTrade openTrade(String marketId, SignalDirection side, String entryPrice) {
        PaperTrade trade = new PaperTrade();
        trade.setId(UUID.randomUUID());
        trade.setMarketId(marketId);
        trade.setSide(side);
        trade.setEntryPrice(new BigDecimal(entryPrice));
        trade.setQuantity(1);
        trade.setStatus(TradeStatus.OPEN);
        return trade;
    }

    private Market storedMarket(String id) {
        Market market = new Market();
        market.setId(id);
        market.setSeriesTicker("KXHIGHNY");
        market.setStatus(MarketStatus.ACTIVE);
        return market;
    }

    private Market resolvedMarket(String id, MarketResult result) {
        Market market = storedMarket(id);
        market.setStatus(MarketStatus.RESOLVED);
        market.setResult(result);
        return market;
    }

    /** entry=0.60, wins (YES) -> exitPrice=1.00; fee=round_up(0.07*0.60*0.40)=0.0168; pnl=0.40-0.0168=0.3832 */
    @Test
    void closesWinningTradeWithFeeAdjustedPnl() {
        PaperTrade trade = openTrade("KXHIGHNY-TEST", SignalDirection.BUY_YES, "0.60");
        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of(trade));
        when(marketRepository.findById("KXHIGHNY-TEST")).thenReturn(Optional.of(storedMarket("KXHIGHNY-TEST")));
        when(kalshiClient.fetchMarket("KXHIGHNY-TEST", "KXHIGHNY"))
                .thenReturn(resolvedMarket("KXHIGHNY-TEST", MarketResult.YES));

        service.reconcileSettlements();

        ArgumentCaptor<PaperTrade> captor = ArgumentCaptor.forClass(PaperTrade.class);
        verify(paperTradeRepository).save(captor.capture());
        PaperTrade closed = captor.getValue();
        assertThat(closed.getStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(closed.getExitPrice()).isEqualByComparingTo("1.0000");
        assertThat(closed.getPnl()).isEqualByComparingTo("0.3832");
        assertThat(closed.getClosedAt()).isNotNull();
        verify(marketRepository).save(any(Market.class));
    }

    /** entry=0.60, loses (NO) -> exitPrice=0.00; fee still 0.0168; pnl=-0.60-0.0168=-0.6168 */
    @Test
    void closesLosingTradeWithFeeAdjustedPnl() {
        PaperTrade trade = openTrade("KXHIGHNY-TEST", SignalDirection.BUY_YES, "0.60");
        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of(trade));
        when(marketRepository.findById("KXHIGHNY-TEST")).thenReturn(Optional.of(storedMarket("KXHIGHNY-TEST")));
        when(kalshiClient.fetchMarket("KXHIGHNY-TEST", "KXHIGHNY"))
                .thenReturn(resolvedMarket("KXHIGHNY-TEST", MarketResult.NO));

        service.reconcileSettlements();

        ArgumentCaptor<PaperTrade> captor = ArgumentCaptor.forClass(PaperTrade.class);
        verify(paperTradeRepository).save(captor.capture());
        assertThat(captor.getValue().getExitPrice()).isEqualByComparingTo("0.0000");
        assertThat(captor.getValue().getPnl()).isEqualByComparingTo("-0.6168");
    }

    @Test
    void leavesTradeOpenWhenMarketNotYetSettled() {
        PaperTrade trade = openTrade("KXHIGHNY-TEST", SignalDirection.BUY_YES, "0.60");
        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of(trade));
        when(marketRepository.findById("KXHIGHNY-TEST")).thenReturn(Optional.of(storedMarket("KXHIGHNY-TEST")));
        when(kalshiClient.fetchMarket("KXHIGHNY-TEST", "KXHIGHNY")).thenReturn(storedMarket("KXHIGHNY-TEST")); // still ACTIVE

        service.reconcileSettlements();

        verify(paperTradeRepository, never()).save(any());
        verify(marketRepository, never()).save(any());
    }

    @Test
    void oneTradeFailingDoesNotStopReconciliationOfTheRest() {
        PaperTrade failing = openTrade("KXHIGHNY-BAD", SignalDirection.BUY_YES, "0.60");
        PaperTrade healthy = openTrade("KXHIGHNY-GOOD", SignalDirection.BUY_YES, "0.60");
        when(paperTradeRepository.findByStatus(TradeStatus.OPEN)).thenReturn(List.of(failing, healthy));
        when(marketRepository.findById("KXHIGHNY-BAD")).thenThrow(new RuntimeException("db blip"));
        when(marketRepository.findById("KXHIGHNY-GOOD")).thenReturn(Optional.of(storedMarket("KXHIGHNY-GOOD")));
        when(kalshiClient.fetchMarket("KXHIGHNY-GOOD", "KXHIGHNY"))
                .thenReturn(resolvedMarket("KXHIGHNY-GOOD", MarketResult.YES));

        service.reconcileSettlements();

        ArgumentCaptor<PaperTrade> captor = ArgumentCaptor.forClass(PaperTrade.class);
        verify(paperTradeRepository).save(captor.capture());
        assertThat(captor.getValue().getMarketId()).isEqualTo("KXHIGHNY-GOOD");
    }
}
