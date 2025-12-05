package com.mmrtr.lolrepository.domain.league.entity.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
@EqualsAndHashCode
public class LeagueSummonerId implements Serializable {

    @Column(name = "league_id")
    private String leagueId;
    @Column(name = "puuid")
    private String puuid;
    @CreatedDate
    @Column(name = "create_at")
    private LocalDateTime createAt;
}
