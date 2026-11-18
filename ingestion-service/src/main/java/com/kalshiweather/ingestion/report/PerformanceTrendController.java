package com.kalshiweather.ingestion.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-gated read endpoint for the weekly/monthly $ + calibration trend — same auth pattern
 * as StatusController/DashboardController. See PerformanceTrendService for what this
 * actually computes.
 */
@RestController
@RequestMapping("/internal")
public class PerformanceTrendController {

    private final PerformanceTrendService performanceTrendService;
    private final String expectedToken;

    public PerformanceTrendController(
            PerformanceTrendService performanceTrendService,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.performanceTrendService = performanceTrendService;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/performance-trend")
    public ResponseEntity<PerformanceTrendResponse> trend(
            @RequestHeader(value = "X-Status-Token", required = false) String token,
            @RequestParam(defaultValue = "WEEK") TrendGranularity granularity
    ) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(performanceTrendService.trend(granularity));
    }
}
