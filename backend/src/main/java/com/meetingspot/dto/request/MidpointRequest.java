package com.meetingspot.dto.request;

import com.meetingspot.dto.response.MidpointResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class MidpointRequest {

    @NotNull(message = "출발지 목록은 필수입니다.")
    @Size(min = 2, max = 4, message = "출발지는 2~4명이어야 합니다.")
    @Valid
    private List<LocationDto> locations;
    private String category = "ALL";
    private String sessionKey;
    private String parentSessionKey;

    @Data
    public static class LocationDto {
        private String name;

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "33.0", message = "지원하지 않는 위도 범위입니다.")
        @DecimalMax(value = "38.9", message = "지원하지 않는 위도 범위입니다.")
        private Double lat;

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "124.6", message = "지원하지 않는 경도 범위입니다.")
        @DecimalMax(value = "132.0", message = "지원하지 않는 경도 범위입니다.")
        private Double lng;
    }

    @Data
    public static class DescribeRequest {
        private String stationName;
        private String address;
        private List<MidpointResponse.UserTransitTime> transitTimes;
    }
}
