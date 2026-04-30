package com.meetingspot.dto.request;

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
}
