package com.kalshiweather.ingestion.client.kalshi;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.enums.MarketResult;
import com.kalshiweather.ingestion.domain.enums.MarketStatus;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Kalshi's markets endpoint is public, no auth required.
 * https://api.elections.kalshi.com/trade-api/v2/markets?series_ticker=X&status=open
 */
@Component
public class KalshiClient {

    private static final Logger log = LoggerFactory.getLogger(KalshiClient.class);

    /**
     * The {@code status=open} query filter is Kalshi's own vocabulary; the status value
     * actually returned on each market object is different (verified against live data).
     */
    private static final Map<String, MarketStatus> STATUS_BY_WIRE_VALUE = Map.of(
            "active", MarketStatus.ACTIVE,
            "closed", MarketStatus.CLOSED,
            "settled", MarketStatus.RESOLVED
    );

    /** Blank is the normal case (not yet settled) — only a non-blank, unrecognized value warns. */
    private static final Map<String, MarketResult> RESULT_BY_WIRE_VALUE = Map.of(
            "yes", MarketResult.YES,
            "no", MarketResult.NO
    );

    // Central Park's KXHIGHNY markets resolve against a US Eastern calendar day.
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final RestClient restClient;

    public KalshiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.elections.kalshi.com/trade-api/v2")
                .build();
    }

    /** Fetches all open markets for a series (e.g. "KXHIGHNY") and maps them to entities. */
    public List<Market> fetchOpenMarkets(String seriesTicker) {
        KalshiMarketsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/markets")
                        .queryParam("series_ticker", seriesTicker)
                        .queryParam("status", "open")
                        .build())
                .retrieve()
                .body(KalshiMarketsResponse.class);

        if (response == null || response.markets() == null) {
            return List.of();
        }

        return response.markets().stream()
                .map(dto -> toEntity(dto, seriesTicker))
                .toList();
    }

    /**
     * Fetches one market by ticker — used to check whether a market a paper trade is open
     * against has settled yet. {@code seriesTicker} isn't on the wire (same reason
     * {@link #fetchOpenMarkets} needs it as a parameter); the caller already has it from the
     * stored {@link Market} row being reconciled.
     */
    public Market fetchMarket(String ticker, String seriesTicker) {
        KalshiMarketResponse response = restClient.get()
                .uri("/markets/{ticker}", ticker)
                .retrieve()
                .body(KalshiMarketResponse.class);

        if (response == null || response.market() == null) {
            throw new IllegalStateException("Kalshi returned no market for ticker " + ticker);
        }

        return toEntity(response.market(), seriesTicker);
    }

    private Market toEntity(KalshiMarketDto dto, String seriesTicker) {
        Market market = new Market();
        market.setId(dto.ticker());
        market.setEventTicker(dto.eventTicker());
        market.setSeriesTicker(seriesTicker);
        market.setStrikeType(StrikeType.valueOf(dto.strikeType().toUpperCase()));
        market.setFloorStrike(dto.floorStrike());
        market.setCapStrike(dto.capStrike());
        market.setOccurrenceDate(dto.occurrenceDatetime().atZone(MARKET_ZONE).toLocalDate());
        market.setStatus(mapStatus(dto.status()));
        market.setResult(mapResult(dto.result()));
        market.setYesBid(dto.yesBidDollars());
        market.setYesAsk(dto.yesAskDollars());
        market.setNoBid(dto.noBidDollars());
        market.setNoAsk(dto.noAskDollars());
        market.setLiquidityDollars(dto.liquidityDollars());
        market.setVolume24h(dto.volume24h());
        market.setOpenInterest(dto.openInterest());
        return market;
    }

    private MarketStatus mapStatus(String wireValue) {
        MarketStatus mapped = STATUS_BY_WIRE_VALUE.get(wireValue);
        if (mapped == null) {
            log.warn("Unrecognized Kalshi market status '{}' — defaulting to CLOSED", wireValue);
            return MarketStatus.CLOSED;
        }
        return mapped;
    }

    private MarketResult mapResult(String wireValue) {
        if (wireValue == null || wireValue.isBlank()) {
            return null; // not yet settled — the normal case for every open market
        }
        MarketResult mapped = RESULT_BY_WIRE_VALUE.get(wireValue);
        if (mapped == null) {
            log.warn("Unrecognized Kalshi market result '{}'", wireValue);
        }
        return mapped;
    }
}
