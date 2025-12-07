package com.mmrtr.lol.domain.match.entity.value.team;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class TeamObjectValue {

    private	boolean baronFirst;
    private	int baronKills;

    private	boolean championFirst;
    private	int championKills;

    private	boolean dragonFirst;
    private	int dragonKills;

    private	boolean inhibitorFirst;
    private	int inhibitorKills;

    private	boolean riftHeraldFirst;
    private	int riftHeraldKills;

    private	boolean towerFirst;
    private	int towerKills;
}
