package com.aerocause.service.replay;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.ingestion.IngestionPipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock EpisodeRepository episodeRepo;
    @Mock StationRepository stationRepo;
    @Mock IngestionPipeline pipeline;
    @Mock AuditEventRepository auditRepo;

    @TempDir Path tempDir;

    private ReplayService replayService;
    private Station station;

    @BeforeEach
    void setUp() {
        replayService = new ReplayService(
                episodeRepo, stationRepo, pipeline, auditRepo, new ObjectMapper());
        station = Station.builder()
                .id(1L).externalId("STN-001").name("Central Monitor")
                .city("Vayu Nagar").lat(18.5).lon(73.8).provider("SYNTHETIC")
                .build();
        lenient().when(auditRepo.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void replayProcessesTimestampSlicesAndTracksCounts() throws Exception {
        Path readings = tempDir.resolve("readings.csv");
        Files.writeString(readings, """
                station_external_id,pollutant,value,unit,observed_at,source
                STN-001,PM25,50,ugm3,2024-01-01T10:00:00Z,SYNTHETIC
                STN-001,PM25,60,ugm3,2024-01-01T11:00:00Z,SYNTHETIC
                """);
        Episode episode = Episode.builder().id(1L).readingsFile(readings.toString()).build();
        when(episodeRepo.findById(1L)).thenReturn(Optional.of(episode));
        when(stationRepo.findByExternalId("STN-001")).thenReturn(Optional.of(station));
        when(pipeline.ingest(any(), anyList(), anyList(), eq(1L), anyString())).thenReturn(1);

        replayService.start(1L, 10_000);
        awaitReplay();

        ReplayService.ReplayStatus status = replayService.getStatus();
        assertThat(status.running()).isFalse();
        assertThat(status.progress()).isEqualTo(1.0);
        assertThat(status.readingsProcessed()).isEqualTo(2);
        assertThat(status.incidentsCreated()).isEqualTo(2);
        verify(pipeline, times(2)).ingest(eq(station), anyList(), anyList(), eq(1L), anyString());
        verify(auditRepo).save(argThat(event -> event.getEventType().equals("REPLAY_COMPLETE")));
    }

    @Test
    void startRejectsInvalidSpeedAndUnknownEpisode() {
        assertThatThrownBy(() -> replayService.start(1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speedMultiplier");

        when(episodeRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> replayService.start(99L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Episode not found");
        assertThat(replayService.getStatus().running()).isFalse();
    }

    @Test
    void missingReadingsFileIsReportedInFailureAudit() throws Exception {
        Episode episode = Episode.builder().id(2L)
                .readingsFile(tempDir.resolve("missing.csv").toString()).build();
        when(episodeRepo.findById(2L)).thenReturn(Optional.of(episode));

        replayService.start(2L, 1000);
        awaitReplay();

        assertThat(replayService.getStatus().running()).isFalse();
        verify(auditRepo).save(argThat(event ->
                event.getEventType().equals("REPLAY_FAILED")
                        && event.getPayloadJson().contains("CSV file not found")));
    }

    private void awaitReplay() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (replayService.getStatus().running() && Instant.now().isBefore(deadline)) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(replayService.getStatus().running()).isFalse();
    }
}
