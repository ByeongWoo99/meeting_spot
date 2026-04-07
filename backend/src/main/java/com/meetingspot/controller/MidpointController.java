package com.meetingspot.controller;

import com.meetingspot.dto.request.MidpointRequest;
import com.meetingspot.dto.response.MidpointResponse;
import com.meetingspot.service.MidpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/midpoint")
@RequiredArgsConstructor
public class MidpointController {

    private final MidpointService midpointService;

    @PostMapping
    public MidpointResponse calculate(@RequestBody MidpointRequest request) {
        return midpointService.calculate(request);
    }
}
