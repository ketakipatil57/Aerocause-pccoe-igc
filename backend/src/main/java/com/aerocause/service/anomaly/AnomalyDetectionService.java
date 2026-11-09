package com.aerocause.service.anomaly;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic anomaly detection using MAD-based robust z-score.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Baseline = median of same-hour readings over past 14 days (configurable).</li>
 *   <li>MAD = Median Absolute Deviation of the baseline set.</li>
 *   <li>Robust z = 0.6745 * (value - median) / MAD  (0.6745 normalises to Gaussian equivalent)</li>
 *   <li>Flag anomaly when: robust_z >= threshold AND absolute_increase >= min_increase
 *       AND it persists for >= K consecutive readings.</li>
 * </ol>
 *
 * <p>Falls back to shorter window (7-day) when history is insufficient.
 * Records the window used in the incident audit event.
 *
 * <p>All thresholds are HEURISTIC values from application.yml.
 * They are NOT scientifically validated.
 */
@Service
public class AnomalyDetectionService {

    // K constant to convert MAD to Gaussian SD equivalent (Rousseeuw & Croux 1993)
    private static final double K_CONSISTENCY = 0.6745;

    private final AeroCauseProperties props;
    private final ReadingRepository readingRepo;
    private final IncidentRepository incidentRepo;
    private final AuditService auditService;

    public AnomalyDetectionService(AeroCauseProperties props,
                                   ReadingRepository readingRepo,
                                   IncidentRepository incidentRepo,
                                   AuditService auditService) {
        this.props = props;
        this.readingRepo = readingRepo;
        this.incidentRepo = incidentRepo;
        this.auditService = auditService;
    }

    /**
     * Check whether the latest K readings for a station+pollutant constitute an anomaly.
     *
     * @param station     the station
     * @param pollutant   pollutant code
     * @param readings    time-ordered recent readings (at least K)
     * @param isSuspect   true if readings are under a serious QC flag
     * @param episodeId   optional episode id (for replay / synthetic data)
     * @return Optional<Incident> — present if anomaly detected
     */
    @Transactional
    public Optional<Incident> detectAnomaly(Station station, String pollutant,
                                            List<Reading> readings,
                                            boolean isSuspect,
                                            Long episodeId) {
        AeroCauseProperties.AnomalyProps cfg = props.getAnomaly();
        int k = cfg.getConsecutiveReadings();

        if (readings == null || readings.size() < k) return Optional.empty();

        List<Reading> recent = readings.subList(readings.size() - k, readings.size());
        Reading latest = recent.get(recent.size() - 1);

        int hour = latest.getObservedAt().atZone(java.time.ZoneOffset.UTC).getHour();
        Instant since14 = latest.getObservedAt().minus(cfg.getBaselineDays(), ChronoUnit.DAYS);
        Instant before  = latest.getObservedAt().minus(1, ChronoUnit.HOURS);

        List<Reading> baselineReadings = readingRepo.findBaselineReadings(
                station, pollutant, since14, before, hour);

        BaselineResult baseline;
        if (baselineReadings.size() >= cfg.getBaselineMinReadings()) {
            baseline = computeBaseline(baselineReadings, false,
                    cfg.getBaselineDays() + "-day same-hour baseline, n=" + baselineReadings.size());
        } else {
            Instant since7 = latest.getObservedAt().minus(7, ChronoUnit.DAYS);
            List<Reading> base7 = readingRepo.findBaselineReadings(
                    station, pollutant, since7, before, hour);
            if (base7.size() >= cfg.getBaselineMinReadings()) {
                baseline = computeBaseline(base7, true,
                        "SHORT WINDOW: 7-day baseline used (insufficient " + cfg.getBaselineDays() +
                        "-day history), n=" + base7.size());
            } else {
                // Not enough history — cannot detect anomaly
                return Optional.empty();
            }
        }

        double minAbsIncrease = cfg.getMinAbsoluteIncrease().getOrDefault(pollutant, 25.0);

        boolean allAbove = recent.stream().allMatch(r -> {
            double z = computeRobustZ(r.getValue(), baseline.median(), baseline.mad());
            double absIncrease = r.getValue() - baseline.median();
            return z >= cfg.getRobustZThreshold() && absIncrease >= minAbsIncrease;
        });

        if (!allAbove) return Optional.empty();

        double peakValue = recent.stream().mapToDouble(Reading::getValue).max().orElse(latest.getValue());

        Incident incident = Incident.builder()
                .station(station)
                .pollutant(pollutant)
                .detectedAt(Instant.now())
                .windowStart(recent.get(0).getObservedAt())
                .windowEnd(latest.getObservedAt())
                .peakValue(peakValue)
                .baselineValue(baseline.median())
                .status(Incident.IncidentStatus.NEW)
                .episodeId(episodeId)
                .synthetic(episodeId != null)
                .build();

        incident = incidentRepo.save(incident);

        auditService.log("INCIDENT_CREATED", incident.getId(), Map.of(
                "station", station.getExternalId(),
                "pollutant", pollutant,
                "peakValue", peakValue,
                "baselineValue", baseline.median(),
                "robustZThreshold", cfg.getRobustZThreshold(),
                "baselineNote", baseline.note(),
                "isSuspect", isSuspect,
                "shortWindow", baseline.shortWindow()
        ));

        return Optional.of(incident);
    }

    /** Compute MAD-based robust z-score. Returns 0.0 when MAD is near zero. */
    public double computeRobustZ(double value, double median, double mad) {
        if (mad < 1e-9) return 0.0;
        return K_CONSISTENCY * (value - median) / mad;
    }

    /** Compute median and MAD from a list of readings. */
    public BaselineResult computeBaseline(List<Reading> readings, boolean shortWindow, String note) {
        List<Double> values = readings.stream()
                .map(Reading::getValue)
                .sorted()
                .collect(Collectors.toList());
        double median = percentile(values, 0.5);
        List<Double> absDevs = values.stream()
                .map(v -> Math.abs(v - median))
                .sorted()
                .collect(Collectors.toList());
        double mad = percentile(absDevs, 0.5);
        return new BaselineResult(median, mad, values.size(), shortWindow, note);
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        int n = sorted.size();
        double index = p * (n - 1);
        int lo = (int) index;
        int hi = Math.min(lo + 1, n - 1);
        double frac = index - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }
}
