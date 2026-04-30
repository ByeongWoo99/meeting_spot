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
    private final TransitService transitService;

    private static final int CATEGORY_RADIUS_M = 736;
    private static final int CATEGORY_MIN_COUNT = 10;
    private static final int[] SEARCH_RADII = {5000, 10000};
    private static final List<String> ALL_CATEGORY_CODES = List.of("FD6", "CE7", "CT1", "AT4");
    private static final List<String> CULTURE_CODES = List.of("CT1", "AT4");

    private record StationInfo(String name, double lat, double lng) {}
    private record ScoredStation(StationInfo station, int[] durations, double score) {}

    public MidpointResponse calculate(MidpointRequest request) {
        List<MidpointRequest.LocationDto> locations = request.getLocations();
        String category = request.getCategory() != null ? request.getCategory() : "ALL";

        double avgLat = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLat).average().orElse(0);
        double avgLng = locations.stream().mapToDouble(MidpointRequest.LocationDto::getLng).average().orElse(0);

        // 1~3단계: 반경 단계별 탐색
        for (int i = 0; i < SEARCH_RADII.length; i++) {
            List<StationInfo> stations = getCandidateStations(avgLat, avgLng, SEARCH_RADII[i]);

            Map<StationInfo, Integer> placeCounts = new LinkedHashMap<>();
            for (StationInfo s : stations) {
                int count = getPlaceCount(s.lat(), s.lng(), category);
                if (count >= CATEGORY_MIN_COUNT) {
                    placeCounts.put(s, count);
                }
            }

            if (!placeCounts.isEmpty()) {
                String note = i > 0
                        ? "중간 좌표 근처에 적합한 장소가 없어 더 넓은 반경에서 탐색했습니다. 소요시간이 공평하지 않을 수 있습니다."
                        : null;
                return buildResponse(new ArrayList<>(placeCounts.keySet()), placeCounts, locations, category, note);
            }
        }

        // 3단계: 중간 좌표 자체 확인
        int midCount = getPlaceCount(avgLat, avgLng, category);
        if (midCount >= CATEGORY_MIN_COUNT) {
            String addr = getAddressFromCoords(avgLat, avgLng);
            String note = "중간 좌표 근처에 적합한 장소가 없어 더 넓은 반경에서 탐색했습니다. 소요시간이 공평하지 않을 수 있습니다.";
            return MidpointResponse.builder()
                    .candidates(List.of(MidpointResponse.Candidate.builder()
                            .rank(1).nearestStation(null)
                            .lat(avgLat).lng(avgLng).address(addr)
                            .stationLat(avgLat).stationLng(avgLng)
                            .transitTimes(null).transitFallback(true).compositeScore(0).build()))
                    .searchNote(note)
                    .build();
        }

        // 4단계: 최종 fallback — 10km 내 전체 카테고리 장소 수 기준
        String fallbackNote = "선택하신 목적에 맞는 장소를 찾지 못했습니다. " +
                "대신 주변에 만날 장소가 가장 많은 역을 추천드립니다.";

        List<StationInfo> nearby10k = getCandidateStations(avgLat, avgLng, 10000);
        // placeCount(ALL) 내림차순 정렬
        List<Map.Entry<StationInfo, Integer>> ranked = new ArrayList<>();
        for (StationInfo s : nearby10k) {
            int cnt = getPlaceCount(s.lat(), s.lng(), "ALL");
            ranked.add(Map.entry(s, cnt));
        }
        ranked.sort((a, b) -> b.getValue() - a.getValue());

        // 10km 내 역이 없으면 무반경 탐색으로 대체
        if (ranked.isEmpty()) {
            List<StationInfo> any = getNearestStationsNoRadius(avgLat, avgLng);
            for (StationInfo s : any) {
                ranked.add(Map.entry(s, getPlaceCount(s.lat(), s.lng(), "ALL")));
            }
            ranked.sort((a, b) -> b.getValue() - a.getValue());
        }

        List<MidpointResponse.Candidate> fallbackResult = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<StationInfo, Integer> entry : ranked) {
            if (rank > 2) break;
            StationInfo s = entry.getKey();
            int[] d = transitService.getAllTransitDurations(locations, s.lng(), s.lat()).block();
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            log.debug("4단계 후보 '{}': placeCount={}", s.name(), entry.getValue());
            fallbackResult.add(MidpointResponse.Candidate.builder()
                    .rank(rank++).nearestStation(s.name())
                    .lat(s.lat()).lng(s.lng()).address(getAddressFromCoords(s.lat(), s.lng()))
                    .stationLat(s.lat()).stationLng(s.lng())
                    .transitTimes(buildTransitTimes(locations, d != null ? d : new int[]{}))
                    .transitFallback(false).compositeScore(entry.getValue()).build());
        }

        if (!fallbackResult.isEmpty()) {
            return MidpointResponse.builder().candidates(fallbackResult).searchNote(fallbackNote).build();
        }

        // 절대 fallback — 20km 내 가장 가까운 역 반환
        String absNote = "주변에 조건에 맞는 장소를 찾지 못했습니다. 중간 지점에서 가장 가까운 역을 안내해드립니다.";
        List<StationInfo> last = getCandidateStations(avgLat, avgLng, 20000);
        if (!last.isEmpty()) {
            StationInfo nearest = last.stream()
                    .min(Comparator.comparingDouble(s -> haversine(avgLat, avgLng, s.lat(), s.lng())))
                    .get();
            int[] d = transitService.getAllTransitDurations(locations, nearest.lng(), nearest.lat()).block();
            String addr = getAddressFromCoords(nearest.lat(), nearest.lng());
            return MidpointResponse.builder()
                    .candidates(List.of(MidpointResponse.Candidate.builder()
                            .rank(1).nearestStation(nearest.name())
                            .lat(nearest.lat()).lng(nearest.lng()).address(addr)
                            .stationLat(nearest.lat()).stationLng(nearest.lng())
                            .transitTimes(buildTransitTimes(locations, d != null ? d : new int[]{}))
                            .transitFallback(false).compositeScore(0).build()))
                    .searchNote(absNote)
                    .build();
        }

        // 완전 절대 fallback — 역을 전혀 찾지 못한 경우 (사실상 발생 불가)
        String addr = getAddressFromCoords(avgLat, avgLng);
        return MidpointResponse.builder()
                .candidates(List.of(MidpointResponse.Candidate.builder()
                        .rank(1).nearestStation(null)
                        .lat(avgLat).lng(avgLng).address(addr)
                        .stationLat(avgLat).stationLng(avgLng)
                        .transitTimes(null).transitFallback(true).compositeScore(0).build()))
                .searchNote(absNote)
                .build();
    }

    private MidpointResponse buildResponse(List<StationInfo> validStations,
                                            Map<StationInfo, Integer> placeCounts,
                                            List<MidpointRequest.LocationDto> users,
                                            String category,
                                            String baseNote) {
        List<ScoredStation> ranked = scoreAndRankStations(validStations, placeCounts, users);

        List<MidpointResponse.Candidate> result = new ArrayList<>();
        for (int i = 0; i < Math.min(2, ranked.size()); i++) {
            ScoredStation s = ranked.get(i);
            String addr = getAddressFromCoords(s.station().lat(), s.station().lng());
            log.debug("후보 {}: name={}, score={}", i + 1, s.station().name(), Math.round(s.score()));
            result.add(MidpointResponse.Candidate.builder()
                    .rank(i + 1).nearestStation(s.station().name())
                    .lat(s.station().lat()).lng(s.station().lng()).address(addr)
                    .stationLat(s.station().lat()).stationLng(s.station().lng())
                    .transitTimes(buildTransitTimes(users, s.durations()))
                    .transitFallback(false).compositeScore(s.score()).build());
        }

        String note = baseNote;
        if (result.size() == 1) {
            String singleNote = "이 지역에서는 추천 장소가 1곳만 찾아졌습니다.";
            note = note != null ? note + " " + singleNote : singleNote;
        }

        return MidpointResponse.builder().candidates(result).searchNote(note).build();
    }

    private List<ScoredStation> scoreAndRankStations(List<StationInfo> stations,
                                                      Map<StationInfo, Integer> placeCounts,
                                                      List<MidpointRequest.LocationDto> users) {
        List<ScoredStation> scored = new ArrayList<>();
        double totalTransitScore = 0;

        for (StationInfo station : stations) {
            int[] durations = transitService.getAllTransitDurations(users, station.lng(), station.lat()).block();
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            if (durations == null) continue;

            if (Arrays.stream(durations).noneMatch(d -> d >= 0)) {
                log.warn("후보 역 '{}': 모든 OdSay 호출 실패, 스킵", station.name());
                continue;
            }

            double[] times = Arrays.stream(durations)
                    .mapToDouble(d -> d < 0 ? 7200.0 : d).toArray();

            double maxTime = Arrays.stream(times).max().orElse(0);
            double mean = Arrays.stream(times).average().orElse(0);
            double stdDev = Math.sqrt(Arrays.stream(times)
                    .map(t -> Math.pow(t - mean, 2)).average().orElse(0));

            double transitScore = 0.4 * maxTime + 0.6 * stdDev;
            totalTransitScore += transitScore;
            scored.add(new ScoredStation(station, durations, transitScore));
        }

        if (scored.isEmpty()) return List.of();

        double avgTransitScore = totalTransitScore / scored.size();

        return scored.stream()
                .map(s -> {
                    int placeCount = placeCounts.getOrDefault(s.station(), CATEGORY_MIN_COUNT);
                    double densityPenalty = avgTransitScore * (1.0 - Math.min(placeCount, 100) / 100.0) * 0.3;
                    double finalScore = s.score() * 0.7 + densityPenalty;
                    log.debug("후보 역 '{}': transitScore={}, placeCount={}, finalScore={}",
                            s.station().name(), Math.round(s.score()), placeCount, Math.round(finalScore));
                    return new ScoredStation(s.station(), s.durations(), finalScore);
                })
                .sorted(Comparator.comparingDouble(ScoredStation::score))
                .collect(Collectors.toList());
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

    private List<String> resolveCategoryCodes(String category) {
        return switch (category) {
            case "ALL" -> ALL_CATEGORY_CODES;
            case "CT1_AT4" -> CULTURE_CODES;
            default -> List.of(category);
        };
    }

    private int getPlaceCount(double lat, double lng, String category) {
        int total = 0;
        for (String code : resolveCategoryCodes(category)) {
            total += queryPlaceCount(lat, lng, code);
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private int queryPlaceCount(double lat, double lng, String categoryCode) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/search/category.json?category_group_code={code}&x={lng}&y={lat}&radius={radius}&size=1",
                            categoryCode, lng, lat, CATEGORY_RADIUS_M)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return 0;
            Map<String, Object> meta = (Map<String, Object>) response.get("meta");
            if (meta == null) return 0;
            Object totalCount = meta.get("total_count");
            return totalCount instanceof Number ? ((Number) totalCount).intValue() : 0;
        } catch (Exception e) {
            log.warn("장소 수 조회 실패: lat={}, lng={}, category={}", lat, lng, categoryCode);
            return 0;
        }
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String extractBaseName(String name) {
        int idx = name.indexOf("역");
        return idx >= 0 ? name.substring(0, idx + 1) : name;
    }

    private List<StationInfo> getNearestStationsNoRadius(double lat, double lng) {
        Set<String> seen = new LinkedHashSet<>();
        List<StationInfo> result = new ArrayList<>();
        for (String keyword : List.of("지하철역", "기차역")) {
            for (StationInfo s : searchStationsByKeyword(keyword, lat, lng, 5)) {
                if (seen.add(s.name())) result.add(s);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<StationInfo> searchStationsByKeyword(String keyword, double lat, double lng, int size) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/search/keyword.json?query={kw}&x={lng}&y={lat}&sort=distance&size={size}",
                            keyword, lng, lat, size)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) return List.of();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
            if (documents == null || documents.isEmpty()) return List.of();
            List<StationInfo> stations = new ArrayList<>();
            for (Map<String, Object> doc : documents) {
                String placeName = (String) doc.get("place_name");
                if (placeName == null || placeName.contains("폐")) continue;
                stations.add(new StationInfo(
                        extractBaseName(placeName),
                        Double.parseDouble((String) doc.get("y")),
                        Double.parseDouble((String) doc.get("x"))
                ));
            }
            return stations;
        } catch (Exception e) {
            log.warn("역 키워드 조회 실패: keyword={}, lat={}, lng={}", keyword, lat, lng);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<StationInfo> getCandidateStations(double lat, double lng, int radiusM) {
        try {
            Map<String, Object> response = kakaoWebClient.get()
                    .uri("/v2/local/search/keyword.json?query=지하철역&x={lng}&y={lat}&radius={radius}&sort=distance&size=15",
                            lng, lat, radiusM)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
            if (documents == null || documents.isEmpty()) return List.of();

            List<StationInfo> stations = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> doc : documents) {
                String placeName = (String) doc.get("place_name");
                if (placeName == null || placeName.contains("폐")) continue;
                String baseName = extractBaseName(placeName);
                if (seen.add(baseName)) {
                    stations.add(new StationInfo(
                            baseName,
                            Double.parseDouble((String) doc.get("y")),
                            Double.parseDouble((String) doc.get("x"))
                    ));
                }
            }
            return stations.stream().limit(10).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("지하철역 후보 조회 실패: lat={}, lng={}, radius={}", lat, lng, radiusM, e);
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
