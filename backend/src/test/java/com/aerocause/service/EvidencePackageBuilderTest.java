package com.aerocause.service;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidencePackageBuilderTest {

    @Mock ReadingRepository readingRepo;
    @Mock WeatherObsRepository weatherRepo;
    @Mock QcFlagRepository qcFlagRepo;
    @Mock HypothesisRepository hypothesisRepo;
    @Mock EvidenceItemRepository evidenceItemRepo;

    private EvidencePackageBuilder builder;
    private Incident incident;

    @BeforeEach
    void setUp() {
        builder = new EvidencePackageBuilder(
                readingRepo, weatherRepo, qcFlagRepo, hypothesisRepo, evidenceItemRepo,
                new ObjectMapper());
        Station station = Station.builder()
                .id(1L).externalId("STN-001").name("Central Monitor")
                .city("Vayu Nagar").lat(18.5).lon(73.8).provider("SYNTHETIC")
                .build();
        Instant start = Instant.parse("2024-01-01T10:00:00Z");
        incident = Incident.builder()
                .id(9L).station(station).pollutant("PM25")
                .detectedAt(start).windowStart(start).windowEnd(start.plusSeconds(3600))
                .peakValue(120.0).baselineValue(30.0)
                .status(Incident.IncidentStatus.NEW).synthetic(true)
                .build();
    }

    @Test
    void buildPackage_includesStructuredEvidenceAndSyntheticNotices() {
        Instant observedAt = incident.getWindowStart();
        when(readingRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                eq(incident.getStation()), any(), any()))
                .thenReturn(List.of(Reading.builder()
                        .pollutant("PM25").value(120.0).unit("µg/m³")
                        .observedAt(observedAt).qualityStatus(Reading.QualityStatus.GOOD).build()));
        when(weatherRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                eq(incident.getStation()), any(), any()))
                .thenReturn(List.of(WeatherObs.builder()
                        .observedAt(observedAt).windSpeed(3.0).fireHotspotUpwind(false).build()));
        when(qcFlagRepo.findByStationAndWindowStartBetween(eq(incident.getStation()), any(), any()))
                .thenReturn(List.of());
        when(hypothesisRepo.findByIncidentIdOrderByRankAsc(9L)).thenReturn(List.of(
                Hypothesis.builder()
                        .rank(1).type(Hypothesis.HypothesisType.DUST).score(0.7)
                        .confidenceLabel(Hypothesis.ConfidenceLabel.HIGH)
                        .evidenceForJson("[\"E1: low PM ratio\"]")
                        .evidenceAgainstJson("[]").missingJson("[]").build()));
        when(evidenceItemRepo.findByIncidentId(9L)).thenReturn(List.of(
                EvidenceItem.builder().sourceRef("E1").type("PM_RATIO")
                        .summary("Low PM2.5/PM10 ratio").build()));

        Map<String, Object> result = builder.buildPackage(incident);

        assertThat(result).containsEntry("packageVersion", "1.0");
        assertThat(result.get("dataNotice").toString()).contains("SAMPLE DATA (synthetic)");
        assertThat((List<?>) result.get("readings")).hasSize(1);
        assertThat((List<?>) result.get("weather")).hasSize(1);
        assertThat((List<?>) result.get("hypotheses")).hasSize(1);
        assertThat((List<?>) result.get("evidenceItems")).hasSize(1);
        assertThat(builder.buildPackageJson(incident)).contains("\"packageVersion\":\"1.0\"");
    }

    @Test
    void buildPackage_rejectsCorruptStoredHypothesisEvidence() {
        when(readingRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                eq(incident.getStation()), any(), any())).thenReturn(List.of());
        when(weatherRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                eq(incident.getStation()), any(), any())).thenReturn(List.of());
        when(qcFlagRepo.findByStationAndWindowStartBetween(eq(incident.getStation()), any(), any()))
                .thenReturn(List.of());
        when(hypothesisRepo.findByIncidentIdOrderByRankAsc(9L)).thenReturn(List.of(
                Hypothesis.builder()
                        .rank(1).type(Hypothesis.HypothesisType.DUST).score(0.7)
                        .confidenceLabel(Hypothesis.ConfidenceLabel.HIGH)
                        .evidenceForJson("not-json").evidenceAgainstJson("[]").missingJson("[]")
                        .build()));

        assertThatThrownBy(() -> builder.buildPackage(incident))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stored hypothesis evidence");
    }
}
