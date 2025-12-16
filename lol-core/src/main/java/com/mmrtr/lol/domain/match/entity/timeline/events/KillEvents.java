package com.mmrtr.lol.domain.match.entity.timeline.events;

import com.mmrtr.lol.domain.match.entity.timeline.TimeLineEvent;
import jakarta.persistence.*;
import com.mmrtr.lol.domain.match.entity.timeline.value.PositionValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class KillEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kill_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
            @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    })
    private TimeLineEvent timeLineEvent;

    private String assistingParticipantIds;
    private int bounty;
    private int killStreakLength;
    private int killerId;

    @Embedded
    private PositionValue position;

    private int shutdownBounty;
    private int victimId;

    private long timestamp;
    private String type;
}
