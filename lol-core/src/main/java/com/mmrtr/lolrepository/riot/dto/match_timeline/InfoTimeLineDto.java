package com.mmrtr.lolrepository.riot.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InfoTimeLineDto {

    private String endOfGameResult;
    private long frameInterval;
    private long gameId;
    private List<ParticipantTimeLineDto> participants;
    private List<FramesTimeLineDto> frames;
}
