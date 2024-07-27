package lol.mmrtr.lolrepository.entity;

import lol.mmrtr.lolrepository.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimeLineEvent {

    private String matchId;
    private int timestamp;

    public TimeLineEvent(){};

    public TimeLineEvent(String matchId, int timestamp) {
        this.matchId = matchId;
        this.timestamp = timestamp;
    }



}
