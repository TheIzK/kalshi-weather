package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.Signal;

/**
 * Classifies a signal as weather or a named sport — shared so reporting
 * ({@code PerformanceTrendService}) and calibration-gating ({@code BucketCalibrationService},
 * live {@code SignalGenerationService}) can't drift into subtly different copies of "what
 * domain is this." Simpler than {@code DashboardController.domainFor}, which additionally
 * handles a series that has ingested markets but never produced a signal yet — not a case
 * that applies here, since every caller of this class already has a signal or a sourceType
 * in hand.
 */
public final class SignalDomain {

    public static final String WEATHER = "WEATHER";

    private SignalDomain() {
    }

    public static String classify(Signal signal) {
        return signal.getForecastId() != null ? WEATHER : classifyFromSourceType(signal.getSourceType());
    }

    public static String classifyFromSourceType(String sourceType) {
        return sourceType == null ? "UNKNOWN" : sourceType.split("_", 2)[0];
    }
}
