package com.aerocause.service.ingestion;

import com.aerocause.domain.Station;
import com.aerocause.domain.WeatherObs;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/**
 * Open-Meteo weather provider stub.
 *
 * <p>Open-Meteo: https://open-meteo.com/en/docs — no API key required.
 * Fields: wind_speed_10m, wind_direction_10m.
 * Note: Boundary layer height is not in Open-Meteo free tier.
 *
 * <p>For the prototype demo, weather is imported from synthetic CSV data.
 */
@Component
public class OpenMeteoProvider implements WeatherProvider {

    @Override
    public List<WeatherObs> fetchWeather(Station station, Instant from, Instant to) {
        // Live weather fetch not implemented for prototype demo.
        // Weather is ingested via replay CSV.
        return Collections.emptyList();
    }

    @Override
    public String providerName() { return "OPEN_METEO"; }
}
