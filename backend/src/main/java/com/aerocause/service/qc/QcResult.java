package com.aerocause.service.qc;

import com.aerocause.domain.QcFlag;
import java.util.List;

/**
 * Result of running all QC rules against a window of readings for one station+pollutant.
 */
public record QcResult(
        boolean hasSeriousFlags,
        List<QcFlag> flags
) {
    public boolean isSuspect() { return hasSeriousFlags; }
}
