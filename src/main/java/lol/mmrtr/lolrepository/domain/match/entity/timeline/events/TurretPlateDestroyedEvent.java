package lol.mmrtr.lolrepository.domain.match.entity.timeline.events;

import lol.mmrtr.lolrepository.domain.match.entity.timeline.TimeLineEvent;
import jakarta.persistence.*;
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
public class TurretPlateDestroyedEvent {

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


    private int killerId;
    private String laneType;

    @Embedded
    private PositionValue positionValue;

    private int teamId;
    private long timestamp;
    private String type;
}
