package com.mmrtr.lol.domain.match.entity.timeline.events;

import com.mmrtr.lol.domain.match.entity.timeline.TimeLineEventEntity;
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
@Table(name = "item_events")
public class ItemEventsEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TimeLineEventEntity timeLineEvent;

    private int itemId;
    private int participantId;
    private long timestamp;
    private String type;

    private int afterId;
    private int beforeId;
    private int goldGain;
}
