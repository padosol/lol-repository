package lol.mmrtr.lolrepository.domain.match.entity.timeline.events;

import jakarta.persistence.*;
import lol.mmrtr.lolrepository.domain.match.entity.timeline.TimeLineEvent;
import lol.mmrtr.lolrepository.domain.match.entity.timeline.value.PositionValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ChampionSpecialKillEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "building_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
            @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    })
    private TimeLineEvent timeLineEvent;

    private String killType;
    private int killerId;
    private int multiKillLength;
    @Embedded
    private PositionValue positionValue;

    private long timestamp;
    private String type;
}
