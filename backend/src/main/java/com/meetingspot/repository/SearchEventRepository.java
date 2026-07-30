package com.meetingspot.repository;

import com.meetingspot.entity.SearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchEventRepository extends JpaRepository<SearchEvent, Long> {
}
