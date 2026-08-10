package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.domain.enums.ThresholdMode;
import com.kalshiweather.ingestion.repository.SignalConfigRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The live integration test can't exercise this logic on its own: real Kalshi markets right
 * now all happen to have zero order-book liquidity, so the liquidity gate short-circuits
 * before the fee/edge/threshold math ever runs. This covers that math directly with a mocked
 * SignalProvider and SignalConfig, no network or DB involved.
 */
@ExtendWith(MockitoExtension.class)
class SignalGenerationServiceTest {

    @Mock
    private SignalProvider signalProvider;
    @Mock
    private SignalConfigRepository signalConfigRepository;
    @Mock
    private SignalRepository signalRepository;

    private SignalGenerationService service;
    private EnsembleForecast forecast;

    @BeforeEach
    void setUp() {
        service = new SignalGenerationService(signalProvider, signalConfigRepository, signalRepository);

        forecast = new EnsembleForecast();
        forecast.setId(UUID.randomUUID());
        forecast.setMemberValuesF(new BigDecimal[100]); // only .length is used outside CONFIDENCE_ADJUSTED math

        lenient().when(signalRepository.save(any(Signal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** yesBid=0.55, yesAsk=0.60 -> implied 0.575; model=0.65 -> edge=7.5%, fee=7%*(1-0.60)=2.8%, net=4.7%. */
    private Market marketWithSpread() {
        Market market = new Market();
        market.setId("KXHIGHNY-TEST");
        market.setStrikeType(StrikeType.GREATER);
        market.setFloorStrike(new BigDecimal("80"));
        market.setYesBid(new BigDecimal("0.55"));
        market.setYesAsk(new BigDecimal("0.60"));
        market.setNoBid(new BigDecimal("0.40"));
        market.setNoAsk(new BigDecimal("0.45"));
        market.setLiquidityDollars(new BigDecimal("100.00"));
        return market;
    }

    @Test
    void feeAdjusted_qualifiesWhenNetEdgeClearsThreshold() {
        when(signalProvider.computeProbability(any(), any())).thenReturn(new BigDecimal("0.65"));
        when(signalConfigRepository.findAll()).thenReturn(List.of(
                config(ThresholdMode.FEE_ADJUSTED, null, new BigDecimal("3.000"), null)));

        Optional<Signal> result = service.evaluate(marketWithSpread(), forecast);

        assertThat(result).isPresent();
        Signal signal = result.get();
        assertThat(signal.getDirection()).isEqualTo(SignalDirection.BUY_YES);
        assertThat(signal.getModelProbability()).isEqualByComparingTo("0.65000");
        assertThat(signal.getMarketImpliedProbability()).isEqualByComparingTo("0.57500");
        assertThat(signal.getEdgePercent()).isEqualByComparingTo("7.500");
        assertThat(signal.getNetEdgePercent()).isEqualByComparingTo("4.700");
        assertThat(signal.getForecastId()).isEqualTo(forecast.getId());
        verify(signalRepository).save(any(Signal.class));
    }

    @Test
    void feeAdjusted_rejectsWhenNetEdgeMissesThreshold() {
        when(signalProvider.computeProbability(any(), any())).thenReturn(new BigDecimal("0.65"));
        when(signalConfigRepository.findAll()).thenReturn(List.of(
                config(ThresholdMode.FEE_ADJUSTED, null, new BigDecimal("5.000"), null))); // net edge is 4.7%

        Optional<Signal> result = service.evaluate(marketWithSpread(), forecast);

        assertThat(result).isEmpty();
        verify(signalRepository, never()).save(any());
    }

    @Test
    void flatPercent_usesRawEdgeNotFeeAdjustedEdge() {
        when(signalProvider.computeProbability(any(), any())).thenReturn(new BigDecimal("0.65"));
        // raw edge (7.5%) clears this, even though it's above the *net* edge (4.7%)
        when(signalConfigRepository.findAll()).thenReturn(List.of(
                config(ThresholdMode.FLAT_PERCENT, new BigDecimal("5.000"), null, null)));

        Optional<Signal> result = service.evaluate(marketWithSpread(), forecast);

        assertThat(result).isPresent();
    }

    @Test
    void buysNoWhenMarketOverpricesYes() {
        // model thinks YES is much less likely than the market implies -> should favor BUY_NO
        when(signalProvider.computeProbability(any(), any())).thenReturn(new BigDecimal("0.40"));
        when(signalConfigRepository.findAll()).thenReturn(List.of(
                config(ThresholdMode.FLAT_PERCENT, new BigDecimal("1.000"), null, null)));

        Optional<Signal> result = service.evaluate(marketWithSpread(), forecast);

        assertThat(result).isPresent();
        assertThat(result.get().getDirection()).isEqualTo(SignalDirection.BUY_NO);
    }

    @Test
    void confidenceAdjusted_usesBinomialStandardErrorOfEnsembleSize() {
        when(signalProvider.computeProbability(any(), any())).thenReturn(new BigDecimal("0.65"));
        // edge=7.5%=0.075, p=0.65, n=100 -> SE=sqrt(0.65*0.35/100)=0.0477 -> z=0.075/0.0477=1.57
        when(signalConfigRepository.findAll()).thenReturn(List.of(
                config(ThresholdMode.CONFIDENCE_ADJUSTED, null, null, new BigDecimal("1.0"))));

        Optional<Signal> result = service.evaluate(marketWithSpread(), forecast);

        assertThat(result).isPresent();
    }

    @Test
    void skipsMarketsWithNoLiquidity() {
        Market illiquid = marketWithSpread();
        illiquid.setLiquidityDollars(BigDecimal.ZERO);

        Optional<Signal> result = service.evaluate(illiquid, forecast);

        assertThat(result).isEmpty();
        verifyNoInteractions(signalProvider);
        verify(signalRepository, never()).save(any());
    }

    @Test
    void skipsWhenNoSignalConfigExists() {
        when(signalConfigRepository.findAll()).thenReturn(List.of());

        Optional<Signal> result = service.evaluate(marketWithSpread(), forecast);

        assertThat(result).isEmpty();
        verifyNoInteractions(signalProvider);
    }

    private SignalConfig config(ThresholdMode mode, BigDecimal flat, BigDecimal netEdge, BigDecimal zScore) {
        SignalConfig config = new SignalConfig();
        config.setId(UUID.randomUUID());
        config.setThresholdMode(mode);
        config.setFlatThresholdPercent(flat);
        config.setMinNetEdgeAfterFees(netEdge);
        config.setMinZScore(zScore);
        return config;
    }
}
