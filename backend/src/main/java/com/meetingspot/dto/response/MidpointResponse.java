package com.meetingspot.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MidpointResponse {
    private double lat;         // 평균 좌표
    private double lng;
    private String address;
    private String nearestStation;
    private double stationLat;  // 실제 역 좌표
    private double stationLng;
}
