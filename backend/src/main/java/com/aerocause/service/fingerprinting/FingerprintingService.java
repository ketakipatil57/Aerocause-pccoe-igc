package com.aerocause.service.fingerprinting;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.domain.Hypothesis.*;
import com.aerocause.repository.*;
import com.aerocause.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based source-type fingerprinting service.
 *
 * <p>Each rule produces one or more {@link RuleContribution}s — a signed contribution
 * to a hypothesis type, with a human-readable evidence string and an explicit
 * "missing evidence" string when an input is unavailable.
 *
 * <p>INCONCLUSIVE logic: triggered when
 * <ul>
 *   <li>The margin between the top and runner-up score is below the config threshold, OR</li>
 *   <li>Key evidence is missing (each missing piece reduces the effective margin by a penalty), OR</li>
 *   <li>The top score is below 0.10 (no hypothesis has sufficient support).</li>
 * </ul>
 *
 * <p>NEVER names a specific factory, company, road, or person.
 * Only source TYPES are assigned: SENSOR_FAULT, DUST, COMBUSTION_TRAFFIC,
 * BIOMASS_BURNING, INDUSTRIAL, STAGNANT_WEATHER, INCONCLUSIVE.
 *
 * <p>All weights and thresholds are HEURISTIC values from application.yml.
 */
@Service
public class FingerprintingService {

