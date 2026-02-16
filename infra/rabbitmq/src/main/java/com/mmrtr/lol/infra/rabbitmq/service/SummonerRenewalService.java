package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.service.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.service.MatchService;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRenewalService {

    private static final int MATCH_FETCH_COUNT = 20;

    private final RabbitTemplate rabbitTemplate;
    private final RiotApiService riotApiService;
    private final MatchService matchService;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final Executor requestExecutor;

    public void renewSummoner(String puuid, Platform platform) {
        long start = System.currentTimeMillis();
        log.info("Lock 획득, 유저 전적 갱신 시작: {}", puuid);

        CompletableFuture<SummonerDto> summonerDtoFuture = riotApiService.getSummonerByPuuid(puuid, platform, requestExecutor);
        SummonerDto summonerDto = summonerDtoFuture.join();
        if (summonerDto == null) {
            log.error("RIOT API에서 소환사 정보를 조회할 수 없습니다. puuid: {}", puuid);
            return;
        }

        Optional<Summoner> existingSummoner = summonerRepositoryPort.findByPuuid(puuid);
        if (existingSummoner.isPresent()) {
            LocalDateTime riotRevisionDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(summonerDto.getRevisionDate()), ZoneId.systemDefault());
            if (existingSummoner.get().getRevisionInfo().revisionDate().equals(riotRevisionDate)) {
                log.info("revisionDate is same. No need to update.");
                return;
            }
        }

        long dbRevisionDateMillis = existingSummoner
                .map(s -> s.getRevisionInfo().revisionDate()
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .orElse(0L);

        CompletableFuture<AccountDto> accountDtoFuture = riotApiService
                .getAccountByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntryDtoFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<List<String>> matchIdListFuture = riotApiService.getMatchListByPuuid(
                puuid, platform, dbRevisionDateMillis, 0, MATCH_FETCH_COUNT, requestExecutor);

        CompletableFuture<List<String>> filteredMatchIdsFuture = matchIdListFuture.thenApply(matchIds -> {
            if (matchIds.size() == MATCH_FETCH_COUNT) {
                log.info("matchIds size is 20. send message for search more matchIds");
                rabbitTemplate.convertAndSend(
                        "renewal.topic.exchange",
                        "renewal.match.find",
                        new SummonerRenewalMessage(puuid, platform.getPlatformId(), dbRevisionDateMillis)
                );
            }

            List<MatchEntity> matchList = matchService.findAllMatch(matchIds);
            List<String> existMatchIds = matchList.stream().map(MatchEntity::getMatchId).toList();
            return matchIds.stream().filter(matchId -> !existMatchIds.contains(matchId)).toList();
        });

        CompletableFuture<List<MatchDto>> matchListFuture = filteredMatchIdsFuture.thenCompose(filteredMatchIds -> {
            List<CompletableFuture<MatchDto>> matchAllOfFuture = filteredMatchIds.stream()
                    .map(matchId -> riotApiService.getMatchById(matchId, platform, requestExecutor))
                    .toList();

            CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(matchAllOfFuture.toArray(new CompletableFuture[0]));

            return allOfFuture.thenApply(v -> matchAllOfFuture.stream()
                    .map(CompletableFuture::join)
                    .toList());
        });

        CompletableFuture<List<TimelineDto>> timelineListFuture = filteredMatchIdsFuture.thenCompose(filteredMatchIds -> {
            List<CompletableFuture<TimelineDto>> timelineAllOfFuture = filteredMatchIds.stream()
                    .map(matchId -> riotApiService.getTimelineById(matchId, platform, requestExecutor))
                    .toList();

            CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(timelineAllOfFuture.toArray(new CompletableFuture[0]));

            return allOfFuture.thenApply(v -> timelineAllOfFuture.stream()
                    .map(CompletableFuture::join)
                    .toList());
        });

        Summoner summoner = accountDtoFuture.thenCombine(
                leagueEntryDtoFuture,
                (accountDto, leagueEntryDtos) -> {
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
                }).join();

        List<MatchDto> matchDtos = matchListFuture.join();
        List<TimelineDto> timelineDtos = timelineListFuture.join();

        long middle = System.currentTimeMillis();
        log.info("middle {} ms", middle - start);

        summoner.resetClickDate();
        saveSummonerDataUseCase.execute(summoner);

        if (matchDtos != null && !matchDtos.isEmpty()) {
            matchService.addAllMatch(matchDtos, timelineDtos);
        }

        long end = System.currentTimeMillis();
        log.info("소요 시간: {}ms", end - start);
        log.info("Lock 해제, 유저 전적 갱신 완료: {}", puuid);
    }
}
