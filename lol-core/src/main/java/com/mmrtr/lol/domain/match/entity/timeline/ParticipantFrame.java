package com.mmrtr.lol.domain.match.entity.timeline;

import jakarta.persistence.*;
import com.mmrtr.lol.domain.match.entity.Match;
import com.mmrtr.lol.domain.match.entity.timeline.id.ParticipantFrameId;
import com.mmrtr.lol.domain.match.entity.timeline.value.ChampionStatsValue;
import com.mmrtr.lol.domain.match.entity.timeline.value.DamageStatsValue;
import com.mmrtr.lol.domain.match.entity.timeline.value.PositionValue;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ParticipantFrameId.class)
public class ParticipantFrame {

    @Id
    private int timestamp;

    @Id
    private int participantId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchId", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Match match;

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
