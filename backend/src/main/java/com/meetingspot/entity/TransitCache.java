package com.meetingspot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transit_cache",
        uniqueConstraints = @UniqueConstraint(columnNames = {"origin_lat", "origin_lng", "station_name"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_lat", nullable = false)
    private double originLat;

    @Column(name = "origin_lng", nullable = false)
    private double originLng;

    @Column(name = "station_name", nullable = false)
    private String stationName;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
