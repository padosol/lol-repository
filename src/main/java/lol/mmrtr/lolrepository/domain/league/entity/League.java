package lol.mmrtr.lolrepository.domain.league.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lol.mmrtr.lolrepository.message.LeagueMessage;
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

    public League(LeagueMessage leagueMessage) {
        this.leagueId = leagueMessage.getLeagueId();
        this.tier = leagueMessage.getTier();
        this.name = leagueMessage.getName();
        this.queue = leagueMessage.getQueue();
    }
}