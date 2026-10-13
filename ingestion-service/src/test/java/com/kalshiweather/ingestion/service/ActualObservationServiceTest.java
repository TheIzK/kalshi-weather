package com.kalshiweather.ingestion.service;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.client.kalshi.KalshiMarketDto;
import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import com.kalshiweather.ingestion.domain.entity.SubjectStation;
import com.kalshiweather.ingestion.repository.ActualObservationRepository;
import com.kalshiweather.ingestion.repository.SubjectStationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActualObservationServiceTest {

    @Mock
    private SubjectStationRepository subjectStationRepository;
    @Mock
    private ActualObservationRepository actualObservationRepository;
    @Mock
    private KalshiClient kalshiClient;

    private ActualObservationService service;

    @BeforeEach
    void setUp() {
        service = new ActualObservationService(subjectStationRepository, actualObservationRepository, kalshiClient);
        lenient().when(actualObservationRepository.findBySubjectKeyAndSource(any(), any())).thenReturn(List.of());
    }

    private SubjectStation station() {
        SubjectStation station = new SubjectStation();
        station.setSubjectKey("NYC");
        station.setSeriesTicker("KXHIGHNY");
        return station;
    }

    /** Both bins for the same day are set a few hours apart (9am and 2pm ET on 2026-10-08),
     * which land on the same America/New_York calendar date. */
    private KalshiMarketDto bin(String ticker, String expirationValue, Instant occurrenceDatetime) {
        return new KalshiMarketDto(
                ticker, "EVT", "greater", null, null, occurrenceDatetime,
                "finalized", "no", null, null, null, null, null, null, expirationValue);
    }

    @Test
    void persistsOneObservationPerAgreeingDate() {
        Instant day1 = Instant.parse("2026-10-08T18:00:00Z");
        Instant day1Other = Instant.parse("2026-10-08T19:00:00Z");
        when(kalshiClient.fetchSettledMarkets("KXHIGHNY")).thenReturn(List.of(
                bin("KXHIGHNY-26OCT08-T79", "73.00", day1),
                bin("KXHIGHNY-26OCT08-T72", "73.00", day1Other)));

        int added = service.backfillSubject(station());

        assertThat(added).isEqualTo(1);
        ArgumentCaptor<ActualObservation> captor = ArgumentCaptor.forClass(ActualObservation.class);
        verify(actualObservationRepository).save(captor.capture());
        ActualObservation saved = captor.getValue();
        assertThat(saved.getSubjectKey()).isEqualTo("NYC");
        assertThat(saved.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(saved.getActualHighF()).isEqualByComparingTo("73.00");
        assertThat(saved.getSource()).isEqualTo(ActualObservationService.SOURCE_KALSHI_SETTLEMENT);
    }

    @Test
    void skipsDatesAlreadyKnown() {
        ActualObservation existing = new ActualObservation();
        existing.setOccurrenceDate(LocalDate.of(2026, 10, 8));
        when(actualObservationRepository.findBySubjectKeyAndSource("NYC", ActualObservationService.SOURCE_KALSHI_SETTLEMENT))
                .thenReturn(List.of(existing));
        when(kalshiClient.fetchSettledMarkets("KXHIGHNY")).thenReturn(List.of(
                bin("KXHIGHNY-26OCT08-T79", "73.00", Instant.parse("2026-10-08T18:00:00Z"))));

        int added = service.backfillSubject(station());

        assertThat(added).isZero();
        verify(actualObservationRepository, never()).save(any());
    }

    @Test
    void skipsAndDoesNotThrowWhenBinsDisagree() {
        // a real data inconsistency -- both bins settle for the same day but report
        // different expiration_values -- must not silently pick one.
        Instant day = Instant.parse("2026-10-08T18:00:00Z");
        when(kalshiClient.fetchSettledMarkets("KXHIGHNY")).thenReturn(List.of(
                bin("KXHIGHNY-26OCT08-T79", "73.00", day),
                bin("KXHIGHNY-26OCT08-T72", "74.00", day)));

        int added = service.backfillSubject(station());

        assertThat(added).isZero();
        verify(actualObservationRepository, never()).save(any());
    }

    @Test
    void skipsBinsWithBlankOrUnparseableExpirationValue() {
        when(kalshiClient.fetchSettledMarkets("KXHIGHNY")).thenReturn(List.of(
                bin("KXHIGHNY-26OCT08-T79", "", Instant.parse("2026-10-08T18:00:00Z")),
                bin("KXHIGHNY-26OCT09-T79", "not-a-number", Instant.parse("2026-10-09T18:00:00Z"))));

        int added = service.backfillSubject(station());

        assertThat(added).isZero();
        verify(actualObservationRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenNoSettledMarketsExist() {
        when(kalshiClient.fetchSettledMarkets("KXHIGHNY")).thenReturn(List.of());

        int added = service.backfillSubject(station());

        assertThat(added).isZero();
        verifyNoInteractions(actualObservationRepository);
    }

    @Test
    void backfillAllSubjectsIsolatesOneStationsFailureFromTheRest() {
        SubjectStation failing = station();
        SubjectStation healthy = new SubjectStation();
        healthy.setSubjectKey("CHI");
        healthy.setSeriesTicker("KXHIGHCHI");

        when(subjectStationRepository.findAll()).thenReturn(List.of(failing, healthy));
        when(kalshiClient.fetchSettledMarkets("KXHIGHNY")).thenThrow(new RuntimeException("boom"));
        when(kalshiClient.fetchSettledMarkets("KXHIGHCHI")).thenReturn(List.of(
                bin("KXHIGHCHI-26OCT08-T79", "70.00", Instant.parse("2026-10-08T18:00:00Z"))));

        service.backfillAllSubjects();

        verify(actualObservationRepository).save(argThat(o -> "CHI".equals(o.getSubjectKey())));
    }
}
