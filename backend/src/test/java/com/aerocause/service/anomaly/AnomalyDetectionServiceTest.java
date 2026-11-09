package com.aerocause.service.anomaly;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.AuditService;
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
class AnomalyDetectionServiceTest {

    @Mock ReadingRepository readingRepo;
    @Mock IncidentRepository incidentRepo;
    @Mock AuditService auditService;

    AnomalyDetectionService service;
    AeroCauseProperties props;
    Station station;

    @BeforeEach
    void setUp() {
        props = new AeroCauseProperties();
        props.getAnomaly().setBaselineDays(14);
        props.getAnomaly().setBaselineMinReadings(5);
        props.getAnomaly().setRobustZThreshold(3.5);
        props.getAnomaly().setConsecutiveReadings(2);
        props.getAnomaly().getMinAbsoluteIncrease().put("PM25", 25.0);

        service = new AnomalyDetectionService(props, readingRepo, incidentRepo, auditService);

        station = Station.builder()
                .id(1L).externalId("STN-001").name("Test").city("Test")
                .lat(0.0).lon(0.0).provider("SYNTHETIC").build();
    }

    // -------------------------------------------------------------------------
    // computeRobustZ
    // -------------------------------------------------------------------------

    @Test
    void robustZ_zeroWhenMadIsZero() {
        assertThat(service.computeRobustZ(100, 50, 0)).isEqualTo(0.0);
    }

    @Test
    void robustZ_positiveForHighValue() {
        // median=50, mad=10, value=200 -> z = 0.6745*(200-50)/10 = 10.1
        double z = service.computeRobustZ(200, 50, 10);
        assertThat(z).isGreaterThan(3.5);
    }

    @Test
    void robustZ_zeroForValueAtMedian() {
        assertThat(service.computeRobustZ(50, 50, 10)).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // computeBaseline
    // -------------------------------------------------------------------------

    @Test
    void computeBaseline_correctMedianAndMad() {
        List<Reading> readings = makeReadings(new double[]{10, 20, 30, 40, 50});
        BaselineResult result = service.computeBaseline(readings, false, "test");
        assertThat(result.median()).isCloseTo(30.0, within(0.01));
        assertThat(result.mad()).isCloseTo(10.0, within(0.01));
        assertThat(result.sampleCount()).isEqualTo(5);
    }

    @Test
    void computeBaseline_singleValue_madIsZero() {
        List<Reading> readings = makeReadings(new double[]{42.0});
        BaselineResult result = service.computeBaseline(readings, false, "test");
        assertThat(result.median()).isCloseTo(42.0, within(0.01));
        assertThat(result.mad()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void computeBaseline_evenNumber_correctMedian() {
        List<Reading> readings = makeReadings(new double[]{10, 20, 30, 40});
        BaselineResult result = service.computeBaseline(readings, false, "test");
        assertThat(result.median()).isCloseTo(25.0, within(0.01));
    }

    // -------------------------------------------------------------------------
    // detectAnomaly
    // -------------------------------------------------------------------------

    @Test
    void detectAnomaly_returnsEmptyWhenNotEnoughReadings() {
        // Only 1 reading, need K=2
        List<Reading> readings = makeReadings(new double[]{500.0});
        Optional<Incident> result = service.detectAnomaly(station, "PM25", readings, false, null);
        assertThat(result).isEmpty();
    }

    @Test
    void detectAnomaly_returnsEmptyWhenNoBaseline() {
        List<Reading> readings = makeReadings(new double[]{500.0, 600.0});
        // Baseline query returns empty list
        when(readingRepo.findBaselineReadings(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        Optional<Incident> result = service.detectAnomaly(station, "PM25", readings, false, null);
        assertThat(result).isEmpty();
    }

    @Test
    void detectAnomaly_createsIncidentForTruePollutionSpike() {
        // Baseline: 30 µg/m³ ± small MAD
        List<Reading> baseline = makeReadings(new double[]{28, 29, 30, 31, 32});
        // Recent: 200 µg/m³ — clearly anomalous
        List<Reading> recent = makeReadings(Instant.now(), new double[]{200.0, 210.0});

        when(readingRepo.findBaselineReadings(any(), any(), any(), any(), anyInt()))
                .thenReturn(baseline);
        when(incidentRepo.save(any())).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            inc = Incident.builder()
                    .id(1L).station(station).pollutant("PM25")
                    .detectedAt(Instant.now()).windowStart(Instant.now()).windowEnd(Instant.now())
                    .peakValue(210.0).baselineValue(30.0)
                    .status(Incident.IncidentStatus.NEW).synthetic(false).build();
            return inc;
        });
        doNothing().when(auditService).log(any(), any(), any());

        Optional<Incident> result = service.detectAnomaly(station, "PM25", recent, false, null);
        assertThat(result).isPresent();
        assertThat(result.get().getPeakValue()).isGreaterThan(100.0);
    }

    @Test
    void detectAnomaly_returnsEmptyForNormalVariation() {
        // Baseline: 50 µg/m³, recent 60 µg/m³ — below threshold
        List<Reading> baseline = makeReadings(new double[]{48, 49, 50, 51, 52});
        List<Reading> recent = makeReadings(Instant.now(), new double[]{58.0, 62.0});
        when(readingRepo.findBaselineReadings(any(), any(), any(), any(), anyInt()))
                .thenReturn(baseline);

        Optional<Incident> result = service.detectAnomaly(station, "PM25", recent, false, null);
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Reading> makeReadings(double[] values) {
        return makeReadings(Instant.parse("2024-01-01T00:00:00Z"), values);
    }

    private List<Reading> makeReadings(Instant base, double[] values) {
        List<Reading> readings = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            readings.add(Reading.builder()
                    .station(station).pollutant("PM25")
                    .value(values[i]).unit("µg/m³")
                    .observedAt(base.plusSeconds(i * 3600))
                    .fetchedAt(Instant.now())
                    .source("TEST").qualityStatus(Reading.QualityStatus.GOOD)
                    .build());
        }
        return readings;
    }
}
