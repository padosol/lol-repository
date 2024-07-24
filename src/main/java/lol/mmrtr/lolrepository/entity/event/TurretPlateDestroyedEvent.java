package lol.mmrtr.lolrepository.entity.event;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurretPlateDestroyedEvent {

    private Long id;
    private Long timeLineEventId;

    private int killerId;
    private String laneType;

    private int x;
    private int y;

    private int teamId;
    private long timestamp;
    private String type;
}
