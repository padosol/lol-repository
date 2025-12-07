package com.mmrtr.lol.domain.entity;

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
