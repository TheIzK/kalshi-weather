package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.service.ActualObservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-gated manual trigger for the actual-observation backfill — same auth pattern as
 * StatusController/MlbAdminController, same token. Useful right after deploying the backfill
 * for the first time, rather than waiting for its once-daily schedule to tick.
 */
@RestController
@RequestMapping("/internal/weather")
public class WeatherAdminController {

    private final ActualObservationService actualObservationService;
    private final String expectedToken;

    public WeatherAdminController(
            ActualObservationService actualObservationService,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.actualObservationService = actualObservationService;
        this.expectedToken = expectedToken;
    }

    @PostMapping("/backfill-actuals")
    public ResponseEntity<Void> backfillActuals(@RequestHeader(value = "X-Status-Token", required = false) String token) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        actualObservationService.backfillAllSubjects();
        return ResponseEntity.ok().build();
    }
}
