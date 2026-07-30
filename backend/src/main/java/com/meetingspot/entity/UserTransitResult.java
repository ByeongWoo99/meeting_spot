package com.meetingspot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "user_transit_result",
        uniqueConstraints = @UniqueConstraint(columnNames = {"station_result_id", "user_index"}))
@Check(constraints = "user_index >= 1 AND user_index <= 4 AND transit_sec >= 0")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTransitResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_result_id", nullable = false)
    private StationResult stationResult;

    @Column(name = "user_index", nullable = false)
    private int userIndex;

    @Column(name = "transit_sec", nullable = false)
    private int transitSec;
}
