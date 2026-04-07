package com.meetingspot.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class DirectionRequest {

    private List<LocationDto> locations;
    private DestinationDto destination;

    @Data
    public static class LocationDto {
        private String name;
        private double lat;
        private double lng;
    }

    @Data
    public static class DestinationDto {
        private double lat;
        private double lng;
    }
}
