package com.meetingspot.service;

import com.meetingspot.dto.response.PlaceResponse;
import com.meetingspot.dto.response.PlaceResponse.PlaceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final WebClient kakaoWebClient;

    // 카테고리 코드 목록 (ALL이면 전체)
    private static final List<String> ALL_CATEGORIES = List.of("FD6", "CE7", "AT4", "CT1");

    public PlaceResponse searchPlaces(double lat, double lng, String category, int radius) {
        List<String> categories = "ALL".equals(category) ? ALL_CATEGORIES : List.of(category);

        List<PlaceDto> places = categories.stream()
                .flatMap(code -> searchByCategory(lat, lng, code, radius).stream())
                .sorted(Comparator.comparingInt(PlaceDto::getDistance))
                .collect(Collectors.toList());

        return PlaceResponse.builder()
                .places(places)
                .totalCount(places.size())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<PlaceDto> searchByCategory(double lat, double lng, String categoryCode, int radius) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/search/category.json?category_group_code={code}&x={lng}&y={lat}&radius={radius}&sort=distance&size=15",
                            categoryCode, lng, lat, radius)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of();

            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
            if (documents == null) return List.of();

            return documents.stream().map(doc -> PlaceDto.builder()
                    .id((String) doc.get("id"))
                    .name((String) doc.get("place_name"))
                    .category((String) doc.get("category_name"))
                    .categoryCode(categoryCode)
                    .address(getAddress(doc))
                    .distance(parseDistance(doc.get("distance")))
                    .phone((String) doc.get("phone"))
                    .placeUrl((String) doc.get("place_url"))
                    .lat(Double.parseDouble((String) doc.get("y")))
                    .lng(Double.parseDouble((String) doc.get("x")))
                    .build()
            ).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("카테고리 {} 장소 검색 실패", categoryCode, e);
            return List.of();
        }
    }

    private String getAddress(Map<String, Object> doc) {
        String road = (String) doc.get("road_address_name");
        return (road != null && !road.isEmpty()) ? road : (String) doc.get("address_name");
    }

    private int parseDistance(Object distance) {
        if (distance == null) return 0;
        try { return Integer.parseInt(distance.toString()); }
        catch (NumberFormatException e) { return 0; }
    }
}
