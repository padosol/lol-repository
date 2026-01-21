package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.domain.league.domain.League;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "league")
public class LeagueEntity {

    @Id
    @Column(name = "league_id")
    private String leagueId;

    private String tier;
    private String name;

    private String queue;

    public static LeagueEntity fromDomain(League league) {
        return LeagueEntity.builder()
                .leagueId(league.getLeagueId())
                .queue(league.getQueue())
                .tier(league.getTier())
                .build();
    }

    public League toDomain() {
        return League.builder()
                .leagueId(this.leagueId)
                .queue(this.queue)
                .tier(this.tier)
                .build();
    }
}
