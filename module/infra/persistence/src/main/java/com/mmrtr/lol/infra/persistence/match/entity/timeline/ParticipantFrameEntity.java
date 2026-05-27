package com.mmrtr.lol.infra.persistence.match.entity.timeline;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.ChampionStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.DamageStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "participant_frame",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_participant_frame",
                        columnNames = {"match_id", "timestamp", "participant_id"}
                )
        }
)
public class ParticipantFrameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("타임스탬프")
    private int timestamp;

    @Comment("참가자 ID")
    private int participantId;

    @Embedded
    private ChampionStatsValue championStats;
    @Comment("현재 보유 골드")
    private int currentGold;
    @Embedded
    private DamageStatsValue damageStats;
    @Comment("초당 골드 획득량")
    private int goldPerSecond;
    @Comment("정글 몬스터 처치 수")
    private int jungleMinionsKilled;
    @Comment("레벨")
    private int level;
    @Comment("미니언 처치 수")
    private int minionsKilled;

    @Embedded
    private PositionValue position;
    @Comment("적 CC 적용 시간")
    private int timeEnemySpentControlled;
    @Comment("총 골드")
    private int totalGold;
    @Comment("경험치")
    private int xp;
}
