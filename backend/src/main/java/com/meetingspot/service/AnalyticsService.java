package com.meetingspot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import com.meetingspot.entity.SearchSession;
import com.meetingspot.entity.StationResult;
import com.meetingspot.entity.UserTransitResult;
import com.meetingspot.repository.PlaceCacheRepository;
import com.meetingspot.repository.SearchSessionRepository;
import com.meetingspot.repository.StationResultRepository;
import com.meetingspot.repository.UserTransitResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SearchSessionRepository sessionRepo;
    private final StationResultRepository stationResultRepo;
    private final UserTransitResultRepository userTransitResultRepo;
    private final PlaceCacheRepository placeCacheRepo;
    private final ObjectMapper objectMapper;

    private static final List<String> ALL_CATEGORY_CODES = List.of("FD6", "CE7", "CT1", "AT4");

    public void save(MidpointRequest request, MidpointResponse response,
                     long durationMs, TransitService.TransitStats stats) {
        UUID sessionKey = parseOrGenerateUUID(request.getSessionKey());
        SearchSession parentSession = resolveParentSession(request.getParentSessionKey());
        String status = determineStatus(response);
        String region = determineRegion(request.getLocations());
        String searchConditions = buildSearchConditions(request.getCategory());

        SearchSession session = sessionRepo.save(SearchSession.builder()
                .sessionKey(sessionKey)
                .userCount(request.getLocations().size())
                .durationMs((int) durationMs)
                .status(status)
                .cacheHitCount(stats.cacheHits())
                .apiCallCount(stats.apiCalls())
                .region(region)
                .searchConditions(searchConditions)
                .parentSession(parentSession)
                .createdAt(OffsetDateTime.now())
                .build());

        if (response == null || response.getCandidates() == null) return;

        for (MidpointResponse.Candidate candidate : response.getCandidates()) {
            if (candidate.getNearestStation() == null) continue;

            String stationName = candidate.getNearestStation().trim();
            List<MidpointResponse.UserTransitTime> transitTimes =
                    candidate.getTransitTimes() != null ? candidate.getTransitTimes() : List.of();

            int totalTransitSec = transitTimes.stream()
                    .mapToInt(MidpointResponse.UserTransitTime::getDurationSeconds)
                    .filter(d -> d >= 0)
                    .sum();

            StationResult stationResult = stationResultRepo.save(StationResult.builder()
                    .session(session)
                    .stationName(stationName)
                    .resultRank(candidate.getRank())
                    .totalTransitSec(totalTransitSec)
                    .placeCounts(buildPlaceCounts(stationName))
                    .fairnessScore(calculateFairnessScore(transitTimes))
                    .build());

            for (int i = 0; i < transitTimes.size(); i++) {
                int sec = transitTimes.get(i).getDurationSeconds();
                if (sec < 0) continue;
                userTransitResultRepo.save(UserTransitResult.builder()
                        .stationResult(stationResult)
                        .userIndex(i + 1)
                        .transitSec(sec)
                        .build());
            }
        }
    }

    UUID parseOrGenerateUUID(String sessionKey) {
        if (sessionKey != null && !sessionKey.isBlank()) {
            try { return UUID.fromString(sessionKey); } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }

    private SearchSession resolveParentSession(String parentSessionKey) {
        if (parentSessionKey == null || parentSessionKey.isBlank()) return null;
        try {
            return sessionRepo.findBySessionKey(UUID.fromString(parentSessionKey)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    String determineStatus(MidpointResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return "NO_RESULT";
        }
        return "SUCCESS";
    }

    String determineRegion(List<MidpointRequest.LocationDto> locations) {
        if (locations == null || locations.isEmpty()) return null;
        double avgLat = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLat).average().orElse(0);
        double avgLng = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLng).average().orElse(0);
        if (avgLat >= 37.4 && avgLat <= 37.7 && avgLng >= 126.7 && avgLng <= 127.3) return "서울";
        if (avgLat >= 36.8 && avgLat <= 38.0 && avgLng >= 126.5 && avgLng <= 127.8) return "경기";
        return "지방";
    }

    private String buildSearchConditions(String category) {
        try {
            return objectMapper.writeValueAsString(Map.of("final_category", category != null ? category : "ALL"));
        } catch (Exception e) {
            return "{\"final_category\":\"ALL\"}";
        }
    }

    Double calculateFairnessScore(List<MidpointResponse.UserTransitTime> transitTimes) {
        IntSummaryStatistics stats = transitTimes.stream()
                .mapToInt(MidpointResponse.UserTransitTime::getDurationSeconds)
                .filter(d -> d >= 0)
                .summaryStatistics();
        if (stats.getCount() < 2 || stats.getMin() <= 0) return null;
        return (double) stats.getMax() / stats.getMin();
    }

    private String buildPlaceCounts(String stationName) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : ALL_CATEGORY_CODES) {
            placeCacheRepo.findByStationNameAndCategoryCode(stationName, code)
                    .ifPresent(cache -> {
                        if (cache.getPlaceCount() >= 0) counts.put(code, cache.getPlaceCount());
                    });
        }
        try {
            return objectMapper.writeValueAsString(counts);
        } catch (Exception e) {
            return "{}";
        }
    }
}
