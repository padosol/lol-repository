package com.mmrtr.lol.infra.persistence.match.entity.timeline;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.events.ItemEventsEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.events.SkillEventsEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Comment;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "time_line_event")
public class TimeLineEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("타임스탬프")
    private int timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "match_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MatchEntity matchEntity;

    @BatchSize(size = 500)
    @OneToMany(mappedBy = "timeLineEvent")
    private List<ItemEventsEntity> itemEvents;

    @BatchSize(size = 500)
    @OneToMany(mappedBy = "timeLineEvent")
    private List<SkillEventsEntity> skillEvents;
}
