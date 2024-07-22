package lol.mmrtr.lolrepository.dto.match_timeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ParticipantFramesDto {

    @JsonProperty(value = "1")
    private ParticipantFrameDto participantFrame1;
    @JsonProperty(value = "2")
    private ParticipantFrameDto participantFrame2;
    @JsonProperty(value = "3")
    private ParticipantFrameDto participantFrame3;
    @JsonProperty(value = "4")
    private ParticipantFrameDto participantFrame4;
    @JsonProperty(value = "5")
    private ParticipantFrameDto participantFrame5;
    @JsonProperty(value = "6")
    private ParticipantFrameDto participantFrame6;
    @JsonProperty(value = "7")
    private ParticipantFrameDto participantFrame7;
    @JsonProperty(value = "8")
    private ParticipantFrameDto participantFrame8;
    @JsonProperty(value = "9")
    private ParticipantFrameDto participantFrame9;
    @JsonProperty(value = "10")
    private ParticipantFrameDto participantFrame10;

    public List<ParticipantFrameDto> getList() {
        return List.of(
            this.participantFrame1,
            this.participantFrame2,
            this.participantFrame3,
            this.participantFrame4,
            this.participantFrame5,
            this.participantFrame6,
            this.participantFrame7,
            this.participantFrame8,
            this.participantFrame9,
            this.participantFrame10
        );
    }

}
