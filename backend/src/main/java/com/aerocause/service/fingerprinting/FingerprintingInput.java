package com.aerocause.service.fingerprinting;

import com.aerocause.domain.*;
import java.util.List;
import java.util.Map;

/**
 * All inputs available for fingerprinting rules for one incident.
 *
 * @param incident               the incident being fingerprinted
 * @param peakValues             pollutant -> peak value in anomaly window
 * @param baselineValues         pollutant -> baseline median
 * @param weatherWindow          weather observations in the incident window
 * @param qcFlags                QC flags on the anomalous station
 * @param neighbourPeaks         station-external-id -> PM25 peak value at same time (other stations)
 * @param fireHotspotEnabled     whether NASA FIRMS feature flag is on
 * @param fireHotspotUpwind      whether any weather obs in the window has fire_hotspot_upwind=true
 */
public record FingerprintingInput(
        Incident incident,
        Map<String, Double> peakValues,
        Map<String, Double> baselineValues,
        List<WeatherObs> weatherWindow,
        List<QcFlag> qcFlags,
        Map<String, Double> neighbourPeaks,
        boolean fireHotspotEnabled,
        boolean fireHotspotUpwind
) {}
