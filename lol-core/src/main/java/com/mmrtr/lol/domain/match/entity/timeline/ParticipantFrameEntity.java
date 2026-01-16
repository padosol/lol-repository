package com.mmrtr.lol.domain.match.entity.timeline;

import com.mmrtr.lol.domain.match.entity.MatchEntity;
import com.mmrtr.lol.domain.match.entity.timeline.id.ParticipantFrameId;
import com.mmrtr.lol.domain.match.entity.timeline.value.ChampionStatsValue;
import com.mmrtr.lol.domain.match.entity.timeline.value.DamageStatsValue;
import com.mmrtr.lol.domain.match.entity.timeline.value.PositionValue;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ParticipantFrameId.class)
@Table(name = "participant_frame")
public class ParticipantFrameEntity {

    @Id
    private int timestamp;

    @Id
    private int participantId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchId", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MatchEntity matchEntity;

    @Embedded
    private ChampionStatsValue championStats;
    private int currentGold;
    @Embedded
    private DamageStatsValue damageStats;
    private int goldPerSecond;
    private int jungleMinionsKilled;
    private int level;
    private int minionsKilled;

    @Embedded
    private PositionValue position;
    private int timeEnemySpentControlled;
    private int totalGold;
    private int xp;
}
