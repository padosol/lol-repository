package com.mmrtr.lol.infra.persistence.match.entity.timeline.events;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.TimeLineEventEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "building_events")
public class BuildingEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("건물 이벤트 ID")
    @Column(name = "building_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    @Comment("어시스트 참가자 ID 목록")
    private String assistingParticipantIds;
    @Comment("현상금")
    private int bounty;
    @Comment("건물 타입")
    private String buildingType;
    @Comment("킬러 ID")
    private int killerId;
    @Comment("라인 타입")
    private String laneType;
    @Embedded
    private PositionValue positionValue;
    @Comment("팀 ID")
    private int teamId;
    @Comment("타임스탬프")
    private long timestamp;
    @Comment("타워 타입")
    private String towerType;
    @Comment("이벤트 타입")
    private String type;
}