    private final AeroCauseProperties props;
    private final HypothesisRepository hypothesisRepo;
    private final EvidenceItemRepository evidenceRepo;
    private final IncidentRepository incidentRepo;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public FingerprintingService(AeroCauseProperties props,
                                 HypothesisRepository hypothesisRepo,
                                 EvidenceItemRepository evidenceRepo,
                                 IncidentRepository incidentRepo,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.props = props;
        this.hypothesisRepo = hypothesisRepo;
        this.evidenceRepo = evidenceRepo;
        this.incidentRepo = incidentRepo;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Hypothesis> fingerprint(FingerprintingInput input) {
        List<RuleContribution> contributions = new ArrayList<>();
        int evNum = 1;

        evNum = applyPmRatioRule(input, contributions, evNum);
        evNum = applyNo2CoRule(input, contributions, evNum);
        evNum = applySo2Rule(input, contributions, evNum);
        evNum = applyWindRule(input, contributions, evNum);
        evNum = applyFireHotspotRule(input, contributions, evNum);
        evNum = applySensorFaultRule(input, contributions, evNum);

        Map<HypothesisType, Double> scores = aggregateScores(contributions);
        boolean inconclusive = isInconclusive(scores, contributions);

        List<Hypothesis> hypotheses = buildAndSaveHypotheses(
                input.incident(), contributions, scores, inconclusive);

        persistEvidenceItems(input.incident(), contributions);

        // Update incident status
        Hypothesis top = hypotheses.get(0);
        if (top.getType() == HypothesisType.INCONCLUSIVE) {
            Incident inc = input.incident();
            inc.setStatus(Incident.IncidentStatus.INCONCLUSIVE);
            incidentRepo.save(inc);
        }

        auditService.log("FINGERPRINTING_COMPLETE", input.incident().getId(), Map.of(
                "topHypothesis", top.getType().name(),
                "topScore", top.getScore(),
                "inconclusive", inconclusive,
                "contributionCount", contributions.size()
        ));

        return hypotheses;
    }

    // =========================================================================
    // Rules
    // =========================================================================

    private int applyPmRatioRule(FingerprintingInput in,
                                  List<RuleContribution> out, int n) {
        AeroCauseProperties.PmRatioRuleProps cfg =
                props.getFingerprinting().getRules().getPmRatio();
        Double pm25 = in.peakValues().get("PM25");
        Double pm10 = in.peakValues().get("PM10");

        if (pm25 == null || pm10 == null || pm10 <= 0) {
            String missing = "PM2.5/PM10 ratio cannot be computed: " +
                    (pm25 == null ? "PM2.5 unavailable. " : "") +
                    (pm10 == null || pm10 <= 0 ? "PM10 unavailable or zero." : "");
            out.add(new RuleContribution(HypothesisType.DUST, 0,
                    eid(n), "PM ratio rule: insufficient data.", missing));
            return n + 1;
        }

        double ratio = pm25 / pm10;

        if (ratio < cfg.getLowRatioThreshold()) {
            out.add(new RuleContribution(HypothesisType.DUST, cfg.getDustWeight(), eid(n),
                    String.format("PM2.5/PM10 ratio=%.2f < %.2f (low ratio favours coarse-particle " +
                            "sources such as dust). HEURISTIC threshold.", ratio, cfg.getLowRatioThreshold()), null));
            out.add(new RuleContribution(HypothesisType.COMBUSTION_TRAFFIC, -cfg.getCombustionWeight() * 0.5,
                    eid(n + 1),
                    String.format("Low PM2.5/PM10 ratio=%.2f weakens combustion/traffic hypothesis " +
                            "(combustion typically produces fine particles).", ratio), null));
            return n + 2;
        } else if (ratio > cfg.getHighRatioThreshold()) {
            out.add(new RuleContribution(HypothesisType.COMBUSTION_TRAFFIC, cfg.getCombustionWeight(),
                    eid(n),
                    String.format("PM2.5/PM10 ratio=%.2f > %.2f (high ratio favours fine-particle " +
                            "sources such as combustion/traffic). HEURISTIC threshold.",
                            ratio, cfg.getHighRatioThreshold()), null));
            out.add(new RuleContribution(HypothesisType.BIOMASS_BURNING, cfg.getCombustionWeight() * 0.4,
                    eid(n + 1),
                    String.format("High PM2.5/PM10 ratio=%.2f also consistent with biomass burning.", ratio), null));
            out.add(new RuleContribution(HypothesisType.DUST, -cfg.getDustWeight() * 0.5,
                    eid(n + 2),
                    String.format("High PM2.5/PM10 ratio=%.2f weakens coarse-dust hypothesis.", ratio), null));
            return n + 3;
        } else {
            out.add(new RuleContribution(HypothesisType.DUST, 0, eid(n),
                    String.format("PM2.5/PM10 ratio=%.2f is intermediate (%.2f–%.2f); " +
                            "not diagnostic of dust or combustion.",
                            ratio, cfg.getLowRatioThreshold(), cfg.getHighRatioThreshold()),
                    "Intermediate PM ratio provides no strong evidence for or against any source type."));
            return n + 1;
        }
    }

    private int applyNo2CoRule(FingerprintingInput in,
                                List<RuleContribution> out, int n) {
        AeroCauseProperties.No2CoRuleProps cfg =
                props.getFingerprinting().getRules().getNo2Co();
        Double no2 = in.peakValues().get("NO2");
        Double co  = in.peakValues().get("CO");

        List<String> missing = new ArrayList<>();
        if (no2 == null) missing.add("NO2 reading not available at this station");
        if (co  == null) missing.add("CO reading not available at this station");
        String missingStr = missing.isEmpty() ? null : String.join("; ", missing);

        boolean no2High = no2 != null && no2 > cfg.getNo2ThresholdUgm3();
        boolean coHigh  = co  != null && co  > cfg.getCoThresholdPpm();

        if (no2High && coHigh) {
            out.add(new RuleContribution(HypothesisType.COMBUSTION_TRAFFIC, cfg.getCombustionWeight(),
                    eid(n),
                    String.format("NO2=%.1f µg/m³ (>%.0f) AND CO=%.2f ppm (>%.1f) both elevated. " +
                            "Co-elevation is consistent with combustion/traffic source types. HEURISTIC.",
                            no2, cfg.getNo2ThresholdUgm3(), co, cfg.getCoThresholdPpm()), missingStr));
            return n + 1;
        } else if (no2High) {
            out.add(new RuleContribution(HypothesisType.COMBUSTION_TRAFFIC, cfg.getCombustionWeight() * 0.6,
                    eid(n),
                    String.format("NO2=%.1f µg/m³ elevated (>%.0f) but CO not elevated. " +
                            "Partial combustion signal. HEURISTIC.",
                            no2, cfg.getNo2ThresholdUgm3()), missingStr));
            return n + 1;
        } else if (coHigh) {
            out.add(new RuleContribution(HypothesisType.COMBUSTION_TRAFFIC, cfg.getCombustionWeight() * 0.5,
                    eid(n),
                    String.format("CO=%.2f ppm elevated (>%.1f) but NO2 not elevated. " +
                            "Partial combustion/biomass signal. HEURISTIC.",
                            co, cfg.getCoThresholdPpm()), missingStr));
            out.add(new RuleContribution(HypothesisType.BIOMASS_BURNING, cfg.getCombustionWeight() * 0.3,
                    eid(n + 1),
                    "Elevated CO with lower NO2 is also consistent with biomass burning. HEURISTIC.",
                    missingStr));
            return n + 2;
        } else {
            out.add(new RuleContribution(HypothesisType.COMBUSTION_TRAFFIC,
                    -cfg.getCombustionWeight() * 0.3, eid(n),
                    "NO2 and CO are not elevated — weakens combustion/traffic hypothesis.",
                    missingStr));
            return n + 1;
        }
    }

    private int applySo2Rule(FingerprintingInput in,
                              List<RuleContribution> out, int n) {
        AeroCauseProperties.So2RuleProps cfg =
                props.getFingerprinting().getRules().getSo2();
        Double so2 = in.peakValues().get("SO2");

        if (so2 == null) {
            out.add(new RuleContribution(HypothesisType.INDUSTRIAL, 0, eid(n),
                    "SO2 rule: no SO2 reading available.",
                    "SO2 not available; industrial source cannot be confirmed or excluded via this indicator."));
            return n + 1;
        }

        if (so2 > cfg.getThresholdUgm3()) {
            out.add(new RuleContribution(HypothesisType.INDUSTRIAL, cfg.getIndustrialWeight(),
                    eid(n),
                    String.format("SO2=%.1f µg/m³ elevated (>%.0f). " +
                            "Elevated SO2 is associated with industrial/combustion source types. " +
                            "SO2 alone is not diagnostic — other source types also emit SO2. HEURISTIC.",
                            so2, cfg.getThresholdUgm3()), null));
        } else {
            out.add(new RuleContribution(HypothesisType.INDUSTRIAL,
                    -cfg.getIndustrialWeight() * 0.2, eid(n),
                    String.format("SO2=%.1f µg/m³ not elevated (threshold %.0f). " +
                            "Weakly reduces industrial source likelihood.", so2, cfg.getThresholdUgm3()), null));
        }
        return n + 1;
    }

    private int applyWindRule(FingerprintingInput in,
                               List<RuleContribution> out, int n) {
        AeroCauseProperties.WindRuleProps cfg =
                props.getFingerprinting().getRules().getWind();

        if (in.weatherWindow() == null || in.weatherWindow().isEmpty()) {
            out.add(new RuleContribution(HypothesisType.STAGNANT_WEATHER, 0, eid(n),
                    "Wind/weather rule: no weather data available.",
                    "No weather observations for this station+window; stagnant-weather and " +
                    "regional-spread assessments are unavailable."));
            return n + 1;
        }

        OptionalDouble avgWindOpt = in.weatherWindow().stream()
                .filter(w -> w.getWindSpeed() != null)
                .mapToDouble(WeatherObs::getWindSpeed).average();
        OptionalDouble avgBlhOpt = in.weatherWindow().stream()
                .filter(w -> w.getBoundaryLayerHeight() != null)
                .mapToDouble(WeatherObs::getBoundaryLayerHeight).average();

        if (avgWindOpt.isEmpty()) {
            out.add(new RuleContribution(HypothesisType.STAGNANT_WEATHER, 0, eid(n),
                    "Wind speed missing in weather observations.",
                    "Wind speed not recorded; stagnant-weather assessment unavailable."));
            return n + 1;
        }

        double avgWind = avgWindOpt.getAsDouble();
        boolean lowWind = avgWind < cfg.getLowWindThreshold();
        boolean lowBlh  = avgBlhOpt.isPresent() &&
                avgBlhOpt.getAsDouble() < cfg.getStagnantBlhThreshold();
        String blhNote    = avgBlhOpt.isPresent()
                ? String.format("BLH=%.0f m", avgBlhOpt.getAsDouble()) : "BLH not available";
        String missingBlh = avgBlhOpt.isEmpty()
                ? "BLH not available; stagnant-weather confidence reduced." : null;

        if (lowWind && lowBlh) {
            out.add(new RuleContribution(HypothesisType.STAGNANT_WEATHER, cfg.getStagnantWeight(),
                    eid(n),
                    String.format("Low wind=%.1f m/s (<%.1f) AND low %s (<%.0f m). " +
                            "Conditions consistent with stagnant atmosphere. HEURISTIC.",
                            avgWind, cfg.getLowWindThreshold(), blhNote,
                            cfg.getStagnantBlhThreshold()), missingBlh));
        } else if (lowWind) {
            out.add(new RuleContribution(HypothesisType.STAGNANT_WEATHER,
                    cfg.getStagnantWeight() * 0.6, eid(n),
                    String.format("Low wind=%.1f m/s (<%.1f). %s. Partial stagnant-weather signal. HEURISTIC.",
                            avgWind, cfg.getLowWindThreshold(), blhNote), missingBlh));
        } else {
            out.add(new RuleContribution(HypothesisType.STAGNANT_WEATHER,
                    -cfg.getStagnantWeight() * 0.3, eid(n),
                    String.format("Wind=%.1f m/s is above stagnant threshold (%.1f m/s). " +
                            "Weakens stagnant-weather hypothesis. %s. HEURISTIC.",
                            avgWind, cfg.getLowWindThreshold(), blhNote), missingBlh));
        }

        // Regional spread
        if (in.neighbourPeaks() != null && in.neighbourPeaks().size() >= 2) {
            double baselinePm25 = in.baselineValues().getOrDefault("PM25", 0.0);
            long elevated = in.neighbourPeaks().values().stream()
                    .filter(v -> v > baselinePm25 * 1.5).count();
            if (elevated >= 2) {
                out.add(new RuleContribution(HypothesisType.BIOMASS_BURNING,
                        cfg.getRegionalSpreadWeight(), eid(n + 1),
                        String.format("%d of %d neighbouring stations also show elevated PM2.5. " +
                                "Regional spread pattern suggests transport from a distant source. HEURISTIC.",
                                elevated, in.neighbourPeaks().size()), null));
                return n + 2;
            }
        }
        return n + 1;
    }

    private int applyFireHotspotRule(FingerprintingInput in,
                                      List<RuleContribution> out, int n) {
        AeroCauseProperties.FireHotspotRuleProps cfg =
                props.getFingerprinting().getRules().getFireHotspot();

        if (!in.fireHotspotEnabled()) {
            out.add(new RuleContribution(HypothesisType.BIOMASS_BURNING, 0, eid(n),
                    "Fire hotspot provider is DISABLED (feature flag off).",
                    "Fire hotspot data not available (provider disabled); biomass-burning hypothesis " +
                    "cannot be confirmed or excluded via this indicator."));
            return n + 1;
        }

        if (in.fireHotspotUpwind()) {
            out.add(new RuleContribution(HypothesisType.BIOMASS_BURNING, cfg.getBiomassWeight(),
                    eid(n),
                    "Fire hotspot detected upwind of this station. Elevated PM2.5 with upwind fire is " +
                    "consistent with biomass-burning transport. HEURISTIC — data from simulated provider.",
                    null));
        } else {
            out.add(new RuleContribution(HypothesisType.BIOMASS_BURNING,
                    -cfg.getBiomassWeight() * 0.3, eid(n),
                    "No fire hotspot detected upwind. Weakens biomass-burning hypothesis. HEURISTIC.",
                    null));
        }
        return n + 1;
    }

    private int applySensorFaultRule(FingerprintingInput in,
                                      List<RuleContribution> out, int n) {
        AeroCauseProperties.SensorFaultRuleProps cfg =
                props.getFingerprinting().getRules().getSensorFault();

        long seriousFlags = in.qcFlags() == null ? 0 : in.qcFlags().stream()
                .filter(f -> f.getSeverity() == QcFlag.Severity.ERROR)
                .count();

        boolean isolated = in.neighbourPeaks() != null && !in.neighbourPeaks().isEmpty() &&
                in.neighbourPeaks().values().stream()
                        .allMatch(v -> v < (in.peakValues().getOrDefault("PM25", 0.0) * 0.4));

        if (seriousFlags > 0) {
            out.add(new RuleContribution(HypothesisType.SENSOR_FAULT, cfg.getQcFlagWeight(),
                    eid(n),
                    String.format("%d serious QC flag(s) on this station (FLATLINE/OUT_OF_RANGE/" +
                            "ISOLATED_SPIKE). Sensor data quality is in doubt. HEURISTIC.", seriousFlags),
                    null));
            n++;
        }

        if (isolated && seriousFlags > 0) {
            out.add(new RuleContribution(HypothesisType.SENSOR_FAULT, cfg.getIsolationWeight(),
                    eid(n),
                    "Anomaly isolated to this station; neighbouring stations show no elevation. " +
                    "Isolated spike pattern is strongly consistent with sensor malfunction. HEURISTIC.",
                    null));
            n++;
        } else if (seriousFlags == 0) {
            out.add(new RuleContribution(HypothesisType.SENSOR_FAULT,
                    -cfg.getQcFlagWeight() * 0.4, eid(n),
                    "No serious QC flags found; sensor fault less likely. HEURISTIC.", null));
            n++;
        }
        return n;
    }

    // =========================================================================
    // Score aggregation and INCONCLUSIVE logic
    // =========================================================================

    private Map<HypothesisType, Double> aggregateScores(List<RuleContribution> contributions) {
        Map<HypothesisType, Double> scores = new EnumMap<>(HypothesisType.class);
        for (HypothesisType t : HypothesisType.values()) {
            if (t != HypothesisType.INCONCLUSIVE) scores.put(t, 0.0);
        }
        for (RuleContribution c : contributions) {
            if (c.hypothesisType() != HypothesisType.INCONCLUSIVE) {
                scores.merge(c.hypothesisType(), c.score(), Double::sum);
            }
        }
        // Clamp to [0, 1]
        scores.replaceAll((k, v) -> Math.max(0.0, Math.min(1.0, v)));
        return scores;
    }

    private boolean isInconclusive(Map<HypothesisType, Double> scores,
                                    List<RuleContribution> contributions) {
        double marginThreshold = props.getFingerprinting().getInconclusiveMarginThreshold();
        double missingPenalty  = props.getFingerprinting().getInconclusiveMissingPenalty();

        List<Double> sorted = scores.values().stream()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        if (sorted.isEmpty()) return true;

        double top    = sorted.get(0);
        double second = sorted.size() > 1 ? sorted.get(1) : 0.0;
        double margin = top - second;

        long missingCount = contributions.stream()
                .filter(RuleContribution::hasMissingEvidence).count();

        double effectiveMargin = margin - (missingCount * missingPenalty);

        return effectiveMargin < marginThreshold || top < 0.10;
    }

    // =========================================================================
    // Build and save Hypothesis entities
    // =========================================================================

    private List<Hypothesis> buildAndSaveHypotheses(Incident incident,
                                                     List<RuleContribution> contributions,
                                                     Map<HypothesisType, Double> scores,
                                                     boolean inconclusive) {
        List<Hypothesis> result = new ArrayList<>();
        int rank = 1;

        if (inconclusive) {
            List<String> allMissing = contributions.stream()
                    .filter(RuleContribution::hasMissingEvidence)
                    .map(RuleContribution::missingEvidence)
                    .distinct().collect(Collectors.toList());
            Hypothesis inc = Hypothesis.builder()
                    .incident(incident)
                    .type(HypothesisType.INCONCLUSIVE)
                    .score(0.0)
                    .confidenceLabel(ConfidenceLabel.LOW)
                    .evidenceForJson(toJson(List.of()))
                    .evidenceAgainstJson(toJson(List.of()))
                    .missingJson(toJson(allMissing))
                    .rank(rank++)
                    .build();
            result.add(hypothesisRepo.save(inc));
        }

        List<Map.Entry<HypothesisType, Double>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<HypothesisType, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        for (Map.Entry<HypothesisType, Double> entry : sorted) {
            HypothesisType type = entry.getKey();
            double score = entry.getValue();

            List<String> evFor = contributions.stream()
                    .filter(c -> c.hypothesisType() == type && c.isFor())
                    .map(c -> c.evidenceId() + ": " + c.summary())
                    .collect(Collectors.toList());
            List<String> evAgainst = contributions.stream()
                    .filter(c -> c.hypothesisType() == type && c.isAgainst())
                    .map(c -> c.evidenceId() + ": " + c.summary())
                    .collect(Collectors.toList());
            List<String> missing = contributions.stream()
                    .filter(c -> c.hypothesisType() == type && c.hasMissingEvidence())
                    .map(RuleContribution::missingEvidence).distinct()
                    .collect(Collectors.toList());

            Hypothesis h = Hypothesis.builder()
                    .incident(incident)
                    .type(type)
                    .score(score)
                    .confidenceLabel(deriveConfidence(score, scores))
                    .evidenceForJson(toJson(evFor))
                    .evidenceAgainstJson(toJson(evAgainst))
                    .missingJson(toJson(missing))
                    .rank(rank++)
                    .build();
            result.add(hypothesisRepo.save(h));
        }
        return result;
    }

    private ConfidenceLabel deriveConfidence(double score, Map<HypothesisType, Double> scores) {
        List<Double> sorted = scores.values().stream()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        double top    = sorted.get(0);
        double second = sorted.size() > 1 ? sorted.get(1) : 0.0;
        double margin = top - second;
        if (score == top) {
            if (score >= 0.6 && margin >= 0.2) return ConfidenceLabel.HIGH;
            if (score >= 0.3 && margin >= 0.1) return ConfidenceLabel.MEDIUM;
        }
        return ConfidenceLabel.LOW;
    }

    private void persistEvidenceItems(Incident incident, List<RuleContribution> contributions) {
        for (RuleContribution c : contributions) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("score", c.score());
            data.put("hypothesisType", c.hypothesisType().name());
            if (c.hasMissingEvidence()) data.put("missingEvidence", c.missingEvidence());
            EvidenceItem item = EvidenceItem.builder()
                    .incident(incident)
                    .type(c.hypothesisType().name())
                    .summary(c.summary())
                    .dataJson(toJson(data))
                    .sourceRef(c.evidenceId())
                    .build();
            evidenceRepo.save(item);
        }
    }

    private String eid(int n) { return "EV-" + String.format("%03d", n); }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }
}
