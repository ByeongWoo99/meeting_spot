package com.meetingspot.repository;

import com.meetingspot.entity.SearchSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SearchSessionRepository extends JpaRepository<SearchSession, Long> {
    Optional<SearchSession> findBySessionKey(UUID sessionKey);
}
