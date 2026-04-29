package com.mmrtr.lol.infra.persistence.match.entity.timeline;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "timeline_event_frame")
@IdClass(TimelineEventFrameId.class)
public class TimelineEventFrameEntity {

    @Id
    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Id
    @Comment("frame.timestamp (프레임 단위)")
    @Column(nullable = false)
    private Long timestamp;

    @Id
    @Comment("frame 내 event 순서 (0부터)")
    @Column(name = "event_index", nullable = false)
    private Integer eventIndex;

    @Comment("type 포함한 모든 이벤트 필드. data->>'type'으로 조회")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String data;
}
