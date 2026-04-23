package com.meetingspot.service;

import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MidpointService {

    private final WebClient kakaoWebClient;
    private final TransitService transitService;

    private record StationInfo(String name, double lat, double lng) {}
    private record ScoredStation(StationInfo station, int[] durations) {}

    public MidpointResponse calculate(MidpointRequest request) {
        List<MidpointRequest.LocationDto> locations = request.getLocations();

        // 1) 평균 좌표 계산
        double avgLat = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLat).average().orElse(0);
        double avgLng = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLng).average().orElse(0);

        // 2) 평균 좌표 주변 지하철역 후보 3개 탐색
        List<StationInfo> candidates = getCandidateStations(avgLat, avgLng);

        if (!candidates.isEmpty()) {
            try {
                // 3) 대중교통 소요시간 기반 최적 역 선택 (복합 점수제)
                ScoredStation scored = selectByCompositeScore(candidates, locations);
                if (scored != null) {
                    String address = getAddressFromCoords(scored.station().lat(), scored.station().lng());
                    log.debug("대중교통 기반 선택: name={}, lat={}, lng={}",
                            scored.station().name(), scored.station().lat(), scored.station().lng());
                    return MidpointResponse.builder()
                            .lat(scored.station().lat())
                            .lng(scored.station().lng())
                            .address(address)
                            .nearestStation(scored.station().name())
                            .stationLat(scored.station().lat())
                            .stationLng(scored.station().lng())
                            .transitTimes(buildTransitTimes(locations, scored.durations()))
                            .transitFallback(false)
                            .build();
                }
            } catch (Exception e) {
                log.warn("대중교통 기반 선택 실패 — 거리 기반 fallback 사용: {}", e.getMessage());
            }

            // fallback: 가장 가까운 역 (첫 번째 결과)
            StationInfo nearest = candidates.get(0);
            String address = getAddressFromCoords(nearest.lat(), nearest.lng());
            log.debug("거리 기반 fallback: name={}", nearest.name());
            return MidpointResponse.builder()
                    .lat(nearest.lat()).lng(nearest.lng())
                    .address(address)
                    .nearestStation(nearest.name())
                    .stationLat(nearest.lat()).stationLng(nearest.lng())
                    .transitTimes(null)
                    .transitFallback(true)
                    .build();
        }

        // 최종 fallback: 평균 좌표
        String address = getAddressFromCoords(avgLat, avgLng);
        log.warn("역 없음 - 평균 좌표 fallback: lat={}, lng={}", avgLat, avgLng);
        return MidpointResponse.builder()
                .lat(avgLat).lng(avgLng).address(address)
                .nearestStation(null).stationLat(avgLat).stationLng(avgLng)
                .transitTimes(null).transitFallback(true)
                .build();
    }

    private ScoredStation selectByCompositeScore(List<StationInfo> candidates,
                                                   List<MidpointRequest.LocationDto> users) {
        StationInfo best = null;
        int[] bestDurations = null;
        double bestScore = Double.MAX_VALUE;

        for (StationInfo station : candidates) {
            int[] durations = transitService.getAllTransitDurations(users, station.lng(), station.lat()).block();
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            if (durations == null) continue;

            boolean anyValid = Arrays.stream(durations).anyMatch(d -> d >= 0);
            if (!anyValid) {
                log.warn("후보 역 '{}': 모든 OdSay 호출 실패 (전부 -1), 스킵", station.name());
                continue;
            }

            // 실패(-1)는 2시간(7200초)으로 대체하여 해당 후보역 순위를 낮춤
            double[] times = Arrays.stream(durations)
                    .mapToDouble(d -> d < 0 ? 7200.0 : d)
                    .toArray();

            double maxTime = Arrays.stream(times).max().orElse(Double.MAX_VALUE);
            double mean = Arrays.stream(times).average().orElse(0);
            // 표준편차(variance의 제곱근): maxTime과 동일한 단위(초)로 공정성 측정
            double stdDev = Math.sqrt(Arrays.stream(times)
                    .map(t -> Math.pow(t - mean, 2))
                    .average()
                    .orElse(0));

            // 복합 점수: 표준편차 60% + 최대 소요시간 40% (균등성 중심)
            double score = 0.4 * maxTime + 0.6 * stdDev;

            log.debug("후보 역 '{}': maxTime={}초, stdDev={}초, compositeScore={}",
                    station.name(), (int) maxTime, Math.round(stdDev), Math.round(score));

            if (score < bestScore) {
                bestScore = score;
                best = station;
                bestDurations = durations;
            }
        }

        return best != null ? new ScoredStation(best, bestDurations) : null;
    }

    private List<MidpointResponse.UserTransitTime> buildTransitTimes(
            List<MidpointRequest.LocationDto> users, int[] durations) {
        List<MidpointResponse.UserTransitTime> result = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            result.add(MidpointResponse.UserTransitTime.builder()
                    .userName(users.get(i).getName())
                    .durationSeconds(i < durations.length ? durations[i] : -1)
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<StationInfo> getCandidateStations(double lat, double lng) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/search/keyword.json?query=지하철역&x={lng}&y={lat}&radius=5000&sort=distance&size=3",
                            lng, lat)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
            if (documents == null || documents.isEmpty()) return List.of();

            List<StationInfo> stations = new ArrayList<>();
            for (Map<String, Object> doc : documents) {
                stations.add(new StationInfo(
                        (String) doc.get("place_name"),
                        Double.parseDouble((String) doc.get("y")),
                        Double.parseDouble((String) doc.get("x"))
                ));
            }
            return stations;

        } catch (Exception e) {
            log.error("지하철역 후보 조회 실패: lat={}, lng={}", lat, lng, e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String getAddressFromCoords(double lat, double lng) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/geo/coord2address.json?x={lng}&y={lat}", lng, lat)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return "주소 정보 없음";
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
            if (documents == null || documents.isEmpty()) return "주소 정보 없음";

            Map<String, Object> roadAddress = (Map<String, Object>) documents.get(0).get("road_address");
            if (roadAddress != null) return (String) roadAddress.get("address_name");
            Map<String, Object> address = (Map<String, Object>) documents.get(0).get("address");
            if (address != null) return (String) address.get("address_name");

        } catch (Exception e) {
            log.error("주소 조회 실패", e);
        }
        return "주소 정보 없음";
    }
}
