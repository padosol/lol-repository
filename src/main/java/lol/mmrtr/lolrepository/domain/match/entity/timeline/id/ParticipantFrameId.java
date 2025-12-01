package lol.mmrtr.lolrepository.domain.match.entity.timeline.id;

import jakarta.websocket.OnOpen;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
@AllArgsConstructor
public class ParticipantFrameId implements Serializable {

    private String match;
    private Long timestamp;
    private int participantId;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ParticipantFrameId participantFrameId = (ParticipantFrameId) obj;
        return Objects.equals(this.match, participantFrameId.match)
            && Objects.equals(this.timestamp, participantFrameId.timestamp)
            && Objects.equals(this.participantId, participantFrameId.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(match, timestamp, participantId);
    }
}
