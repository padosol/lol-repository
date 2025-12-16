package com.mmrtr.lol.domain.league.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;


@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class League {

    @Id
    private String leagueId;
    private String tier;
    private String name;
    private String queue;

    public League(String leagueId, String tier, String queue) {
        this.leagueId = leagueId;
        this.tier = tier;
        this.queue = queue;
    }
}