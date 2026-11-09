package com.aerocause.service;

import com.aerocause.config.AeroCauseProperties;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * CPCB-style AQI calculation using breakpoints from application.yml.
 *
 * <p>IMPORTANT: The breakpoints used here are based on CPCB National AQI guidelines.
 * Verify these breakpoints against the official CPCB document before use in any
 * official capacity.  Source: https://cpcb.nic.in/
 *
 * <p>AQI sub-index formula (linear interpolation between breakpoints):
 * <pre>
 *   I = ((Ihigh - Ilow) / (Chigh - Clow)) * (C - Clow) + Ilow
 * </pre>
 * Overall AQI = max of all sub-indexes.
 */
@Service
public class AqiService {

    private final AeroCauseProperties props;

    public AqiService(AeroCauseProperties props) {
        this.props = props;
    }

    public record AqiResult(
            int aqi,
            String category,
            String dominantPollutant,
            Map<String, Integer> subIndexes
    ) {}

    /**
     * Compute AQI from a map of pollutant -> concentration.
     * Only pollutants with configured breakpoints are used.
     */
    public AqiResult computeAqi(Map<String, Double> concentrations) {
        Map<String, Integer> subIndexes = new LinkedHashMap<>();
        Map<String, List<AeroCauseProperties.BreakpointProps>> bps =
                props.getAqi().getBreakpoints();

        for (Map.Entry<String, Double> entry : concentrations.entrySet()) {
            String pollutant = entry.getKey();
            Double concentration = entry.getValue();
            if (pollutant == null || pollutant.isBlank() || concentration == null
                    || !Double.isFinite(concentration)) {
                continue;
            }
            pollutant = pollutant.trim().toUpperCase(Locale.ROOT);
            List<AeroCauseProperties.BreakpointProps> pollBps = bps.get(pollutant);
            if (pollBps == null || pollBps.isEmpty()) continue;
            int sub = computeSubIndex(concentration, pollBps);
            if (sub >= 0) subIndexes.put(pollutant, sub);
        }

        if (subIndexes.isEmpty()) {
            return new AqiResult(0, "No Data", "N/A", subIndexes);
        }

        int overallAqi = Collections.max(subIndexes.values());
        String dominant = subIndexes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("N/A");

        return new AqiResult(overallAqi, category(overallAqi), dominant, subIndexes);
    }

    /**
     * Compute sub-index for one pollutant.
     * Returns -1 if concentration is out of all configured ranges.
     */
    public int computeSubIndex(double concentration,
                                List<AeroCauseProperties.BreakpointProps> breakpoints) {
        if (!Double.isFinite(concentration) || concentration < 0
                || breakpoints == null || breakpoints.isEmpty()) {
            return -1;
        }

        // CPCB breakpoints are expressed as whole-number concentration ranges.
        double normalizedConcentration = Math.round(concentration);
        for (AeroCauseProperties.BreakpointProps bp : breakpoints) {
            if (bp == null || bp.getChigh() <= bp.getClow()) continue;
            if (normalizedConcentration >= bp.getClow()
                    && normalizedConcentration <= bp.getChigh()) {
                double index = ((bp.getIhigh() - bp.getIlow()) / (bp.getChigh() - bp.getClow()))
                        * (normalizedConcentration - bp.getClow()) + bp.getIlow();
                return (int) Math.round(index);
            }
        }
        // Above highest breakpoint — cap at max index
        AeroCauseProperties.BreakpointProps last = breakpoints.get(breakpoints.size() - 1);
        if (last != null && normalizedConcentration > last.getChigh()) {
            return (int) last.getIhigh();
        }
        return -1;
    }

    /**
     * CPCB AQI category boundaries.
     * IMPORTANT: Verify against official CPCB document before production use.
     */
    public String category(int aqi) {
        if (aqi <= 50)  return "Good";
        if (aqi <= 100) return "Satisfactory";
        if (aqi <= 200) return "Moderate";
        if (aqi <= 300) return "Poor";
        if (aqi <= 400) return "Very Poor";
        return "Severe";
    }
}
