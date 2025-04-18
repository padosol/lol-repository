package lol.mmrtr.lolrepository.domain.summoner.service;

import lol.mmrtr.lolrepository.domain.summoner.dto.response.SummonerResponse;
import lol.mmrtr.lolrepository.entity.League;
import lol.mmrtr.lolrepository.entity.Summoner;
import lol.mmrtr.lolrepository.repository.LeagueRepository;
import lol.mmrtr.lolrepository.repository.SummonerRepository;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lol.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SummonerService {

    private final SummonerRepository summonerRepository;
    private final LeagueRepository leagueRepository;

    @Transactional
    public SummonerResponse getSummonerInfo(String region, String gameName, String tagLine) {
        Platform platform = Platform.valueOfName(region);

        AccountDto accountDto = RiotAPI.account(platform).byRiotId(gameName, tagLine);
        String puuid = accountDto.getPuuid();

        SummonerDTO summonerDTO = RiotAPI.summoner(platform).byPuuid(puuid);
        Set<LeagueEntryDTO> leagueEntryDTOS = RiotAPI.league(platform).byPuuid(puuid);

        Summoner summoner = Summoner.builder()
                .summonerId(summonerDTO.getId())
                .accountId(summonerDTO.getAccountId())
                .puuid(summonerDTO.getPuuid())
                .profileIconId(summonerDTO.getProfileIconId())
                .revisionDate(summonerDTO.getRevisionDate())
                .summonerLevel(summonerDTO.getSummonerLevel())
                .gameName(accountDto.getGameName())
                .tagLine(accountDto.getTagLine())
                .region(region)
                .revisionClickDate(LocalDateTime.MIN)
                .build();

        summonerRepository.save(summoner);

        for (LeagueEntryDTO leagueEntryDTO : leagueEntryDTOS) {
        }

        return SummonerResponse.builder()
                .puuid(accountDto.getPuuid())
                .gameName(accountDto.getGameName())
                .tagLine(accountDto.getTagLine())
                .id(summonerDTO.getId())
                .accountId(summonerDTO.getAccountId())
                .profileIconId(summonerDTO.getProfileIconId())
                .revisionDate(summonerDTO.getRevisionDate())
                .summonerLevel(summonerDTO.getSummonerLevel())
                .leagueEntryDTOS(leagueEntryDTOS)
                .build();
    }

}
