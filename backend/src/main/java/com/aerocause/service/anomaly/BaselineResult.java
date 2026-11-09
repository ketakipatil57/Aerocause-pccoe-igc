package com.aerocause.service.anomaly;

/**
 * Result of baseline computation for a station+pollutant at a given hour.
 */
public record BaselineResult(
        double median,
        double mad,          // Median Absolute Deviation
        int sampleCount,
        boolean shortWindow, // true if less than full baseline-days history used
        String note          // human-readable note about baseline quality
) {}
