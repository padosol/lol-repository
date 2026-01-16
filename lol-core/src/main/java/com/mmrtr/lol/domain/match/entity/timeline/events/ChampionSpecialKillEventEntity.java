package com.mmrtr.lol.domain.match.entity.timeline.events;

import com.mmrtr.lol.domain.match.entity.timeline.TimeLineEventEntity;
import com.mmrtr.lol.domain.match.entity.timeline.value.PositionValue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "champion_special_kill_event")
public class ChampionSpecialKillEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "champion_special_kill_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    private String killType;
    private int killerId;
    private int multiKillLength;
    @Embedded
    private PositionValue positionValue;

    private long timestamp;
    private String type;
}
