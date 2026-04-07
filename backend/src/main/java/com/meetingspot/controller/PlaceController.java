package com.meetingspot.controller;

import com.meetingspot.dto.response.PlaceResponse;
import com.meetingspot.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping
    public PlaceResponse searchPlaces(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "1000") int radius
    ) {
        return placeService.searchPlaces(lat, lng, category, radius);
    }
}
