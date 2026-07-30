package com.meetingspot.repository;

import com.meetingspot.entity.SearchSession;
import com.meetingspot.entity.StationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StationResultRepository extends JpaRepository<StationResult, Long> {
    List<StationResult> findBySession(SearchSession session);
}
