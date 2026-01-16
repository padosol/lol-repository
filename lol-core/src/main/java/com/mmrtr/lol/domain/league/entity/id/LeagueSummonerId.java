package com.mmrtr.lol.domain.league.entity.id;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
@EqualsAndHashCode
public class LeagueSummonerId implements Serializable {

    private String leagueId;
    private String puuid;
    private LocalDateTime createAt;

}

