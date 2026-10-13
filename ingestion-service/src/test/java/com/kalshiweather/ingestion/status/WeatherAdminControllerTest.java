package com.kalshiweather.ingestion.status;

import com.kalshiweather.ingestion.service.ActualObservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeatherAdminControllerTest {

    @Mock
    private ActualObservationService actualObservationService;

    @Test
    void rejectsMissingOrWrongToken() {
        WeatherAdminController controller = new WeatherAdminController(actualObservationService, "correct-token");

        assertThat(controller.backfillActuals(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.backfillActuals("wrong-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(actualObservationService, never()).backfillAllSubjects();
    }

    @Test
    void rejectsAnyTokenWhenNoneConfigured() {
        WeatherAdminController controller = new WeatherAdminController(actualObservationService, "");

        assertThat(controller.backfillActuals("anything").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(actualObservationService, never()).backfillAllSubjects();
    }

    @Test
    void triggersBackfillWithCorrectToken() {
        WeatherAdminController controller = new WeatherAdminController(actualObservationService, "correct-token");

        ResponseEntity<Void> response = controller.backfillActuals("correct-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(actualObservationService).backfillAllSubjects();
    }
}
