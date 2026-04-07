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
public class PlaceResponse {

    private List<PlaceDto> places;
    private int totalCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceDto {
        private String id;
        private String name;
        private String category;
        private String categoryCode;
        private String address;
        private int distance;
        private String phone;
        private String placeUrl;
        private double lat;
        private double lng;
    }
}
