package com.kalshiweather.ingestion.calibration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-gated manual trigger for the bucket-calibration recompute — same auth pattern as
 * every other internal endpoint. Useful right after deploying this feature for the first
 * time, rather than waiting for its once-daily schedule to tick (and before that first run,
 * every bucket has no calibration row at all, which fails closed — see
 * SignalEligibility.hasProvenCalibration).
 */
@RestController
@RequestMapping("/internal/calibration")
public class BucketCalibrationController {

    private final BucketCalibrationService bucketCalibrationService;
    private final String expectedToken;

    public BucketCalibrationController(
            BucketCalibrationService bucketCalibrationService,
            @Value("${status.api.token:}") String expectedToken
    ) {
        this.bucketCalibrationService = bucketCalibrationService;
        this.expectedToken = expectedToken;
    }

    @PostMapping("/recompute")
    public ResponseEntity<Void> recompute(@RequestHeader(value = "X-Status-Token", required = false) String token) {
        if (expectedToken.isBlank() || !expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        bucketCalibrationService.recompute();
        return ResponseEntity.ok().build();
    }
}
