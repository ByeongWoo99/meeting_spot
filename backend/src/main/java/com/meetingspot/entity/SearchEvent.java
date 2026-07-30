package com.meetingspot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "search_event")
@Check(constraints = "event_type IN ('PLACE_NAVIGATED','PLACE_DETAILED','RESULT_SHARED')")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SearchSession session;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_value", columnDefinition = "jsonb")
    private String eventValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
