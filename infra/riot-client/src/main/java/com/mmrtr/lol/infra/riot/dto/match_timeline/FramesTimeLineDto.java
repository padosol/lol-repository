package com.mmrtr.lol.infra.riot.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FramesTimeLineDto {
    private List<EventsTimeLineDto> events;
    private ParticipantFramesDto participantFrames;
    private int timestamp;
}
