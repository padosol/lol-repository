package lol.mmrtr.lolrepository.domain.summoner.dto.response;

import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lombok.*;

import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SummonerResponse {
    private String puuid;
    private String gameName;
    private String tagLine;
    private String id;
    private String accountId;
    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;

    private Set<LeagueEntryDTO> leagueEntryDTOS;
}
