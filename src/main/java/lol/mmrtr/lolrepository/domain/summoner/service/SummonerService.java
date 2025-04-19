package lol.mmrtr.lolrepository.domain.summoner.service;

import lol.mmrtr.lolrepository.domain.league.entity.League;
import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.summoner.dto.response.SummonerResponse;
import lol.mmrtr.lolrepository.entity.Summoner;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import lol.mmrtr.lolrepository.repository.LeagueSummonerRepository;
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

    private final LeagueSummonerRepository leagueSummonerRepository;
    private final SummonerRepository summonerRepository;
    private final LeagueRepository leagueRepository;

    @Transactional
    public SummonerResponse getSummonerInfo(String region, String gameName, String tagLine) {
        LocalDateTime now = LocalDateTime.now();

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
            String leagueId = leagueEntryDTO.getLeagueId();
            League league = leagueRepository.findById(leagueId);
            if (league == null) {
                League newLeague = League.builder()
                        .leagueId(leagueEntryDTO.getLeagueId())
                        .queue(leagueEntryDTO.getQueueType())
                        .tier(leagueEntryDTO.getTier())
                        .build();

                leagueRepository.save(newLeague);
            }
            LeagueSummoner leagueSummoner = LeagueSummoner.builder()
                    .puuid(summoner.getPuuid())
                    .leagueId(leagueEntryDTO.getLeagueId())
                    .createAt(now)
                    .leaguePoints(leagueEntryDTO.getLeaguePoints())
                    .rank(leagueEntryDTO.getRank())
                    .wins(leagueEntryDTO.getWins())
                    .losses(leagueEntryDTO.getLosses())
                    .veteran(leagueEntryDTO.isVeteran())
                    .inactive(leagueEntryDTO.isInactive())
                    .freshBlood(leagueEntryDTO.isFreshBlood())
                    .hotStreak(leagueEntryDTO.isHotStreak())
                    .build();

            leagueSummonerRepository.save(leagueSummoner);
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
