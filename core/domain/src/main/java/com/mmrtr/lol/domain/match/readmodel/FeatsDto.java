package com.mmrtr.lol.domain.match.readmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatsDto {
    @JsonProperty("EPIC_MONSTER_KILL")
    private FeatDto epicMonsterKill;
    @JsonProperty("FIRST_BLOOD")
    private FeatDto firstBlood;
    @JsonProperty("FIRST_TURRET")
    private FeatDto firstTurret;
}
