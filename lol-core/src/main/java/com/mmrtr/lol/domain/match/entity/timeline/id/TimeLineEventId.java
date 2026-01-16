package com.mmrtr.lol.domain.match.entity.timeline.id;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
@AllArgsConstructor
public class TimeLineEventId implements Serializable {

    private String matchEntity;
    private int timestamp;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TimeLineEventId timeLineEventId = (TimeLineEventId) obj;
        return Objects.equals(this.matchEntity, timeLineEventId.matchEntity)
                && Objects.equals(this.timestamp, timeLineEventId.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchEntity, timestamp);
    }
}
