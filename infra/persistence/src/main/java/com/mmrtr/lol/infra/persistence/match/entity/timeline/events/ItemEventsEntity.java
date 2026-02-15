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
@Table(name = "item_events")
public class ItemEventsEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("아이템 이벤트 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    @Comment("아이템 ID")
    private int itemId;
    @Comment("참가자 ID")
    private int participantId;
    @Comment("타임스탬프")
    private long timestamp;
    @Comment("이벤트 타입")
    private String type;

    @Comment("변환 후 아이템 ID")
    private int afterId;
    @Comment("변환 전 아이템 ID")
    private int beforeId;
    @Comment("획득 골드")
    private int goldGain;
}
