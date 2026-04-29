package com.mmrtr.lol.infra.persistence.match.entity.timeline;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TimelineEventFrameId implements Serializable {

    private String matchId;
    private Long timestamp;
    private Integer eventIndex;
}
