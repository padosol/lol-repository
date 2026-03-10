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
@Table(name = "ward_event")
public class WardEventsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Comment("생성자 ID")
    private int creatorId;
    @Comment("와드 타입")
    private String wardType;
    @Comment("타임스탬프")
    private long timestamp;

    @Comment("이벤트 인덱스")
    @Column(name = "event_index", nullable = false)
    private int eventIndex;
}
