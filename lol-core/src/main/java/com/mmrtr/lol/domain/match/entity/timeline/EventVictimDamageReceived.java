package com.mmrtr.lol.domain.match.entity.timeline;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
//@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventVictimDamageReceived {

    @Id
    private String matchId;

    @Id
    private Long timestamp;

    @Id
    private Long timeLineEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "matchId", referencedColumnName = "match_id"),
            @JoinColumn(name = "timestamp", referencedColumnName = "timestamp"),
            @JoinColumn(name = "timeLineEventId", referencedColumnName = "time_line_event_id")
    })
    private TimeLineEvent timeLineEvent;

    private boolean basic;
    private int magicDamage;
    private String name;
    private int participantId;
    private int physicalDamage;
    private String spellName;
    private int spellSlot;
    private int trueDamage;
    private String type;
}
