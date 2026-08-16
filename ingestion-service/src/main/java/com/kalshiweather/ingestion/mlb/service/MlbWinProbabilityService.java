package com.kalshiweather.ingestion.mlb.service;

import com.kalshiweather.ingestion.mlb.entity.MlbGame;
import com.kalshiweather.ingestion.mlb.entity.MlbWinProbabilitySnapshot;

import java.util.Optional;

public interface MlbWinProbabilityService {
    /**
     * Computes and persists a snapshot in one call — the design doc's own intent is that
     * every prediction leaves a full audit trail specifically for later recalibration, so
     * computing without persisting isn't a meaningful separate operation here. Empty if
     * either side's standings split or confirmed starter's stats aren't available yet.
     */
    Optional<MlbWinProbabilitySnapshot> computeAndPersist(MlbGame game);
}
