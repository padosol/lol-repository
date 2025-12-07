package com.mmrtr.lol.domain.match.entity.value.team;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class TeamBanValue {

    private	int champion1Id;
    private	int pick1Turn;

    private	int champion2Id;
    private	int pick2Turn;

    private	int champion3Id;
    private	int pick3Turn;

    private	int champion4Id;
    private	int pick4Turn;

    private	int champion5Id;
    private	int pick5Turn;

    public String test() {
        return "test";
    }

}
