package com.meetingspot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KakaoApiConfig {

    @Value("${kakao.api.rest-key}")
    private String restApiKey;

    @Value("${kakao.api.base-url}")
    private String baseUrl;

    @Value("${odsay.api.key}")
    private String odsayApiKey;

    @Value("${odsay.api.base-url}")
    private String odsayBaseUrl;

    @Bean
    public WebClient kakaoWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    @Bean
    public WebClient kakaoMobilityWebClient() {
        return WebClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com")
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    @Bean
    public WebClient odsayWebClient() {
        return WebClient.builder()
                .baseUrl(odsayBaseUrl)
                .defaultHeader("Referer", "http://localhost:5173")
                .build();
    }
}
