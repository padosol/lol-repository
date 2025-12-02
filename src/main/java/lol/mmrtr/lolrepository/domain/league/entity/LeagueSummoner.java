package lol.mmrtr.lolrepository.domain.league.entity;

import jakarta.persistence.*;
import lol.mmrtr.lolrepository.domain.league.entity.id.LeagueSummonerId;
import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lombok.*;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "league_summoner",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_puuid_and_league_id_and_queue",
                        columnNames = {"puuid", "league_id", "queue"}
                )
        }
)
public class LeagueSummoner {

    @Id
    @Column(name = "league_summoner_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String puuid;

    @Column(name = "league_id")
    private String leagueId;

    @Column(name = "queue")
    private String queue;

    public void changeLeague(League league) {
        this.leagueId = league.getLeagueId();
    }

}
