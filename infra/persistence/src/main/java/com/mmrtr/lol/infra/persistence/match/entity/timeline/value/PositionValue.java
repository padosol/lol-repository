package com.mmrtr.lol.infra.persistence.match.entity.timeline.value;

import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PositionValue {
    @Comment("X 좌표")
    private int x;
    @Comment("Y 좌표")
    private int y;
}
