package com.meetingspot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "place_cache",
        uniqueConstraints = @UniqueConstraint(columnNames = {"station_name", "category_code"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_name", nullable = false)
    private String stationName;

    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    @Column(name = "place_count", nullable = false)
    private int placeCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
