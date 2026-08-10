package com.kalshiweather.ingestion.client;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.client.nws.NwsClient;
import com.kalshiweather.ingestion.client.openmeteo.OpenMeteoClient;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real Kalshi, Open-Meteo, and NWS APIs — not fixtures. Meant to be run manually
 * against real network access to confirm the clients still parse live responses correctly
 * (these are public APIs Isaac Melton doesn't control, so response shape can drift).
 * Not wired into CI.
 */
@SpringBootTest
class LiveApiSmokeTest {

    // Central Park, NY — matches Kalshi's KXHIGHNY settlement source and the design doc's example.
    private static final BigDecimal LATITUDE = new BigDecimal("40.7829");
    private static final BigDecimal LONGITUDE = new BigDecimal("-73.9654");

    @Autowired
    private KalshiClient kalshiClient;

    @Autowired
    private OpenMeteoClient openMeteoClient;

    @Autowired
    private NwsClient nwsClient;

    @Test
    void kalshiClient_fetchesOpenNyHighTempMarkets() {
        List<Market> markets = kalshiClient.fetchOpenMarkets("KXHIGHNY");

        assertThat(markets).isNotEmpty();
        Market first = markets.get(0);
        assertThat(first.getId()).startsWith("KXHIGHNY-");
        assertThat(first.getSeriesTicker()).isEqualTo("KXHIGHNY");
        assertThat(first.getYesBid()).isNotNull();
        assertThat(first.getYesAsk()).isNotNull();
        assertThat(first.getOccurrenceDate()).isNotNull();
    }

    @Test
    void openMeteoClient_fetchesEnsembleForecastForTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        EnsembleForecast forecast = openMeteoClient.fetchDailyHighTemp("NYC", LATITUDE, LONGITUDE, tomorrow);

        assertThat(forecast.getSource()).isEqualTo("OPEN_METEO_ENSEMBLE");
        assertThat(forecast.getValidFor()).isEqualTo(tomorrow);
        assertThat(forecast.getMemberValuesF()).hasSizeGreaterThan(50);
        assertThat(forecast.getPredictedValueF()).isBetween(new BigDecimal("-20"), new BigDecimal("130"));
        assertThat(forecast.getRawPayload()).isNotBlank();
    }

    @Test
    void nwsClient_looksUpGridPointAndFetchesForecastForTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        NwsClient.GridPoint gridPoint = nwsClient.lookupGridPoint(LATITUDE.doubleValue(), LONGITUDE.doubleValue());
        assertThat(gridPoint.gridId()).isNotBlank();

        EnsembleForecast forecast = nwsClient.fetchDailyHighTemp(
                "NYC", gridPoint.gridId(), gridPoint.gridX(), gridPoint.gridY(), tomorrow);

        assertThat(forecast.getSource()).isEqualTo("NWS");
        assertThat(forecast.getValidFor()).isEqualTo(tomorrow);
        assertThat(forecast.getMemberValuesF()).isNull();
        assertThat(forecast.getPredictedValueF()).isBetween(new BigDecimal("-20"), new BigDecimal("130"));
        assertThat(forecast.getRawPayload()).isNotBlank();
    }
}
