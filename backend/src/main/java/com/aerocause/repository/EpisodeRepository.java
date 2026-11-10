package com.aerocause.repository;

import com.aerocause.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    Optional<Episode> findByReadingsFile(String readingsFile);
}
