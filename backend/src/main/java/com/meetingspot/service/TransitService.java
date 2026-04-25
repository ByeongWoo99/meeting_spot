package com.meetingspot.service;

import com.meetingspot.dto.request.MidpointRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /**
     * OdSay API로 대중교통 소요시간 조회 (초 단위 반환, 실패 시 -1)
     */
    @SuppressWarnings("unchecked")
    public Mono<Integer> getTransitDuration(double originLng, double originLat,
                                             double destLng, double destLat) {
        LocalDateTime now = LocalDateTime.now();
        String searchDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String searchTime = now.format(DateTimeFormatter.ofPattern("HHmm"));

        return odsayWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/api/searchPubTransPathT")
                        .queryParam("SX", originLng)
                        .queryParam("SY", originLat)
                        .queryParam("EX", destLng)
                        .queryParam("EY", destLat)
                        .queryParam("SearchDate", searchDate)
                        .queryParam("SearchTime", searchTime)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> {
                    try {
                        log.debug("OdSay 응답: {}", resp);
                        Map<String, Object> result = (Map<String, Object>) resp.get("result");
                        if (result == null) {
                            Map<String, Object> error = (Map<String, Object>) resp.get("error");
                            if (error != null) {
                                Object code = error.get("code");
                                if (code != null && Integer.parseInt(code.toString()) == -98) {
                                    log.debug("OdSay -98: 700m 이내 도보 거리, 소요시간 0으로 처리");
                                    return 0;
                                }
                            }
                            log.warn("OdSay 응답에 result 없음: {}", resp);
                            return -1;
                        }
                        List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("path");
                        if (paths == null || paths.isEmpty()) {
                            log.warn("OdSay 경로 없음 (result={})", result);
                            return -1;
                        }
                        Map<String, Object> info = (Map<String, Object>) paths.get(0).get("info");
                        if (info == null) return -1;
                        Number totalTime = (Number) info.get("totalTime");
                        if (totalTime == null) return -1;
                        return totalTime.intValue() * 60; // 분 → 초
                    } catch (Exception e) {
                        log.warn("OdSay 응답 파싱 실패: {} / 응답: {}", e.getMessage(), resp);
                        return -1;
                    }
                })
                .doOnError(e -> log.warn("OdSay API 호출 실패: {}", e.getMessage()))
                .onErrorReturn(-1);
    }

    /**
     * 모든 사용자 → 목적지 소요시간 순차 조회 (Rate Limit 대응)
     */
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
