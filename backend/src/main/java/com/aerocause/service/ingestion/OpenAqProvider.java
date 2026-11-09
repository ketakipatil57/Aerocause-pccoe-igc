package com.aerocause.service.ingestion;

import com.aerocause.domain.Reading;
import com.aerocause.domain.Station;
import com.aerocause.service.AuditService;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/**
 * OpenAQ v3 provider stub.
 *
 * <p>API: OpenAQ v3 at https://api.openaq.org/v3
 * Auth: X-API-Key header (optional for low-rate usage).
 * Key: configured via OPENAQ_API_KEY env variable.
 *
 * <p>Live polling is disabled for the prototype demo.
 * Episode replay is used instead (see ReplayService).
 * Implement fetchLatest/fetchHistorical for production use.
 */
@Component
public class OpenAqProvider implements AirQualityProvider {

    private final AuditService auditService;

    public OpenAqProvider(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public List<Reading> fetchLatest(Station station) {
        auditService.log("PROVIDER_FETCH_SKIPPED", null, Map.of(
                "provider", providerName(),
                "reason", "Live polling disabled; use replay for demo",
                "station", station.getExternalId()
        ));
        return Collections.emptyList();
    }

    @Override
    public List<Reading> fetchHistorical(Station station, Instant from, Instant to) {
        return Collections.emptyList();
    }

    @Override
    public String providerName() { return "OPENAQ_V3"; }
}
