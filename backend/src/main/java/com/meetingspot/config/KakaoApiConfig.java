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
}
