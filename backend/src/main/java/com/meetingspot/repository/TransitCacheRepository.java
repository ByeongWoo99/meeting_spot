package com.meetingspot.repository;

import com.meetingspot.entity.TransitCache;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransitCacheRepository extends JpaRepository<TransitCache, Long> {
    Optional<TransitCache> findByOriginLatAndOriginLngAndStationName(
            double originLat, double originLng, String stationName);
}
