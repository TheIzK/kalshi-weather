package com.kalshiweather.ingestion.mlb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-gated manual trigger for MlbIngestionOrchestrator's daily refresh (schedule,
 * pitchers, stats, standings) — same auth pattern as StatusController, same token. Useful
 * any time the daily 7am America/New_York cron was missed for that day (a redeploy after
 * 7am, a droplet restart) and waiting until tomorrow isn't worth it; the invoked logic
 * itself is exactly what the cron already runs, nothing new or untested.
 */
@RestController
@RequestMapping("/internal/mlb")
public class MlbAdminController {

    private final MlbIngestionOrchestrator orchestrator;
    private final String expectedToken;

    public MlbAdminController(
            MlbIngestionOrchestrator orchestrator,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.orchestrator = orchestrator;
        this.expectedToken = expectedToken;
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@RequestHeader(value = "X-Status-Token", required = false) String token) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        orchestrator.dailyRefresh();
        return ResponseEntity.ok().build();
    }
}
