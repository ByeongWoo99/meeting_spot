package com.meetingspot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import com.meetingspot.repository.PlaceCacheRepository;
import com.meetingspot.repository.SearchSessionRepository;
import com.meetingspot.repository.StationResultRepository;
import com.meetingspot.repository.UserTransitResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private SearchSessionRepository sessionRepo;
    @Mock private StationResultRepository stationResultRepo;
    @Mock private UserTransitResultRepository userTransitResultRepo;
    @Mock private PlaceCacheRepository placeCacheRepo;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AnalyticsService analyticsService;

    // ── parseOrGenerateUUID ──────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 UUID 문자열 → 동일한 UUID 반환")
    void parseOrGenerateUUID_validString() {
        UUID expected = UUID.randomUUID();
        UUID result = analyticsService.parseOrGenerateUUID(expected.toString());
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 입력 → 랜덤 UUID 생성")
    void parseOrGenerateUUID_null() {
        UUID result = analyticsService.parseOrGenerateUUID(null);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("빈 문자열 → 랜덤 UUID 생성")
    void parseOrGenerateUUID_blank() {
        UUID result = analyticsService.parseOrGenerateUUID("   ");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("잘못된 UUID 형식 → 랜덤 UUID 생성")
    void parseOrGenerateUUID_invalidFormat() {
        UUID result = analyticsService.parseOrGenerateUUID("not-a-uuid");
        assertThat(result).isNotNull();
    }

    // ── determineStatus ──────────────────────────────────────────────────────

    @Test
    @DisplayName("null 응답 → NO_RESULT")
    void determineStatus_nullResponse() {
        assertThat(analyticsService.determineStatus(null)).isEqualTo("NO_RESULT");
    }

    @Test
    @DisplayName("candidates가 null → NO_RESULT")
    void determineStatus_nullCandidates() {
        MidpointResponse response = MidpointResponse.builder().candidates(null).build();
        assertThat(analyticsService.determineStatus(response)).isEqualTo("NO_RESULT");
    }

    @Test
    @DisplayName("빈 candidates 목록 → NO_RESULT")
    void determineStatus_emptyCandidates() {
        MidpointResponse response = MidpointResponse.builder().candidates(List.of()).build();
        assertThat(analyticsService.determineStatus(response)).isEqualTo("NO_RESULT");
    }

    @Test
    @DisplayName("candidates 1개 이상 → SUCCESS")
    void determineStatus_withCandidates() {
        MidpointResponse.Candidate candidate = MidpointResponse.Candidate.builder()
                .nearestStation("강남역").build();
        MidpointResponse response = MidpointResponse.builder()
                .candidates(List.of(candidate)).build();
        assertThat(analyticsService.determineStatus(response)).isEqualTo("SUCCESS");
    }

    // ── determineRegion ──────────────────────────────────────────────────────

    @Test
    @DisplayName("서울 좌표 → 서울")
    void determineRegion_seoul() {
        List<MidpointRequest.LocationDto> locations = List.of(
                location(37.5, 127.0),
                location(37.55, 126.9)
        );
        assertThat(analyticsService.determineRegion(locations)).isEqualTo("서울");
    }

    @Test
    @DisplayName("경기 좌표(서울 범위 밖) → 경기")
    void determineRegion_gyeonggi() {
        List<MidpointRequest.LocationDto> locations = List.of(
                location(37.2, 127.0),
                location(37.3, 127.1)
        );
        assertThat(analyticsService.determineRegion(locations)).isEqualTo("경기");
    }

    @Test
    @DisplayName("지방 좌표(부산) → 지방")
    void determineRegion_other() {
        List<MidpointRequest.LocationDto> locations = List.of(
                location(35.1, 129.0)
        );
        assertThat(analyticsService.determineRegion(locations)).isEqualTo("지방");
    }

    @Test
    @DisplayName("null → null")
    void determineRegion_null() {
        assertThat(analyticsService.determineRegion(null)).isNull();
    }

    @Test
    @DisplayName("빈 목록 → null")
    void determineRegion_emptyList() {
        assertThat(analyticsService.determineRegion(List.of())).isNull();
    }

    // ── calculateFairnessScore ───────────────────────────────────────────────

    @Test
    @DisplayName("정상 케이스(100, 200) → 2.0")
    void calculateFairnessScore_normal() {
        List<MidpointResponse.UserTransitTime> times = List.of(
                transitTime(100),
                transitTime(200)
        );
        assertThat(analyticsService.calculateFairnessScore(times)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("유효값 1개(나머지 음수) → null")
    void calculateFairnessScore_singleValidValue() {
        List<MidpointResponse.UserTransitTime> times = List.of(
                transitTime(100),
                transitTime(-1)
        );
        assertThat(analyticsService.calculateFairnessScore(times)).isNull();
    }

    @Test
    @DisplayName("min = 0 → null (0으로 나누기 방지)")
    void calculateFairnessScore_minZero() {
        List<MidpointResponse.UserTransitTime> times = List.of(
                transitTime(0),
                transitTime(200)
        );
        assertThat(analyticsService.calculateFairnessScore(times)).isNull();
    }

    @Test
    @DisplayName("빈 목록 → null")
    void calculateFairnessScore_empty() {
        assertThat(analyticsService.calculateFairnessScore(List.of())).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MidpointRequest.LocationDto location(double lat, double lng) {
        MidpointRequest.LocationDto dto = new MidpointRequest.LocationDto();
        dto.setLat(lat);
        dto.setLng(lng);
        return dto;
    }

    private MidpointResponse.UserTransitTime transitTime(int seconds) {
        return MidpointResponse.UserTransitTime.builder()
                .durationSeconds(seconds)
                .build();
    }
}
