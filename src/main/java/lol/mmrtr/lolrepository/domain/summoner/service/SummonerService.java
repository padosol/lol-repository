package lol.mmrtr.lolrepository.domain.summoner.service;

import lol.mmrtr.lolrepository.domain.league.entity.League;
import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummonerDetail;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueSummonerDetailJpaRepository;
import lol.mmrtr.lolrepository.controller.dto.response.SummonerResponse;
import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueSummonerRepository;
import lol.mmrtr.lolrepository.domain.summoner.repository.SummonerRepository;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lol.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lol.mmrtr.lolrepository.support.error.CoreException;
import lol.mmrtr.lolrepository.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final LeagueSummonerDetailJpaRepository leagueSummonerDetailJpaRepository;
    private final LeagueSummonerRepository leagueSummonerRepository;
    private final SummonerRepository summonerRepository;
    private final LeagueRepository leagueRepository;

    /**
     * 유저 조회
     * RIOT API 를 통해 SummonerDTO, AccountDTO 에 대한 정보를 가져온다.
     * RIOT API 를 통해 LeagueDTO 정보를 가져온다.
     * 최조 조회시 revisionDate 는 최소값이 세팅된다.
     * @param region   지역
     * @param gameName 유저명
     * @param tagLine  유저 태그
     * @return 유저 정보
     */
    @Transactional
    public SummonerResponse getSummonerInfo(String region, String gameName, String tagLine) {
        Platform platform = Platform.valueOfName(region);

        AccountDto accountDto = RiotAPI
                .account(platform)
                .byRiotId(gameName, tagLine);
        if(accountDto.isError()) {
            log.info("존재하지 않는 유저 입니다. [유저명: {}, 태그: {}]", gameName, tagLine);
            throw new CoreException(ErrorType.NOT_FOUND_USER, "존재하지 않는 유저 입니다.");
        }

        String puuid = accountDto.getPuuid();

        SummonerDTO summonerDTO = RiotAPI.summoner(platform).byPuuid(puuid);
        Set<LeagueEntryDTO> leagueEntryDTOS = RiotAPI.league(platform).byPuuid(puuid);

        Summoner summoner = new Summoner(accountDto, summonerDTO, platform);

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

                league = leagueRepository.save(newLeague);
            }

            LeagueSummoner leagueSummoner = leagueSummonerRepository.findBy(puuid, league.getLeagueId());
            if (leagueSummoner == null) {
                leagueSummoner = leagueSummonerRepository.save(LeagueSummoner.builder()
                        .puuid(puuid)
                        .leagueId(league.getLeagueId())
                        .build());
            }

            LeagueSummonerDetail leagueSummonerDetail = LeagueSummonerDetail.builder()
                    .leagueSummonerId(leagueSummoner.getId())
                    .leaguePoints(leagueEntryDTO.getLeaguePoints())
                    .rank(leagueEntryDTO.getRank())
                    .wins(leagueEntryDTO.getWins())
                    .losses(leagueEntryDTO.getLosses())
                    .veteran(leagueEntryDTO.isVeteran())
                    .inactive(leagueEntryDTO.isInactive())
                    .freshBlood(leagueEntryDTO.isFreshBlood())
                    .hotStreak(leagueEntryDTO.isHotStreak())
                    .build();

            leagueSummonerDetailJpaRepository.save(leagueSummonerDetail);
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
