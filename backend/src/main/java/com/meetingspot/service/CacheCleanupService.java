package com.meetingspot.service;

import com.meetingspot.repository.PlaceCacheRepository;
import com.meetingspot.repository.TransitCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheCleanupService {

    private final TransitCacheRepository transitCacheRepository;
    private final PlaceCacheRepository placeCacheRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredCache() {
        LocalDateTime now = LocalDateTime.now();
        transitCacheRepository.deleteByExpiresAtBefore(now);
        placeCacheRepository.deleteByExpiresAtBefore(now);
        log.info("만료 캐시 정리 완료");
    }
}
