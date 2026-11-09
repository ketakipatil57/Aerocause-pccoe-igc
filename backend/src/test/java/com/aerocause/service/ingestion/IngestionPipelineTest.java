package com.aerocause.service.ingestion;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.AuditService;
import com.aerocause.service.anomaly.AnomalyDetectionService;
import com.aerocause.service.fingerprinting.FingerprintingService;
import com.aerocause.service.qc.QcResult;
import com.aerocause.service.qc.QcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionPipelineTest {

    @Mock StationRepository stationRepo;
    @Mock ReadingRepository readingRepo;
    @Mock WeatherObsRepository weatherRepo;
    @Mock RawPayloadRepository rawPayloadRepo;
    @Mock QcService qcService;
    @Mock AnomalyDetectionService anomalyService;
    @Mock FingerprintingService fingerprintingService;
    @Mock AuditService auditService;

    private IngestionPipeline pipeline;
    private Station station;

    @BeforeEach
    void setUp() {
        pipeline = new IngestionPipeline(stationRepo, readingRepo, weatherRepo, rawPayloadRepo,
                qcService, anomalyService, fingerprintingService, auditService,
                new ObjectMapper(), new AeroCauseProperties());
        station = Station.builder()
                .id(1L).externalId("STN-001").name("Central Monitor")
                .city("Vayu Nagar").lat(18.5).lon(73.8).provider("SYNTHETIC")
                .build();
        when(stationRepo.findAll()).thenReturn(List.of(station));
        lenient().when(readingRepo.findByStationAndPollutantAndObservedAt(any(), anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(readingRepo.save(any(Reading.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(weatherRepo.findByStationAndObservedAt(any(), any())).thenReturn(Optional.empty());
        lenient().when(weatherRepo.save(any(WeatherObs.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(rawPayloadRepo.save(any(RawPayload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(qcService.runQc(any(), anyString(), anyList(), anyMap()))
                .thenReturn(new QcResult(false, List.of()));
        when(anomalyService.detectAnomaly(any(), anyString(), anyList(), anyBoolean(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void ingestPersistsRawReadingsWeatherAndAuditWithoutAnomaly() {
        Instant observedAt = Instant.parse("2024-01-01T10:00:00Z");
        Reading reading = Reading.builder().pollutant("PM25").value(50.0).unit("µg/m³")
                .observedAt(observedAt).source("SYNTHETIC")
                .qualityStatus(Reading.QualityStatus.GOOD).build();
        WeatherObs weather = WeatherObs.builder().observedAt(observedAt)
                .windSpeed(3.0).source("SYNTHETIC").build();

        int incidentsCreated = pipeline.ingest(station, List.of(reading), List.of(weather),
                7L, "{\"sample\":true}");

        assertThat(incidentsCreated).isZero();
        verify(rawPayloadRepo).save(argThat(payload ->
                payload.getSource().equals("SYNTHETIC")
                        && payload.getPayloadJson().equals("{\"sample\":true}")));
        verify(readingRepo).save(reading);
        verify(weatherRepo).save(weather);
        verify(qcService).runQc(eq(station), eq("PM25"), anyList(), anyMap());
        verify(anomalyService).detectAnomaly(eq(station), eq("PM25"), anyList(), eq(false), eq(7L));
        verify(auditService).log(eq("INGESTION_COMPLETED"), isNull(), anyMap());
        verifyNoInteractions(fingerprintingService);
    }

    @Test
    void ingestDoesNotInsertDuplicateReadingsOrWeather() {
        Instant observedAt = Instant.parse("2024-01-01T10:00:00Z");
        Reading existing = Reading.builder().station(station).pollutant("PM25")
                .value(50.0).unit("µg/m³").observedAt(observedAt)
                .source("SYNTHETIC").qualityStatus(Reading.QualityStatus.GOOD).build();
        WeatherObs existingWeather = WeatherObs.builder().station(station)
                .observedAt(observedAt).windSpeed(3.0).source("SYNTHETIC").build();
        when(readingRepo.findByStationAndPollutantAndObservedAt(station, "PM25", observedAt))
                .thenReturn(Optional.of(existing));
        when(weatherRepo.findByStationAndObservedAt(station, observedAt))
                .thenReturn(Optional.of(existingWeather));

        pipeline.ingest(station,
                List.of(Reading.builder().pollutant("PM25").value(50.0).unit("µg/m³")
                        .observedAt(observedAt).source("SYNTHETIC").build()),
                List.of(WeatherObs.builder().observedAt(observedAt).source("SYNTHETIC").build()),
                null, "{}");

        verify(readingRepo, never()).save(any(Reading.class));
        verify(weatherRepo, never()).save(any(WeatherObs.class));
        verify(anomalyService).detectAnomaly(eq(station), eq("PM25"), anyList(), eq(false), isNull());
    }
}
