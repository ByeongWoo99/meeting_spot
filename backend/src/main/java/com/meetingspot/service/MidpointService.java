package com.meetingspot.service;

import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MidpointService {

    private final WebClient kakaoWebClient;

    // 역 정보를 담는 내부 레코드
    private record StationInfo(String name, double lat, double lng) {}

    public MidpointResponse calculate(MidpointRequest request) {
        List<MidpointRequest.LocationDto> locations = request.getLocations();

        // 1) 평균 좌표 계산
        double avgLat = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLat).average().orElse(0);
        double avgLng = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLng).average().orElse(0);

        // 2) 출발지 간 최대 거리 계산 → 탐색 반경 동적 결정
        double maxDist = 0;
        for (MidpointRequest.LocationDto a : locations) {
            for (MidpointRequest.LocationDto b : locations) {
                maxDist = Math.max(maxDist, haversine(a.getLat(), a.getLng(), b.getLat(), b.getLng()));
            }
        }
        int searchRadius = (int) Math.min(Math.max(maxDist * 0.4, 3000), 20000); // 최소 3km, 최대 20km

        // 3) 평균 좌표 주변에서 역 탐색 (중간 지역 커버)
        Map<String, StationInfo> candidateMap = new LinkedHashMap<>();
        List<StationInfo> centerStations = getNearbyStations(avgLat, avgLng, searchRadius);
        for (StationInfo s : centerStations) {
            candidateMap.putIfAbsent(s.name(), s);
        }

        // 4) 후보가 부족하면 각 출발지 주변도 추가
        if (candidateMap.size() < 5) {
            for (MidpointRequest.LocationDto loc : locations) {
                List<StationInfo> stations = getNearbyStations(loc.getLat(), loc.getLng(), 5000);
                for (StationInfo s : stations) {
                    candidateMap.putIfAbsent(s.name(), s);
                }
            }
        }

        if (candidateMap.isEmpty()) {
            // fallback: 평균 좌표 + 주소
            String address = getAddressFromCoords(avgLat, avgLng);
            log.warn("후보 역 없음 - 평균 좌표 fallback: lat={}, lng={}", avgLat, avgLng);
            return MidpointResponse.builder()
                    .lat(avgLat).lng(avgLng).address(address)
                    .nearestStation(null).stationLat(avgLat).stationLng(avgLng)
                    .build();
        }

        // minimax: 가장 멀리서 오는 사람의 거리가 최소인 역 선택 (가장 공평)
        StationInfo best = candidateMap.values().stream()
                .min(Comparator.comparingDouble(s ->
                        locations.stream()
                                .mapToDouble(loc -> haversine(loc.getLat(), loc.getLng(), s.lat(), s.lng()))
                                .max()
                                .orElse(Double.MAX_VALUE)
                ))
                .orElseThrow();

        String address = getAddressFromCoords(best.lat(), best.lng());

        log.debug("선택된 중간역: name={}, lat={}, lng={}", best.name(), best.lat(), best.lng());

        return MidpointResponse.builder()
                .lat(best.lat())
                .lng(best.lng())
                .address(address)
                .nearestStation(best.name())
                .stationLat(best.lat())
                .stationLng(best.lng())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<StationInfo> getNearbyStations(double lat, double lng, int radius) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/search/keyword.json?query=지하철역&x={lng}&y={lat}&radius={radius}&sort=distance&size=15",
                            lng, lat, radius)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return Collections.emptyList();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
            if (documents == null) return Collections.emptyList();

            return documents.stream()
                    .map(doc -> new StationInfo(
                            (String) doc.get("place_name"),
                            Double.parseDouble((String) doc.get("y")),
                            Double.parseDouble((String) doc.get("x"))
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("지하철역 조회 실패: lat={}, lng={}", lat, lng, e);
            return Collections.emptyList();
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

    // Haversine 공식: 두 좌표 간 직선거리(m)
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
