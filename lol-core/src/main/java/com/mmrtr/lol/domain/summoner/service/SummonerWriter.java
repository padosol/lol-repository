package com.mmrtr.lol.domain.summoner.service;

import com.mmrtr.lol.domain.league.entity.League;
import com.mmrtr.lol.domain.league.entity.LeagueSummoner;
import com.mmrtr.lol.domain.league.repository.LeagueRepository;
import com.mmrtr.lol.domain.league.repository.LeagueSummonerRepository;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.entity.SummonerEntity;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepository;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SummonerWriter {

    private final LeagueSummonerRepository leagueSummonerRepository;
    private final SummonerRepository summonerRepository;
    private final LeagueRepository leagueRepository;

    // DB 저장 로직을 별도 메서드로 분리
    @Transactional
    public void saveSummonerData(AccountDto accountDto, SummonerDto summonerDto, Set<LeagueEntryDto> leagueEntryDtos, Platform platform) {
        SummonerEntity summonerEntity = new SummonerEntity(accountDto, summonerDto, platform);
        summonerEntity.initRevisionDate();
        summonerRepository.save(summonerEntity);

        for (LeagueEntryDto leagueEntryDTO : leagueEntryDtos) {
            String leagueId = leagueEntryDTO.getLeagueId();
            League league = leagueRepository.findById(leagueId);
            if (league == null) {
                league = leagueRepository.save(League.builder()
                        .leagueId(leagueEntryDTO.getLeagueId())
                        .queue(leagueEntryDTO.getQueueType())
                        .tier(leagueEntryDTO.getTier())
                        .build());
            }

            LeagueSummoner savedLeagueSummoner = leagueSummonerRepository.findBy(accountDto.getPuuid(), league.getLeagueId());
            if (savedLeagueSummoner == null) {
                LeagueSummoner leagueSummoner = LeagueSummoner.of(accountDto.getPuuid(), league, leagueEntryDTO);
                leagueSummonerRepository.save(leagueSummoner);
            }
        }
    }

    @Transactional
    public void saveSummonerData(Summoner summoner) {
        SummonerEntity summonerEntity = SummonerEntity.of(summoner);
        summonerEntity.initRevisionDate();
        summonerRepository.save(summonerEntity);

        for (LeagueEntryDto leagueEntryDTO : summoner.getLeagueEntryDtos()) {
            String leagueId = leagueEntryDTO.getLeagueId();
            League league = leagueRepository.findById(leagueId);
            if (league == null) {
                league = leagueRepository.save(League.builder()
                        .leagueId(leagueEntryDTO.getLeagueId())
                        .queue(leagueEntryDTO.getQueueType())
                        .tier(leagueEntryDTO.getTier())
                        .build());
            }

            LeagueSummoner savedLeagueSummoner = leagueSummonerRepository.findBy(summoner.getPuuid(), league.getLeagueId());
            if (savedLeagueSummoner == null) {
                LeagueSummoner leagueSummoner = LeagueSummoner.of(summoner.getPuuid(), league, leagueEntryDTO);
                leagueSummonerRepository.save(leagueSummoner);
            }
        }
    }


}
