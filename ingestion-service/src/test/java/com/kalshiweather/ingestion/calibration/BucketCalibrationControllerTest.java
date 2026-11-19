package com.kalshiweather.ingestion.calibration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BucketCalibrationControllerTest {

    @Mock
    private BucketCalibrationService bucketCalibrationService;

    @Test
    void rejectsMissingOrWrongToken() {
        BucketCalibrationController controller = new BucketCalibrationController(bucketCalibrationService, "correct-token");

        assertThat(controller.recompute(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.recompute("wrong-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(bucketCalibrationService, never()).recompute();
    }

    @Test
    void rejectsAnyTokenWhenNoneConfigured() {
        BucketCalibrationController controller = new BucketCalibrationController(bucketCalibrationService, "");

        assertThat(controller.recompute("anything").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(bucketCalibrationService, never()).recompute();
    }

    @Test
    void triggersRecomputeWithCorrectToken() {
        BucketCalibrationController controller = new BucketCalibrationController(bucketCalibrationService, "correct-token");

        assertThat(controller.recompute("correct-token").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bucketCalibrationService).recompute();
    }
}
