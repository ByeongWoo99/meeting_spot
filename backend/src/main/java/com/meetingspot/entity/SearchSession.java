package com.meetingspot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_session")
@Check(constraints = "user_count >= 2 AND user_count <= 4 AND " +
        "(duration_ms IS NULL OR duration_ms >= 0) AND " +
        "cache_hit_count >= 0 AND api_call_count >= 0 AND " +
        "status IN ('SUCCESS','FAILED','NO_RESULT')")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID sessionKey;

    @Column(name = "user_count", nullable = false)
    private int userCount;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Builder.Default
    @Column(name = "cache_hit_count", nullable = false)
    private int cacheHitCount = 0;

    @Builder.Default
    @Column(name = "api_call_count", nullable = false)
    private int apiCallCount = 0;

    @Column(name = "region", length = 50)
    private String region;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_conditions", columnDefinition = "jsonb")
    private String searchConditions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_session_id")
    private SearchSession parentSession;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
