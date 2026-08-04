package com.meetingspot.service;

import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.entity.TransitCache;
import com.meetingspot.repository.TransitCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransitServiceTest {

    @Mock private WebClient odsayWebClient;
    @Mock private TransitCacheRepository transitCacheRepo;

    private TransitService transitService;

    @BeforeEach
    void setUp() {
        TransitService.resetStats();
        transitService = spy(new TransitService(odsayWebClient, "test-key", transitCacheRepo));
    }

    // ── 캐시 히트 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("캐시 히트 → API 호출 없이 캐시 값 반환")
    void getAllTransitDurations_cacheHit_returnsCachedValue() {
        TransitCache cache = validCache(37.5, 127.0, "강남역", 600);
        when(transitCacheRepo.findByOriginLatAndOriginLngAndStationName(37.5, 127.0, "강남역"))
                .thenReturn(Optional.of(cache));

        int[] result = transitService
                .getAllTransitDurations(List.of(location(37.5, 127.0)), 127.1, 37.6, "강남역")
                .block();

        assertThat(result[0]).isEqualTo(600);
        verify(transitService, never()).getTransitDuration(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(transitCacheRepo, never()).save(any());
    }

    @Test
    @DisplayName("캐시 히트 시 cacheHits 카운트 증가, apiCalls 유지")
    void getAllTransitDurations_cacheHit_statsIncrement() {
        TransitCache cache = validCache(37.5, 127.0, "강남역", 600);
        when(transitCacheRepo.findByOriginLatAndOriginLngAndStationName(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(cache));

        transitService.getAllTransitDurations(List.of(location(37.5, 127.0)), 127.1, 37.6, "강남역").block();

        TransitService.TransitStats stats = TransitService.getStats();
        assertThat(stats.cacheHits()).isEqualTo(1);
        assertThat(stats.apiCalls()).isEqualTo(0);
    }

    // ── 캐시 미스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("캐시 미스 → API 호출 후 결과 반환 및 캐시 저장")
    void getAllTransitDurations_cacheMiss_callsApiAndSaves() {
        when(transitCacheRepo.findByOriginLatAndOriginLngAndStationName(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());
        doReturn(Mono.just(900)).when(transitService)
                .getTransitDuration(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        int[] result = transitService
                .getAllTransitDurations(List.of(location(37.5, 127.0)), 127.1, 37.6, "강남역")
                .block();

        assertThat(result[0]).isEqualTo(900);
        verify(transitCacheRepo).save(any(TransitCache.class));
    }

    @Test
    @DisplayName("캐시 미스 시 apiCalls 카운트 증가, cacheHits 유지")
    void getAllTransitDurations_cacheMiss_statsIncrement() {
        when(transitCacheRepo.findByOriginLatAndOriginLngAndStationName(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());
        doReturn(Mono.just(600)).when(transitService)
                .getTransitDuration(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        transitService.getAllTransitDurations(List.of(location(37.5, 127.0)), 127.1, 37.6, "강남역").block();

        TransitService.TransitStats stats = TransitService.getStats();
        assertThat(stats.cacheHits()).isEqualTo(0);
        assertThat(stats.apiCalls()).isEqualTo(1);
    }

    // ── 캐시 만료 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("캐시 만료 → 기존 캐시 삭제 후 API 재호출 및 새 캐시 저장")
    void getAllTransitDurations_cacheExpired_deletesAndRefreshes() {
        TransitCache expired = expiredCache(37.5, 127.0, "강남역", 600);
        when(transitCacheRepo.findByOriginLatAndOriginLngAndStationName(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(expired));
        doReturn(Mono.just(720)).when(transitService)
                .getTransitDuration(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        int[] result = transitService
                .getAllTransitDurations(List.of(location(37.5, 127.0)), 127.1, 37.6, "강남역")
                .block();

        assertThat(result[0]).isEqualTo(720);
        verify(transitCacheRepo).delete(expired);
        verify(transitCacheRepo).save(any(TransitCache.class));
    }

    // ── 좌표 반올림 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("좌표 소수점 3자리 반올림 후 캐시 조회")
    void getAllTransitDurations_coordinatesRoundedTo3Decimals() {
        when(transitCacheRepo.findByOriginLatAndOriginLngAndStationName(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());
        doReturn(Mono.just(300)).when(transitService)
                .getTransitDuration(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        // lat=37.12345 → 37.123 / lng=127.12345 → 127.123
        transitService.getAllTransitDurations(List.of(location(37.12345, 127.12345)), 127.1, 37.6, "강남역").block();

        verify(transitCacheRepo).findByOriginLatAndOriginLngAndStationName(37.123, 127.123, "강남역");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MidpointRequest.LocationDto location(double lat, double lng) {
        MidpointRequest.LocationDto dto = new MidpointRequest.LocationDto();
        dto.setLat(lat);
        dto.setLng(lng);
        return dto;
    }

    private TransitCache validCache(double lat, double lng, String station, int durationSec) {
        return TransitCache.builder()
                .originLat(lat).originLng(lng).stationName(station)
                .durationSec(durationSec)
                .createdAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(29))
                .build();
    }

    private TransitCache expiredCache(double lat, double lng, String station, int durationSec) {
        return TransitCache.builder()
                .originLat(lat).originLng(lng).stationName(station)
                .durationSec(durationSec)
                .createdAt(LocalDateTime.now().minusDays(31))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
