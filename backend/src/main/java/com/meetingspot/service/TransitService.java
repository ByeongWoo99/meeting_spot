package com.meetingspot.service;

import com.meetingspot.dto.request.MidpointRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
        int arrLocal = fetchCityInternalSeconds(terminals.arrLng(), terminals.arrLat(), destLng, destLat);

        return baseSeconds + depLocal + arrLocal;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchResponse(double sLng, double sLat, double eLng, double eLat, Integer searchType) {
        int maxRetry = 5;
        long rateLimitStart = -1;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            long start = System.currentTimeMillis();
            try {
                Map<String, Object> result = odsayWebClient.get()
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
                if (result != null) {
                    Map<String, Object> jsonError = (Map<String, Object>) result.get("error");
                    if (jsonError != null) {
                        Object code = jsonError.get("code");
                        if (code != null && Integer.parseInt(code.toString()) == 429) {
                            if (rateLimitStart < 0) rateLimitStart = System.currentTimeMillis();
                            long targetMs = 600L + (attempt - 1) * 100L; // 목표: 600, 700, 800, 900, 1000ms
                            long elapsed = System.currentTimeMillis() - rateLimitStart;
                            long remaining = Math.max(0, targetMs - elapsed);
                            log.warn("OdSay JSON 429 — {}/{}회 시도, 목표 {}ms, 경과 {}ms, 추가 대기 {}ms",
                                    attempt, maxRetry, targetMs, elapsed, remaining);
                            if (attempt < maxRetry) {
                                if (remaining > 0) try { Thread.sleep(remaining); } catch (InterruptedException ignored) {}
                                continue;
                            }
                            log.warn("OdSay JSON rate limit 재시도 {}회 모두 실패 (출발 {},{} → 도착 {},{})", maxRetry, sLng, sLat, eLng, eLat);
                            return null;
                        }
                    }
                }
                if (rateLimitStart >= 0) {
                    log.info("OdSay 429 쿨다운 측정: {}ms 이후 성공 (출발 {},{} → 도착 {},{})",
                            System.currentTimeMillis() - rateLimitStart, sLng, sLat, eLng, eLat);
                }
                log.info("OdSay API 응답시간: {}ms (출발 {},{} → 도착 {},{})",
                        System.currentTimeMillis() - start, sLng, sLat, eLng, eLat);
                return result;
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    long waitMs = 500;
                    String retryAfter = e.getHeaders().getFirst("Retry-After");
                    if (retryAfter != null) {
                        try {
                            waitMs = Long.parseLong(retryAfter.trim()) * 1000L;
                            log.warn("OdSay rate limit 초과 (429) — Retry-After: {}s, {}/{}회 시도", retryAfter, attempt, maxRetry);
                        } catch (NumberFormatException ignored) {
                            log.warn("OdSay rate limit 초과 (429) — Retry-After 파싱 실패({}), 500ms 대기, {}/{}회 시도", retryAfter, attempt, maxRetry);
                        }
                    } else {
                        log.warn("OdSay rate limit 초과 (429) — Retry-After 헤더 없음, 500ms 대기, {}/{}회 시도", attempt, maxRetry);
                    }
                    if (attempt < maxRetry) {
                        try { Thread.sleep(waitMs); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    log.warn("OdSay rate limit 재시도 {}회 모두 실패 (출발 {},{} → 도착 {},{})", maxRetry, sLng, sLat, eLng, eLat);
                } else {
                    log.warn("OdSay API 호출 실패 (HTTP {}, {}ms)", e.getStatusCode().value(), System.currentTimeMillis() - start);
                }
                return null;
            } catch (Exception e) {
                log.warn("OdSay API 호출 실패 ({}ms): {}", System.currentTimeMillis() - start, e.getMessage());
                return null;
            }
        }
        return null;
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
        long totalStart = System.currentTimeMillis();
        int[] durations = new int[users.size()];
        for (int i = 0; i < users.size(); i++) {
            MidpointRequest.LocationDto u = users.get(i);
            Integer duration = getTransitDuration(u.getLng(), u.getLat(), stationLng, stationLat).block();
            durations[i] = duration != null ? duration : -1;
        }
        log.info("OdSay 전체 소요시간: {}ms (사용자 {}명, 역 {},{} 기준)",
                System.currentTimeMillis() - totalStart, users.size(), stationLng, stationLat);
        return Mono.just(durations);
    }
}
