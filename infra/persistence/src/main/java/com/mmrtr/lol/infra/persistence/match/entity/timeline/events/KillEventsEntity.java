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
@Table(name = "kill_events")
public class KillEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("킬 이벤트 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    @Comment("어시스트 참가자 ID 목록")
    private String assistingParticipantIds;
    @Comment("현상금")
    private int bounty;
    @Comment("연속 킬 수")
    private int killStreakLength;
    @Comment("킬러 ID")
    private int killerId;

    @Embedded
    private PositionValue position;

    @Comment("셧다운 현상금")
    private int shutdownBounty;
    @Comment("피해자 ID")
    private int victimId;

    @Comment("타임스탬프")
    private long timestamp;
    @Comment("이벤트 타입")
    private String type;
}
