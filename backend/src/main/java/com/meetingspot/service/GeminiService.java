package com.meetingspot.service;

import com.meetingspot.dto.response.MidpointResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final WebClient geminiWebClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String MODEL = "gemini-3.1-flash-lite";

    public String generateCandidateDescription(
            String stationName,
            String address,
            List<MidpointResponse.UserTransitTime> transitTimes) {

        String timeSummary = transitTimes.stream()
                .map(t -> t.getUserName() + ": " + (t.getDurationSeconds() / 60) + "분")
                .collect(Collectors.joining(", "));

        String prompt = String.format("""
                다음은 중간 만남 장소 추천 결과입니다.
                사용자가 납득할 수 있는 자연스러운 한국어로 추천 이유를 2문장으로 설명해주세요.
                숫자나 기술적 용어 없이 친근하게 작성해주세요.

                역명: %s
                주소: %s
                대중교통 소요시간: %s
                """, stationName, address, timeSummary);

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            );

            Map<?, ?> response = geminiWebClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", MODEL, apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return null;
            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;
            Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            List<?> parts = (List<?>) content.get("parts");
            return (String) ((Map<?, ?>) parts.get(0)).get("text");

        } catch (Exception e) {
            log.warn("Gemini 설명 생성 실패: station={}", stationName, e);
            return null;
        }
    }
}
