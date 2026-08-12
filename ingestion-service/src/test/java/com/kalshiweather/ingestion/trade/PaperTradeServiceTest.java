package com.kalshiweather.ingestion.trade;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.SignalStatus;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaperTradeServiceTest {

    @Mock
    private PaperTradeRepository paperTradeRepository;
    @Mock
    private SignalRepository signalRepository;

    private PaperTradeService service;

    @BeforeEach
    void setUp() {
        service = new PaperTradeService(paperTradeRepository, signalRepository);
    }

    private Market market() {
        Market market = new Market();
        market.setId("KXHIGHNY-TEST");
        market.setYesBid(new BigDecimal("0.55"));
        market.setYesAsk(new BigDecimal("0.60"));
        market.setNoBid(new BigDecimal("0.40"));
        market.setNoAsk(new BigDecimal("0.45"));
        return market;
    }

    private Signal signal(SignalDirection direction) {
        Signal signal = new Signal();
        signal.setId(UUID.randomUUID());
        signal.setMarketId("KXHIGHNY-TEST");
        signal.setDirection(direction);
        signal.setStatus(SignalStatus.ACTIVE);
        return signal;
    }

    @Test
    void opensTradeAtYesAskForBuyYes() {
        Signal signal = signal(SignalDirection.BUY_YES);

        service.open(signal, market());

        ArgumentCaptor<PaperTrade> captor = ArgumentCaptor.forClass(PaperTrade.class);
        verify(paperTradeRepository).save(captor.capture());
        PaperTrade trade = captor.getValue();
        assertThat(trade.getEntryPrice()).isEqualByComparingTo("0.60");
        assertThat(trade.getSide()).isEqualTo(SignalDirection.BUY_YES);
        assertThat(trade.getQuantity()).isEqualTo(1);
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.OPEN);
        assertThat(trade.getOpenedAt()).isNotNull();
        assertThat(trade.getSignalId()).isEqualTo(signal.getId());
        assertThat(trade.getMarketId()).isEqualTo("KXHIGHNY-TEST");
    }

    @Test
    void opensTradeAtNoAskForBuyNo() {
        service.open(signal(SignalDirection.BUY_NO), market());

        ArgumentCaptor<PaperTrade> captor = ArgumentCaptor.forClass(PaperTrade.class);
        verify(paperTradeRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryPrice()).isEqualByComparingTo("0.45");
    }

    @Test
    void marksSignalActedOn() {
        Signal signal = signal(SignalDirection.BUY_YES);

        service.open(signal, market());

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.ACTED_ON);
        verify(signalRepository).save(signal);
    }
}
