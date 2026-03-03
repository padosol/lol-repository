package com.mmrtr.lol.infra.persistence.match.entity.timeline.events;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "turret_plate_destroyed_event")
public class TurretPlateDestroyedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("킬러 ID")
    private int killerId;
    @Comment("라인 타입")
    private String laneType;
    @Embedded
    private PositionValue position;
    @Comment("팀 ID")
    private int teamId;
    @Comment("타임스탬프")
    private long timestamp;
    @Comment("이벤트 타입")
    private String type;
}
