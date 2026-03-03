package com.mmrtr.lol.infra.persistence.match.entity.timeline.events;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_end_event")
public class GameEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("게임 ID")
    private long gameId;
    @Comment("실제 타임스탬프")
    private long realTimestamp;
    @Comment("승리 팀")
    private int winningTeam;
    @Comment("타임스탬프")
    private long timestamp;
}
