package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SummonerAssembler {

    public Summoner assemble(
            AccountDto accountDto,
            Set<LeagueEntryDto> leagueEntryDtos,
            SummonerDto summonerDto,
            Platform platform) {

        Set<LeagueInfo> leagueInfos = leagueEntryDtos.stream()
                .map(dto -> LeagueInfo.builder()
                        .leagueId(dto.getLeagueId())
                        .queueType(dto.getQueueType())
                        .tier(dto.getTier())
                        .rank(dto.getRank())
                        .leaguePoints(dto.getLeaguePoints())
                        .wins(dto.getWins())
                        .losses(dto.getLosses())
                        .hotStreak(dto.isHotStreak())
                        .veteran(dto.isVeteran())
                        .freshBlood(dto.isFreshBlood())
                        .inactive(dto.isInactive())
                        .build())
                .collect(Collectors.toSet());

        return Summoner.create(
                accountDto.getPuuid(),
                accountDto.getGameName(),
                accountDto.getTagLine(),
                platform.getPlatformId(),
                summonerDto.getProfileIconId(),
                summonerDto.getSummonerLevel(),
                summonerDto.getRevisionDate(),
                leagueInfos
        );
    }
}
