package com.mmrtr.lol.domain.match.readmodel.timeline;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InfoTimeLineDto {
    private String endOfGameResult;
    private long frameInterval;
    private long gameId;
    private List<FramesTimeLineDto> frames;
    private List<ParticipantTimeLineDto> participants;
}
