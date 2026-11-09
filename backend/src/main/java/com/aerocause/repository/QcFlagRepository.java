package com.aerocause.repository;

import com.aerocause.domain.QcFlag;
import com.aerocause.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface QcFlagRepository extends JpaRepository<QcFlag, Long> {
    List<QcFlag> findByStationAndPollutantAndWindowStartGreaterThanEqual(
            Station station, String pollutant, Instant since);
    List<QcFlag> findByStationAndWindowStartBetween(
            Station station, Instant from, Instant to);
}
