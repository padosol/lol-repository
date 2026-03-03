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
@Table(name = "skill_level_up_event")
public class SkillEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("참가자 ID")
    private int participantId;
    @Comment("스킬 슬롯")
    private int skillSlot;
    @Comment("레벨업 타입")
    private String levelUpType;
    @Comment("타임스탬프")
    private long timestamp;
}
