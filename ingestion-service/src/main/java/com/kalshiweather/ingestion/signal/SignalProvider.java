package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;

import java.math.BigDecimal;

/**
 * Estimates the model's probability that {@code market} resolves YES, given an ensemble
 * forecast for the same subject/date. Pluggable so the probability methodology can change
 * (or a future model service can be swapped in) without touching signal/threshold logic.
 */
public interface SignalProvider {

    /** {@code forecast} must carry member values (i.e. an OPEN_METEO_ENSEMBLE forecast, not NWS). */
    BigDecimal computeProbability(Market market, EnsembleForecast forecast);
}
