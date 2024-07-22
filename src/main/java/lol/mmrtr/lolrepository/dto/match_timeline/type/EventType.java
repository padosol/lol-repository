package lol.mmrtr.lolrepository.dto.match_timeline.type;

import lombok.Getter;

@Getter
public enum EventType {

    ITEM_PURCHASED, ITEM_UNDO, ITEM_DESTROYED, ITEM_SOLD,
    SKILL_LEVEL_UP, LEVEL_UP,
    WARD_PLACED, WARD_KILL,
    ELITE_MONSTER_KILL, BUILDING_KILL,
    CHAMPION_SPECIAL_KILL, CHAMPION_KILL,
    TURRET_PLATE_DESTROYED,
    OBJECTIVE_BOUNTY_PRESTART,
    PAUSE_END, GAME_END
    ;

}
