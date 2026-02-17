package com.mmrtr.lol.infra.persistence.match.entity.timeline.events;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.TimeLineEventEntity;
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
@Table(name = "skill_events")
public class SkillEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("스킬 이벤트 ID")
    @Column(name = "skill_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    @Comment("스킬 슬롯")
    private int skillSlot;
    @Comment("참가자 ID")
    private int participantId;
    @Comment("레벨업 타입")
    private String levelUpType;
    @Comment("타임스탬프")
    private long timestamp;
    @Comment("이벤트 타입")
    private String type;
}
