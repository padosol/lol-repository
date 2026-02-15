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
@Table(name = "champion_special_kill_event")
public class ChampionSpecialKillEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 특수 킬 이벤트 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    @Comment("킬 타입")
    private String killType;
    @Comment("킬러 ID")
    private int killerId;
    @Comment("멀티킬 수")
    private int multiKillLength;
    @Embedded
    private PositionValue positionValue;

    @Comment("타임스탬프")
    private long timestamp;
    @Comment("이벤트 타입")
    private String type;
}
