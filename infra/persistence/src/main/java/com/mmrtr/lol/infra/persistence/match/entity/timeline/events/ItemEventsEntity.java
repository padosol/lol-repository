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
@Table(name = "item_event")
public class ItemEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("이벤트 타입")
    private String type;
    @Comment("아이템 ID")
    private int itemId;
    @Comment("참가자 ID")
    private int participantId;
    @Comment("타임스탬프")
    private long timestamp;

    @Comment("변환 후 아이템 ID")
    private int afterId;
    @Comment("변환 전 아이템 ID")
    private int beforeId;
    @Comment("획득 골드")
    private int goldGain;

    @Comment("이벤트 인덱스")
    @Column(name = "event_index", nullable = false)
    private int eventIndex;
}
