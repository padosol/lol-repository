package com.mmrtr.lol.domain.league.entity;

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
}
