package com.meetingspot.dto.request;

import com.meetingspot.dto.response.MidpointResponse;
import lombok.Data;
import java.util.List;

@Data
public class MidpointRequest {

    private List<LocationDto> locations;
    private String category = "ALL";

    @Data
    public static class LocationDto {
        private String name;
        private double lat;
        private double lng;
    }

    @Data
    public static class DescribeRequest {
        private String stationName;
        private String address;
        private List<MidpointResponse.UserTransitTime> transitTimes;
    }
}
