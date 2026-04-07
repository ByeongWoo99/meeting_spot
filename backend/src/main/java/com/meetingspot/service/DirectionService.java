package com.meetingspot.service;

import com.meetingspot.dto.request.DirectionRequest;
import com.meetingspot.dto.response.DirectionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectionService {

    private final WebClient kakaoMobilityWebClient;

    public List<DirectionResponse> getCarDirections(DirectionRequest request) {
        double destLng = request.getDestination().getLng();
        double destLat = request.getDestination().getLat();

        return request.getLocations().stream()
                .map(loc -> getCarRoute(loc, destLng, destLat))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private DirectionResponse getCarRoute(DirectionRequest.LocationDto loc, double destLng, double destLat) {
        try {
            // 카카오 모빌리티 API: origin/destination은 {경도},{위도} 순서
            String origin = loc.getLng() + "," + loc.getLat();
            String destination = destLng + "," + destLat;

            Map<String, Object> response = kakaoMobilityWebClient.get()
                    .uri("/v1/directions?origin={origin}&destination={destination}&priority=RECOMMEND",
                            origin, destination)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return fallback(loc.getName());

            List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
            if (routes == null || routes.isEmpty()) return fallback(loc.getName());

            Map<String, Object> route = routes.get(0);
            Integer resultCode = (Integer) route.get("result_code");
            if (resultCode != null && resultCode != 0) return fallback(loc.getName());

            Map<String, Object> summary = (Map<String, Object>) route.get("summary");
            Map<String, Object> fare = (Map<String, Object>) summary.get("fare");

            return DirectionResponse.builder()
                    .userName(loc.getName())
                    .distance(toInt(summary.get("distance")))
                    .duration(toInt(summary.get("duration")))
                    .tollFee(fare != null ? toInt(fare.getOrDefault("toll", 0)) : 0)
                    .build();

        } catch (Exception e) {
            log.error("자가용 경로 조회 실패: {}", loc.getName(), e);
            return fallback(loc.getName());
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return 0;
    }

    private DirectionResponse fallback(String userName) {
        return DirectionResponse.builder()
                .userName(userName)
                .distance(-1)
                .duration(-1)
                .tollFee(0)
                .build();
    }
}
