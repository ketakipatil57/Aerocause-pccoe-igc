package com.aerocause.service.qc;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.QcFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QcServiceTest {

    @Mock QcFlagRepository qcFlagRepo;
    @InjectMocks QcService qcService;

    AeroCauseProperties props;
    Station station;

    @BeforeEach
    void setUp() {
        props = new AeroCauseProperties();
        // Set QC thresholds
        props.getQc().setFlatlineWindow(5);
        props.getQc().setFlatlineTolerance(0.001);
        props.getQc().setIsolatedSpikeMinStations(2);
        props.getQc().setIsolatedSpikeRatio(3.0);
        props.getQc().setStaleDataMinutes(90);
        // Out-of-range for PM25
        AeroCauseProperties.RangeProps pm25Range = new AeroCauseProperties.RangeProps();
        pm25Range.setMin(0); pm25Range.setMax(1000);
        props.getQc().getOutOfRange().put("PM25", pm25Range);

        qcService = new QcService(props, qcFlagRepo);

        station = Station.builder()
                .id(1L).externalId("STN-001").name("Test Station")
                .city("Test").lat(0.0).lon(0.0).provider("SYNTHETIC")
                .build();

        lenient().when(qcFlagRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // FLATLINE tests
    // -------------------------------------------------------------------------

    @Test
    void flatline_detectsExactRepetition() {
        List<Reading> readings = makeFlatlineReadings(7, 42.0);
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.hasSeriousFlags()).isTrue();
        assertThat(result.flags()).anyMatch(f -> f.getRule() == QcFlag.QcRule.FLATLINE);
    }

    @Test
    void flatline_noFlagWhenNotEnoughReadings() {
        List<Reading> readings = makeFlatlineReadings(3, 42.0); // need 5
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.flags().stream().anyMatch(f -> f.getRule() == QcFlag.QcRule.FLATLINE)).isFalse();
    }

    @Test
    void flatline_noFlagWhenValuesVary() {
        List<Reading> readings = new ArrayList<>();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        double[] vals = {40.0, 42.5, 38.0, 55.0, 41.0, 39.0};
        for (int i = 0; i < vals.length; i++) {
            readings.add(makeReading(base.plusSeconds(i * 3600), vals[i]));
        }
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.flags().stream().anyMatch(f -> f.getRule() == QcFlag.QcRule.FLATLINE)).isFalse();
    }

    // -------------------------------------------------------------------------
    // OUT_OF_RANGE tests
    // -------------------------------------------------------------------------

    @Test
    void outOfRange_detectsExtremeSpike() {
        List<Reading> readings = List.of(makeReading(Instant.now(), 1500.0)); // above 1000
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.hasSeriousFlags()).isTrue();
        assertThat(result.flags()).anyMatch(f -> f.getRule() == QcFlag.QcRule.OUT_OF_RANGE);
    }

    @Test
    void outOfRange_noFlagForNormalValue() {
        List<Reading> readings = List.of(makeReading(Instant.now(), 85.0));
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.flags().stream().anyMatch(f -> f.getRule() == QcFlag.QcRule.OUT_OF_RANGE)).isFalse();
    }

    @Test
    void outOfRange_detectsNegativeValue() {
        List<Reading> readings = List.of(makeReading(Instant.now(), -5.0));
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.hasSeriousFlags()).isTrue();
    }

    // -------------------------------------------------------------------------
    // ISOLATED_SPIKE tests
    // -------------------------------------------------------------------------

    @Test
    void isolatedSpike_detectedWhenOnlyOneStationSpikes() {
        Instant ts = Instant.now();
        List<Reading> readings = List.of(makeReading(ts, 999.0));
        // Neighbours at 30 µg/m³ — ratio = 999/30 = 33.3 >> 3.0 threshold
        Map<Long, Double> neighbours = Map.of(2L, 30.0, 3L, 28.0);
        QcResult result = qcService.runQc(station, "PM25", readings, neighbours);
        assertThat(result.flags()).anyMatch(f -> f.getRule() == QcFlag.QcRule.ISOLATED_SPIKE);
    }

    @Test
    void isolatedSpike_notTriggeredWhenNeighboursAlsoElevated() {
        Instant ts = Instant.now();
        List<Reading> readings = List.of(makeReading(ts, 200.0));
        Map<Long, Double> neighbours = Map.of(2L, 180.0, 3L, 190.0); // ratio < 3.0
        QcResult result = qcService.runQc(station, "PM25", readings, neighbours);
        assertThat(result.flags().stream().anyMatch(f -> f.getRule() == QcFlag.QcRule.ISOLATED_SPIKE)).isFalse();
    }

    @Test
    void isolatedSpike_requiresMinStations() {
        Instant ts = Instant.now();
        List<Reading> readings = List.of(makeReading(ts, 999.0));
        // Only 1 neighbour — below minimum of 2
        Map<Long, Double> neighbours = Map.of(2L, 30.0);
        QcResult result = qcService.runQc(station, "PM25", readings, neighbours);
        assertThat(result.flags().stream().anyMatch(f -> f.getRule() == QcFlag.QcRule.ISOLATED_SPIKE)).isFalse();
    }

    // -------------------------------------------------------------------------
    // STALE_DATA tests
    // -------------------------------------------------------------------------

    @Test
    void staleData_flaggedForLargeGap() {
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        List<Reading> readings = List.of(
                makeReading(base, 50.0),
                makeReading(base.plusSeconds(7200), 55.0) // 120 min gap > 90 min threshold
        );
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.flags()).anyMatch(f -> f.getRule() == QcFlag.QcRule.STALE_DATA);
    }

    @Test
    void staleData_notFlaggedForNormalInterval() {
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        List<Reading> readings = List.of(
                makeReading(base, 50.0),
                makeReading(base.plusSeconds(3600), 55.0) // 60 min < 90 min threshold
        );
        QcResult result = qcService.runQc(station, "PM25", readings, Map.of());
        assertThat(result.flags().stream().anyMatch(f -> f.getRule() == QcFlag.QcRule.STALE_DATA)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void emptyReadings_returnsNoFlags() {
        QcResult result = qcService.runQc(station, "PM25", Collections.emptyList(), Map.of());
        assertThat(result.hasSeriousFlags()).isFalse();
        assertThat(result.flags()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Reading> makeFlatlineReadings(int count, double value) {
        List<Reading> readings = new ArrayList<>();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            readings.add(makeReading(base.plusSeconds(i * 3600), value));
        }
        return readings;
    }

    private Reading makeReading(Instant ts, double value) {
        return Reading.builder()
                .station(station).pollutant("PM25")
                .value(value).unit("µg/m³")
                .observedAt(ts).fetchedAt(Instant.now())
                .source("SYNTHETIC").qualityStatus(Reading.QualityStatus.GOOD)
                .build();
    }
}
