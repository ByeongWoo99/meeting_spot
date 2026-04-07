package com.meetingspot.controller;

import com.meetingspot.dto.request.DirectionRequest;
import com.meetingspot.dto.response.DirectionResponse;
import com.meetingspot.service.DirectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/directions")
@RequiredArgsConstructor
public class DirectionController {

    private final DirectionService directionService;

    @PostMapping("/car")
    public List<DirectionResponse> getCarDirections(@RequestBody DirectionRequest request) {
        return directionService.getCarDirections(request);
    }
}
