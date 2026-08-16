package com.kalshiweather.ingestion.mlb;

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
class MlbAdminControllerTest {

    @Mock
    private MlbIngestionOrchestrator orchestrator;

    @Test
    void rejectsMissingOrWrongToken() {
        MlbAdminController controller = new MlbAdminController(orchestrator, "correct-token");

        assertThat(controller.refresh(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.refresh("wrong-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(orchestrator, never()).dailyRefresh();
    }

    @Test
    void rejectsAnyTokenWhenNoneConfigured() {
        MlbAdminController controller = new MlbAdminController(orchestrator, "");

        assertThat(controller.refresh("anything").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(orchestrator, never()).dailyRefresh();
    }

    @Test
    void triggersDailyRefreshWithCorrectToken() {
        MlbAdminController controller = new MlbAdminController(orchestrator, "correct-token");

        ResponseEntity<Void> response = controller.refresh("correct-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(orchestrator).dailyRefresh();
    }
}
