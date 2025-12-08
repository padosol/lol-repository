package com.mmrtr.lol.domain.summoner.service;


import com.mmrtr.lol.domain.league.entity.League;
import com.mmrtr.lol.domain.league.entity.LeagueSummoner;
import com.mmrtr.lol.domain.league.repository.LeagueRepository;
import com.mmrtr.lol.domain.league.repository.LeagueSummonerRepository;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.Region;
import com.mmrtr.lol.domain.summoner.entity.SummonerEntity;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepository;
import com.mmrtr.lol.riot.core.api.RiotAPI;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDTO;
import com.mmrtr.lol.riot.dto.summoner.SummonerDTO;
import com.mmrtr.lol.riot.type.Platform;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final LeagueSummonerRepository leagueSummonerRepository;
    private final SummonerRepository summonerRepository;
    private final LeagueRepository leagueRepository;

    /**
     * 유저 조회
     * RIOT API 를 통해 SummonerDTO, AccountDTO 에 대한 정보를 가져온다.
     * RIOT API 를 통해 LeagueDTO 정보를 가져온다.
     * 최조 조회시 revisionDate 는 최소값이 세팅된다.
     * @param regionType   지역
     * @param gameName 유저명
     * @param tagLine  유저 태그
     * @return 유저 정보
     */
    @Transactional
    public Summoner getSummonerInfo(String regionType, String gameName, String tagLine) {
        Platform platform = Platform.valueOfName(regionType);
        Region region = Region.valueOf(regionType);

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

        SummonerEntity summonerEntity = new SummonerEntity(accountDto, summonerDTO, platform);
        summonerEntity.initRevisionDate();

        summonerRepository.save(summonerEntity);

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

            LeagueSummoner savedLeagueSummoner = leagueSummonerRepository.findBy(puuid, league.getLeagueId());
            if (savedLeagueSummoner == null) {
                LeagueSummoner leagueSummoner = LeagueSummoner.of(puuid, league, leagueEntryDTO);
                leagueSummonerRepository.save(leagueSummoner);
            }
        }

        return Summoner.of(accountDto, summonerDTO, region, leagueEntryDTOS);
    }

    public Summoner getSummonerInfoByPuuid(String regionType, String puuid) {
        Platform platform = Platform.valueOfName(regionType);
        Region region = Region.valueOf(regionType);

        AccountDto accountDto = RiotAPI
                .account(platform)
                .byPuuid(puuid);
        if(accountDto.isError()) {
            log.info("존재하지 않는 puuid 입니다. {}", puuid);
            throw new CoreException(ErrorType.NOT_FOUND_USER, "존재하지 않는 PUUID 입니다.");
        }

        SummonerDTO summonerDTO = RiotAPI.summoner(platform).byPuuid(puuid);
        Set<LeagueEntryDTO> leagueEntryDTOS = RiotAPI.league(platform).byPuuid(puuid);

        SummonerEntity summonerEntity = new SummonerEntity(accountDto, summonerDTO, platform);
        summonerEntity.initRevisionDate();

        summonerRepository.save(summonerEntity);

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

            LeagueSummoner leagueSummoner = leagueSummonerRepository.findAllByPuuid(puuid, league.getQueue());
            if (leagueSummoner == null) {
                leagueSummonerRepository.save(LeagueSummoner.builder()
                        .puuid(puuid)
                        .leagueId(league.getLeagueId())
                        .queue(league.getQueue())
                        .build());
            }
        }

        return Summoner.of(accountDto, summonerDTO, region, leagueEntryDTOS);
    }
}
