package com.meetingspot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MidpointResponse {
    private double lat;
    private double lng;
    private String address;
    private String nearestStation;
    private double stationLat;
    private double stationLng;
    private List<UserTransitTime> transitTimes;  // 사용자별 대중교통 소요시간
    private boolean transitFallback;             // true = 직선거리 기준 선택

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTransitTime {
        private String userName;
        private int durationSeconds;  // -1 = 정보 없음
    }
}
