package com.meetingspot.controller;

import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import com.meetingspot.service.AnalyticsService;
import com.meetingspot.service.GeminiService;
import com.meetingspot.service.MidpointService;
import com.meetingspot.service.TransitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/midpoint")
@RequiredArgsConstructor
public class MidpointController {

    private final MidpointService midpointService;
    private final GeminiService geminiService;
    private final AnalyticsService analyticsService;

    @PostMapping
    public MidpointResponse calculate(@RequestBody MidpointRequest request) {
        long start = System.currentTimeMillis();
        TransitService.resetStats();
        MidpointResponse response = midpointService.calculate(request);
        try {
            analyticsService.save(request, response,
                    System.currentTimeMillis() - start, TransitService.getStats());
        } catch (Exception e) {
            log.warn("Analytics 저장 실패 - 서비스에는 영향 없음", e);
        }
        return response;
    }

    @PostMapping("/describe")
    public ResponseEntity<Map<String, String>> describe(@RequestBody MidpointRequest.DescribeRequest request) {
        String description = geminiService.generateCandidateDescription(
                request.getStationName(), request.getAddress(), request.getTransitTimes());
        return ResponseEntity.ok(Map.of("description", description != null ? description : ""));
    }
}
