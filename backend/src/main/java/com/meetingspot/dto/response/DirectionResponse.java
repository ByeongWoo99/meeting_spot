package com.meetingspot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectionResponse {
    private String userName;
    private int distance;   // 미터
    private int duration;   // 초
    private int tollFee;    // 원
}
