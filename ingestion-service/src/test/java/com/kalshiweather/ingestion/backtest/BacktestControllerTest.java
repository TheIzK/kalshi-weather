package com.kalshiweather.ingestion.backtest;

import com.kalshiweather.ingestion.domain.enums.ThresholdMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestControllerTest {

    @Mock
    private BacktestService backtestService;

    private BacktestRequest sampleRequest() {
        return new BacktestRequest(
                ThresholdMode.FEE_ADJUSTED, null, new BigDecimal("3.000"), null, null, null,
                Instant.parse("2026-08-15T00:00:00Z"), Instant.parse("2026-08-25T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void rejectsMissingOrWrongToken() {
        BacktestController controller = new BacktestController(backtestService, "correct-token");

        assertThat(controller.backtest(null, sampleRequest()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.backtest("wrong-token", sampleRequest()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(backtestService, never()).run(any());
    }

    @Test
    void rejectsAnyTokenWhenNoneConfigured() {
        BacktestController controller = new BacktestController(backtestService, "");

        assertThat(controller.backtest("anything", sampleRequest()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(backtestService, never()).run(any());
    }

    @Test
    void runsBacktestWithCorrectToken() {
        BacktestController controller = new BacktestController(backtestService, "correct-token");
        BacktestRequest request = sampleRequest();
        BacktestResponse expected = new BacktestResponse(request, null, null);
        when(backtestService.run(request)).thenReturn(expected);

        ResponseEntity<?> response = controller.backtest("correct-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void mapsValidationFailureToBadRequest() {
        BacktestController controller = new BacktestController(backtestService, "correct-token");
        BacktestRequest request = sampleRequest();
        when(backtestService.run(request)).thenThrow(new IllegalArgumentException("bad window"));

        ResponseEntity<?> response = controller.backtest("correct-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("bad window");
    }
}
