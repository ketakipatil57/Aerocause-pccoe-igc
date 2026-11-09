package com.aerocause.service.ingestion;

import com.aerocause.domain.Station;
import com.aerocause.domain.WeatherObs;
import java.time.Instant;
import java.util.List;

/**
 * Interface for weather data providers.
 * Default implementation: Open-Meteo (no API key required).
 */
public interface WeatherProvider {
    List<WeatherObs> fetchWeather(Station station, Instant from, Instant to);
    String providerName();
}
