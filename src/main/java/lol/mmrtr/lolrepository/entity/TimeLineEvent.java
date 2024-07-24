package lol.mmrtr.lolrepository.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimeLineEvent {

    private Long id;
    private String matchId;
    private int timestamp;

    public TimeLineEvent(){};


}
