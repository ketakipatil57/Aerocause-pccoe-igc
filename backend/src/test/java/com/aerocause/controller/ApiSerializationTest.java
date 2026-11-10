package com.aerocause.controller;

import com.aerocause.domain.Incident;
import com.aerocause.domain.Reading;
import com.aerocause.domain.Station;
import com.aerocause.repository.IncidentRepository;
import com.aerocause.repository.ReadingRepository;
import com.aerocause.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aerocause.ai-service.base-url=http://127.0.0.1:1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSerializationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void readingsEndpointSerializesWithoutLazyStationRelationship() throws Exception {
        Station station = stationRepository.findById(1L).orElseThrow();
        Instant observedAt = Instant.now().minusSeconds(10);
        readingRepository.save(Reading.builder()
                .station(station)
                .pollutant("PM25")
                .value(42.0)
                .unit("ug/m3")
                .observedAt(observedAt)
                .fetchedAt(observedAt)
                .source("TEST")
                .build());

        mockMvc.perform(get("/api/stations/{id}/readings", station.getId())
                        .param("from", observedAt.minusSeconds(1).toString())
                        .param("to", observedAt.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pollutant").value("PM25"))
                .andExpect(jsonPath("$[0].station").doesNotExist());
    }

    @Test
    void investigateEndpointResolvesDetachedStationBeforeBuildingEvidence() throws Exception {
        Station station = stationRepository.findById(1L).orElseThrow();
        Instant detectedAt = Instant.now().minusSeconds(60);
        Incident incident = incidentRepository.saveAndFlush(Incident.builder()
                .station(station)
                .pollutant("PM25")
                .detectedAt(detectedAt)
                .windowStart(detectedAt.minusSeconds(3600))
                .windowEnd(detectedAt.plusSeconds(3600))
                .peakValue(80.0)
                .baselineValue(30.0)
                .status(Incident.IncidentStatus.NEW)
                .synthetic(true)
                .build());

        mockMvc.perform(post("/api/incidents/{id}/investigate", incident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefId").exists())
                .andExpect(jsonPath("$.llmMode").value("MOCK"));
    }
}
