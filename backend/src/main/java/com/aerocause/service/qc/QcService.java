package com.aerocause.service.qc;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Quality Control service.
 * Applies 5 deterministic rules to a window of readings for a station+pollutant.
 * Rules produce QcFlag records stored in the database.
 *
 * <p>RULES (each produces a QcFlag with rule name and detail):
 * <ul>
 *   <li>FLATLINE:        N identical consecutive values (within tolerance)</li>
 *   <li>OUT_OF_RANGE:    value outside configured physical bounds</li>
 *   <li>ISOLATED_SPIKE:  station jumps while nearby stations do not</li>
 *   <li>STALE_DATA:      gap between consecutive readings exceeds threshold</li>
 *   <li>MISSING_DATA:    flagged externally when expected readings are absent</li>
 * </ul>
 *
 * <p>All thresholds are HEURISTIC values from application.yml.
 * They are NOT scientifically validated.
 */
@Service
public class QcService {

    private final AeroCauseProperties props;
    private final QcFlagRepository qcFlagRepo;

    public QcService(AeroCauseProperties props, QcFlagRepository qcFlagRepo) {
        this.props = props;
        this.qcFlagRepo = qcFlagRepo;
    }

    /**
     * Run all QC rules against a list of readings for one station+pollutant.
     * Saves any flags found to the database. Returns a QcResult.
     *
     * @param station                  the station
     * @param pollutant                the pollutant code (PM25, NO2, etc.)
     * @param readings                 time-ordered list of readings to check
     * @param allStationValuesAtTime   map stationId -> value at current time (for ISOLATED_SPIKE)
     */
    @Transactional
    public QcResult runQc(Station station, String pollutant,
                          List<Reading> readings,
                          Map<Long, Double> allStationValuesAtTime) {
        List<QcFlag> flags = new ArrayList<>();

        if (readings == null || readings.isEmpty()) {
            return new QcResult(false, flags);
        }

        flags.addAll(checkOutOfRange(station, pollutant, readings));
        flags.addAll(checkFlatline(station, pollutant, readings));
        flags.addAll(checkStaleData(station, pollutant, readings));

        if (allStationValuesAtTime != null && !allStationValuesAtTime.isEmpty()) {
            flags.addAll(checkIsolatedSpike(station, pollutant, readings, allStationValuesAtTime));
        }

        flags = qcFlagRepo.saveAll(flags);

        boolean serious = flags.stream()
                .anyMatch(f -> f.getSeverity() == QcFlag.Severity.ERROR);

        return new QcResult(serious, flags);
    }

    // -------------------------------------------------------------------------
    // Rule implementations
    // -------------------------------------------------------------------------

    private List<QcFlag> checkOutOfRange(Station station, String pollutant, List<Reading> readings) {
        List<QcFlag> flags = new ArrayList<>();
        AeroCauseProperties.RangeProps range = props.getQc().getOutOfRange()
                .getOrDefault(pollutant, defaultRange(pollutant));

        for (Reading r : readings) {
            if (r.getValue() < range.getMin() || r.getValue() > range.getMax()) {
                flags.add(QcFlag.builder()
                        .station(station)
                        .pollutant(pollutant)
                        .windowStart(r.getObservedAt())
                        .windowEnd(r.getObservedAt())
                        .rule(QcFlag.QcRule.OUT_OF_RANGE)
                        .severity(QcFlag.Severity.ERROR)
                        .detail(String.format(
                                "OUT_OF_RANGE: value %.2f %s outside physical range [%.1f, %.1f] for %s. " +
                                "HEURISTIC threshold — verify against instrument specifications.",
                                r.getValue(), r.getUnit(), range.getMin(), range.getMax(), pollutant))
                        .build());
            }
        }
        return flags;
    }

