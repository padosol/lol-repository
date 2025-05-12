package lol.mmrtr.lolrepository.domain.match.entity.timeline.events;

import jakarta.persistence.*;
import lol.mmrtr.lolrepository.domain.match.entity.timeline.TimeLineEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ItemEvents {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "match_id", referencedColumnName = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
            @JoinColumn(name = "timeline_timestamp", referencedColumnName = "timestamp", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    })
    private TimeLineEvent timeLineEvent;

    private int itemId;
    private int participantId;
    private long timestamp;
    private String type;

    private int afterId;
    private int beforeId;
    private int goldGain;
}
