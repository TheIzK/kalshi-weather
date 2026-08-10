package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EmpiricalCdfSignalProviderTest {

    private final EmpiricalCdfSignalProvider provider = new EmpiricalCdfSignalProvider();

    @Test
    void greaterStrike_usesEmpiricalFractionWhenMembersAreMixed() {
        Market market = market(StrikeType.GREATER, "80", null);
        EnsembleForecast forecast = forecastOf("70", "75", "80", "85", "90"); // 2 of 5 strictly > 80

        BigDecimal probability = provider.computeProbability(market, forecast);

        assertThat(probability).isEqualByComparingTo("0.400000");
    }

    @Test
    void lessStrike_usesEmpiricalFractionWhenMembersAreMixed() {
        Market market = market(StrikeType.LESS, null, "80");
        EnsembleForecast forecast = forecastOf("70", "75", "80", "85", "90"); // 2 of 5 strictly < 80

        BigDecimal probability = provider.computeProbability(market, forecast);

        assertThat(probability).isEqualByComparingTo("0.400000");
    }

    @Test
    void betweenStrike_usesEmpiricalFractionWhenMembersAreMixed() {
        Market market = market(StrikeType.BETWEEN, "75", "85"); // (75, 85]
        EnsembleForecast forecast = forecastOf("70", "76", "80", "85", "90"); // 3 of 5 in range

        BigDecimal probability = provider.computeProbability(market, forecast);

        assertThat(probability).isEqualByComparingTo("0.600000");
    }

    @Test
    void fallsBackToNormalFitWhenEmpiricalProbabilityIsOne() {
        Market market = market(StrikeType.GREATER, "70", null);
        EnsembleForecast forecast = forecastOf("80", "82", "85", "90", "95"); // all 5 > 70

        BigDecimal probability = provider.computeProbability(market, forecast);

        // Statistical hygiene: a unanimous small ensemble should read as "very likely", not
        // "certain" — the normal fit should land short of a naive 1.0.
        assertThat(probability).isLessThan(BigDecimal.ONE);
        assertThat(probability.doubleValue()).isGreaterThan(0.9);
    }

    @Test
    void fallsBackToNormalFitWhenEmpiricalProbabilityIsZero() {
        Market market = market(StrikeType.GREATER, "100", null);
        EnsembleForecast forecast = forecastOf("80", "82", "85", "90", "95"); // none > 100

        BigDecimal probability = provider.computeProbability(market, forecast);

        assertThat(probability).isGreaterThan(BigDecimal.ZERO);
        assertThat(probability.doubleValue()).isLessThan(0.1);
    }

    @Test
    void degenerateUnanimousEnsemble_returnsExactZeroOrOne() {
        Market market = market(StrikeType.GREATER, "70", null);
        EnsembleForecast forecast = forecastOf("85", "85", "85", "85"); // zero variance

        BigDecimal probability = provider.computeProbability(market, forecast);

        assertThat(probability).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void rejectsForecastWithNoMemberValues() {
        Market market = market(StrikeType.GREATER, "70", null);
        EnsembleForecast forecast = new EnsembleForecast();
        forecast.setSource("NWS");
        forecast.setMemberValuesF(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> provider.computeProbability(market, forecast));
    }

    private static Market market(StrikeType strikeType, String floorStrike, String capStrike) {
        Market market = new Market();
        market.setStrikeType(strikeType);
        if (floorStrike != null) {
            market.setFloorStrike(new BigDecimal(floorStrike));
        }
        if (capStrike != null) {
            market.setCapStrike(new BigDecimal(capStrike));
        }
        return market;
    }

    private static EnsembleForecast forecastOf(String... values) {
        BigDecimal[] members = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            members[i] = new BigDecimal(values[i]);
        }
        EnsembleForecast forecast = new EnsembleForecast();
        forecast.setSource("OPEN_METEO_ENSEMBLE");
        forecast.setMemberValuesF(members);
        return forecast;
    }
}
