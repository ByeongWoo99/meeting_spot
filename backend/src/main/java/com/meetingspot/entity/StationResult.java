package com.meetingspot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "station_result")
@Check(constraints = "result_rank >= 1 AND " +
        "(total_transit_sec IS NULL OR total_transit_sec >= 0) AND " +
        "(fairness_score IS NULL OR fairness_score >= 1.0)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SearchSession session;

    @Column(name = "station_name", nullable = false, length = 20)
    private String stationName;

    @Column(name = "result_rank", nullable = false)
    private int resultRank;

    @Column(name = "total_transit_sec")
    private Integer totalTransitSec;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "place_counts", nullable = false, columnDefinition = "jsonb")
    private String placeCounts = "{}";

    @Column(name = "fairness_score")
    private Double fairnessScore;
}
