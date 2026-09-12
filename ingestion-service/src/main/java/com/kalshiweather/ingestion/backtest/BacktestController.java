package com.kalshiweather.ingestion.backtest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-gated manual trigger for a walk-forward backtest replay — same auth pattern as
 * StatusController/MlbAdminController, same token. See BacktestService for what this actually
 * computes and its limitations.
 */
@RestController
@RequestMapping("/internal")
public class BacktestController {

    private final BacktestService backtestService;
    private final String expectedToken;

    public BacktestController(
            BacktestService backtestService,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.backtestService = backtestService;
        this.expectedToken = expectedToken;
    }

    @PostMapping("/backtest")
    public ResponseEntity<?> backtest(
            @RequestHeader(value = "X-Status-Token", required = false) String token,
            @RequestBody BacktestRequest request
    ) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(backtestService.run(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
