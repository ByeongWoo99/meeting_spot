package com.meetingspot.controller;

import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import com.meetingspot.service.GeminiService;
import com.meetingspot.service.MidpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/midpoint")
@RequiredArgsConstructor
public class MidpointController {

    private final MidpointService midpointService;
    private final GeminiService geminiService;

    @PostMapping
    public MidpointResponse calculate(@RequestBody MidpointRequest request) {
        return midpointService.calculate(request);
    }

    @PostMapping("/describe")
    public ResponseEntity<Map<String, String>> describe(@RequestBody MidpointRequest.DescribeRequest request) {
        String description = geminiService.generateCandidateDescription(
                request.getStationName(), request.getAddress(), request.getTransitTimes());
        return ResponseEntity.ok(Map.of("description", description != null ? description : ""));
    }
}
