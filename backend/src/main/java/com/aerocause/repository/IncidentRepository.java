package com.aerocause.repository;

import com.aerocause.domain.Incident;
import com.aerocause.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatus(Incident.IncidentStatus status);
    List<Incident> findByStation(Station station);
    List<Incident> findByEpisodeId(Long episodeId);
    List<Incident> findByEpisodeIdOrderByDetectedAtAsc(Long episodeId);
}
