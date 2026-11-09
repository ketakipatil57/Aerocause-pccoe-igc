package com.aerocause.repository;

import com.aerocause.domain.Station;
import com.aerocause.domain.WeatherObs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherObsRepository extends JpaRepository<WeatherObs, Long> {
    Optional<WeatherObs> findByStationAndObservedAt(Station station, Instant observedAt);

    List<WeatherObs> findByStationAndObservedAtBetweenOrderByObservedAtAsc(
            Station station, Instant from, Instant to);

    Optional<WeatherObs> findTopByStationOrderByObservedAtDesc(Station station);
}
