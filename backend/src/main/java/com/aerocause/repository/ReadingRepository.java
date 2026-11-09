package com.aerocause.repository;

import com.aerocause.domain.Reading;
import com.aerocause.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingRepository extends JpaRepository<Reading, Long> {

    Optional<Reading> findByStationAndPollutantAndObservedAt(
            Station station, String pollutant, Instant observedAt);

    List<Reading> findByStationAndPollutantAndObservedAtBetweenOrderByObservedAtAsc(
            Station station, String pollutant, Instant from, Instant to);

    List<Reading> findByStationAndObservedAtBetweenOrderByObservedAtAsc(
            Station station, Instant from, Instant to);

    /** Same hour-of-day readings over the past N days, for baseline computation.
     *  Uses FUNCTION('HOUR', ...) for H2/MySQL compatibility. */
    @Query("""
        SELECT r FROM Reading r
        WHERE r.station = :station
          AND r.pollutant = :pollutant
          AND r.observedAt >= :since
          AND r.observedAt < :before
          AND r.qualityStatus <> 'BAD'
        ORDER BY r.observedAt ASC
        """)
    List<Reading> findBaselineReadingsRaw(
            @Param("station") Station station,
            @Param("pollutant") String pollutant,
            @Param("since") Instant since,
            @Param("before") Instant before);

    /** Same hour-of-day readings — filtered in Java after fetch to avoid H2/MySQL dialect issues */
    default List<Reading> findBaselineReadings(Station station, String pollutant,
                                               Instant since, Instant before, int hour) {
        return findBaselineReadingsRaw(station, pollutant, since, before).stream()
                .filter(r -> r.getObservedAt().atZone(java.time.ZoneOffset.UTC).getHour() == hour)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Most recent N readings for a station+pollutant */
    List<Reading> findTop10ByStationAndPollutantOrderByObservedAtDesc(
            Station station, String pollutant);

    /** All readings for a station+pollutant in a time window, ordered by time */
    List<Reading> findByStationIdAndPollutantAndObservedAtBetweenOrderByObservedAtAsc(
            Long stationId, String pollutant, Instant from, Instant to);
}
