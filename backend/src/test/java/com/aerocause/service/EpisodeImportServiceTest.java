package com.aerocause.service;

import com.aerocause.domain.Episode;
import com.aerocause.repository.EpisodeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpisodeImportServiceTest {
    @Mock EpisodeRepository episodeRepo;
    @Mock AuditService auditService;

    @TempDir Path tempDir;

    @Test
    void importsRealEpisodeWithoutInventingExpectedLabels() throws Exception {
        createRealDataFiles("""
                {"episodes":[{"name":"Field export","readings_file":"readings.csv"}]}
                """);
        when(episodeRepo.findByReadingsFile("real/readings.csv")).thenReturn(Optional.empty());
        when(episodeRepo.save(any(Episode.class))).thenAnswer(call -> call.getArgument(0));

        List<java.util.Map<String, Object>> imported = service()
                .importRealEpisodes();

        ArgumentCaptor<Episode> saved = ArgumentCaptor.forClass(Episode.class);
        verify(episodeRepo).save(saved.capture());
        assertThat(saved.getValue().getSynthetic()).isFalse();
        assertThat(saved.getValue().getExpectedTopHypothesis()).isNull();
        assertThat(saved.getValue().getExpectedFault()).isNull();
        assertThat(imported).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("synthetic", false)
                    .containsEntry("expectedTopHypothesis", null)
                    .containsEntry("expectedFault", null);
        });
        verify(auditService).log(eq("REAL_EPISODES_IMPORTED"), isNull(), any());
    }

    @Test
    void rejectsManifestPathsOutsideRealDataDirectory() throws Exception {
        createRealDataFiles("""
                {"episodes":[{"name":"Invalid","readings_file":"../outside.csv"}]}
                """);

        assertThatThrownBy(() -> service().importRealEpisodes())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must exist under data/real");

        verify(episodeRepo, never()).save(any());
        verify(auditService, never()).log(any(), any(), any());
    }

    private EpisodeImportService service() {
        return new EpisodeImportService(episodeRepo, auditService,
                new ObjectMapper(), tempDir.resolve("data").toString());
    }

    private void createRealDataFiles(String manifest) throws Exception {
        Path realDir = tempDir.resolve("data").resolve("real");
        Files.createDirectories(realDir);
        Files.writeString(realDir.resolve("manifest.json"), manifest);
        Files.writeString(realDir.resolve("readings.csv"),
                "station_external_id,pollutant,value,observed_at\n");
    }
}
