package com.mmrtr.lol.domain.match.readmodel.timeline;

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
