package com.kalshiweather.ingestion.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceTrendControllerTest {

    @Mock
    private PerformanceTrendService performanceTrendService;

    @Test
    void rejectsMissingOrWrongToken() {
        PerformanceTrendController controller = new PerformanceTrendController(performanceTrendService, "correct-token");

        assertThat(controller.trend(null, TrendGranularity.WEEK).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.trend("wrong-token", TrendGranularity.WEEK).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(performanceTrendService, never()).trend(any());
    }

    @Test
    void rejectsAnyTokenWhenNoneConfigured() {
        PerformanceTrendController controller = new PerformanceTrendController(performanceTrendService, "");

        assertThat(controller.trend("anything", TrendGranularity.WEEK).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(performanceTrendService, never()).trend(any());
    }

    @Test
    void returnsTrendWithCorrectToken() {
        PerformanceTrendController controller = new PerformanceTrendController(performanceTrendService, "correct-token");
        PerformanceTrendResponse expected = new PerformanceTrendResponse(TrendGranularity.MONTH, List.of());
        when(performanceTrendService.trend(TrendGranularity.MONTH)).thenReturn(expected);

        ResponseEntity<PerformanceTrendResponse> response = controller.trend("correct-token", TrendGranularity.MONTH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }
}
