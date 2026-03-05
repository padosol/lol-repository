package com.mmrtr.lol.infra.persistence.match.entity.timeline.events;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "kill_event")
public class KillEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("킬러 ID")
    private int killerId;
    @Comment("피해자 ID")
    private int victimId;
    @Comment("어시스트 참가자 ID 목록")
    private String assistingParticipantIds;
    @Comment("현상금")
    private int bounty;
    @Comment("셧다운 현상금")
    private int shutdownBounty;
    @Comment("연속 킬 수")
    private int killStreakLength;

    @Embedded
    private PositionValue position;

    @Comment("피해자가 가한 피해 상세")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String victimDamageDealt;

    @Comment("피해자가 받은 피해 상세")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String victimDamageReceived;

    @Comment("타임스탬프")
    private long timestamp;

    @Comment("이벤트 인덱스")
    @Column(name = "event_index", nullable = false)
    private int eventIndex;
}
