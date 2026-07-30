package com.meetingspot.controller;

import com.meetingspot.dto.request.EventRequest;
import com.meetingspot.entity.SearchEvent;
import com.meetingspot.entity.SearchSession;
import com.meetingspot.repository.SearchEventRepository;
import com.meetingspot.repository.SearchSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final SearchSessionRepository sessionRepo;
    private final SearchEventRepository searchEventRepo;

    @PostMapping
    public ResponseEntity<Void> saveEvent(@RequestBody EventRequest request) {
        try {
            if (request.getSessionKey() == null) return ResponseEntity.ok().build();
            UUID uuid = UUID.fromString(request.getSessionKey());
            SearchSession session = sessionRepo.findBySessionKey(uuid).orElse(null);
            if (session == null) return ResponseEntity.ok().build();
            searchEventRepo.save(SearchEvent.builder()
                    .session(session)
                    .eventType(request.getEventType())
                    .eventValue(request.getEventValue())
                    .createdAt(OffsetDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("이벤트 저장 실패: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
