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
@Table(name = "champion_special_kill_event")
public class ChampionSpecialKillEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("킬러 ID")
    private int killerId;
    @Comment("킬 타입")
    private String killType;
    @Comment("멀티킬 수")
    private int multiKillLength;
    @Embedded
    private PositionValue position;
    @Comment("타임스탬프")
    private long timestamp;
}
