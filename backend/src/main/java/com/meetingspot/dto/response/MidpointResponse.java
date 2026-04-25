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
    private List<Candidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private int rank;
        private String nearestStation;
        private double lat;
        private double lng;
        private String address;
        private double stationLat;
        private double stationLng;
        private List<UserTransitTime> transitTimes;
        private boolean transitFallback;
        private double compositeScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTransitTime {
        private String userName;
        private int durationSeconds;
    }
}
