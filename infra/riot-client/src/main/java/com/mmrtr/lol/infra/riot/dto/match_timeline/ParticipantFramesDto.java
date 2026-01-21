package com.mmrtr.lol.infra.riot.dto.match_timeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipantFramesDto {
    @JsonProperty("1")
    private ParticipantFrameDto p1;
    @JsonProperty("2")
    private ParticipantFrameDto p2;
    @JsonProperty("3")
    private ParticipantFrameDto p3;
    @JsonProperty("4")
    private ParticipantFrameDto p4;
    @JsonProperty("5")
    private ParticipantFrameDto p5;
    @JsonProperty("6")
    private ParticipantFrameDto p6;
    @JsonProperty("7")
    private ParticipantFrameDto p7;
    @JsonProperty("8")
    private ParticipantFrameDto p8;
    @JsonProperty("9")
    private ParticipantFrameDto p9;
    @JsonProperty("10")
    private ParticipantFrameDto p10;
}
