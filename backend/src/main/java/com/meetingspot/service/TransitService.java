package com.meetingspot.service;

import com.meetingspot.dto.request.MidpointRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TransitService {

    private final WebClient odsayWebClient;
    private final String apiKey;

    public TransitService(WebClient odsayWebClient,
                          @Value("${odsay.api.key}") String apiKey) {
        this.odsayWebClient = odsayWebClient;
        this.apiKey = apiKey;
    }

    public Mono<Integer> getTransitDuration(double originLng, double originLat,
                                             double destLng, double destLat) {
        return Mono.fromCallable(() -> calcDuration(originLng, originLat, destLng, destLat));
    }

    @SuppressWarnings("unchecked")
    private int calcDuration(double originLng, double originLat, double destLng, double destLat) {
        Map<String, Object> resp = fetchResponse(originLng, originLat, destLng, destLat, null);
        if (resp == null) return -1;

        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        if (result == null) {
            Map<String, Object> error = (Map<String, Object>) resp.get("error");
            if (error != null) {
                Object code = error.get("code");
                Object msg = error.get("message");
                if (code != null && Integer.parseInt(code.toString()) == -98) {
                    log.debug("OdSay -98: 700m 이내 도보 거리, 0으로 처리");
                    return 0;
                }
                log.warn("OdSay 오류 code={} message={} (출발 {},{} → 도착 {},{})",
                        code, msg, originLng, originLat, destLng, destLat);
            } else {
                log.warn("OdSay 응답에 result/error 모두 없음 (출발 {},{} → 도착 {},{})",
                        originLng, originLat, destLng, destLat);
            }
            return -1;
        }

        List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("path");
        if (paths == null || paths.isEmpty()) return -1;

        Map<String, Object> path = paths.get(0);
        Map<String, Object> info = (Map<String, Object>) path.get("info");
        if (info == null) return -1;

        Number totalTime = (Number) info.get("totalTime");
        if (totalTime == null) return -1;
        int baseSeconds = totalTime.intValue() * 60;

        // 도시간 경로 확인: result.searchType (1=도시간 직통, 2=도시간 환승)
        Number searchTypeNum = (Number) result.get("searchType");
        boolean isInterCity = searchTypeNum != null && searchTypeNum.intValue() > 0;
        if (!isInterCity) return baseSeconds;

        List<Map<String, Object>> subPaths = (List<Map<String, Object>>) path.get("subPath");
        InterCityTerminals terminals = extractTerminals(subPaths);
        if (terminals == null) return baseSeconds;

        log.debug("도시간 경로 감지(searchType={}) → 출발터미널({},{}) 도착터미널({},{})",
                searchTypeNum.intValue(), terminals.depLng(), terminals.depLat(), terminals.arrLng(), terminals.arrLat());

        int depLocal = fetchCityInternalSeconds(originLng, originLat, terminals.depLng(), terminals.depLat());
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        int arrLocal = fetchCityInternalSeconds(terminals.arrLng(), terminals.arrLat(), destLng, destLat);

        return baseSeconds + depLocal + arrLocal;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchResponse(double sLng, double sLat, double eLng, double eLat, Integer searchType) {
        try {
            return odsayWebClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder
                                .path("/v1/api/searchPubTransPathT")
                                .queryParam("SX", sLng)
                                .queryParam("SY", sLat)
                                .queryParam("EX", eLng)
                                .queryParam("EY", eLat)
                                .queryParam("apiKey", apiKey);
                        if (searchType != null) b = b.queryParam("SearchType", searchType);
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("OdSay API 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private int fetchCityInternalSeconds(double sLng, double sLat, double eLng, double eLat) {
        try {
            Map<String, Object> resp = fetchResponse(sLng, sLat, eLng, eLat, 0); // SearchType=0: 도시내
            if (resp == null) return 0;
            Map<String, Object> result = (Map<String, Object>) resp.get("result");
            if (result == null) return 0;
            List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("path");
            if (paths == null || paths.isEmpty()) return 0;
            Map<String, Object> info = (Map<String, Object>) paths.get(0).get("info");
            if (info == null) return 0;
            Number t = (Number) info.get("totalTime");
            return t != null ? t.intValue() * 60 : 0;
        } catch (Exception e) {
            log.warn("도시내 구간 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    private record InterCityTerminals(double depLng, double depLat, double arrLng, double arrLat) {}

    private InterCityTerminals extractTerminals(List<Map<String, Object>> subPaths) {
        if (subPaths == null || subPaths.isEmpty()) return null;

        // 도보(trafficType=3) 제외한 실제 이동 구간만 추출
        List<Map<String, Object>> transitOnly = subPaths.stream()
                .filter(sp -> {
                    Object t = sp.get("trafficType");
                    return t != null && Integer.parseInt(t.toString()) != 3;
                })
                .toList();

        if (transitOnly.isEmpty()) return null;

        try {
            Map<String, Object> first = transitOnly.get(0);
            Map<String, Object> last = transitOnly.get(transitOnly.size() - 1);

            // 출발 터미널: 첫 번째 구간 passStopList.stations[0] 의 x/y
            double[] dep = stationCoords(first, true);
            // 도착 터미널: 마지막 구간 passStopList.stations[-1] 의 x/y
            double[] arr = stationCoords(last, false);

            return new InterCityTerminals(dep[0], dep[1], arr[0], arr[1]);
        } catch (Exception e) {
            log.warn("터미널 좌표 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private double[] stationCoords(Map<String, Object> subPath, boolean isFirst) {
        Object x = subPath.get(isFirst ? "startX" : "endX");
        Object y = subPath.get(isFirst ? "startY" : "endY");
        if (x == null || y == null) throw new RuntimeException("터미널 좌표 없음: " + subPath.keySet());
        return new double[]{Double.parseDouble(x.toString()), Double.parseDouble(y.toString())};
    }

    public Mono<int[]> getAllTransitDurations(List<MidpointRequest.LocationDto> users,
                                              double stationLng, double stationLat) {
        int[] durations = new int[users.size()];
        for (int i = 0; i < users.size(); i++) {
            MidpointRequest.LocationDto u = users.get(i);
            Integer duration = getTransitDuration(u.getLng(), u.getLat(), stationLng, stationLat).block();
            durations[i] = duration != null ? duration : -1;
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        return Mono.just(durations);
    }
}
