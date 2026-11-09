package com.aerocause.service.ingestion;

import com.aerocause.domain.Reading;
import com.aerocause.domain.Station;
import java.time.Instant;
import java.util.List;

/**
 * Interface for air-quality data providers.
 * Implementations: OpenAQ (live), synthetic replay (CSV).
 * Never throws — returns empty list on failure and records the failure in audit_events.
 */
public interface AirQualityProvider {
    List<Reading> fetchLatest(Station station);
    List<Reading> fetchHistorical(Station station, Instant from, Instant to);
    String providerName();
}