    private List<QcFlag> checkFlatline(Station station, String pollutant, List<Reading> readings) {
        List<QcFlag> flags = new ArrayList<>();
        int n = props.getQc().getFlatlineWindow();
        double tol = props.getQc().getFlatlineTolerance();

        if (readings.size() < n) return flags;

        int consecutive = 1;
        for (int i = 1; i < readings.size(); i++) {
            double diff = Math.abs(readings.get(i).getValue() - readings.get(i - 1).getValue());
            if (diff <= tol) {
                consecutive++;
                if (consecutive >= n) {
                    Instant start = readings.get(i - n + 1).getObservedAt();
                    Instant end   = readings.get(i).getObservedAt();
                    flags.add(QcFlag.builder()
                            .station(station)
                            .pollutant(pollutant)
                            .windowStart(start)
                            .windowEnd(end)
                            .rule(QcFlag.QcRule.FLATLINE)
                            .severity(QcFlag.Severity.ERROR)
                            .detail(String.format(
                                    "FLATLINE: %d consecutive readings at ~%.4f (tolerance %.5f). " +
                                    "Possible sensor freeze or communication fault.",
                                    n, readings.get(i).getValue(), tol))
                            .build());
                    consecutive = 1; // reset to avoid duplicate flags
                }
            } else {
                consecutive = 1;
            }
        }
        return flags;
    }

    private List<QcFlag> checkStaleData(Station station, String pollutant, List<Reading> readings) {
        List<QcFlag> flags = new ArrayList<>();
        long maxGapMinutes = props.getQc().getStaleDataMinutes();

        for (int i = 1; i < readings.size(); i++) {
            long gapMinutes = ChronoUnit.MINUTES.between(
                    readings.get(i - 1).getObservedAt(),
                    readings.get(i).getObservedAt());
            if (gapMinutes > maxGapMinutes) {
                flags.add(QcFlag.builder()
                        .station(station)
                        .pollutant(pollutant)
                        .windowStart(readings.get(i - 1).getObservedAt())
                        .windowEnd(readings.get(i).getObservedAt())
                        .rule(QcFlag.QcRule.STALE_DATA)
                        .severity(QcFlag.Severity.WARNING)
                        .detail(String.format(
                                "STALE_DATA: gap of %d minutes between readings (threshold: %d min). " +
                                "Data may be missing or delayed.",
                                gapMinutes, maxGapMinutes))
                        .build());
            }
        }
        return flags;
    }

    private List<QcFlag> checkIsolatedSpike(Station station, String pollutant,
                                             List<Reading> readings,
                                             Map<Long, Double> neighbourValues) {
        List<QcFlag> flags = new ArrayList<>();
        int minStations = props.getQc().getIsolatedSpikeMinStations();
        double ratioThreshold = props.getQc().getIsolatedSpikeRatio();

        if (neighbourValues.size() < minStations) return flags;

        Reading latest = readings.get(readings.size() - 1);
        double stationValue = latest.getValue();
        if (stationValue <= 0) return flags;

        double neighbourAvg = neighbourValues.values().stream()
                .mapToDouble(Double::doubleValue)
                .filter(v -> v > 0)
                .average()
                .orElse(0);

        if (neighbourAvg > 0 && (stationValue / neighbourAvg) >= ratioThreshold) {
            flags.add(QcFlag.builder()
                    .station(station)
                    .pollutant(pollutant)
                    .windowStart(latest.getObservedAt())
                    .windowEnd(latest.getObservedAt())
                    .rule(QcFlag.QcRule.ISOLATED_SPIKE)
                    .severity(QcFlag.Severity.ERROR)
                    .detail(String.format(
                            "ISOLATED_SPIKE: station value %.2f is %.1fx neighbour average %.2f " +
                            "(threshold: %.1fx). Only this station is elevated — possible sensor fault.",
                            stationValue, stationValue / neighbourAvg, neighbourAvg, ratioThreshold))
                    .build());
        }
        return flags;
    }

    private AeroCauseProperties.RangeProps defaultRange(String pollutant) {
        AeroCauseProperties.RangeProps r = new AeroCauseProperties.RangeProps();
        r.setMin(0);
        r.setMax(switch (pollutant) {
            case "PM25" -> 1000.0;
            case "PM10" -> 1500.0;
            case "CO"   -> 50.0;
            default     -> 500.0;
        });
        return r;
    }
}
