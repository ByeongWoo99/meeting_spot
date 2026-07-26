package com.meetingspot.repository;

import com.meetingspot.entity.PlaceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlaceCacheRepository extends JpaRepository<PlaceCache, Long> {
    Optional<PlaceCache> findByStationNameAndCategoryCode(String stationName, String categoryCode);
}
