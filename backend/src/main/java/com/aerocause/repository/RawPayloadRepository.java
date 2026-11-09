package com.aerocause.repository;

import com.aerocause.domain.RawPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RawPayloadRepository extends JpaRepository<RawPayload, Long> {
    Optional<RawPayload> findTopBySourceOrderByFetchedAtDesc(String source);
}
