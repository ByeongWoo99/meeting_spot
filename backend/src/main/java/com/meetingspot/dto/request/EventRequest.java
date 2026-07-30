package com.meetingspot.dto.request;

import lombok.Data;

@Data
public class EventRequest {
    private String sessionKey;
    private String eventType;
    private String eventValue;
}
