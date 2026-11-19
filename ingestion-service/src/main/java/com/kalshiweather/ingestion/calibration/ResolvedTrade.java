package com.kalshiweather.ingestion.calibration;

import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.StrikeType;

/**
 * One closed trade with its domain classification and, where knowable, whether the market
 * actually resolved YES. {@code resolvedYes} is null when ground truth isn't available yet
 * (weather: no matching actual_observations row; sports: the trade's own exit_price, which
 * is always available once CLOSED — see GroundTruthResolver for why that's used over the
 * markets table's own result column).
 */
public record ResolvedTrade(
        PaperTrade trade,
        Signal signal,
        Market market,
        String domain,
        StrikeType strikeType,
        Boolean resolvedYes
) {
}
