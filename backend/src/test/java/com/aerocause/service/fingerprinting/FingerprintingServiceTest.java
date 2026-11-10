package com.aerocause.service.fingerprinting;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FingerprintingServiceTest {

    @Mock HypothesisRepository hypothesisRepo;
    @Mock EvidenceItemRepository evidenceRepo;
    @Mock IncidentRepository incidentRepo;
    @Mock AuditService auditService;

    FingerprintingService service;
    AeroCauseProperties props;
    Station station;
    Incident incident;

    @BeforeEach
    void setUp() {
        props = new AeroCauseProperties();
        // PM ratio rule
        props.getFingerprinting().getRules().getPmRatio().setLowRatioThreshold(0.45);
        props.getFingerprinting().getRules().getPmRatio().setHighRatioThreshold(0.60);
        props.getFingerprinting().getRules().getPmRatio().setDustWeight(0.30);
        props.getFingerprinting().getRules().getPmRatio().setCombustionWeight(0.25);
        // NO2/CO rule
        props.getFingerprinting().getRules().getNo2Co().setNo2ThresholdUgm3(60);
        props.getFingerprinting().getRules().getNo2Co().setCoThresholdPpm(1.5);
        props.getFingerprinting().getRules().getNo2Co().setCombustionWeight(0.30);
        // SO2 rule
        props.getFingerprinting().getRules().getSo2().setThresholdUgm3(40);
        props.getFingerprinting().getRules().getSo2().setIndustrialWeight(0.35);
        // Wind rule
        props.getFingerprinting().getRules().getWind().setLowWindThreshold(2.0);
        props.getFingerprinting().getRules().getWind().setStagnantBlhThreshold(500);
        props.getFingerprinting().getRules().getWind().setStagnantWeight(0.25);
        props.getFingerprinting().getRules().getWind().setRegionalSpreadWeight(0.15);
        // Fire hotspot
        props.getFingerprinting().getRules().getFireHotspot().setBiomassWeight(0.40);
        // Sensor fault
        props.getFingerprinting().getRules().getSensorFault().setQcFlagWeight(0.50);
        props.getFingerprinting().getRules().getSensorFault().setIsolationWeight(0.30);
        // Inconclusive
        props.getFingerprinting().setInconclusiveMarginThreshold(0.15);
        props.getFingerprinting().setInconclusiveMissingPenalty(0.1);

        service = new FingerprintingService(props, hypothesisRepo, evidenceRepo,
                incidentRepo, auditService, new ObjectMapper());

        station = Station.builder()
                .id(1L).externalId("STN-001").name("Test").city("Test")
                .lat(0.0).lon(0.0).provider("SYNTHETIC").build();

        incident = Incident.builder()
                .id(1L).station(station).pollutant("PM25")
                .detectedAt(Instant.now()).windowStart(Instant.now()).windowEnd(Instant.now())
                .peakValue(200.0).baselineValue(30.0)
                .status(Incident.IncidentStatus.NEW).synthetic(true).build();

        when(hypothesisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evidenceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(auditService).log(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // DUST hypothesis
    // -------------------------------------------------------------------------

    @Test
    void dustHypothesis_lowPmRatioWithHighWind() {
        // PM25/PM10 ratio = 80/280 = 0.286 < 0.45 -> strong dust signal
        // High wind -> NOT stagnant
        Map<String, Double> peaks = Map.of(
                "PM25", 80.0, "PM10", 280.0, "NO2", 32.0, "SO2", 10.0, "CO", 0.6);
        WeatherObs weather = WeatherObs.builder()
                .station(station).observedAt(Instant.now())
                .windSpeed(8.5).windDirDeg(315.0).boundaryLayerHeight(1500.0)
                .fireHotspotUpwind(false).source("SYNTHETIC").build();

        FingerprintingInput input = new FingerprintingInput(
                incident, peaks, Map.of("PM25", 30.0), List.of(weather),
                List.of(), Map.of("2", 30.0, "3", 32.0), false, false);

        List<Hypothesis> result = service.fingerprint(input);
        Hypothesis top = result.get(0);
        assertThat(top.getType()).isEqualTo(Hypothesis.HypothesisType.DUST);
    }

    // -------------------------------------------------------------------------
    // COMBUSTION_TRAFFIC hypothesis
    // -------------------------------------------------------------------------

    @Test
    void combustionHypothesis_highPmRatioWithNo2AndCo() {
        // PM25/PM10 ratio = 145/200 = 0.725 > 0.60 -> combustion
        // NO2=95 > 60, CO=2.8 > 1.5 -> strong combustion
        Map<String, Double> peaks = Map.of(
                "PM25", 145.0, "PM10", 200.0, "NO2", 95.0, "SO2", 18.0, "CO", 2.8);
        WeatherObs weather = WeatherObs.builder()
                .station(station).observedAt(Instant.now())
                .windSpeed(1.8).windDirDeg(200.0).boundaryLayerHeight(400.0)
                .fireHotspotUpwind(false).source("SYNTHETIC").build();

        FingerprintingInput input = new FingerprintingInput(
                incident, peaks, Map.of("PM25", 50.0), List.of(weather),
                List.of(), Map.of("2", 70.0, "3", 65.0), false, false);

        List<Hypothesis> result = service.fingerprint(input);
        Hypothesis top = result.get(0);
        assertThat(top.getType()).isEqualTo(Hypothesis.HypothesisType.COMBUSTION_TRAFFIC);
    }

    // -------------------------------------------------------------------------
    // SENSOR_FAULT hypothesis
    // -------------------------------------------------------------------------

    @Test
    void sensorFaultHypothesis_withQcFlagsAndIsolation() {
        Map<String, Double> peaks = Map.of("PM25", 998.0, "PM10", 42.0);
        // Serious QC flag
        QcFlag flatlineFlag = QcFlag.builder()
                .station(station).pollutant("PM25")
                .windowStart(Instant.now()).windowEnd(Instant.now())
                .rule(QcFlag.QcRule.FLATLINE).severity(QcFlag.Severity.ERROR)
                .detail("FLATLINE detected").build();
        // Neighbours at normal background (station is isolated)
        Map<String, Double> neighbours = Map.of("2", 35.0, "3", 38.0);

        FingerprintingInput input = new FingerprintingInput(
                incident, peaks, Map.of("PM25", 35.0), List.of(),
                List.of(flatlineFlag), neighbours, false, false);

        List<Hypothesis> result = service.fingerprint(input);
        // Top hypothesis should be SENSOR_FAULT (or INCONCLUSIVE if margin is small)
        // With both QC flag AND isolation, SENSOR_FAULT should dominate
        Hypothesis top = result.get(0);
        assertThat(top.getType()).isIn(
                Hypothesis.HypothesisType.SENSOR_FAULT,
                Hypothesis.HypothesisType.INCONCLUSIVE);
    }

    // -------------------------------------------------------------------------
    // INCONCLUSIVE logic
    // -------------------------------------------------------------------------

    @Test
    void inconclusive_whenMissingCriticalEvidence() {
        // No PM10, no NO2, no CO, no weather, fire hotspot disabled
        // -> lots of missing evidence -> INCONCLUSIVE
        Map<String, Double> peaks = Map.of("PM25", 95.0); // only PM25 available
        FingerprintingInput input = new FingerprintingInput(
                incident, peaks, Map.of("PM25", 42.0), List.of(),
                List.of(), Map.of(), false, false);

        List<Hypothesis> result = service.fingerprint(input);
        Hypothesis top = result.get(0);
        assertThat(top.getType()).isEqualTo(Hypothesis.HypothesisType.INCONCLUSIVE);
    }

    @Test
    void missingWeatherAndFireDataAreRecordedAsMissingEvidence() {
        FingerprintingInput input = new FingerprintingInput(
                incident, Map.of("PM25", 95.0), Map.of("PM25", 42.0), List.of(),
                List.of(), Map.of(), true, false);

        service.fingerprint(input);

        verify(evidenceRepo, atLeastOnce()).save(argThat(item ->
                item.getDataJson().toLowerCase(Locale.ROOT)
                        .contains("fire hotspot data not available")
                        || item.getDataJson().contains("Wind speed not recorded")));
    }

    // -------------------------------------------------------------------------
    // Evidence IDs are present
    // -------------------------------------------------------------------------

    @Test
    void allContributions_haveNonNullEvidenceIds() {
        Map<String, Double> peaks = Map.of("PM25", 80.0, "PM10", 200.0, "NO2", 30.0);
        FingerprintingInput input = new FingerprintingInput(
                incident, peaks, Map.of("PM25", 30.0), List.of(),
                List.of(), Map.of(), false, false);

        List<Hypothesis> result = service.fingerprint(input);

        // All hypotheses should have non-null evidenceFor/Against/Missing JSON
        for (Hypothesis h : result) {
            assertThat(h.getEvidenceForJson()).isNotNull().isNotBlank();
            assertThat(h.getEvidenceAgainstJson()).isNotNull().isNotBlank();
            assertThat(h.getMissingJson()).isNotNull().isNotBlank();
        }
    }
}
